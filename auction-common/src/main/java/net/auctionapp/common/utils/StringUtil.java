package net.auctionapp.common.utils;

import java.util.Locale;

public final class StringUtil {
    private StringUtil() {
    }

    public static String normalizeString(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }
}
