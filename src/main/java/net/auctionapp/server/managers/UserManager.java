package net.auctionapp.server.managers;

import net.auctionapp.common.models.users.Admin;
import net.auctionapp.common.models.users.Bidder;
import net.auctionapp.common.models.users.Seller;
import net.auctionapp.server.exceptions.AuthenticationException;
import net.auctionapp.server.exceptions.NotFoundException;
import net.auctionapp.server.exceptions.ValidationException;
import org.mindrot.jbcrypt.BCrypt;

import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class UserManager {
    private static UserManager instance;

    private final ConcurrentMap<String, AccountRecord> accountsById = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Seller> sellerProfilesById = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Bidder> bidderProfilesById = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Admin> adminProfilesById = new ConcurrentHashMap<>();

    private UserManager() {
    }

    public static synchronized UserManager getInstance() {
        if (instance == null) {
            instance = new UserManager();
        }
        return instance;
    }

    public AccountRecord registerUser(String username, String rawPassword) {
        String normalizedUsername = normalizeUsername(username);
        validateCredentials(normalizedUsername, rawPassword);
        return saveAccount(normalizedUsername, username.trim(), BCrypt.hashpw(rawPassword, BCrypt.gensalt()), false, false);
    }

    public AccountRecord registerAdmin(String username, String rawPassword) {
        String normalizedUsername = normalizeUsername(username);
        validateCredentials(normalizedUsername, rawPassword);
        return saveAccount(normalizedUsername, username.trim(), BCrypt.hashpw(rawPassword, BCrypt.gensalt()), true, false);
    }

    public AccountRecord syncAccountFromDatabase(String username, String passwordHash, String databaseRole) {
        String normalizedUsername = normalizeUsername(username);
        if (normalizedUsername.isEmpty() || passwordHash == null || passwordHash.isBlank()) {
            throw new ValidationException("Database account data is invalid.");
        }

        boolean admin = "admin".equalsIgnoreCase(databaseRole);
        return saveAccount(normalizedUsername, username.trim(), passwordHash, admin, true);
    }

    public AccountRecord login(String username, String rawPassword) {
        String accountId = normalizeUsername(username);
        if (accountId.isEmpty() || rawPassword == null || rawPassword.isEmpty()) {
            throw new AuthenticationException("Username and password are required.");
        }

        AccountRecord account = accountsById.get(accountId);
        if (account == null || !BCrypt.checkpw(rawPassword, account.passwordHash())) {
            throw new AuthenticationException("Invalid username or password.");
        }

        return account;
    }

    public Optional<AccountRecord> getAccountById(String userId) {
        return Optional.ofNullable(accountsById.get(userId));
    }

    public AccountRecord requireAccountById(String userId) {
        AccountRecord account = accountsById.get(userId);
        if (account == null) {
            throw new NotFoundException("User not found.");
        }
        return account;
    }

    public Optional<AccountRecord> getAccountByUsername(String username) {
        return Optional.ofNullable(accountsById.get(normalizeUsername(username)));
    }

    public Seller requireSellerProfile(String userId) {
        Seller seller = sellerProfilesById.get(userId);
        if (seller == null) {
            throw new ValidationException("This account cannot act as a seller.");
        }
        return seller;
    }

    public Bidder requireBidderProfile(String userId) {
        Bidder bidder = bidderProfilesById.get(userId);
        if (bidder == null) {
            throw new ValidationException("This account cannot act as a bidder.");
        }
        return bidder;
    }

    public boolean isAdmin(String userId) {
        return adminProfilesById.containsKey(userId);
    }

    public void clear() {
        accountsById.clear();
        sellerProfilesById.clear();
        bidderProfilesById.clear();
        adminProfilesById.clear();
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

    private AccountRecord saveAccount(
            String accountId,
            String username,
            String passwordHash,
            boolean admin,
            boolean overwriteExisting
    ) {
        AccountRecord account = new AccountRecord(accountId, username, passwordHash, admin);

        if (!overwriteExisting && accountsById.containsKey(accountId)) {
            throw new ValidationException("Username already exists.");
        }

        accountsById.put(accountId, account);
        sellerProfilesById.put(accountId, new Seller(accountId, username, passwordHash));
        bidderProfilesById.put(accountId, new Bidder(accountId, username, passwordHash));

        if (admin) {
            adminProfilesById.put(accountId, new Admin(accountId, username, passwordHash));
        } else {
            adminProfilesById.remove(accountId);
        }

        return account;
    }

    public record AccountRecord(String id, String username, String passwordHash, boolean admin) {
    }
}
