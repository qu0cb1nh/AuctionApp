package net.auctionapp.server.config;

import net.auctionapp.common.utils.ConfigUtil;

import java.util.Properties;

public final class ServerConfig {
    private static final Properties PROPERTIES = ConfigUtil.loadProperties(ServerConfig.class, "server.properties");

    private ServerConfig() {
    }

    public static String getServerHost() {
        return ConfigUtil.getStringProperty(PROPERTIES, "server.host", "localhost");
    }

    public static int getServerPort() {
        return ConfigUtil.getIntProperty(PROPERTIES, "server.port", 3667);
    }
}
