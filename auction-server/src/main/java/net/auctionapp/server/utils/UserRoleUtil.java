package net.auctionapp.server.utils;

import net.auctionapp.common.models.users.User;
import net.auctionapp.common.models.users.UserRole;

import java.util.EnumSet;
import java.util.Set;

public final class UserRoleUtil {
    private static final String ADMIN_ROLE = "admin";
    private static final String USER_ROLE = "user";

    private UserRoleUtil() {
    }

    public static EnumSet<UserRole> fromDatabaseRole(String databaseRole) {
        if (ADMIN_ROLE.equalsIgnoreCase(databaseRole)) {
            return EnumSet.of(UserRole.ADMIN, UserRole.SELLER, UserRole.BIDDER);
        }
        return EnumSet.of(UserRole.SELLER, UserRole.BIDDER);
    }

    public static String toDatabaseRole(User user) {
        return toDatabaseRole(user == null ? Set.of() : user.getRoles());
    }

    public static String toDatabaseRole(Set<UserRole> roles) {
        return hasAdminRole(roles) ? ADMIN_ROLE : USER_ROLE;
    }

    public static String toClientRole(User user) {
        return toClientRole(user == null ? Set.of() : user.getRoles());
    }

    public static String toClientRole(Set<UserRole> roles) {
        return hasAdminRole(roles) ? ADMIN_ROLE : USER_ROLE;
    }

    private static boolean hasAdminRole(Set<UserRole> roles) {
        return roles != null && roles.contains(UserRole.ADMIN);
    }
}
