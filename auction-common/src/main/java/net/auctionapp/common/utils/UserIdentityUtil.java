package net.auctionapp.common.utils;

import java.util.Locale;

public final class UserIdentityUtil {
    private UserIdentityUtil() {
    }

    public static String normalizeUserId(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }
}
