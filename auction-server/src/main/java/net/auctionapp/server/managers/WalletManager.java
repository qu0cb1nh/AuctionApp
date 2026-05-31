package net.auctionapp.server.managers;

import net.auctionapp.common.auction.AuctionStatus;
import net.auctionapp.common.exceptions.ValidationException;
import net.auctionapp.common.messages.Message;
import net.auctionapp.common.messages.MessageType;
import net.auctionapp.common.messages.system.ErrorResponseMessage;
import net.auctionapp.common.messages.wallet.DepositRequestMessage;
import net.auctionapp.common.messages.wallet.GetWalletRequestMessage;
import net.auctionapp.common.messages.wallet.WalletResponseMessage;
import net.auctionapp.common.messages.wallet.WithdrawRequestMessage;
import net.auctionapp.common.utils.MoneyUtil;
import net.auctionapp.common.utils.StringUtil;
import net.auctionapp.server.ClientHandler;
import net.auctionapp.server.dao.AuctionDao;
import net.auctionapp.server.dao.BalanceDao;
import net.auctionapp.server.exceptions.AuthenticationException;
import net.auctionapp.server.exceptions.DatabaseException;
import net.auctionapp.server.exceptions.InsufficientFundsException;
import net.auctionapp.server.exceptions.NotFoundException;
import net.auctionapp.server.messages.MessageRouter;
import net.auctionapp.server.models.auction.Auction;
import net.auctionapp.server.models.auction.BidTransaction;
import net.auctionapp.server.models.users.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

public final class WalletManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(WalletManager.class);
    private static final WalletManager INSTANCE = new WalletManager();

    private final AuthManager authManager = AuthManager.getInstance();
    private final SessionManager sessionManager = SessionManager.getInstance();
    private volatile BalanceDao balanceDao;
    private volatile AuctionDao auctionDao;

    private WalletManager() {
    }

    public static WalletManager getInstance() {
        return INSTANCE;
    }

    public void registerCommands(MessageRouter messageRouter) {
        messageRouter.register(MessageType.DEPOSIT_REQUEST, DepositRequestMessage.class, this::handleDeposit);
        messageRouter.register(MessageType.WITHDRAW_REQUEST, WithdrawRequestMessage.class, this::handleWithdraw);
        messageRouter.register(MessageType.GET_WALLET_REQUEST, GetWalletRequestMessage.class, this::handleGetWallet);
    }

    public void setBalanceDao(BalanceDao balanceDao) {
        this.balanceDao = balanceDao;
    }

    public void setAuctionDao(AuctionDao auctionDao) {
        this.auctionDao = auctionDao;
    }

    public void handleDeposit(DepositRequestMessage request, ClientHandler clientHandler) {
        try {
            clientHandler.ensureAuthenticated();
            BigDecimal amount = request.getAmount();
            MoneyUtil.requirePositiveMoney(amount, "Deposit amount");

            User user = deposit(clientHandler.getAuthenticatedId(), amount);
            clientHandler.sendResponse(walletResponse(user, "Deposit successful."), request);
            LOGGER.info("User '{}' deposited ${}.", user.getUsername(), amount);
        } catch (ValidationException | InsufficientFundsException
                 | AuthenticationException | NotFoundException e) {
            sendError(clientHandler, request, e.getMessage());
        } catch (DatabaseException e) {
            sendError(clientHandler, request, "Unable to process deposit.");
            LOGGER.warn("Deposit persistence failed: {}", e.getMessage(), e);
        } catch (RuntimeException e) {
            sendError(clientHandler, request, "Unable to process deposit.");
            LOGGER.warn("Deposit failed unexpectedly: {}", e.getMessage(), e);
        }
    }

    public void handleWithdraw(WithdrawRequestMessage request, ClientHandler clientHandler) {
        try {
            clientHandler.ensureAuthenticated();
            BigDecimal amount = request.getAmount();
            MoneyUtil.requirePositiveMoney(amount, "Withdraw amount");

            User user = withdraw(clientHandler.getAuthenticatedId(), amount);
            clientHandler.sendResponse(walletResponse(user, "Withdrawal successful."), request);
            LOGGER.info("User '{}' withdrew ${}.", user.getUsername(), amount);
        } catch (ValidationException | InsufficientFundsException
                 | AuthenticationException | NotFoundException e) {
            sendError(clientHandler, request, e.getMessage());
        } catch (DatabaseException e) {
            sendError(clientHandler, request, "Unable to process withdrawal.");
            LOGGER.warn("Withdrawal persistence failed: {}", e.getMessage(), e);
        } catch (RuntimeException e) {
            sendError(clientHandler, request, "Unable to process withdrawal.");
            LOGGER.warn("Withdrawal failed unexpectedly: {}", e.getMessage(), e);
        }
    }

    public void handleGetWallet(GetWalletRequestMessage request, ClientHandler clientHandler) {
        try {
            clientHandler.ensureAuthenticated();
            User user = authManager.requireActiveUserById(clientHandler.getAuthenticatedId());
            clientHandler.sendResponse(walletResponse(user, "Wallet loaded."), request);
        } catch (AuthenticationException | NotFoundException e) {
            sendError(clientHandler, request, e.getMessage());
        } catch (DatabaseException e) {
            sendError(clientHandler, request, "Unable to load wallet.");
            LOGGER.warn("Wallet persistence request failed: {}", e.getMessage(), e);
        } catch (RuntimeException e) {
            sendError(clientHandler, request, "Unable to load wallet.");
            LOGGER.warn("Wallet request failed unexpectedly: {}", e.getMessage(), e);
        }
    }

    public void applyLockedBidFunds(String bidderId, BigDecimal amount) {
        MoneyUtil.requirePositiveMoney(amount, "Bid amount");
        String normalizedBidderId = StringUtil.normalizeString(bidderId);
        User bidder = authManager.requireUserById(normalizedBidderId);
        updateCachedWallet(bidder, amount.negate(), amount);
        pushWalletUpdate(bidder);
        LOGGER.info("Locked {} pending funds for bidder {}.", amount, normalizedBidderId);
    }

    public BigDecimal getBidderCommitment(Auction auction, String bidderId) {
        if (auction == null || bidderId == null || bidderId.isBlank()) {
            return BigDecimal.ZERO;
        }
        return getCommittedAmountsByBidder(auction).getOrDefault(StringUtil.normalizeString(bidderId), BigDecimal.ZERO);
    }

    public void applyReleasedFunds(Map<String, BigDecimal> releasedFundsByBidder) {
        if (releasedFundsByBidder == null) {
            return;
        }
        for (Map.Entry<String, BigDecimal> entry : releasedFundsByBidder.entrySet()) {
            BigDecimal amount = entry.getValue();
            if (amount == null || amount.signum() <= 0) {
                continue;
            }
            User bidder = authManager.requireUserById(StringUtil.normalizeString(entry.getKey()));
            updateCachedWallet(bidder, amount, amount.negate());
            pushWalletUpdate(bidder);
            LOGGER.info("Released {} pending funds for bidder {}.", amount, bidder.getId());
        }
    }

    public void closeAuctionWallets(Auction auction) {
        if (auction == null || auction.getId() == null || auction.getId().isBlank()) {
            return;
        }
        Map<String, BigDecimal> committedAmountsByBidder = getCommittedAmountsByBidder(auction);
        LOGGER.info(
                "Closing wallets for auction {} with status {} and {} committed bidder(s).",
                auction.getId(),
                auction.getStatus(),
                committedAmountsByBidder.size()
        );
        String winnerId = StringUtil.normalizeString(auction.getWinnerBidderId());
        BigDecimal winningAmount = auction.getCurrentPrice();
        boolean hasWinner = !winnerId.isEmpty() && auction.getStatus() == AuctionStatus.PAID;
        User winner = hasWinner ? authManager.requireUserById(winnerId) : null;
        User seller = hasWinner ? authManager.requireUserById(StringUtil.normalizeString(auction.getSellerId())) : null;
        Map<String, User> biddersToRelease = new HashMap<>();
        for (Map.Entry<String, BigDecimal> entry : committedAmountsByBidder.entrySet()) {
            String bidderId = entry.getKey();
            if (hasWinner && bidderId.equalsIgnoreCase(winnerId)) {
                continue;
            }
            biddersToRelease.put(bidderId, authManager.requireUserById(bidderId));
        }

        persistAuctionState(auction, committedAmountsByBidder);

        if (hasWinner) {
            updateCachedWallet(winner, BigDecimal.ZERO, winningAmount.negate());
            updateCachedWallet(seller, winningAmount, BigDecimal.ZERO);
            pushWalletUpdate(winner);
            pushWalletUpdate(seller);
            LOGGER.info("Winner {} paid {}. Seller {} received funds for auction {}",
                    winnerId, winningAmount, auction.getSellerId(), auction.getId());
        }

        for (Map.Entry<String, User> entry : biddersToRelease.entrySet()) {
            String bidderId = entry.getKey();
            BigDecimal committedAmount = committedAmountsByBidder.get(bidderId);
            updateCachedWallet(entry.getValue(), committedAmount, committedAmount.negate());
            pushWalletUpdate(entry.getValue());
            LOGGER.info("Released {} pending funds for non-winning bidder {} in auction {}.",
                    committedAmount, bidderId, auction.getId());
        }
    }

    private User deposit(String userId, BigDecimal amount) {
        String normalizedUserId = StringUtil.normalizeString(userId);
        User user = authManager.requireUserById(normalizedUserId);
        if (requireBalanceDao().increaseBalance(normalizedUserId, amount)) {
            updateCachedWallet(user, amount, BigDecimal.ZERO);
            pushWalletUpdate(user);
            return user;
        }
        throw new DatabaseException("Failed to process deposit.");
    }

    private User withdraw(String userId, BigDecimal amount) {
        String normalizedUserId = StringUtil.normalizeString(userId);
        User user = authManager.requireUserById(normalizedUserId);
        if (requireBalanceDao().tryDecreaseBalance(normalizedUserId, amount)) {
            updateCachedWallet(user, amount.negate(), BigDecimal.ZERO);
            pushWalletUpdate(user);
            return user;
        }
        throw new InsufficientFundsException("Insufficient liquid balance for withdrawal.");
    }

    private void persistAuctionState(Auction auction, Map<String, BigDecimal> committedAmountsByBidder) {
        AuctionDao dao = auctionDao;
        if (dao == null) {
            return;
        }
        try {
            if (dao.settleAuction(auction, committedAmountsByBidder)) {
                return;
            }
        } catch (DatabaseException e) {
            throw new DatabaseException("Failed to persist auction state.", e);
        }
        throw new DatabaseException("Auction state could not be persisted.");
    }

    public Map<String, BigDecimal> getCommittedAmountsByBidder(Auction auction) {
        Map<String, BigDecimal> committedAmounts = new HashMap<>();
        if (auction == null) {
            return committedAmounts;
        }
        for (BidTransaction bid : auction.getBidHistory()) {
            if (bid == null || !bid.isActive() || bid.getBidderId() == null || bid.getAmount() == null) {
                continue;
            }
            String bidderId = StringUtil.normalizeString(bid.getBidderId());
            if (bidderId.isEmpty()) {
                continue;
            }
            committedAmounts.merge(bidderId, bid.getAmount(), BigDecimal::max);
        }
        return committedAmounts;
    }

    private void updateCachedWallet(User user, BigDecimal balanceDelta, BigDecimal pendingDelta) {
        user.addBalance(balanceDelta);
        user.addPendingBalance(pendingDelta);
        LOGGER.debug(
                "Updated cached wallet for user {} with balance delta {} and pending delta {}.",
                user.getId(),
                balanceDelta,
                pendingDelta
        );
    }

    private WalletResponseMessage walletResponse(User user, String message) {
        return new WalletResponseMessage(
                MessageType.WALLET_RESPONSE,
                user.getBalance(),
                user.getPendingBalance(),
                message
        );
    }

    private void pushWalletUpdate(User user) {
        WalletResponseMessage update = new WalletResponseMessage(
                MessageType.BALANCE_UPDATE,
                user.getBalance(),
                user.getPendingBalance(),
                null
        );
        for (ClientHandler clientHandler : sessionManager.getClientsByUserId(user.getId())) {
            clientHandler.sendMessage(update);
        }
        LOGGER.debug("Pushed wallet update for user {}.", user.getId());
    }

    private BalanceDao requireBalanceDao() {
        if (balanceDao == null) {
            throw new DatabaseException("Balance persistence is not configured.");
        }
        return balanceDao;
    }

    private void sendError(ClientHandler clientHandler, Message request, String message) {
        clientHandler.sendResponse(new ErrorResponseMessage(message), request);
    }
}
