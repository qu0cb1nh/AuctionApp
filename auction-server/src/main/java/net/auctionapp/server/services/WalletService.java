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

    public boolean lockBidFunds(String bidderId, BigDecimal amount) {
        MoneyUtil.requirePositiveMoney(amount, "Bid amount");
        String normalizedBidderId = StringUtil.normalizeString(bidderId);
        User bidder = authService.requireUserById(normalizedBidderId);
        if (!requireUserDao().lockFunds(normalizedBidderId, amount)) {
            return false;
        }
        updateCachedWallet(bidder, amount.negate(), amount);
        return true;
    }

    public void releaseBidFunds(String bidderId, BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            return;
        }
        String normalizedBidderId = StringUtil.normalizeString(bidderId);
        if (normalizedBidderId.isEmpty()) {
            return;
        }
        if (requireUserDao().releaseFunds(normalizedBidderId, amount)) {
            User bidder = authService.requireUserById(normalizedBidderId);
            updateCachedWallet(bidder, amount, amount.negate());
        }
    }

    public BigDecimal getBidderCommitment(Auction auction, String bidderId) {
        if (auction == null || bidderId == null || bidderId.isBlank()) {
            return BigDecimal.ZERO;
        }
        return getCommittedAmountsByBidder(auction).getOrDefault(StringUtil.normalizeString(bidderId), BigDecimal.ZERO);
    }

    public void closeAuctionWallets(Auction auction) {
        if (auction == null || auction.getId() == null || auction.getId().isBlank()) {
            return;
        }
        Map<String, BigDecimal> committedAmountsByBidder = getCommittedAmountsByBidder(auction);
        persistAuctionClose(auction, committedAmountsByBidder);

        String winnerId = StringUtil.normalizeString(auction.getWinnerBidderId());
        BigDecimal winningAmount = auction.getCurrentPrice();
        if (!winnerId.isEmpty() && auction.getStatus() == AuctionStatus.PAID) {
            User winner = authService.requireUserById(winnerId);
            updateCachedWallet(winner, BigDecimal.ZERO, winningAmount.negate());

            User seller = authService.requireUserById(StringUtil.normalizeString(auction.getSellerId()));
            updateCachedWallet(seller, winningAmount, BigDecimal.ZERO);
            LOGGER.info("Winner {} paid {}. Seller {} received funds for auction {}",
                    winnerId, winningAmount, auction.getSellerId(), auction.getId());
        }

        for (Map.Entry<String, BigDecimal> entry : committedAmountsByBidder.entrySet()) {
            String bidderId = entry.getKey();
            BigDecimal committedAmount = entry.getValue();
            if (bidderId.equalsIgnoreCase(winnerId) && auction.getStatus() == AuctionStatus.PAID) {
                continue;
            }
            User user = authService.requireUserById(bidderId);
            updateCachedWallet(user, committedAmount, committedAmount.negate());
        }
    }

    private User deposit(String userId, BigDecimal amount) {
        String normalizedUserId = StringUtil.normalizeString(userId);
        User user = authService.requireUserById(normalizedUserId);
        if (requireUserDao().increaseBalance(normalizedUserId, amount)) {
            updateCachedWallet(user, amount, BigDecimal.ZERO);
            return user;
        }
        throw new DatabaseException("Failed to process deposit.");
    }

    private User withdraw(String userId, BigDecimal amount) {
        String normalizedUserId = StringUtil.normalizeString(userId);
        User user = authService.requireUserById(normalizedUserId);
        if (requireUserDao().tryDecreaseBalance(normalizedUserId, amount)) {
            updateCachedWallet(user, amount.negate(), BigDecimal.ZERO);
            return user;
        }
        throw new ValidationException("Insufficient liquid balance for withdrawal.");
    }

    private void persistAuctionClose(Auction auction, Map<String, BigDecimal> committedAmountsByBidder) {
        AuctionDao dao = auctionDao;
        if (dao == null) {
            return;
        }
        try {
            if (dao.settleAuction(auction, committedAmountsByBidder)) {
                return;
            }
        } catch (DatabaseException e) {
            throw new AuctionAppException("Failed to close auction wallet state.");
        }
        throw new AuctionAppException("Auction wallet state could not be closed.");
    }

    private Map<String, BigDecimal> getCommittedAmountsByBidder(Auction auction) {
        Map<String, BigDecimal> committedAmounts = new HashMap<>();
        if (auction == null) {
            return committedAmounts;
        }
        for (BidTransaction bid : auction.getBidHistory()) {
            if (bid == null || bid.getBidderId() == null || bid.getAmount() == null) {
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
