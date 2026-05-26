package net.auctionapp.server.dao;

import java.math.BigDecimal;

public interface BalanceDao {
    boolean increaseBalance(String userId, BigDecimal amount);

    boolean tryDecreaseBalance(String userId, BigDecimal amount);
}
