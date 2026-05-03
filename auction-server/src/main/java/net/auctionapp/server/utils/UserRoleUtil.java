package net.auctionapp.server.utils;

import net.auctionapp.common.models.users.User;
import net.auctionapp.common.models.users.UserRole;

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
        return hasAdminRole(user == null ? null : user.getRole()) ? ADMIN_ROLE : USER_ROLE;
    }

    public static String toClientRole(User user) {
        return hasAdminRole(user == null ? null : user.getRole()) ? ADMIN_ROLE : USER_ROLE;
    }

    private static boolean hasAdminRole(UserRole role) {
        return role == UserRole.ADMIN;
    }
}
