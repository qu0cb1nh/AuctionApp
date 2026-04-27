package net.auctionapp.client.utils;

import java.time.Duration;

public final class DurationFormatUtil {
    private DurationFormatUtil() {
    }

    public static String formatRemainingDuration(Duration duration) {
        if (duration == null || duration.isNegative() || duration.isZero()) {
            return "less than 1m";
        }

        long totalSeconds = duration.getSeconds();
        long days = totalSeconds / 86_400;
        long hours = (totalSeconds % 86_400) / 3_600;
        long minutes = (totalSeconds % 3_600) / 60;
        if (days > 0) {
            return days + "d " + hours + "h";
        }
        if (hours > 0) {
            return hours + "h " + minutes + "m";
        }
        if (minutes > 0) {
            return minutes + "m";
        }
        return "less than 1m";
    }
}
