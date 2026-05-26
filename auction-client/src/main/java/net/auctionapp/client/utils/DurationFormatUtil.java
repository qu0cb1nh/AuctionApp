package net.auctionapp.client.utils;

import java.time.Duration;

public final class DurationFormatUtil {
    private DurationFormatUtil() {
    }

    public static String formatRemainingDuration(Duration duration) {
        if (duration == null || duration.isNegative() || duration.isZero()) {
            return "00:00:00";
        }

        long totalSeconds = duration.getSeconds();
        long hours = totalSeconds / 3_600;
        long minutes = (totalSeconds % 3_600) / 60;
        long seconds = totalSeconds % 60;
        return String.format("%02d:%02d:%02d", hours, minutes, seconds);
    }
}
