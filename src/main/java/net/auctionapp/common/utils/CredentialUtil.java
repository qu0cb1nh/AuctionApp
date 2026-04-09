package net.auctionapp.common.utils;

import net.auctionapp.common.exceptions.ValidationException;

import java.util.regex.Pattern;

public final class CredentialUtil {
    private static final Pattern VALID_USERNAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_-]+$");
    private static final Pattern VALID_PASSWORD_PATTERN = Pattern.compile("^[a-zA-Z0-9!@#$%^&*=+]+$");

    private CredentialUtil() {
    }

    public static void validateLogin(String username, String password) {
        validateUsernameAndPassword(username, password);
    }

    public static void validateRegistration(String username, String password, String confirmPassword) {
        if (isBlank(username) || isBlank(password) || isBlank(confirmPassword)) {
            throw new ValidationException("Please fill in all the information.");
        }

        validateUsernameAndPassword(username, password);

        if (!password.equals(confirmPassword)) {
            throw new ValidationException("Verification password does not match.");
        }
    }

    private static void validateUsernameAndPassword(String username, String password) {
        if (isBlank(username) || isBlank(password)) {
            throw new ValidationException("Username and password are required.");
        }

        String trimmedUsername = username.trim();
        if (trimmedUsername.length() < 3) {
            throw new ValidationException("Username must be at least 3 characters.");
        }
        if (trimmedUsername.length() > 20) {
            throw new ValidationException("Username must not exceed 20 characters.");
        }
        if (!Character.isLetter(trimmedUsername.charAt(0))) {
            throw new ValidationException("Username must start with a letter.");
        }
        if (!VALID_USERNAME_PATTERN.matcher(trimmedUsername).matches()) {
            throw new ValidationException("Username cannot contain invalid characters.");
        }

        if (password.length() < 6) {
            throw new ValidationException("Password must be at least 6 characters.");
        }
        if (password.length() > 64) {
            throw new ValidationException("Password must not exceed 64 characters.");
        }
        if (!VALID_PASSWORD_PATTERN.matcher(password).matches()) {
            throw new ValidationException("Password cannot contain invalid characters.");
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
