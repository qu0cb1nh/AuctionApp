package net.auctionapp.server.managers;

import net.auctionapp.server.ClientHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SessionManagerTest {
    private final SessionManager sessionManager = SessionManager.getInstance();
    private final List<Socket> sockets = new ArrayList<>();

    @BeforeEach
    void setUp() {
        sessionManager.clear();
        sockets.clear();
    }

    @AfterEach
    void tearDown() {
        sessionManager.clear();
        for (Socket socket : sockets) {
            try {
                socket.close();
            } catch (IOException ignored) {
                // Nothing to do in tests.
            }
        }
    }

    @Test
    void bindSessionShouldRegisterClientAndUserOnline() {
        ClientHandler handler = createHandler();

        sessionManager.bindSession("UserA", "UserA", "user", handler);

        assertTrue(sessionManager.isUserOnline("usera"));
        assertEquals(1, sessionManager.getAuthenticatedClientCount());
        assertEquals(1, sessionManager.getClientsByUserId("usera").size());
    }

    @Test
    void bindSessionShouldMoveClientToNewUserWhenReauthenticated() {
        ClientHandler handler = createHandler();

        sessionManager.bindSession("seller-a", "SellerA", "user", handler);
        sessionManager.bindSession("seller-b", "SellerB", "user", handler);

        assertFalse(sessionManager.isUserOnline("seller-a"));
        assertTrue(sessionManager.isUserOnline("seller-b"));
        assertEquals(1, sessionManager.getAuthenticatedClientCount());
    }

    @Test
    void unbindSessionShouldRemoveClientMappings() {
        ClientHandler firstHandler = createHandler();
        ClientHandler secondHandler = createHandler();

        sessionManager.bindSession("bidder-1", "Bidder1", "user", firstHandler);
        sessionManager.bindSession("bidder-1", "Bidder1", "user", secondHandler);

        sessionManager.unbindSession(firstHandler);
        assertTrue(sessionManager.isUserOnline("bidder-1"));
        assertEquals(1, sessionManager.getAuthenticatedClientCount());

        sessionManager.unbindSession(secondHandler);
        assertFalse(sessionManager.isUserOnline("bidder-1"));
        assertEquals(0, sessionManager.getAuthenticatedClientCount());
    }

    private ClientHandler createHandler() {
        Socket socket = new Socket();
        sockets.add(socket);
        return new ClientHandler(socket);
    }
}

