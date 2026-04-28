package net.auctionapp.server.managers;

import net.auctionapp.server.ClientHandler;
import net.auctionapp.common.utils.StringUtil;

import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Tracks authenticated socket sessions and maps users to their active connections.
 */
public final class SessionManager {
    private static final SessionManager INSTANCE = new SessionManager();

    private final ConcurrentMap<ClientHandler, SessionInfo> sessionsByClient = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Set<ClientHandler>> clientsByUserId = new ConcurrentHashMap<>();

    private SessionManager() {
    }

    public static SessionManager getInstance() {
        return INSTANCE;
    }

    public void bindSession(String userId, String username, String role, ClientHandler clientHandler) {
        if (clientHandler == null) {
            return;
        }

        String normalizedUserId = StringUtil.normalizeString(userId);
        if (normalizedUserId.isEmpty()) {
            return;
        }

        SessionInfo newSession = new SessionInfo(normalizedUserId, username, role);
        SessionInfo previousSession = sessionsByClient.put(clientHandler, newSession);

        if (previousSession != null && !previousSession.userId().equals(normalizedUserId)) {
            removeClientFromUser(previousSession.userId(), clientHandler);
        }

        clientsByUserId
                .computeIfAbsent(normalizedUserId, ignored -> ConcurrentHashMap.newKeySet())
                .add(clientHandler);
    }

    public void unbindSession(ClientHandler clientHandler) {
        if (clientHandler == null) {
            return;
        }

        SessionInfo session = sessionsByClient.remove(clientHandler);
        if (session != null) {
            removeClientFromUser(session.userId(), clientHandler);
        }
    }

    public Optional<SessionInfo> getSession(ClientHandler clientHandler) {
        return Optional.ofNullable(sessionsByClient.get(clientHandler));
    }

    public Set<ClientHandler> getClientsByUserId(String userId) {
        String normalizedUserId = StringUtil.normalizeString(userId);
        if (normalizedUserId.isEmpty()) {
            return Set.of();
        }
        Set<ClientHandler> clients = clientsByUserId.get(normalizedUserId);
        return clients == null ? Set.of() : Collections.unmodifiableSet(clients);
    }

    public boolean isUserOnline(String userId) {
        return !getClientsByUserId(userId).isEmpty();
    }

    public int getAuthenticatedClientCount() {
        return sessionsByClient.size();
    }

    public void clear() {
        sessionsByClient.clear();
        clientsByUserId.clear();
    }

    private void removeClientFromUser(String userId, ClientHandler clientHandler) {
        clientsByUserId.computeIfPresent(userId, (ignored, clients) -> {
            clients.remove(clientHandler);
            return clients.isEmpty() ? null : clients;
        });
    }

    public record SessionInfo(String userId, String username, String role) {
    }
}
