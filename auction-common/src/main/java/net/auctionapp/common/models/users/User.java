package net.auctionapp.common.models.users;

import net.auctionapp.common.models.Entity;
import net.auctionapp.common.models.auction.Auction;
import net.auctionapp.common.models.auction.AutoBidConfig;
import net.auctionapp.common.models.auction.BidTransaction;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

public class User extends Entity {
    private final String username;
    private final String passwordHash;
    private final EnumSet<UserRole> roles;
    private AutoBidConfig autoBidConfig;

    public User(String id, String username, String passwordHash, Set<UserRole> roles) {
        super(id);
        this.username = username;
        this.passwordHash = passwordHash;
        this.roles = roles == null || roles.isEmpty()
                ? EnumSet.noneOf(UserRole.class)
                : EnumSet.copyOf(roles);
    }

    public String getUsername() {
        return username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public Set<UserRole> getRoles() {
        return Collections.unmodifiableSet(roles);
    }

    public boolean hasRole(UserRole role) {
        return roles.contains(role);
    }

    public AutoBidConfig getAutoBidConfig() {
        return autoBidConfig;
    }

    public void setAutoBidConfig(AutoBidConfig autoBidConfig) {
        this.autoBidConfig = autoBidConfig;
    }
}
