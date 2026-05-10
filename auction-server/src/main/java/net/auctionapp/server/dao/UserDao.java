package net.auctionapp.server.dao;

import net.auctionapp.common.models.users.User;

import java.math.BigDecimal;
import java.util.Optional;

public interface UserDao {
    Optional<User> findByUsername(String normalizedUsername);

    boolean createUser(User user);

    /**
     * Adds {@code amount} to the user's balance. {@code amount} must be positive.
     *
     * @return true if the user existed and one row was updated
     */
    boolean increaseBalance(String normalizedUsername, BigDecimal amount);

    /**
     * Subtracts {@code amount} from the user's balance if it is sufficient.
     * {@code amount} must be positive.
     *
     * @return true if the debit succeeded
     */
    boolean tryDecreaseBalance(String normalizedUsername, BigDecimal amount);
}
