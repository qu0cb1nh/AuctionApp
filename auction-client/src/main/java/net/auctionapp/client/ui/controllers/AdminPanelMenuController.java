package net.auctionapp.client.ui.controllers;

import net.auctionapp.client.ui.controllers.components.HeaderController;

import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.input.MouseEvent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.VBox;
import net.auctionapp.client.ClientApp;
import net.auctionapp.client.services.AdminService;
import net.auctionapp.client.services.AuctionService;
import net.auctionapp.client.ClientSession;
import net.auctionapp.client.ui.managers.SceneManager;
import net.auctionapp.common.messages.Message;
import net.auctionapp.common.messages.types.AdminActionResultMessage;
import net.auctionapp.common.messages.types.AdminGetUsersResponseMessage;
import net.auctionapp.common.messages.types.AdminUserViewMessage;
import net.auctionapp.common.messages.types.AuctionListResponseMessage;
import net.auctionapp.common.messages.types.AuctionSummary;
import net.auctionapp.common.messages.types.ErrorMessage;
import net.auctionapp.common.auction.AuctionStatus;
import net.auctionapp.common.users.UserRole;
import net.auctionapp.common.utils.StringUtil;

import java.net.URL;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.ResourceBundle;

public class AdminPanelMenuController implements Initializable {
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    @FXML
    private HeaderController appHeaderController;
    @FXML
    private Label statusLabel;
    @FXML
    private Label userManagementNavLabel;
    @FXML
    private Label auctionManagementNavLabel;
    @FXML
    private VBox userManagementPage;
    @FXML
    private VBox auctionManagementPage;

    @FXML
    private TableView<UserRow> usersTable;
    @FXML
    private TableColumn<UserRow, String> userIdColumn;
    @FXML
    private TableColumn<UserRow, String> usernameColumn;
    @FXML
    private TableColumn<UserRow, String> roleColumn;
    @FXML
    private TableColumn<UserRow, Boolean> bannedColumn;
    @FXML
    private TableColumn<UserRow, Boolean> onlineColumn;
    @FXML
    private Button banButton;
    @FXML
    private Button unbanButton;

    @FXML
    private TableView<AuctionRow> auctionsTable;
    @FXML
    private TableColumn<AuctionRow, String> auctionIdColumn;
    @FXML
    private TableColumn<AuctionRow, String> auctionTitleColumn;
    @FXML
    private TableColumn<AuctionRow, String> auctionStatusColumn;
    @FXML
    private TableColumn<AuctionRow, String> auctionCurrentPriceColumn;
    @FXML
    private TableColumn<AuctionRow, String> auctionStartTimeColumn;
    @FXML
    private TableColumn<AuctionRow, String> auctionEndTimeColumn;
    @FXML
    private Button openAuctionButton;
    @FXML
    private Button manageAuctionButton;

    private boolean usersLoaded;
    private boolean auctionsLoaded;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        appHeaderController.setupHeader("Admin Panel", true);
        configureTables();
        boolean isAdmin = ClientSession.getInstance().isAdmin();
        if (!isAdmin) {
            setErrorStatus("Admin privileges are required to view this page.");
            disableAdminActions();
            return;
        }
        setActiveSection(AdminSection.USER_MANAGEMENT);
        handleRefreshUsers(null);
    }

    @FXML
    public void handleRefreshUsers(ActionEvent event) {
        setNeutralStatus("Loading users...");
        AdminService.getInstance().requestUsers(this::handleUsersResponse);
    }

    @FXML
    public void handleBanSelected(ActionEvent event) {
        UserRow selected = usersTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            setErrorStatus("Please select a user to ban.");
            return;
        }
        AdminService.getInstance().updateUserBanStatus(selected.userId(), true, this::handleUserBanUpdateResponse);
    }

    @FXML
    public void handleUnbanSelected(ActionEvent event) {
        UserRow selected = usersTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            setErrorStatus("Please select a user to unban.");
            return;
        }
        AdminService.getInstance().updateUserBanStatus(selected.userId(), false, this::handleUserBanUpdateResponse);
    }

    @FXML
    public void handleRefreshAuctions(ActionEvent event) {
        setNeutralStatus("Loading auctions...");
        AuctionService.getInstance().requestAuctionList(this::handleAuctionListResponse);
    }

    @FXML
    public void handleShowUserManagement(MouseEvent event) {
        setActiveSection(AdminSection.USER_MANAGEMENT);
        if (!usersLoaded) {
            handleRefreshUsers(null);
        }
    }

    @FXML
    public void handleShowAuctionManagement(MouseEvent event) {
        setActiveSection(AdminSection.AUCTION_MANAGEMENT);
        if (!auctionsLoaded) {
            handleRefreshAuctions(null);
        }
    }

    @FXML
    public void handleOpenAuction(ActionEvent event) {
        AuctionRow selected = auctionsTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            setErrorStatus("Please select an auction to open.");
            return;
        }
        ClientApp.getInstance().setSelectedAuctionId(selected.auctionId());
        SceneManager.switchScene("AuctionItemMenu.fxml");
    }

    @FXML
    public void handleManageAuction(ActionEvent event) {
        AuctionRow selected = auctionsTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            setErrorStatus("Please select an auction to manage.");
            return;
        }
        ClientApp.getInstance().setSelectedAuctionId(selected.auctionId());
        SceneManager.switchScene("ManageAuctionMenu.fxml");
    }

    private void configureTables() {
        usersTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        auctionsTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        userIdColumn.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().userId()));
        usernameColumn.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().username()));
        roleColumn.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().role()));
        bannedColumn.setCellValueFactory(cell -> new SimpleBooleanProperty(cell.getValue().banned()));
        onlineColumn.setCellValueFactory(cell -> new SimpleBooleanProperty(cell.getValue().online()));

        auctionIdColumn.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().auctionId()));
        auctionTitleColumn.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().title()));
        auctionStatusColumn.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().displayStatus()));
        auctionCurrentPriceColumn.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().currentPriceText()));
        auctionStartTimeColumn.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().startTimeText()));
        auctionEndTimeColumn.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().endTimeText()));
    }

    private void handleUsersResponse(Message message) {
        if (message instanceof ErrorMessage errorMessage) {
            setErrorStatus(errorMessage.getErrorMessage());
            return;
        }
        if (!(message instanceof AdminGetUsersResponseMessage response)) {
            setErrorStatus("Unexpected response while loading users.");
            return;
        }

        usersTable.getItems().clear();
        for (AdminUserViewMessage user : response.getUsers()) {
            String username = user.getUsername() == null ? "" : user.getUsername();
            String userId = user.getUserId();
            if (userId == null || userId.isBlank()) {
                userId = StringUtil.normalizeString(username);
            }
            usersTable.getItems().add(new UserRow(
                    userId,
                    username,
                    user.getRole() == null ? UserRole.USER.name() : user.getRole().name(),
                    user.isBanned(),
                    user.isOnline()
            ));
        }
        usersLoaded = true;
        clearStatus();
    }

    private void handleUserBanUpdateResponse(Message message) {
        if (message instanceof ErrorMessage errorMessage) {
            setErrorStatus(errorMessage.getErrorMessage());
            return;
        }
        if (!(message instanceof AdminActionResultMessage result)) {
            setErrorStatus("Unexpected response while updating user ban state.");
            return;
        }
        setSuccessStatus(result.getMessage());
        handleRefreshUsers(null);
    }

    private void handleAuctionListResponse(Message message) {
        if (message instanceof ErrorMessage errorMessage) {
            setErrorStatus(errorMessage.getErrorMessage());
            return;
        }
        if (!(message instanceof AuctionListResponseMessage response)) {
            setErrorStatus("Unexpected response while loading auctions.");
            return;
        }

        auctionsTable.getItems().clear();
        List<AuctionSummary> auctions = response.getAuctions() == null ? List.of() : response.getAuctions();
        for (AuctionSummary summary : auctions) {
            if (summary == null) {
                continue;
            }
            auctionsTable.getItems().add(new AuctionRow(
                    summary.getAuctionId(),
                    summary.getTitle(),
                    deriveDisplayStatus(summary.getStatus(), summary.getEndTime(), summary.getLeadingBidderId()),
                    formatPrice(summary.getCurrentPrice()),
                    formatDateTime(summary.getStartTime()),
                    formatDateTime(summary.getEndTime())
            ));
        }
        auctionsLoaded = true;
        clearStatus();
    }

    private void disableAdminActions() {
        usersTable.setDisable(true);
        auctionsTable.setDisable(true);
        userManagementNavLabel.setDisable(true);
        auctionManagementNavLabel.setDisable(true);
        banButton.setDisable(true);
        unbanButton.setDisable(true);
        openAuctionButton.setDisable(true);
        manageAuctionButton.setDisable(true);
    }

    private void setActiveSection(AdminSection section) {
        boolean userSectionActive = section == AdminSection.USER_MANAGEMENT;
        userManagementPage.setVisible(userSectionActive);
        userManagementPage.setManaged(userSectionActive);
        auctionManagementPage.setVisible(!userSectionActive);
        auctionManagementPage.setManaged(!userSectionActive);

        userManagementNavLabel.setStyle(userSectionActive
                ? "-fx-font-size: 15px; -fx-font-weight: bold; -fx-underline: true; -fx-text-fill: #153e5c; -fx-cursor: hand; -fx-padding: 6 10 6 10;"
                : "-fx-font-size: 15px; -fx-text-fill: #53677a; -fx-cursor: hand; -fx-padding: 6 10 6 10;");
        auctionManagementNavLabel.setStyle(userSectionActive
                ? "-fx-font-size: 15px; -fx-text-fill: #53677a; -fx-cursor: hand; -fx-padding: 6 10 6 10;"
                : "-fx-font-size: 15px; -fx-font-weight: bold; -fx-underline: true; -fx-text-fill: #153e5c; -fx-cursor: hand; -fx-padding: 6 10 6 10;");
    }

    private String deriveDisplayStatus(AuctionStatus status, LocalDateTime endTime, String leadingBidderId) {
        if (status == AuctionStatus.CANCELED) {
            return "CANCELED";
        }
        if (status == AuctionStatus.PAID) {
            return "PAID";
        }
        if (endTime != null && !LocalDateTime.now().isBefore(endTime)) {
            return leadingBidderId == null || leadingBidderId.isBlank() ? "CANCELED" : "PAID";
        }
        return "RUNNING";
    }

    private String formatDateTime(LocalDateTime value) {
        if (value == null) {
            return "";
        }
        return DATE_TIME_FORMATTER.format(value);
    }

    private String formatPrice(java.math.BigDecimal value) {
        if (value == null) {
            return "N/A";
        }
        return "$" + value.stripTrailingZeros().toPlainString();
    }

    private void setErrorStatus(String text) {
        setStatus(text, "-fx-text-fill: #c13c21;");
    }

    private void setSuccessStatus(String text) {
        setStatus(text, "-fx-text-fill: #1f8f4c;");
    }

    private void setNeutralStatus(String text) {
        setStatus(text, "-fx-text-fill: #3f5569;");
    }

    private void clearStatus() {
        statusLabel.setText("");
        statusLabel.setManaged(false);
        statusLabel.setVisible(false);
    }

    private void setStatus(String text, String style) {
        if (text == null || text.isBlank()) {
            clearStatus();
            return;
        }
        statusLabel.setManaged(true);
        statusLabel.setVisible(true);
        statusLabel.setStyle(style);
        statusLabel.setText(text);
    }

    private record UserRow(String userId, String username, String role, boolean banned, boolean online) {
    }

    private record AuctionRow(
            String auctionId,
            String title,
            String displayStatus,
            String currentPriceText,
            String startTimeText,
            String endTimeText
    ) {
    }

    private enum AdminSection {
        USER_MANAGEMENT,
        AUCTION_MANAGEMENT
    }
}
