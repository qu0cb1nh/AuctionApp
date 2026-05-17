package net.auctionapp.server.dao;

import net.auctionapp.server.models.auction.Auction;
import net.auctionapp.common.auction.AuctionStatus;
import net.auctionapp.server.models.items.Art;
import net.auctionapp.server.models.items.Electronics;
import net.auctionapp.server.models.items.Item;
import net.auctionapp.common.items.ItemType;
import net.auctionapp.server.models.items.Vehicle;
import net.auctionapp.server.exceptions.DatabaseException;
import net.auctionapp.server.factories.ArtFactory;
import net.auctionapp.server.factories.ElectronicsFactory;
import net.auctionapp.server.factories.ItemFactory;
import net.auctionapp.server.factories.VehicleFactory;
import net.auctionapp.server.services.DatabaseService;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
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
                image_url TEXT,
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
    private static final String DELETE_AUCTION_BY_ID_QUERY =
            "DELETE FROM auctions WHERE id = ?";

    private final DatabaseService databaseService;

    public JdbcAuctionDao() {
        this(DatabaseService.getInstance());
    }

    public JdbcAuctionDao(DatabaseService databaseService) {
        this.databaseService = databaseService;
        ensureAuctionsTable();
    }

    @Override
    public List<Auction> findAllAuctions() {
        List<Auction> auctions = new ArrayList<>();
        try (Connection connection = databaseService.getConnection();
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
            bindItemAttributes(statement, 16, item);

            return statement.executeUpdate() == 1;
        } catch (SQLException e) {
            throw new DatabaseException("Failed to create auction.", e);
        }
    }

    @Override
    public boolean updateAuctionState(Auction auction) {
        try (Connection connection = databaseService.getConnection();
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
            bindItemAttributes(statement, 12, item);
            statement.setString(17, auction.getId());
            return statement.executeUpdate() == 1;
        } catch (SQLException e) {
            throw new DatabaseException("Failed to update auction.", e);
        }
    }

    @Override
    public boolean deleteAuctionById(String auctionId) {
        try (Connection connection = databaseService.getConnection();
             PreparedStatement statement = connection.prepareStatement(DELETE_AUCTION_BY_ID_QUERY)) {
            statement.setString(1, auctionId);
            return statement.executeUpdate() == 1;
        } catch (SQLException e) {
            throw new DatabaseException("Failed to delete auction.", e);
        }
    }

    private void ensureAuctionsTable() {
        try (Connection connection = databaseService.getConnection();
             Statement statement = connection.createStatement()) {
            statement.executeUpdate(CREATE_AUCTIONS_TABLE_QUERY);
            ensureImageUrlColumn(connection, statement);
        } catch (SQLException e) {
            throw new DatabaseException("Failed to create auctions table.", e);
        }
    }

    private void ensureImageUrlColumn(Connection connection, Statement statement) throws SQLException {
        DatabaseMetaData metadata = connection.getMetaData();
        try (ResultSet columns = metadata.getColumns(null, null, "auctions", "image_url")) {
            if (columns.next()) {
                return;
            }
        }
        statement.executeUpdate("ALTER TABLE auctions ADD COLUMN image_url TEXT");
    }

    private Auction mapAuction(ResultSet resultSet) throws SQLException {
        ItemType itemType = ItemType.valueOf(resultSet.getString("item_type"));
        ItemFactory itemFactory = switch (itemType) {
            case ART -> new ArtFactory();
            case ELECTRONICS -> new ElectronicsFactory();
            case VEHICLE -> new VehicleFactory();
        };
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

    private void bindItemAttributes(PreparedStatement statement, int startIndex, Item item) throws SQLException {
        if (item instanceof Electronics electronics) {
            statement.setString(startIndex, electronics.getBrand());
            statement.setString(startIndex + 1, electronics.getModel());
            statement.setInt(startIndex + 2, electronics.getWarrantyMonths());
            statement.setNull(startIndex + 3, java.sql.Types.VARCHAR);
            statement.setNull(startIndex + 4, java.sql.Types.INTEGER);
            return;
        }
        if (item instanceof Vehicle vehicle) {
            statement.setString(startIndex, vehicle.getBrand());
            statement.setString(startIndex + 1, vehicle.getModel());
            statement.setNull(startIndex + 2, java.sql.Types.INTEGER);
            statement.setNull(startIndex + 3, java.sql.Types.VARCHAR);
            statement.setInt(startIndex + 4, vehicle.getYearCreated());
            return;
        }
        if (item instanceof Art art) {
            statement.setNull(startIndex, java.sql.Types.VARCHAR);
            statement.setNull(startIndex + 1, java.sql.Types.VARCHAR);
            statement.setNull(startIndex + 2, java.sql.Types.INTEGER);
            statement.setString(startIndex + 3, art.getAuthor());
            statement.setInt(startIndex + 4, art.getYearCreated());
            return;
        }
        throw unsupportedItemTypeError(item.getClass().getName());
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

    private void setNullableString(PreparedStatement statement, int parameterIndex, String value) throws SQLException {
        if (value == null || value.isBlank()) {
            statement.setNull(parameterIndex, java.sql.Types.VARCHAR);
            return;
        }
        statement.setString(parameterIndex, value);
    }
}
