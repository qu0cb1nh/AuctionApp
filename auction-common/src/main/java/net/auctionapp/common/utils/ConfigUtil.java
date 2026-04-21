package net.auctionapp.common.utils;

import io.github.cdimascio.dotenv.Dotenv;

import java.io.InputStream;
import java.util.Properties;

public final class ConfigUtil {

    private ConfigUtil() { }

    private static final Properties PROPERTIES = new Properties();
    private static final Dotenv DOTENV;

    static {
        // Load dotenv with explicit directory
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
        
        // Load application.properties
        try (InputStream input = ConfigUtil.class.getClassLoader().getResourceAsStream("application.properties")) {
            if (input == null) {
                System.out.println("application.properties was not found on the classpath.");
            } else {
                PROPERTIES.load(input);
            }
        } catch (Exception e) {
            System.err.println("DEBUG: Failed to load application.properties: " + e.getMessage());
        }
    }

    public static int getServerPort() {
        String portStr = PROPERTIES.getProperty("server.port", "5000");
        return Integer.parseInt(portStr);
    }

    public static String getServerHost() {
        return PROPERTIES.getProperty("server.host", "localhost");
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

    private static String getEnv(String envKey) {
        String envValue = System.getenv(envKey);
        if (envValue != null && !envValue.trim().isEmpty()) {
            return envValue.trim();
        }

        String dotenvValue = DOTENV.get(envKey);
        if (dotenvValue != null && !dotenvValue.trim().isEmpty()) {
            return dotenvValue.trim();
        }
        return "";
    }
}
