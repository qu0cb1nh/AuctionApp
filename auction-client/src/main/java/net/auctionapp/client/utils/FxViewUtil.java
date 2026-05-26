package net.auctionapp.client.utils;

import javafx.scene.Node;

public final class FxViewUtil {
    private FxViewUtil() {
    }

    public static void setVisible(Node node, boolean visible) {
        node.setManaged(visible);
        node.setVisible(visible);
    }
}
