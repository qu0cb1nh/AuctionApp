package net.auctionapp.server.dao;

import net.auctionapp.server.models.users.User;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface UserDao {
    Optional<User> findByUsername(String normalizedUsername);

    boolean createUser(User user);

    List<User> findAllUsers();

    boolean updateBanStatus(String normalizedUsername, boolean banned);

    boolean increaseBalance(String normalizedUsername, BigDecimal amount);

    boolean tryDecreaseBalance(String normalizedUsername, BigDecimal amount);

}
