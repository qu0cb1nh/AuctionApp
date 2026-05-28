package net.auctionapp.server.managers;

import net.auctionapp.common.messages.MessageType;
import net.auctionapp.common.messages.wallet.DepositRequestMessage;
import net.auctionapp.common.messages.wallet.WalletResponseMessage;
import net.auctionapp.common.messages.wallet.WithdrawRequestMessage;
import net.auctionapp.common.users.UserRole;
import net.auctionapp.server.ClientHandler;
import net.auctionapp.server.dao.BalanceDao;
import net.auctionapp.server.dao.UserDao;
import net.auctionapp.server.models.users.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class WalletManagerTest {
    private WalletManager walletManager;
    private BalanceDao mockBalanceDao;
    private ClientHandler mockClientHandler;

    @BeforeEach
    void setUp() {
        walletManager = WalletManager.getInstance();
        mockBalanceDao = mock(BalanceDao.class);
        walletManager.setBalanceDao(mockBalanceDao);

        UserDao mockUserDao = mock(UserDao.class);
        AuthManager.getInstance().setUserDao(mockUserDao);
        mockClientHandler = mock(ClientHandler.class);

        User user = new User("user-1", "tester", "hash", UserRole.USER, false);
        user.addBalance(new BigDecimal("100.00")); // Số dư ban đầu
        when(mockUserDao.findById("user-1")).thenReturn(Optional.of(user));
        when(mockClientHandler.getAuthenticatedId()).thenReturn("user-1");
    }

    @Test
    void handleDeposit_HappyCase() {
        BigDecimal depositAmount = new BigDecimal("50.00");
        DepositRequestMessage request = new DepositRequestMessage(depositAmount);
        when(mockBalanceDao.increaseBalance("user-1", depositAmount)).thenReturn(true);

        walletManager.handleDeposit(request, mockClientHandler);

        ArgumentCaptor<WalletResponseMessage> responseCaptor = ArgumentCaptor.forClass(WalletResponseMessage.class);
        verify(mockClientHandler).sendResponse(responseCaptor.capture(), eq(request));
        assertEquals(new BigDecimal("150.00"), responseCaptor.getValue().getBalance());
    }

    @Test
    void handleWithdraw_InsufficientFunds_CriticalCase() {
        BigDecimal withdrawAmount = new BigDecimal("200.00");
        WithdrawRequestMessage request = new WithdrawRequestMessage(withdrawAmount);
        when(mockBalanceDao.tryDecreaseBalance("user-1", withdrawAmount)).thenReturn(false);

        walletManager.handleWithdraw(request, mockClientHandler);

        verify(mockClientHandler).sendResponse(any(net.auctionapp.common.messages.system.ErrorResponseMessage.class), eq(request));
    }
}