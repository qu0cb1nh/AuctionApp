package net.auctionapp.server.dao;

import net.auctionapp.common.models.users.User;

import java.util.Optional;

public interface UserDao {
    Optional<User> findByUsername(String normalizedUsername);

    boolean createUser(User user);
}
