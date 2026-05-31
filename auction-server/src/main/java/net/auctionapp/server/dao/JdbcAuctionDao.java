package net.auctionapp.server.dao;

import net.auctionapp.server.models.auction.Auction;
import net.auctionapp.server.models.auction.BidTransaction;
import net.auctionapp.server.models.auction.BidStatus;
import net.auctionapp.common.auction.AuctionStatus;
import net.auctionapp.server.models.items.Item;
import net.auctionapp.server.exceptions.DatabaseException;
import net.auctionapp.server.database.DatabaseConnection;
import net.auctionapp.server.utils.JdbcUtil;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class JdbcAuctionDao implements AuctionDao {
    private static final String CREATE_AUCTIONS_TABLE_QUERY = """
            CREATE TABLE IF NOT EXISTS auctions (
                id VARCHAR(64) PRIMARY KEY,
                seller_id VARCHAR(64) NOT NULL,
                item_id VARCHAR(64) NOT NULL,
                item_type VARCHAR(32) NOT NULL,
                title VARCHAR(255) NOT NULL,
                description TEXT NOT NULL,
                image_url TEXT,
                starting_price DECIMAL(19, 2) NOT NULL,
                minimum_bid_increment DECIMAL(19, 2) NOT NULL,
                current_price DECIMAL(19, 2) NOT NULL,
                status VARCHAR(32) NOT NULL,
                leading_bidder_id VARCHAR(64),
                winner_bidder_id VARCHAR(64),
                start_time DATETIME NOT NULL,
                end_time DATETIME NOT NULL,
                brand VARCHAR(255),
                model VARCHAR(255),
                warranty_months INT,
                author VARCHAR(255),
                year_created INT
            )
            """;
    private static final String CREATE_BID_TRANSACTIONS_TABLE_QUERY = """
            CREATE TABLE IF NOT EXISTS bid_transactions (
                id VARCHAR(64) PRIMARY KEY,
                auction_id VARCHAR(64) NOT NULL,
                bidder_id VARCHAR(64) NOT NULL,
                amount DECIMAL(19, 2) NOT NULL,
                bid_time DATETIME NOT NULL,
                status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
                INDEX idx_bid_transactions_auction_id (auction_id),
                INDEX idx_bid_transactions_bidder_id (bidder_id)
            )
            """;
    private static final String FIND_ALL_AUCTIONS_QUERY = """
            SELECT
                id,
                seller_id,
                item_id,
                item_type,
                title,
                description,
                image_url,
                starting_price,
                minimum_bid_increment,
                current_price,
                status,
                leading_bidder_id,
                winner_bidder_id,
                start_time,
                end_time,
                brand,
                model,
                warranty_months,
                author,
                year_created
            FROM auctions
            """;
    private static final String FIND_ALL_BID_TRANSACTIONS_QUERY = """
            SELECT id, auction_id, bidder_id, amount, bid_time, status
            FROM bid_transactions
            ORDER BY auction_id ASC, bid_time ASC, id ASC
            """;
    private static final String CREATE_AUCTION_QUERY = """
            INSERT INTO auctions (
                id,
                seller_id,
                item_id,
                item_type,
                title,
                description,
                image_url,
                starting_price,
                minimum_bid_increment,
                current_price,
                status,
                leading_bidder_id,
                winner_bidder_id,
                start_time,
                end_time,
                brand,
                model,
                warranty_months,
                author,
                year_created
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
    private static final String INSERT_BID_TRANSACTION_QUERY = """
            INSERT INTO bid_transactions (id, auction_id, bidder_id, amount, bid_time, status)
            VALUES (?, ?, ?, ?, ?, ?)
            """;
    private static final String UPDATE_AUCTION_STATE_QUERY = """
            UPDATE auctions
            SET
                current_price = ?,
                status = ?,
                leading_bidder_id = ?,
                winner_bidder_id = ?,
                end_time = ?
            WHERE id = ?
            """;
    private static final String UPDATE_AUCTION_QUERY = """
            UPDATE auctions
            SET
                title = ?,
                description = ?,
                image_url = ?,
                starting_price = ?,
                minimum_bid_increment = ?,
                current_price = ?,
                status = ?,
                leading_bidder_id = ?,
                winner_bidder_id = ?,
                start_time = ?,
                end_time = ?,
                brand = ?,
                model = ?,
                warranty_months = ?,
                author = ?,
                year_created = ?
            WHERE id = ?
            """;
    private static final String INVALIDATE_BID_QUERY =
            "UPDATE bid_transactions SET status = ? WHERE id = ? AND status = ?";

    private final DatabaseConnection databaseConnection;
    private final JdbcBalanceDao balanceDao;
    private final JdbcUserDao userDao;

    public JdbcAuctionDao(DatabaseConnection databaseConnection, JdbcBalanceDao balanceDao, JdbcUserDao userDao) {
        this.databaseConnection = databaseConnection;
        this.balanceDao = balanceDao;
        this.userDao = userDao;
        JdbcUtil.ensureTable(databaseConnection, CREATE_AUCTIONS_TABLE_QUERY, "auctions");
        JdbcUtil.ensureTable(databaseConnection, CREATE_BID_TRANSACTIONS_TABLE_QUERY, "bid transactions");
    }

    @Override
    public List<Auction> findAllAuctions() {
        List<Auction> auctions = new ArrayList<>();
        try (Connection connection = databaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(FIND_ALL_AUCTIONS_QUERY)) {
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    auctions.add(mapAuction(resultSet));
                }
            }
            Map<String, List<BidTransaction>> bidsByAuctionId = findAllBidTransactions(connection);
            for (Auction auction : auctions) {
                auction.restoreBidHistory(bidsByAuctionId.get(auction.getId()));
            }
        } catch (SQLException e) {
            throw new DatabaseException("Failed to load auctions.", e);
        }
        return auctions;
    }

    @Override
    public boolean createAuction(Auction auction) {
        Item item = auction.getItem();
        try (Connection connection = databaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(CREATE_AUCTION_QUERY)) {
            statement.setString(1, auction.getId());
            statement.setString(2, auction.getSellerId());
            statement.setString(3, item.getId());
            statement.setString(4, item.getType().name());
            bindAuctionDetails(statement, 5, auction, item);
            return statement.executeUpdate() == 1;
        } catch (SQLException e) {
            throw new DatabaseException("Failed to create auction.", e);
        }
    }

    @Override
    public boolean recordBid(Auction auction, BidTransaction bid, BigDecimal amountToLock) {
        return executeInTransaction(
                "Failed to record bid.",
                connection -> balanceDao.lockFunds(connection, bid.getBidderId(), amountToLock)
                        && updateAuctionState(connection, auction)
                        && insertBidTransaction(connection, bid)
        );
    }

    @Override
    public boolean updateAuction(Auction auction) {
        Item item = auction.getItem();
        try (Connection connection = databaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(UPDATE_AUCTION_QUERY)) {
            bindAuctionDetails(statement, 1, auction, item);
            statement.setString(17, auction.getId());
            return statement.executeUpdate() == 1;
        } catch (SQLException e) {
            throw new DatabaseException("Failed to update auction.", e);
        }
    }

    @Override
    public boolean settleAuction(Auction auction, Map<String, BigDecimal> committedAmountsByBidder) {
        return executeInTransaction(
                "Failed to settle auction.",
                connection -> updateAuctionState(connection, auction)
                        && balanceDao.settleAuctionBalances(connection, auction, committedAmountsByBidder)
        );
    }

    @Override
    public boolean cancelBids(
            Auction auction,
            List<BidTransaction> invalidatedBids,
            Map<String, BigDecimal> fundsToRelease
    ) {
        return executeInTransaction(
                "Failed to cancel bids.",
                connection -> updateAuctionState(connection, auction)
                        && invalidateBids(connection, invalidatedBids)
                        && balanceDao.releaseFunds(connection, fundsToRelease)
        );
    }

    @Override
    public boolean applyUserBanEffects(
            String bannedUserId,
            List<Auction> updatedAuctions,
            List<BidTransaction> invalidatedBids,
            Map<String, BigDecimal> fundsToRelease
    ) {
        return executeInTransaction(
                "Failed to apply user ban effects.",
                connection -> userDao.banUser(connection, bannedUserId)
                        && updateAffectedAuctions(connection, updatedAuctions)
                        && invalidateBids(connection, invalidatedBids)
                        && balanceDao.releaseFunds(connection, fundsToRelease)
        );
    }

    private boolean executeInTransaction(String failureMessage, TransactionalOperation operation) {
        try (Connection connection = databaseConnection.getConnection()) {
            boolean previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                boolean completed = operation.execute(connection);
                if (completed) {
                    connection.commit();
                } else {
                    connection.rollback();
                }
                return completed;
            } catch (SQLException | RuntimeException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(previousAutoCommit);
            }
        } catch (SQLException e) {
            throw new DatabaseException(failureMessage, e);
        }
    }

    private boolean updateAuctionState(Connection connection, Auction auction) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(UPDATE_AUCTION_STATE_QUERY)) {
            bindAuctionState(statement, auction);
            return statement.executeUpdate() == 1;
        }
    }

    private boolean insertBidTransaction(Connection connection, BidTransaction bid) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(INSERT_BID_TRANSACTION_QUERY)) {
            statement.setString(1, bid.getId());
            statement.setString(2, bid.getAuctionId());
            statement.setString(3, bid.getBidderId());
            statement.setBigDecimal(4, bid.getAmount());
            statement.setTimestamp(5, Timestamp.valueOf(bid.getTimestamp()));
            statement.setString(6, bid.getStatus().name());
            return statement.executeUpdate() == 1;
        }
    }

    private boolean updateAffectedAuctions(Connection connection, List<Auction> updatedAuctions) throws SQLException {
        if (updatedAuctions == null) {
            return true;
        }
        for (Auction auction : updatedAuctions) {
            if (auction != null && !updateAuctionState(connection, auction)) {
                return false;
            }
        }
        return true;
    }

    private boolean invalidateBids(Connection connection, List<BidTransaction> invalidatedBids) throws SQLException {
        if (invalidatedBids == null) {
            return true;
        }
        try (PreparedStatement statement = connection.prepareStatement(INVALIDATE_BID_QUERY)) {
            for (BidTransaction bid : invalidatedBids) {
                if (bid == null) {
                    continue;
                }
                statement.setString(1, BidStatus.INVALIDATED.name());
                statement.setString(2, bid.getId());
                statement.setString(3, BidStatus.ACTIVE.name());
                if (statement.executeUpdate() != 1) {
                    return false;
                }
            }
        }
        return true;
    }

    private void bindAuctionDetails(PreparedStatement statement, int startIndex, Auction auction, Item item)
            throws SQLException {
        statement.setString(startIndex, item.getTitle());
        statement.setString(startIndex + 1, item.getDescription());
        JdbcUtil.setNullableString(statement, startIndex + 2, item.getImageUrl());
        statement.setBigDecimal(startIndex + 3, auction.getStartingPrice());
        statement.setBigDecimal(startIndex + 4, auction.getMinimumBidIncrement());
        statement.setBigDecimal(startIndex + 5, auction.getCurrentPrice());
        statement.setString(startIndex + 6, auction.getStatus().name());
        JdbcUtil.setNullableString(statement, startIndex + 7, auction.getLeadingBidderId());
        JdbcUtil.setNullableString(statement, startIndex + 8, auction.getWinnerBidderId());
        statement.setTimestamp(startIndex + 9, Timestamp.valueOf(auction.getStartTime()));
        statement.setTimestamp(startIndex + 10, Timestamp.valueOf(auction.getEndTime()));
        JdbcUtil.bindItemAttributes(statement, startIndex + 11, item);
    }

    private void bindAuctionState(PreparedStatement statement, Auction auction) throws SQLException {
        statement.setBigDecimal(1, auction.getCurrentPrice());
        statement.setString(2, auction.getStatus().name());
        JdbcUtil.setNullableString(statement, 3, auction.getLeadingBidderId());
        JdbcUtil.setNullableString(statement, 4, auction.getWinnerBidderId());
        statement.setTimestamp(5, Timestamp.valueOf(auction.getEndTime()));
        statement.setString(6, auction.getId());
    }

    private Map<String, List<BidTransaction>> findAllBidTransactions(Connection connection) throws SQLException {
        Map<String, List<BidTransaction>> bidsByAuctionId = new HashMap<>();
        try (PreparedStatement statement = connection.prepareStatement(FIND_ALL_BID_TRANSACTIONS_QUERY);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                BidTransaction bid = mapBidTransaction(resultSet);
                bidsByAuctionId.computeIfAbsent(bid.getAuctionId(), ignored -> new ArrayList<>()).add(bid);
            }
        }
        return bidsByAuctionId;
    }

    private Auction mapAuction(ResultSet resultSet) throws SQLException {
        String winnerBidderId = resultSet.getString("winner_bidder_id");
        return new Auction(
                resultSet.getString("id"),
                resultSet.getString("seller_id"),
                toLocalDateTime(resultSet.getTimestamp("start_time")),
                toLocalDateTime(resultSet.getTimestamp("end_time")),
                JdbcUtil.mapItem(resultSet),
                resultSet.getBigDecimal("starting_price"),
                resultSet.getBigDecimal("minimum_bid_increment"),
                resultSet.getBigDecimal("current_price"),
                resultSet.getString("leading_bidder_id"),
                winnerBidderId,
                parseAuctionStatus(resultSet.getString("status"), winnerBidderId)
        );
    }

    private BidTransaction mapBidTransaction(ResultSet resultSet) throws SQLException {
        Timestamp bidTimestamp = resultSet.getTimestamp("bid_time");
        if (bidTimestamp == null) {
            throw new DatabaseException("Bid timestamp cannot be null.");
        }
        return new BidTransaction(
                resultSet.getString("id"),
                resultSet.getBigDecimal("amount"),
                bidTimestamp.toLocalDateTime(),
                resultSet.getString("bidder_id"),
                resultSet.getString("auction_id"),
                parseBidStatus(resultSet.getString("status"))
        );
    }

    private LocalDateTime toLocalDateTime(Timestamp timestamp) {
        if (timestamp == null) {
            throw new DatabaseException("Auction timestamp cannot be null.");
        }
        return timestamp.toLocalDateTime();
    }

    private AuctionStatus parseAuctionStatus(String rawStatus, String winnerBidderId) {
        if (rawStatus == null || rawStatus.isBlank()) {
            return AuctionStatus.RUNNING;
        }
        return switch (rawStatus.trim().toUpperCase()) {
            case "OPEN" -> AuctionStatus.RUNNING;
            case "FINISHED" -> (winnerBidderId == null || winnerBidderId.isBlank())
                    ? AuctionStatus.CANCELED
                    : AuctionStatus.PAID;
            default -> AuctionStatus.valueOf(rawStatus.trim().toUpperCase());
        };
    }

    private BidStatus parseBidStatus(String rawStatus) {
        if (rawStatus == null || rawStatus.isBlank()) {
            return BidStatus.ACTIVE;
        }
        return BidStatus.valueOf(rawStatus.trim().toUpperCase());
    }

    @FunctionalInterface
    private interface TransactionalOperation {
        boolean execute(Connection connection) throws SQLException;
    }
}
