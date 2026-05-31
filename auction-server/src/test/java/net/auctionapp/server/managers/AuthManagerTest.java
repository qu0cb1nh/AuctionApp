package net.auctionapp.server.managers;

import net.auctionapp.common.messages.auth.LoginRequestMessage;
import net.auctionapp.common.messages.auth.LoginResponseMessage;
import net.auctionapp.common.messages.auth.RegisterRequestMessage;
import net.auctionapp.common.messages.auth.RegisterResponseMessage;
import net.auctionapp.server.models.users.User;
import net.auctionapp.common.users.UserRole;
import net.auctionapp.server.ClientHandler;
import net.auctionapp.server.dao.UserDao;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mindrot.jbcrypt.BCrypt;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuthManagerTest {

    private AuthManager authManager;

    @Mock
    private UserDao mockUserDao;
    @Mock
    private ClientHandler mockClientHandler;

    private AutoCloseable closeable;

    @BeforeEach
    void setUp() {
        closeable = MockitoAnnotations.openMocks(this);
        authManager = AuthManager.getInstance();
        authManager.setUserDao(mockUserDao);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (closeable != null) {
            closeable.close();
        }
    }

    @Test
    void registerUserSuccessfully() {
        String username = "testuser";
        String password = "password123";
        RegisterRequestMessage request = new RegisterRequestMessage(username, password);

        when(mockUserDao.findByUsername(anyString())).thenReturn(Optional.empty());
        when(mockUserDao.createUser(any(User.class))).thenReturn(true);

        authManager.handleRegister(request, mockClientHandler);

        verify(mockUserDao, times(1)).findByUsername(username);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(mockUserDao, times(1)).createUser(userCaptor.capture());

        User createdUser = userCaptor.getValue();
        assertEquals(username, createdUser.getUsername());
        assertTrue(BCrypt.checkpw(password, createdUser.getPasswordHash()));
        assertEquals(UserRole.USER, createdUser.getRole());
        assertFalse(createdUser.isBanned());

        ArgumentCaptor<RegisterResponseMessage> responseCaptor = ArgumentCaptor.forClass(RegisterResponseMessage.class);
        verify(mockClientHandler, times(1)).sendResponse(responseCaptor.capture(), eq(request));
        assertEquals("Registration successful. Redirecting...", responseCaptor.getValue().getMessage());
    }

    @Test
    void loginUserSuccessfully() {
        String userId = UUID.randomUUID().toString();
        String username = "existinguser";
        String password = "password123";
        String hashedPassword = BCrypt.hashpw(password, BCrypt.gensalt());
        User user = new User(userId, username, hashedPassword, UserRole.USER, false);
        LoginRequestMessage request = new LoginRequestMessage(username, password);

        when(mockUserDao.findByUsername(username)).thenReturn(Optional.of(user));

        authManager.handleLogin(request, mockClientHandler);

        verify(mockUserDao, times(1)).findByUsername(username);
        verify(mockClientHandler, times(1)).authenticate(userId, UserRole.USER);

        ArgumentCaptor<LoginResponseMessage> responseCaptor = ArgumentCaptor.forClass(LoginResponseMessage.class);
        verify(mockClientHandler, times(1)).sendResponse(responseCaptor.capture(), eq(request));
        assertEquals("Login successful.", responseCaptor.getValue().getMessage());
        assertEquals(userId, responseCaptor.getValue().getUserId());
        assertEquals(username, responseCaptor.getValue().getUsername());
        assertEquals(UserRole.USER, responseCaptor.getValue().getRole());
    }

    @Test
    void registerWithExistingUsername() {
        String username = "existinguser";
        String password = "password123";
        RegisterRequestMessage request = new RegisterRequestMessage(username, password);
        User existingUser = new User(UUID.randomUUID().toString(), username, "hash", UserRole.USER, false);

        when(mockUserDao.findByUsername(username)).thenReturn(Optional.of(existingUser));

        authManager.handleRegister(request, mockClientHandler);

        verify(mockUserDao, times(1)).findByUsername(username);
        verify(mockUserDao, never()).createUser(any(User.class));

        ArgumentCaptor<RegisterResponseMessage> responseCaptor = ArgumentCaptor.forClass(RegisterResponseMessage.class);
        verify(mockClientHandler, times(1)).sendResponse(responseCaptor.capture(), eq(request));
        assertEquals("Username already exists.", responseCaptor.getValue().getMessage());
    }

    @Test
    void loginWithInvalidPassword() {
        String userId = UUID.randomUUID().toString();
        String username = "existinguser";
        String correctPassword = "password123";
        String wrongPassword = "wrongpassword";
        String hashedPassword = BCrypt.hashpw(correctPassword, BCrypt.gensalt());
        User user = new User(userId, username, hashedPassword, UserRole.USER, false);
        LoginRequestMessage request = new LoginRequestMessage(username, wrongPassword);

        when(mockUserDao.findByUsername(username)).thenReturn(Optional.of(user));

        authManager.handleLogin(request, mockClientHandler);

        verify(mockUserDao, times(1)).findByUsername(username);
        verify(mockClientHandler, never()).authenticate(anyString(), any(UserRole.class));

        ArgumentCaptor<LoginResponseMessage> responseCaptor = ArgumentCaptor.forClass(LoginResponseMessage.class);
        verify(mockClientHandler, times(1)).sendResponse(responseCaptor.capture(), eq(request));
        assertEquals("Invalid username or password.", responseCaptor.getValue().getMessage());
    }

    @Test
    void loginWithNonExistentUsername() {
        String username = "nonexistentuser";
        String password = "password123";
        LoginRequestMessage request = new LoginRequestMessage(username, password);

        when(mockUserDao.findByUsername(username)).thenReturn(Optional.empty());

        authManager.handleLogin(request, mockClientHandler);

        verify(mockUserDao, times(1)).findByUsername(username);
        verify(mockClientHandler, never()).authenticate(anyString(), any(UserRole.class));

        ArgumentCaptor<LoginResponseMessage> responseCaptor = ArgumentCaptor.forClass(LoginResponseMessage.class);
        verify(mockClientHandler, times(1)).sendResponse(responseCaptor.capture(), eq(request));
        assertEquals("Invalid username or password.", responseCaptor.getValue().getMessage());
    }

    @Test
    void loginWithBannedUser() {
        String userId = UUID.randomUUID().toString();
        String username = "banneduser";
        String password = "password123";
        String hashedPassword = BCrypt.hashpw(password, BCrypt.gensalt());
        User bannedUser = new User(userId, username, hashedPassword, UserRole.USER, true);
        LoginRequestMessage request = new LoginRequestMessage(username, password);

        when(mockUserDao.findByUsername(username)).thenReturn(Optional.of(bannedUser));

        authManager.handleLogin(request, mockClientHandler);

        verify(mockUserDao, times(1)).findByUsername(username);
        verify(mockClientHandler, never()).authenticate(anyString(), any(UserRole.class));

        ArgumentCaptor<LoginResponseMessage> responseCaptor = ArgumentCaptor.forClass(LoginResponseMessage.class);
        verify(mockClientHandler, times(1)).sendResponse(responseCaptor.capture(), eq(request));
        assertEquals("This account is banned.", responseCaptor.getValue().getMessage());
    }

    @Test
    void registerWithInvalidUsernameFormat() {
        String invalidUsername = "user name";
        String password = "password123";
        RegisterRequestMessage request = new RegisterRequestMessage(invalidUsername, password);

        authManager.handleRegister(request, mockClientHandler);

        verify(mockUserDao, never()).findByUsername(anyString());
        verify(mockUserDao, never()).createUser(any(User.class));

        ArgumentCaptor<RegisterResponseMessage> responseCaptor = ArgumentCaptor.forClass(RegisterResponseMessage.class);
        verify(mockClientHandler, times(1)).sendResponse(responseCaptor.capture(), eq(request));
        assertEquals("Username cannot contain invalid characters.", responseCaptor.getValue().getMessage());
    }

    @Test
    void loginWithEmptyCredentials() {
        LoginRequestMessage requestEmptyUsername = new LoginRequestMessage("", "password123");
        LoginRequestMessage requestEmptyPassword = new LoginRequestMessage("testuser", "");

        authManager.handleLogin(requestEmptyUsername, mockClientHandler);
        ArgumentCaptor<LoginResponseMessage> responseCaptor1 = ArgumentCaptor.forClass(LoginResponseMessage.class);
        verify(mockClientHandler, times(1)).sendResponse(responseCaptor1.capture(), eq(requestEmptyUsername));


        assertEquals("Username and password are required.", responseCaptor1.getValue().getMessage());

        reset(mockClientHandler);

        authManager.handleLogin(requestEmptyPassword, mockClientHandler);
        ArgumentCaptor<LoginResponseMessage> responseCaptor2 = ArgumentCaptor.forClass(LoginResponseMessage.class);
        verify(mockClientHandler, times(1)).sendResponse(responseCaptor2.capture(), eq(requestEmptyPassword));


        assertEquals("Username and password are required.", responseCaptor2.getValue().getMessage());

        verify(mockUserDao, never()).findByUsername(anyString());
    }
}