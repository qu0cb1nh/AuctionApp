package net.auctionapp.common.utils;

import java.io.InputStream;
import java.util.Properties;

public final class ConfigUtil {

    private ConfigUtil() { }

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
}
