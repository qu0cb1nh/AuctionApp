package net.auctionapp.server.models.users;

import net.auctionapp.common.users.UserRole;

import net.auctionapp.server.models.Entity;

import java.math.BigDecimal;
import java.util.Objects;

public class User extends Entity {
    private final String username;
    private final String passwordHash;
    private final UserRole role;
    private BigDecimal balance;
    private BigDecimal pendingBalance;
    private boolean banned;

    public User(String id, String username, String passwordHash, UserRole role, boolean banned) {
        this(id, username, passwordHash, role, BigDecimal.ZERO, BigDecimal.ZERO, banned);
    }

    public User(String id, String username, String passwordHash, UserRole role, BigDecimal balance, BigDecimal pendingBalance, boolean banned) {
        super(id);
        this.username = username;
        this.passwordHash = passwordHash;
        this.role = role == null ? UserRole.USER : role;
        this.balance = Objects.requireNonNullElse(balance, BigDecimal.ZERO);
        this.pendingBalance = Objects.requireNonNullElse(pendingBalance, BigDecimal.ZERO);
        this.banned = banned;
    }

    public String getUsername() {
        return username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public UserRole getRole() {
        return role;
    }

    public boolean hasRole(UserRole role) {
        return this.role == role;
    }

    public synchronized BigDecimal getBalance() {
        return balance;
    }

    public synchronized void setBalance(BigDecimal balance) {
        this.balance = Objects.requireNonNullElse(balance, BigDecimal.ZERO);
    }

    public synchronized BigDecimal getPendingBalance() {
        return pendingBalance;
    }

    public synchronized void setPendingBalance(BigDecimal pendingBalance) {
        this.pendingBalance = Objects.requireNonNullElse(pendingBalance, BigDecimal.ZERO);
    }

    public synchronized void addBalance(BigDecimal amount) {
        if (amount == null) {
            return;
        }
        balance = balance.add(amount);
    }

    public synchronized void addPendingBalance(BigDecimal amount) {
        if (amount == null) {
            return;
        }
        pendingBalance = pendingBalance.add(amount);
    }

    public synchronized void copyMutableStateFrom(User source) {
        if (source == null) {
            return;
        }
        balance = source.getBalance();
        pendingBalance = source.getPendingBalance();
        banned = source.isBanned();
    }

    public synchronized boolean isBanned() {
        return banned;
    }

    public synchronized void setBanned(boolean banned) {
        this.banned = banned;
    }
}
