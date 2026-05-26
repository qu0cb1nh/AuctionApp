package net.auctionapp.server.dao;

import net.auctionapp.server.models.users.User;

import java.util.List;
import java.util.Optional;

public interface UserDao {
    Optional<User> findByUsername(String normalizedUsername);

    Optional<User> findById(String userId);

    boolean createUser(User user);

    List<User> findAllUsers();

    boolean clearBan(String userId);
}
