package net.auctionapp.server.messages;

import net.auctionapp.common.messages.Message;
import net.auctionapp.common.messages.MessageType;
import net.auctionapp.common.messages.system.ErrorResponseMessage;
import net.auctionapp.server.ClientHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.EnumMap;
import java.util.Map;

public final class MessageRouter {
    private static final Logger LOGGER = LoggerFactory.getLogger(MessageRouter.class);

    private final Map<MessageType, RegisteredCommand<? extends Message>> routes =
            new EnumMap<>(MessageType.class);

    public void dispatch(Message message, ClientHandler clientHandler) {
        if (message == null) {
            return;
        }
        RegisteredCommand<? extends Message> route = routes.get(message.getType());
        if (route == null) {
            LOGGER.warn("Received unsupported message type: {}", message.getType());
            clientHandler.sendResponse(new ErrorResponseMessage("Unsupported message type."), message);
            return;
        }
        LOGGER.debug("Routing message type {}.", message.getType());
        route.execute(message, clientHandler);
    }

    public <T extends Message> void register(
            MessageType messageType,
            Class<T> messageClass,
            MessageCommand<T> command
    ) {
        routes.put(messageType, new RegisteredCommand<>(messageClass, command));
    }

    private record RegisteredCommand<T extends Message>(
            Class<T> messageClass,
            MessageCommand<T> command
    ) {
        void execute(Message message, ClientHandler clientHandler) {
            command.execute(messageClass.cast(message), clientHandler);
        }
    }
}
