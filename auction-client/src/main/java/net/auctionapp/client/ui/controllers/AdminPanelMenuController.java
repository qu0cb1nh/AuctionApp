package net.auctionapp.client.ui.controllers;

import net.auctionapp.client.ui.controllers.components.HeaderController;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.css.PseudoClass;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.VBox;
import net.auctionapp.client.services.AdminService;
import net.auctionapp.client.services.AuctionService;
import net.auctionapp.client.ClientSession;
import net.auctionapp.client.ui.managers.NotificationToastManager;
import net.auctionapp.client.ui.managers.SceneManager;
import net.auctionapp.client.utils.AuctionDisplayUtil;
import net.auctionapp.client.utils.FxViewUtil;
import net.auctionapp.common.messages.Message;
import net.auctionapp.common.messages.admin.SetUserBanResponseMessage;
import net.auctionapp.common.messages.admin.GetUserListResponseMessage;
import net.auctionapp.common.dto.AdminUserDto;
import net.auctionapp.common.messages.auction.AuctionListResponseMessage;
import net.auctionapp.common.dto.AuctionSummaryDto;
import net.auctionapp.common.messages.system.ErrorResponseMessage;
import net.auctionapp.common.users.UserRole;
import net.auctionapp.common.utils.StringUtil;


public class AdminPanelMenuController {
    private static final PseudoClass ACTIVE_STATE = PseudoClass.getPseudoClass("active");
    private static final PseudoClass ERROR_STATE = PseudoClass.getPseudoClass("error");
    private static final PseudoClass SUCCESS_STATE = PseudoClass.getPseudoClass("success");

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
    private final BooleanProperty userBanButtonsDisabled = new SimpleBooleanProperty();

    @FXML
    public void initialize() {
        appHeaderController.setupHeader("Admin panel");
        configureTables();
        banButton.disableProperty().bind(userBanButtonsDisabled);
        unbanButton.disableProperty().bind(userBanButtonsDisabled);
        updateSectionSelection(AdminSection.USER_MANAGEMENT);
        if (!ClientSession.getInstance().isAdmin()) {
            setErrorStatus("Admin privileges are required to view this page.");
            disableAdminActions();
            return;
        }
        setActiveSection(AdminSection.USER_MANAGEMENT);
        handleRefreshUsers();
    }

    @FXML
    public void handleRefreshUsers() {
        setNeutralStatus("Loading users...");
        AdminService.getInstance().requestUsers(this::handleUsersResponse);
    }

    @FXML
    public void handleBanSelected() {
        updateSelectedUserBanStatus(true);
    }

    @FXML
    public void handleUnbanSelected() {
        updateSelectedUserBanStatus(false);
    }

    private void updateSelectedUserBanStatus(boolean banned) {
        UserRow selected = usersTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            setErrorStatus("Please select a user to " + (banned ? "ban." : "unban."));
            return;
        }
        userBanButtonsDisabled.set(true);
        AdminService.getInstance().updateUserBanStatus(selected.userId(), banned, this::handleUserBanUpdateResponse);
    }

    @FXML
    public void handleRefreshAuctions() {
        setNeutralStatus("Loading auctions...");
        AuctionService.getInstance().requestAuctionList(this::handleAuctionListResponse);
    }

    @FXML
    public void handleShowUserManagement() {
        setActiveSection(AdminSection.USER_MANAGEMENT);
        if (!usersLoaded) {
            handleRefreshUsers();
        }
    }

    @FXML
    public void handleShowAuctionManagement() {
        setActiveSection(AdminSection.AUCTION_MANAGEMENT);
        if (!auctionsLoaded) {
            handleRefreshAuctions();
        }
    }

    @FXML
    public void handleOpenAuction() {
        AuctionRow selected = auctionsTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            setErrorStatus("Please select an auction to open.");
            return;
        }
        SceneManager.switchToAuctionDetails(selected.auctionId());
    }

    @FXML
    public void handleManageAuction() {
        AuctionRow selected = auctionsTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            setErrorStatus("Please select an auction to manage.");
            return;
        }
        SceneManager.switchToManageAuction(selected.auctionId());
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
        if (message instanceof ErrorResponseMessage errorMessage) {
            setErrorStatus(errorMessage.getErrorMessage());
            return;
        }
        if (!(message instanceof GetUserListResponseMessage response)) {
            setErrorStatus("Unexpected response while loading users.");
            return;
        }

        usersTable.getItems().clear();
        for (AdminUserDto user : response.getUsers()) {
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
        userBanButtonsDisabled.set(false);
        if (message instanceof ErrorResponseMessage errorMessage) {
            setErrorStatus(errorMessage.getErrorMessage());
            return;
        }
        if (!(message instanceof SetUserBanResponseMessage result)) {
            setErrorStatus("Unexpected response while updating user ban state.");
            return;
        }
        setSuccessStatus(result.getMessage());
        NotificationToastManager.showSuccess(result.getMessage());
        handleRefreshUsers();
    }

    private void handleAuctionListResponse(Message message) {
        if (message instanceof ErrorResponseMessage errorMessage) {
            setErrorStatus(errorMessage.getErrorMessage());
            return;
        }
        if (!(message instanceof AuctionListResponseMessage response)) {
            setErrorStatus("Unexpected response while loading auctions.");
            return;
        }

        auctionsTable.getItems().clear();
        for (AuctionSummaryDto summary : response.getAuctions()) {
            if (summary == null) {
                continue;
            }
            auctionsTable.getItems().add(new AuctionRow(
                    summary.getAuctionId(),
                    summary.getTitle(),
                    AuctionDisplayUtil.displayStatus(summary),
                    AuctionDisplayUtil.formatPrice(summary.getCurrentPrice()),
                    AuctionDisplayUtil.formatDateTime(summary.getStartTime()),
                    AuctionDisplayUtil.formatDateTime(summary.getEndTime())
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
        userBanButtonsDisabled.set(true);
        openAuctionButton.setDisable(true);
        manageAuctionButton.setDisable(true);
    }

    private void setActiveSection(AdminSection section) {
        boolean userSectionActive = section == AdminSection.USER_MANAGEMENT;
        FxViewUtil.setVisible(userManagementPage, userSectionActive);
        FxViewUtil.setVisible(auctionManagementPage, !userSectionActive);
        updateSectionSelection(section);
    }

    private void updateSectionSelection(AdminSection section) {
        boolean userSectionActive = section == AdminSection.USER_MANAGEMENT;
        userManagementNavLabel.pseudoClassStateChanged(ACTIVE_STATE, userSectionActive);
        auctionManagementNavLabel.pseudoClassStateChanged(ACTIVE_STATE, !userSectionActive);
    }

    private void setErrorStatus(String text) {
        setStatus(text, ERROR_STATE);
    }

    private void setSuccessStatus(String text) {
        setStatus(text, SUCCESS_STATE);
    }

    private void setNeutralStatus(String text) {
        setStatus(text, null);
    }

    private void clearStatus() {
        statusLabel.setText("");
        FxViewUtil.setVisible(statusLabel, false);
    }

    private void setStatus(String text, PseudoClass state) {
        if (text == null || text.isBlank()) {
            clearStatus();
            return;
        }
        FxViewUtil.setVisible(statusLabel, true);
        statusLabel.pseudoClassStateChanged(ERROR_STATE, state == ERROR_STATE);
        statusLabel.pseudoClassStateChanged(SUCCESS_STATE, state == SUCCESS_STATE);
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
