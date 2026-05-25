package net.auctionapp.server.dao;

import net.auctionapp.server.models.auction.Auction;
import net.auctionapp.server.models.auction.BidTransaction;
import net.auctionapp.server.models.auction.BidStatus;
import net.auctionapp.common.auction.AuctionStatus;
import net.auctionapp.server.models.items.Item;
import net.auctionapp.common.items.ItemType;
import net.auctionapp.server.exceptions.DatabaseException;
import net.auctionapp.server.factories.ItemFactories;
import net.auctionapp.server.factories.ItemFactory;
import net.auctionapp.server.services.DatabaseService;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
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
    private static final String INCREASE_BALANCE_QUERY =
            "UPDATE users SET balance = balance + ? WHERE id = ?";
    private static final String LOCK_FUNDS_QUERY =
            "UPDATE users SET balance = balance - ?, pending_balance = pending_balance + ? "
                    + "WHERE id = ? AND balance >= ?";
    private static final String RELEASE_FUNDS_QUERY =
            "UPDATE users SET balance = balance + ?, pending_balance = pending_balance - ? WHERE id = ? AND pending_balance >= ?";
    private static final String TRANSFER_PENDING_QUERY =
            "UPDATE users SET pending_balance = pending_balance - ? WHERE id = ? AND pending_balance >= ?";
    private static final String UPDATE_BAN_STATUS_QUERY =
            "UPDATE users SET is_banned = TRUE WHERE id = ?";
    private static final String INVALIDATE_BID_QUERY =
            "UPDATE bid_transactions SET status = ? WHERE id = ? AND status = ?";

    private final DatabaseService databaseService;

    public JdbcAuctionDao() {
        this(DatabaseService.getInstance());
    }

    public JdbcAuctionDao(DatabaseService databaseService) {
        this.databaseService = databaseService;
        ensureAuctionsTable();
        ensureBidTransactionsTable();
    }

    @Override
    public List<Auction> findAllAuctions() {
        List<Auction> auctions = new ArrayList<>();
        try (Connection connection = databaseService.getConnection();
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
        try (Connection connection = databaseService.getConnection();
             PreparedStatement statement = connection.prepareStatement(CREATE_AUCTION_QUERY)) {
            statement.setString(1, auction.getId());
            statement.setString(2, auction.getSellerId());
            statement.setString(3, item.getId());
            statement.setString(4, item.getType().name());
            statement.setString(5, item.getTitle());
            statement.setString(6, item.getDescription());
            setNullableString(statement, 7, item.getImageUrl());
            statement.setBigDecimal(8, auction.getStartingPrice());
            statement.setBigDecimal(9, auction.getMinimumBidIncrement());
            statement.setBigDecimal(10, auction.getCurrentPrice());
            statement.setString(11, auction.getStatus().name());
            setNullableString(statement, 12, auction.getLeadingBidderId());
            setNullableString(statement, 13, auction.getWinnerBidderId());
            statement.setTimestamp(14, Timestamp.valueOf(auction.getStartTime()));
            statement.setTimestamp(15, Timestamp.valueOf(auction.getEndTime()));
            ItemFactories.forType(item.getType()).bindAttributes(statement, 16, item);

            return statement.executeUpdate() == 1;
        } catch (SQLException e) {
            throw new DatabaseException("Failed to create auction.", e);
        }
    }

    @Override
    public boolean recordBid(Auction auction, BidTransaction bid, BigDecimal amountToLock) {
        try (Connection connection = databaseService.getConnection()) {
            boolean previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                if (!lockFunds(connection, bid.getBidderId(), amountToLock)
                        || !updateAuctionState(connection, auction)
                        || !insertBidTransaction(connection, bid)) {
                    connection.rollback();
                    return false;
                }
                connection.commit();
                return true;
            } catch (SQLException | RuntimeException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(previousAutoCommit);
            }
        } catch (SQLException e) {
            throw new DatabaseException("Failed to record bid.", e);
        }
    }

    @Override
    public boolean updateAuction(Auction auction) {
        Item item = auction.getItem();
        try (Connection connection = databaseService.getConnection();
             PreparedStatement statement = connection.prepareStatement(UPDATE_AUCTION_QUERY)) {
            statement.setString(1, item.getTitle());
            statement.setString(2, item.getDescription());
            setNullableString(statement, 3, item.getImageUrl());
            statement.setBigDecimal(4, auction.getStartingPrice());
            statement.setBigDecimal(5, auction.getMinimumBidIncrement());
            statement.setBigDecimal(6, auction.getCurrentPrice());
            statement.setString(7, auction.getStatus().name());
            setNullableString(statement, 8, auction.getLeadingBidderId());
            setNullableString(statement, 9, auction.getWinnerBidderId());
            statement.setTimestamp(10, Timestamp.valueOf(auction.getStartTime()));
            statement.setTimestamp(11, Timestamp.valueOf(auction.getEndTime()));
            ItemFactories.forType(item.getType()).bindAttributes(statement, 12, item);
            statement.setString(17, auction.getId());
            return statement.executeUpdate() == 1;
        } catch (SQLException e) {
            throw new DatabaseException("Failed to update auction.", e);
        }
    }

    @Override
    public boolean settleAuction(Auction auction, Map<String, BigDecimal> committedAmountsByBidder) {
        try (Connection connection = databaseService.getConnection()) {
            boolean previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                if (!updateAuctionState(connection, auction)) {
                    connection.rollback();
                    return false;
                }
                if (!settleUserBalances(connection, auction, committedAmountsByBidder)) {
                    connection.rollback();
                    return false;
                }
                connection.commit();
                return true;
            } catch (SQLException | RuntimeException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(previousAutoCommit);
            }
        } catch (SQLException e) {
            throw new DatabaseException("Failed to settle auction.", e);
        }
    }

    @Override
    public boolean applyUserBanEffects(
            String bannedUserId,
            List<Auction> updatedAuctions,
            List<BidTransaction> invalidatedBids,
            Map<String, BigDecimal> fundsToRelease
    ) {
        try (Connection connection = databaseService.getConnection()) {
            boolean previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                if (!banUser(connection, bannedUserId)
                        || !updateAffectedAuctions(connection, updatedAuctions)
                        || !invalidateBids(connection, invalidatedBids)
                        || !releaseCommittedFunds(connection, fundsToRelease)) {
                    connection.rollback();
                    return false;
                }
                connection.commit();
                return true;
            } catch (SQLException | RuntimeException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(previousAutoCommit);
            }
        } catch (SQLException e) {
            throw new DatabaseException("Failed to apply user ban effects.", e);
        }
    }

    private boolean updateAuctionState(Connection connection, Auction auction) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(UPDATE_AUCTION_STATE_QUERY)) {
            return bindAuctionState(statement, auction).executeUpdate() == 1;
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

    private boolean banUser(Connection connection, String bannedUserId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(UPDATE_BAN_STATUS_QUERY)) {
            statement.setString(1, normalize(bannedUserId));
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
        for (BidTransaction bid : invalidatedBids) {
            if (bid == null) {
                continue;
            }
            try (PreparedStatement statement = connection.prepareStatement(INVALIDATE_BID_QUERY)) {
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

    private boolean releaseCommittedFunds(Connection connection, Map<String, BigDecimal> fundsToRelease)
            throws SQLException {
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

    private boolean lockFunds(Connection connection, String userId, BigDecimal amount) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(LOCK_FUNDS_QUERY)) {
            statement.setBigDecimal(1, amount);
            statement.setBigDecimal(2, amount);
            statement.setString(3, normalize(userId));
            statement.setBigDecimal(4, amount);
            return statement.executeUpdate() == 1;
        }
    }

    private PreparedStatement bindAuctionState(PreparedStatement statement, Auction auction) throws SQLException {
        statement.setBigDecimal(1, auction.getCurrentPrice());
        statement.setString(2, auction.getStatus().name());
        setNullableString(statement, 3, auction.getLeadingBidderId());
        setNullableString(statement, 4, auction.getWinnerBidderId());
        statement.setTimestamp(5, Timestamp.valueOf(auction.getEndTime()));
        statement.setString(6, auction.getId());
        return statement;
    }

    private boolean settleUserBalances(
            Connection connection,
            Auction auction,
            Map<String, BigDecimal> committedAmountsByBidder
    ) throws SQLException {
        String winnerId = normalize(auction.getWinnerBidderId());
        String sellerId = normalize(auction.getSellerId());
        boolean hasWinner = auction.getStatus() == AuctionStatus.PAID && !winnerId.isEmpty();

        if (hasWinner) {
            BigDecimal winningAmount = auction.getCurrentPrice();
            if (!transferPendingFunds(connection, winnerId, winningAmount)) {
                return false;
            }
            if (!increaseBalance(connection, sellerId, winningAmount)) {
                return false;
            }
        }

        if (committedAmountsByBidder == null || committedAmountsByBidder.isEmpty()) {
            return true;
        }
        for (Map.Entry<String, BigDecimal> entry : committedAmountsByBidder.entrySet()) {
            String bidderId = normalize(entry.getKey());
            BigDecimal committedAmount = entry.getValue();
            if (bidderId.isEmpty() || committedAmount == null || committedAmount.signum() <= 0) {
                continue;
            }
            if (hasWinner && bidderId.equals(winnerId)) {
                continue;
            }
            if (!releaseFunds(connection, bidderId, committedAmount)) {
                return false;
            }
        }
        return true;
    }

    private boolean transferPendingFunds(Connection connection, String userId, BigDecimal amount)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(TRANSFER_PENDING_QUERY)) {
            statement.setBigDecimal(1, amount);
            statement.setString(2, userId);
            statement.setBigDecimal(3, amount);
            return statement.executeUpdate() == 1;
        }
    }

    private boolean increaseBalance(Connection connection, String userId, BigDecimal amount)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(INCREASE_BALANCE_QUERY)) {
            statement.setBigDecimal(1, amount);
            statement.setString(2, userId);
            return statement.executeUpdate() == 1;
        }
    }

    private boolean releaseFunds(Connection connection, String userId, BigDecimal amount)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(RELEASE_FUNDS_QUERY)) {
            statement.setBigDecimal(1, amount);
            statement.setBigDecimal(2, amount);
            statement.setString(3, userId);
            statement.setBigDecimal(4, amount);
            return statement.executeUpdate() == 1;
        }
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }

    private void ensureAuctionsTable() {
        try (Connection connection = databaseService.getConnection();
             Statement statement = connection.createStatement()) {
            statement.executeUpdate(CREATE_AUCTIONS_TABLE_QUERY);
        } catch (SQLException e) {
            throw new DatabaseException("Failed to create auctions table.", e);
        }
    }

    private void ensureBidTransactionsTable() {
        try (Connection connection = databaseService.getConnection();
             Statement statement = connection.createStatement()) {
            statement.executeUpdate(CREATE_BID_TRANSACTIONS_TABLE_QUERY);
        } catch (SQLException e) {
            throw new DatabaseException("Failed to create bid transactions table.", e);
        }
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
        ItemType itemType = ItemType.valueOf(resultSet.getString("item_type"));
        ItemFactory itemFactory = ItemFactories.forType(itemType);
        Item item = itemFactory.createItem(resultSet);

        String winnerBidderId = resultSet.getString("winner_bidder_id");
        return new Auction(
                resultSet.getString("id"),
                resultSet.getString("seller_id"),
                toLocalDateTime(resultSet.getTimestamp("start_time")),
                toLocalDateTime(resultSet.getTimestamp("end_time")),
                item,
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

    private void setNullableString(PreparedStatement statement, int parameterIndex, String value) throws SQLException {
        if (value == null || value.isBlank()) {
            statement.setNull(parameterIndex, java.sql.Types.VARCHAR);
            return;
        }
        statement.setString(parameterIndex, value);
    }
}
