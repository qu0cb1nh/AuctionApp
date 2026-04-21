package net.auctionapp.server.managers;

import net.auctionapp.common.models.users.User;
import net.auctionapp.common.models.users.UserRole;
import net.auctionapp.server.exceptions.AuthenticationException;
import net.auctionapp.server.exceptions.NotFoundException;
import net.auctionapp.server.exceptions.ValidationException;
import org.mindrot.jbcrypt.BCrypt;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class UserManager {
    private static UserManager instance;

    private final ConcurrentMap<String, User> usersById = new ConcurrentHashMap<>();

    private UserManager() {
    }

    public static synchronized UserManager getInstance() {
        if (instance == null) {
            instance = new UserManager();
        }
        return instance;
    }

    public User registerUser(String username, String rawPassword) {
        String normalizedUsername = normalizeUsername(username);
        validateCredentials(normalizedUsername, rawPassword);
        return saveUser(
                normalizedUsername,
                username.trim(),
                BCrypt.hashpw(rawPassword, BCrypt.gensalt()),
                EnumSet.of(UserRole.SELLER, UserRole.BIDDER),
                false
        );
    }

    public User registerAdmin(String username, String rawPassword) {
        String normalizedUsername = normalizeUsername(username);
        validateCredentials(normalizedUsername, rawPassword);
        return saveUser(
                normalizedUsername,
                username.trim(),
                BCrypt.hashpw(rawPassword, BCrypt.gensalt()),
                EnumSet.of(UserRole.ADMIN, UserRole.SELLER, UserRole.BIDDER),
                false
        );
    }

    public User syncAccountFromDatabase(User user) {
        if (user == null
                || normalizeUsername(user.getUsername()).isEmpty()
                || user.getPasswordHash() == null
                || user.getPasswordHash().isBlank()) {
            throw new ValidationException("Database account data is invalid.");
        }
        return saveUser(user.getId(), user.getUsername().trim(), user.getPasswordHash(), user.getRoles(), true);
    }

    public User login(String username, String rawPassword) {
        String accountId = normalizeUsername(username);
        if (accountId.isEmpty() || rawPassword == null || rawPassword.isEmpty()) {
            throw new AuthenticationException("Username and password are required.");
        }

        User user = usersById.get(accountId);
        if (user == null || !BCrypt.checkpw(rawPassword, user.getPasswordHash())) {
            throw new AuthenticationException("Invalid username or password.");
        }

        return user;
    }

    public Optional<User> getUserById(String userId) {
        return Optional.ofNullable(usersById.get(userId));
    }

    public User requireUserById(String userId) {
        User user = usersById.get(userId);
        if (user == null) {
            throw new NotFoundException("User not found.");
        }
        return user;
    }

    public Optional<User> getUserByUsername(String username) {
        return Optional.ofNullable(usersById.get(normalizeUsername(username)));
    }

    public User requireSeller(String userId) {
        User user = requireUserById(userId);
        if (!user.hasRole(UserRole.SELLER)) {
            throw new ValidationException("This account cannot act as a seller.");
        }
        return user;
    }

    public User requireBidder(String userId) {
        User user = requireUserById(userId);
        if (!user.hasRole(UserRole.BIDDER)) {
            throw new ValidationException("This account cannot act as a bidder.");
        }
        return user;
    }

    public boolean isAdmin(String userId) {
        return requireUserById(userId).hasRole(UserRole.ADMIN);
    }

    public void clear() {
        usersById.clear();
    }

    private void validateCredentials(String normalizedUsername, String rawPassword) {
        if (normalizedUsername.isEmpty()) {
            throw new ValidationException("Username is required.");
        }
        if (rawPassword == null || rawPassword.length() < 6) {
            throw new ValidationException("Password must be at least 6 characters.");
        }
    }

    private String normalizeUsername(String username) {
        if (username == null) {
            return "";
        }
        return username.trim().toLowerCase(Locale.ROOT);
    }

    private User saveUser(
            String userId,
            String username,
            String passwordHash,
            java.util.Set<UserRole> roles,
            boolean overwriteExisting
    ) {
        User user = new User(userId, username, passwordHash, roles);

        if (!overwriteExisting && usersById.containsKey(userId)) {
            throw new ValidationException("Username already exists.");
        }

        usersById.put(userId, user);
        return user;
    }
}
