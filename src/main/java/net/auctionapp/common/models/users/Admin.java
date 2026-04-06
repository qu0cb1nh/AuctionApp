package net.auctionapp.common.models.users;

public class Admin extends User {
    public Admin(String id, String username, String passwordHash) {
        super(id, username, passwordHash, UserRole.ADMIN);
    }
}
