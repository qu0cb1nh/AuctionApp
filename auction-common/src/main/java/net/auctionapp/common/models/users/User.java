package net.auctionapp.common.models.users;

import net.auctionapp.common.models.Entity;
import net.auctionapp.common.models.auction.AutoBidConfig;

public class User extends Entity {
    private final String username;
    private final String passwordHash;
    private final UserRole role;
    private AutoBidConfig autoBidConfig;

    public User(String id, String username, String passwordHash, UserRole role) {
        super(id);
        this.username = username;
        this.passwordHash = passwordHash;
        this.role = role == null ? UserRole.USER : role;
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
}
