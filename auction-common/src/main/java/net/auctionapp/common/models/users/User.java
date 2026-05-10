package net.auctionapp.common.models.users;

import net.auctionapp.common.models.Entity;
import net.auctionapp.common.models.auction.AutoBidConfig;

import java.math.BigDecimal;
import java.util.Objects;

public class User extends Entity {
    private final String username;
    private final String passwordHash;
    private final UserRole role;
    private AutoBidConfig autoBidConfig;
    private BigDecimal balance;

    public User(String id, String username, String passwordHash, UserRole role) {
        this(id, username, passwordHash, role, BigDecimal.ZERO);
    }

    public User(String id, String username, String passwordHash, UserRole role, BigDecimal balance) {
        super(id);
        this.username = username;
        this.passwordHash = passwordHash;
        this.role = role == null ? UserRole.USER : role;
        this.balance = Objects.requireNonNullElse(balance, BigDecimal.ZERO);
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

    public AutoBidConfig getAutoBidConfig() {
        return autoBidConfig;
    }

    public void setAutoBidConfig(AutoBidConfig autoBidConfig) {
        this.autoBidConfig = autoBidConfig;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public void setBalance(BigDecimal balance) {
        this.balance = Objects.requireNonNullElse(balance, BigDecimal.ZERO);
    }
}
