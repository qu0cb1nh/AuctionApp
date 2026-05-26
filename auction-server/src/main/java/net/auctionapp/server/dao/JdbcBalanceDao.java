package net.auctionapp.server.dao;

import net.auctionapp.common.auction.AuctionStatus;
import net.auctionapp.common.utils.StringUtil;
import net.auctionapp.server.exceptions.DatabaseException;
import net.auctionapp.server.models.auction.Auction;
import net.auctionapp.server.database.DatabaseConnection;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Map;

public class JdbcBalanceDao implements BalanceDao {
    private static final String INCREASE_BALANCE_QUERY =
            "UPDATE users SET balance = balance + ? WHERE id = ?";
    private static final String DECREASE_BALANCE_QUERY =
            "UPDATE users SET balance = balance - ? WHERE id = ? AND balance >= ?";
    private static final String LOCK_FUNDS_QUERY =
            "UPDATE users SET balance = balance - ?, pending_balance = pending_balance + ? "
                    + "WHERE id = ? AND balance >= ?";
    private static final String RELEASE_FUNDS_QUERY =
            "UPDATE users SET balance = balance + ?, pending_balance = pending_balance - ? WHERE id = ? AND pending_balance >= ?";
    private static final String TRANSFER_PENDING_QUERY =
            "UPDATE users SET pending_balance = pending_balance - ? WHERE id = ? AND pending_balance >= ?";

    private final DatabaseConnection databaseConnection;

    public JdbcBalanceDao() {
        this(DatabaseConnection.getInstance());
    }

    public JdbcBalanceDao(DatabaseConnection databaseConnection) {
        this.databaseConnection = databaseConnection;
    }

    @Override
    public boolean increaseBalance(String userId, BigDecimal amount) {
        requirePositiveMoney(amount);
        try (Connection connection = databaseConnection.getConnection()) {
            return increaseBalance(connection, userId, amount);
        } catch (SQLException e) {
            throw new DatabaseException("Failed to increase balance.", e);
        }
    }

    @Override
    public boolean tryDecreaseBalance(String userId, BigDecimal amount) {
        requirePositiveMoney(amount);
        try (Connection connection = databaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(DECREASE_BALANCE_QUERY)) {
            statement.setBigDecimal(1, amount);
            statement.setString(2, StringUtil.normalizeString(userId));
            statement.setBigDecimal(3, amount);
            return statement.executeUpdate() == 1;
        } catch (SQLException e) {
            throw new DatabaseException("Failed to decrease balance.", e);
        }
    }

    boolean lockFunds(Connection connection, String userId, BigDecimal amount) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(LOCK_FUNDS_QUERY)) {
            statement.setBigDecimal(1, amount);
            statement.setBigDecimal(2, amount);
            statement.setString(3, StringUtil.normalizeString(userId));
            statement.setBigDecimal(4, amount);
            return statement.executeUpdate() == 1;
        }
    }

    boolean settleAuctionBalances(
            Connection connection,
            Auction auction,
            Map<String, BigDecimal> committedAmountsByBidder
    ) throws SQLException {
        String winnerId = StringUtil.normalizeString(auction.getWinnerBidderId());
        boolean hasWinner = auction.getStatus() == AuctionStatus.PAID && !winnerId.isEmpty();

        if (hasWinner) {
            BigDecimal winningAmount = auction.getCurrentPrice();
            if (!transferPendingFunds(connection, winnerId, winningAmount)
                    || !increaseBalance(connection, auction.getSellerId(), winningAmount)) {
                return false;
            }
        }

        if (committedAmountsByBidder == null) {
            return true;
        }
        for (Map.Entry<String, BigDecimal> entry : committedAmountsByBidder.entrySet()) {
            String bidderId = StringUtil.normalizeString(entry.getKey());
            BigDecimal amount = entry.getValue();
            if (bidderId.isEmpty() || amount == null || amount.signum() <= 0
                    || (hasWinner && bidderId.equals(winnerId))) {
                continue;
            }
            if (!releaseFunds(connection, bidderId, amount)) {
                return false;
            }
        }
        return true;
    }

    boolean releaseFunds(Connection connection, Map<String, BigDecimal> fundsToRelease) throws SQLException {
        if (fundsToRelease == null) {
            return true;
        }
        for (Map.Entry<String, BigDecimal> entry : fundsToRelease.entrySet()) {
            BigDecimal amount = entry.getValue();
            if (amount != null && amount.signum() > 0 && !releaseFunds(connection, entry.getKey(), amount)) {
                return false;
            }
        }
        return true;
    }

    private boolean increaseBalance(Connection connection, String userId, BigDecimal amount) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(INCREASE_BALANCE_QUERY)) {
            statement.setBigDecimal(1, amount);
            statement.setString(2, StringUtil.normalizeString(userId));
            return statement.executeUpdate() == 1;
        }
    }

    private boolean transferPendingFunds(Connection connection, String userId, BigDecimal amount)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(TRANSFER_PENDING_QUERY)) {
            statement.setBigDecimal(1, amount);
            statement.setString(2, StringUtil.normalizeString(userId));
            statement.setBigDecimal(3, amount);
            return statement.executeUpdate() == 1;
        }
    }

    private boolean releaseFunds(Connection connection, String userId, BigDecimal amount) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(RELEASE_FUNDS_QUERY)) {
            statement.setBigDecimal(1, amount);
            statement.setBigDecimal(2, amount);
            statement.setString(3, StringUtil.normalizeString(userId));
            statement.setBigDecimal(4, amount);
            return statement.executeUpdate() == 1;
        }
    }

    private static void requirePositiveMoney(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("Amount must be positive.");
        }
    }
}
