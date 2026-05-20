package net.auctionapp.common.utils;

import io.github.cdimascio.dotenv.Dotenv;

import java.io.InputStream;
import java.util.Properties;

public final class ConfigUtil {

    private ConfigUtil() { }

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

    public static Properties loadProperties(Class<?> anchorClass, String resourceName) {
        Properties properties = new Properties();
        try (InputStream input = anchorClass.getResourceAsStream("/" + resourceName)) {
            if (input == null) {
                System.out.println(resourceName + " was not found on the classpath.");
            } else {
                properties.load(input);
            }
        } catch (Exception e) {
            System.err.println("DEBUG: Failed to load " + resourceName + ": " + e.getMessage());
        }
        return properties;
    }

    public static String getStringProperty(Properties properties, String propertyName, String defaultValue) {
        String value = properties.getProperty(propertyName);
        if (value == null || value.trim().isEmpty()) {
            return defaultValue;
        }
        return value.trim();
    }

    public static int getIntProperty(Properties properties, String propertyName, int defaultValue) {
        String value = getStringProperty(properties, propertyName, Integer.toString(defaultValue));
        return Integer.parseInt(value);
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
