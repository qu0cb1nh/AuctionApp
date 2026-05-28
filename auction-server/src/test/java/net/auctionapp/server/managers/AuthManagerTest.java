package net.auctionapp.server.managers;

import net.auctionapp.common.messages.MessageType;
import net.auctionapp.common.messages.auth.LoginRequestMessage;
import net.auctionapp.common.messages.auth.LoginResponseMessage;
import net.auctionapp.common.users.UserRole;
import net.auctionapp.server.ClientHandler;
import net.auctionapp.server.dao.UserDao;
import net.auctionapp.server.models.users.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mindrot.jbcrypt.BCrypt;
import org.mockito.ArgumentCaptor;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class AuthManagerTest {
    private AuthManager authManager;
    private UserDao mockUserDao;
    private ClientHandler mockClientHandler;

    @BeforeEach
    void setUp() {
        authManager = AuthManager.getInstance();
        mockUserDao = mock(UserDao.class);
        authManager.setUserDao(mockUserDao);
        mockClientHandler = mock(ClientHandler.class);
    }

    @Test
    void handleLogin_Success_HappyCase() {
        String username = "validUser";
        String password = "password123";
        String passwordHash = BCrypt.hashpw(password, BCrypt.gensalt());
        User user = new User("uuid-1", username, passwordHash, UserRole.USER, false);

        LoginRequestMessage request = new LoginRequestMessage(username, password);
        when(mockUserDao.findByUsername(anyString())).thenReturn(Optional.of(user));

        authManager.handleLogin(request, mockClientHandler);

        ArgumentCaptor<LoginResponseMessage> responseCaptor = ArgumentCaptor.forClass(LoginResponseMessage.class);
        verify(mockClientHandler).sendResponse(responseCaptor.capture(), eq(request));
        
        LoginResponseMessage response = responseCaptor.getValue();
        assertEquals(MessageType.LOGIN_SUCCESS, response.getType());
        verify(mockClientHandler).authenticate(eq("uuid-1"), eq(UserRole.USER));
    }

    @Test
    void handleLogin_AccountBanned_CriticalCase() {
        String username = "bannedUser";
        String password = "password123";
        String passwordHash = BCrypt.hashpw(password, BCrypt.gensalt());
        User user = new User("uuid-2", username, passwordHash, UserRole.USER, true); // Banned

        LoginRequestMessage request = new LoginRequestMessage(username, password);
        when(mockUserDao.findByUsername(anyString())).thenReturn(Optional.of(user));

        authManager.handleLogin(request, mockClientHandler);

        ArgumentCaptor<LoginResponseMessage> responseCaptor = ArgumentCaptor.forClass(LoginResponseMessage.class);
        verify(mockClientHandler).sendResponse(responseCaptor.capture(), eq(request));
        assertEquals("This account is banned.", responseCaptor.getValue().getMessage());
    }
}