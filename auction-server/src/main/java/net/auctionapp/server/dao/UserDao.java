package net.auctionapp.server.dao;

import net.auctionapp.server.models.users.User;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface UserDao {
    Optional<User> findByUsername(String normalizedUsername);

    Optional<User> findById(String userId);

    boolean createUser(User user);

    List<User> findAllUsers();

    boolean updateBanStatus(String userId, boolean banned);

    boolean increaseBalance(String userId, BigDecimal amount);

    boolean tryDecreaseBalance(String userId, BigDecimal amount);

}
