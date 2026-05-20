package net.auctionapp.client.config;

import net.auctionapp.common.utils.ConfigUtil;

import java.util.Properties;

public final class ClientConfig {
    private static final Properties PROPERTIES = ConfigUtil.loadProperties(ClientConfig.class, "client.properties");

    private ClientConfig() {
    }

    public static String getServerHost() {
        return ConfigUtil.getStringProperty(PROPERTIES, "server.host", "localhost");
    }

    public static int getServerPort() {
        return ConfigUtil.getIntProperty(PROPERTIES, "server.port", 3667);
    }
}
