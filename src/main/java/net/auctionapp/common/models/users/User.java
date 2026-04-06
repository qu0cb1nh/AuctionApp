package net.auctionapp.common.models.users;

import net.auctionapp.common.models.Entity;

public abstract class User extends Entity {
    private final String username;
    private final String passwordHash;
    private final UserRole role;

    protected User(String id, String username, String passwordHash, UserRole role) {
        super(id);
        this.username = username;
        this.passwordHash = passwordHash;
        this.role = role;
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
}
