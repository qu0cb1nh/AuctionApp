package net.auctionapp.server.utils;

import net.auctionapp.server.models.users.User;
import net.auctionapp.common.users.UserRole;

public final class UserRoleUtil {
    private static final String ADMIN_ROLE = "admin";
    private static final String USER_ROLE = "user";

    private UserRoleUtil() {
    }

    public static UserRole fromDatabaseRole(String databaseRole) {
        if (ADMIN_ROLE.equalsIgnoreCase(databaseRole)) {
            return UserRole.ADMIN;
        }
        return UserRole.USER;
    }

    public static String toDatabaseRole(User user) {
        return user != null && user.getRole() == UserRole.ADMIN ? ADMIN_ROLE : USER_ROLE;
    }

    public static UserRole toClientRole(User user) {
        return user != null && user.getRole() == UserRole.ADMIN ? UserRole.ADMIN : UserRole.USER;
    }
}
