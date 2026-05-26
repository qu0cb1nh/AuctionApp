package net.auctionapp.server.utils;

import io.github.cdimascio.dotenv.Dotenv;

public class EnvUtil {
    private static final Dotenv DOTENV;

    static {
        Dotenv tempDotenv = null;
        try {
            tempDotenv = Dotenv.configure()
                    .ignoreIfMalformed()
                    .ignoreIfMissing()
                    .load();
        } catch (Exception e) {
            System.err.println("DEBUG: Failed to load .env: " + e.getMessage());
        }
        DOTENV = tempDotenv;
    }

    public static String getDatabaseUrl() {
        return getEnv("DB_URL");
    }

    public static String getDatabaseUser() {
        return getEnv("DB_USER");
    }

    public static String getDatabasePassword() {
        return getEnv("DB_PASSWORD");
    }

    public static String getCloudinaryUrl() {
        return getEnv("CLOUDINARY_URL");
    }

    private static String getEnv(String envKey) {
        String envValue = System.getenv(envKey);
        if (envValue != null && !envValue.trim().isEmpty()) {
            return envValue.trim();
        }

        if (DOTENV == null) {
            return "";
        }

        String dotenvValue = DOTENV.get(envKey);
        if (dotenvValue != null && !dotenvValue.trim().isEmpty()) {
            return dotenvValue.trim();
        }
        return "";
    }
}
