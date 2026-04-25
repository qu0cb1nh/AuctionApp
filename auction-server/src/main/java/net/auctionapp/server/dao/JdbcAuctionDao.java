package net.auctionapp.server.dao;

import net.auctionapp.common.models.auction.Auction;
import net.auctionapp.common.models.auction.AuctionStatus;
import net.auctionapp.common.models.items.Art;
import net.auctionapp.common.models.items.Electronics;
import net.auctionapp.common.models.items.Item;
import net.auctionapp.common.models.items.ItemType;
import net.auctionapp.common.models.items.Vehicle;
import net.auctionapp.server.exceptions.DatabaseException;
import net.auctionapp.server.factories.ArtFactory;
import net.auctionapp.server.factories.ElectronicsFactory;
import net.auctionapp.server.factories.ItemFactory;
import net.auctionapp.server.factories.VehicleFactory;
import net.auctionapp.server.managers.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class JdbcAuctionDao implements AuctionDao {
    private static final String CREATE_AUCTIONS_TABLE_QUERY = """
            CREATE TABLE IF NOT EXISTS auctions (
                id VARCHAR(64) PRIMARY KEY,
                seller_id VARCHAR(255) NOT NULL,
                item_id VARCHAR(64) NOT NULL,
                item_type VARCHAR(32) NOT NULL,
                title VARCHAR(255) NOT NULL,
                description TEXT NOT NULL,
                starting_price DECIMAL(19, 2) NOT NULL,
                minimum_bid_increment DECIMAL(19, 2) NOT NULL,
                current_price DECIMAL(19, 2) NOT NULL,
                status VARCHAR(32) NOT NULL,
                leading_bidder_id VARCHAR(255),
                winner_bidder_id VARCHAR(255),
                start_time DATETIME NOT NULL,
                end_time DATETIME NOT NULL,
                brand VARCHAR(255),
                model VARCHAR(255),
                warranty_months INT,
                author VARCHAR(255),
                year_created INT
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
    private static final String CREATE_AUCTION_QUERY = """
            INSERT INTO auctions (
                id,
                seller_id,
                item_id,
                item_type,
                title,
                description,
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
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
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

    private final DatabaseManager databaseManager;

    public JdbcAuctionDao() {
        this(DatabaseManager.getInstance());
    }

    public JdbcAuctionDao(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
        ensureAuctionsTable();
    }

    @Override
    public List<Auction> findAllAuctions() {
        List<Auction> auctions = new ArrayList<>();
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(FIND_ALL_AUCTIONS_QUERY);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                auctions.add(mapAuction(resultSet));
            }
        } catch (SQLException e) {
            throw new DatabaseException("Failed to load auctions.", e);
        }
        return auctions;
    }

    @Override
    public boolean createAuction(Auction auction) {
        Item item = auction.getItem();
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(CREATE_AUCTION_QUERY)) {
            statement.setString(1, auction.getId());
            statement.setString(2, auction.getSellerId());
            statement.setString(3, item.getId());
            statement.setString(4, item.getType().name());
            statement.setString(5, item.getTitle());
            statement.setString(6, item.getDescription());
            statement.setBigDecimal(7, auction.getStartingPrice());
            statement.setBigDecimal(8, auction.getMinimumBidIncrement());
            statement.setBigDecimal(9, auction.getCurrentPrice());
            statement.setString(10, auction.getStatus().name());
            setNullableString(statement, 11, auction.getLeadingBidderId());
            setNullableString(statement, 12, auction.getWinnerBidderId());
            statement.setTimestamp(13, Timestamp.valueOf(auction.getStartTime()));
            statement.setTimestamp(14, Timestamp.valueOf(auction.getEndTime()));

            if (item instanceof Electronics electronics) {
                statement.setString(15, electronics.getBrand());
                statement.setString(16, electronics.getModel());
                statement.setInt(17, electronics.getWarrantyMonths());
                statement.setNull(18, java.sql.Types.VARCHAR);
                statement.setNull(19, java.sql.Types.INTEGER);
            } else if (item instanceof Vehicle vehicle) {
                statement.setString(15, vehicle.getBrand());
                statement.setString(16, vehicle.getModel());
                statement.setNull(17, java.sql.Types.INTEGER);
                statement.setNull(18, java.sql.Types.VARCHAR);
                statement.setInt(19, vehicle.getYearCreated());
            } else if (item instanceof Art art) {
                statement.setNull(15, java.sql.Types.VARCHAR);
                statement.setNull(16, java.sql.Types.VARCHAR);
                statement.setNull(17, java.sql.Types.INTEGER);
                statement.setString(18, art.getAuthor());
                statement.setInt(19, art.getYearCreated());
            } else {
                throw unsupportedItemTypeError(item.getClass().getName());
            }

            return statement.executeUpdate() == 1;
        } catch (SQLException e) {
            throw new DatabaseException("Failed to create auction.", e);
        }
    }

    @Override
    public boolean updateAuctionState(Auction auction) {
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(UPDATE_AUCTION_STATE_QUERY)) {
            statement.setBigDecimal(1, auction.getCurrentPrice());
            statement.setString(2, auction.getStatus().name());
            setNullableString(statement, 3, auction.getLeadingBidderId());
            setNullableString(statement, 4, auction.getWinnerBidderId());
            statement.setTimestamp(5, Timestamp.valueOf(auction.getEndTime()));
            statement.setString(6, auction.getId());
            return statement.executeUpdate() == 1;
        } catch (SQLException e) {
            throw new DatabaseException("Failed to update auction state.", e);
        }
    }

    private void ensureAuctionsTable() {
        try (Connection connection = databaseManager.getConnection();
             Statement statement = connection.createStatement()) {
            statement.executeUpdate(CREATE_AUCTIONS_TABLE_QUERY);
        } catch (SQLException e) {
            throw new DatabaseException("Failed to create auctions table.", e);
        }
    }

    private Auction mapAuction(ResultSet resultSet) throws SQLException {
        ItemType itemType = ItemType.valueOf(resultSet.getString("item_type"));
        ItemFactory itemFactory = switch (itemType) {
            case ART -> new ArtFactory();
            case ELECTRONICS -> new ElectronicsFactory();
            case VEHICLE -> new VehicleFactory();
        };
        Item item = itemFactory.createItem(resultSet);

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
                resultSet.getString("winner_bidder_id"),
                parseAuctionStatus(resultSet.getString("status"))
        );
    }

    private LocalDateTime toLocalDateTime(Timestamp timestamp) {
        if (timestamp == null) {
            throw new DatabaseException("Auction timestamp cannot be null.", new IllegalStateException());
        }
        return timestamp.toLocalDateTime();
    }

    private int getNullableIntOrDefault(ResultSet resultSet, String columnName, int fallback) throws SQLException {
        int value = resultSet.getInt(columnName);
        if (resultSet.wasNull()) {
            return fallback;
        }
        return value;
    }

    private DatabaseException unsupportedItemTypeError(String itemTypeName) {
        return new DatabaseException(
                "Unsupported item class: " + itemTypeName,
                new IllegalArgumentException(itemTypeName)
        );
    }

    private AuctionStatus parseAuctionStatus(String rawStatus) {
        if (rawStatus == null || rawStatus.isBlank()) {
            return AuctionStatus.OPEN;
        }
        return AuctionStatus.valueOf(rawStatus);
    }

    private void setNullableString(PreparedStatement statement, int parameterIndex, String value) throws SQLException {
        if (value == null || value.isBlank()) {
            statement.setNull(parameterIndex, java.sql.Types.VARCHAR);
            return;
        }
        statement.setString(parameterIndex, value);
    }
}

