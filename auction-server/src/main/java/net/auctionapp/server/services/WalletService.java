package net.auctionapp.server.services;

import net.auctionapp.common.auction.AuctionStatus;
import net.auctionapp.common.messages.MessageType;
import net.auctionapp.common.messages.types.DepositRequestMessage;
import net.auctionapp.common.messages.types.ErrorMessage;
import net.auctionapp.common.messages.types.GetWalletRequestMessage;
import net.auctionapp.common.messages.types.WalletResponseMessage;
import net.auctionapp.common.messages.types.WithdrawRequestMessage;
import net.auctionapp.common.utils.MoneyUtil;
import net.auctionapp.common.utils.StringUtil;
import net.auctionapp.server.ClientHandler;
import net.auctionapp.server.dao.AuctionDao;
import net.auctionapp.server.dao.UserDao;
import net.auctionapp.server.exceptions.AuctionAppException;
import net.auctionapp.server.exceptions.DatabaseException;
import net.auctionapp.server.exceptions.ValidationException;
import net.auctionapp.server.managers.SessionManager;
import net.auctionapp.server.models.auction.Auction;
import net.auctionapp.server.models.auction.BidTransaction;
import net.auctionapp.server.models.users.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

public final class WalletService {
    private static final Logger LOGGER = LoggerFactory.getLogger(WalletService.class);
    private static final WalletService INSTANCE = new WalletService();

    private final AuthService authService = AuthService.getInstance();
    private final SessionManager sessionManager = SessionManager.getInstance();
    private volatile UserDao userDao;
    private volatile AuctionDao auctionDao;

    private WalletService() {
    }

    public static WalletService getInstance() {
        return INSTANCE;
    }

    public void setUserDao(UserDao userDao) {
        this.userDao = userDao;
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
        } catch (IllegalArgumentException e) {
            sendError(clientHandler, request, e.getMessage());
        } catch (AuctionAppException e) {
            sendError(clientHandler, request, e.getMessage());
            LOGGER.warn("Deposit failed: {}", e.getMessage());
        } catch (RuntimeException e) {
            sendError(clientHandler, request, e.getMessage());
            LOGGER.warn("Deposit failed: {}", e.getMessage());
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
        } catch (IllegalArgumentException e) {
            sendError(clientHandler, request, e.getMessage());
        } catch (AuctionAppException e) {
            sendError(clientHandler, request, e.getMessage());
            LOGGER.warn("Withdrawal failed: {}", e.getMessage());
        } catch (RuntimeException e) {
            sendError(clientHandler, request, e.getMessage());
            LOGGER.warn("Withdrawal failed: {}", e.getMessage());
        }
    }

    public void handleGetWallet(GetWalletRequestMessage request, ClientHandler clientHandler) {
        try {
            clientHandler.ensureAuthenticated();
            User user = authService.requireActiveUserById(clientHandler.getAuthenticatedId());
            clientHandler.sendResponse(walletResponse(user, "Wallet loaded."), request);
        } catch (AuctionAppException e) {
            sendError(clientHandler, request, e.getMessage());
            LOGGER.warn("Wallet request failed: {}", e.getMessage());
        } catch (RuntimeException e) {
            sendError(clientHandler, request, e.getMessage());
            LOGGER.warn("Wallet request failed: {}", e.getMessage());
        }
    }

    public void applyLockedBidFunds(String bidderId, BigDecimal amount) {
        MoneyUtil.requirePositiveMoney(amount, "Bid amount");
        String normalizedBidderId = StringUtil.normalizeString(bidderId);
        User bidder = authService.requireUserById(normalizedBidderId);
        updateCachedWallet(bidder, amount.negate(), amount);
        pushWalletUpdate(bidder);
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
            User bidder = authService.requireUserById(StringUtil.normalizeString(entry.getKey()));
            updateCachedWallet(bidder, amount, amount.negate());
            pushWalletUpdate(bidder);
        }
    }

    public void closeAuctionWallets(Auction auction) {
        if (auction == null || auction.getId() == null || auction.getId().isBlank()) {
            return;
        }
        Map<String, BigDecimal> committedAmountsByBidder = getCommittedAmountsByBidder(auction);
        String winnerId = StringUtil.normalizeString(auction.getWinnerBidderId());
        BigDecimal winningAmount = auction.getCurrentPrice();
        boolean hasWinner = !winnerId.isEmpty() && auction.getStatus() == AuctionStatus.PAID;
        User winner = hasWinner ? authService.requireUserById(winnerId) : null;
        User seller = hasWinner ? authService.requireUserById(StringUtil.normalizeString(auction.getSellerId())) : null;
        Map<String, User> biddersToRelease = new HashMap<>();
        for (Map.Entry<String, BigDecimal> entry : committedAmountsByBidder.entrySet()) {
            String bidderId = entry.getKey();
            if (hasWinner && bidderId.equalsIgnoreCase(winnerId)) {
                continue;
            }
            biddersToRelease.put(bidderId, authService.requireUserById(bidderId));
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
        }
    }

    private User deposit(String userId, BigDecimal amount) {
        String normalizedUserId = StringUtil.normalizeString(userId);
        User user = authService.requireUserById(normalizedUserId);
        if (requireUserDao().increaseBalance(normalizedUserId, amount)) {
            updateCachedWallet(user, amount, BigDecimal.ZERO);
            pushWalletUpdate(user);
            return user;
        }
        throw new DatabaseException("Failed to process deposit.");
    }

    private User withdraw(String userId, BigDecimal amount) {
        String normalizedUserId = StringUtil.normalizeString(userId);
        User user = authService.requireUserById(normalizedUserId);
        if (requireUserDao().tryDecreaseBalance(normalizedUserId, amount)) {
            updateCachedWallet(user, amount.negate(), BigDecimal.ZERO);
            pushWalletUpdate(user);
            return user;
        }
        throw new ValidationException("Insufficient liquid balance for withdrawal.");
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
            throw new AuctionAppException("Failed to persist auction state.");
        }
        throw new AuctionAppException("Auction state could not be persisted.");
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
        if (user == null) {
            return;
        }
        user.addBalance(balanceDelta);
        user.addPendingBalance(pendingDelta);
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
        if (user == null) {
            return;
        }
        WalletResponseMessage update = new WalletResponseMessage(
                MessageType.BALANCE_UPDATE,
                user.getBalance(),
                user.getPendingBalance(),
                null
        );
        for (ClientHandler clientHandler : sessionManager.getClientsByUserId(user.getId())) {
            clientHandler.sendMessage(update);
        }
    }

    private UserDao requireUserDao() {
        if (userDao == null) {
            throw new IllegalStateException("User DAO has not been configured.");
        }
        return userDao;
    }

    private void sendError(ClientHandler clientHandler, DepositRequestMessage request, String message) {
        clientHandler.sendResponse(new ErrorMessage(message), request);
    }

    private void sendError(ClientHandler clientHandler, WithdrawRequestMessage request, String message) {
        clientHandler.sendResponse(new ErrorMessage(message), request);
    }

    private void sendError(ClientHandler clientHandler, GetWalletRequestMessage request, String message) {
        clientHandler.sendResponse(new ErrorMessage(message), request);
    }
}
