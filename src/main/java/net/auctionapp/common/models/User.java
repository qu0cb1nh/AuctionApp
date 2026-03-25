package net.auctionapp.common.models;

/**
 * Represents a user in the system.
 * In a real application, this class would contain more information such as password, role, etc.
 */
public class User {
    private String name;

    public User(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    // In the future, you might add equals() and hashCode() methods
    // to compare User objects more accurately.
}
