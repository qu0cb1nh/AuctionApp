package net.auctionapp.server.managers;

import net.auctionapp.common.models.users.User;
import net.auctionapp.common.models.users.UserRole;
import net.auctionapp.common.utils.UserIdentityUtil;
import net.auctionapp.server.exceptions.AuthenticationException;
import net.auctionapp.server.exceptions.NotFoundException;
import net.auctionapp.server.exceptions.ValidationException;
import org.mindrot.jbcrypt.BCrypt;

import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
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
        return registerAccount(username, rawPassword, EnumSet.of(UserRole.SELLER, UserRole.BIDDER));
    }

    public User registerAdmin(String username, String rawPassword) {
        return registerAccount(username, rawPassword, EnumSet.of(UserRole.ADMIN, UserRole.SELLER, UserRole.BIDDER));
    }

    public User syncAccountFromDatabase(User user) {
        String normalizedUserId = user == null
                ? ""
                : UserIdentityUtil.normalizeUserId(user.getId());
        if (normalizedUserId.isEmpty() && user != null) {
            normalizedUserId = UserIdentityUtil.normalizeUserId(user.getUsername());
        }
        if (user == null
                || normalizedUserId.isEmpty()
                || user.getPasswordHash() == null
                || user.getPasswordHash().isBlank()) {
            throw new ValidationException("Database account data is invalid.");
        }
        return saveUser(
                normalizedUserId,
                user.getUsername().trim(),
                user.getPasswordHash(),
                user.getRoles(),
                true
        );
    }

    public User login(String username, String rawPassword) {
        String accountId = UserIdentityUtil.normalizeUserId(username);
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
        return Optional.ofNullable(usersById.get(UserIdentityUtil.normalizeUserId(userId)));
    }

    public User requireUserById(String userId) {
        User user = usersById.get(UserIdentityUtil.normalizeUserId(userId));
        if (user == null) {
            throw new NotFoundException("User not found.");
        }
        return user;
    }

    public Optional<User> getUserByUsername(String username) {
        return Optional.ofNullable(usersById.get(UserIdentityUtil.normalizeUserId(username)));
    }

    public User requireSeller(String userId) {
        return requireRole(userId, UserRole.SELLER, "This account cannot act as a seller.");
    }

    public User requireBidder(String userId) {
        return requireRole(userId, UserRole.BIDDER, "This account cannot act as a bidder.");
    }

    public User requireRole(String userId, UserRole role, String denialMessage) {
        User user = requireUserById(userId);
        if (!user.hasRole(role)) {
            throw new ValidationException(denialMessage);
        }
        return user;
    }

    public boolean isAdmin(String userId) {
        return requireUserById(userId).hasRole(UserRole.ADMIN);
    }

    public void clear() {
        usersById.clear();
    }

    private User registerAccount(String username, String rawPassword, Set<UserRole> roles) {
        String normalizedUsername = UserIdentityUtil.normalizeUserId(username);
        validateCredentials(normalizedUsername, rawPassword);
        String displayName = username == null ? "" : username.trim();
        String passwordHash = BCrypt.hashpw(rawPassword, BCrypt.gensalt());
        return saveUser(
                normalizedUsername,
                displayName,
                passwordHash,
                roles,
                false
        );
    }

    private void validateCredentials(String normalizedUsername, String rawPassword) {
        if (normalizedUsername.isEmpty()) {
            throw new ValidationException("Username is required.");
        }
        if (rawPassword == null || rawPassword.length() < 6) {
            throw new ValidationException("Password must be at least 6 characters.");
        }
    }

    private User saveUser(
            String userId,
            String username,
            String passwordHash,
            java.util.Set<UserRole> roles,
            boolean overwriteExisting
    ) {
        User user = new User(userId, username, passwordHash, roles);

        if (!overwriteExisting) {
            User existing = usersById.putIfAbsent(userId, user);
            if (existing != null) {
                throw new ValidationException("Username already exists.");
            }
            return user;
        }

        usersById.put(userId, user);
        return user;
    }
}
