package net.auctionapp.client.utils;

import javafx.fxml.FXMLLoader;
import javafx.scene.image.Image;
import net.auctionapp.common.items.ItemType;

import java.net.URL;
import java.util.Locale;
import java.util.Objects;

public final class ResourcesUtil {
    private static final String UI_ROOT = "/net/auctionapp/client/";
    private static final String DEFAULT_ITEM_PLACEHOLDER = "art.png";
    private static final String[] APP_ICON = {"app_icon_16.png", "app_icon_24.png", "app_icon_32.png", "app_icon_64.png", "app_icon_128.png"};

    private ResourcesUtil() {
    }

    public static URL fxml(String relativePath) {
        return requireResource("fxml/" + relativePath);
    }

    public static FXMLLoader fxmlLoader(String relativePath) {
        return new FXMLLoader(fxml(relativePath));
    }

    public static URL image(String relativePath) {
        return requireResource("images/" + relativePath);
    }

    public static Image[] appIcons() {
        Image[] icons = new Image[APP_ICON.length];
        for (int i = 0; i < APP_ICON.length; i++) {
            icons[i] = new Image(image(APP_ICON[i]).toExternalForm());
        }
        return icons;
    }

    public static URL itemPlaceholder(ItemType itemType) {
        if (itemType == null) {
            return image(DEFAULT_ITEM_PLACEHOLDER);
        }
        return image(itemType.name().toLowerCase(Locale.ROOT) + ".png");
    }

    public static URL sound(String relativePath) {
        return resource("sounds/" + relativePath);
    }

    public static URL requireResource(String relativePath) {
        String resourcePath = resourcePath(relativePath);
        URL resource = ResourcesUtil.class.getResource(resourcePath);
        if (resource == null) {
            throw new IllegalStateException("Missing resource: " + resourcePath);
        }
        return resource;
    }

    public static URL resource(String relativePath) {
        return ResourcesUtil.class.getResource(resourcePath(relativePath));
    }

    private static String resourcePath(String relativePath) {
        Objects.requireNonNull(relativePath, "relativePath");
        String normalizedPath = relativePath.strip();
        if (normalizedPath.isBlank()) {
            throw new IllegalArgumentException("Resource path must not be blank.");
        }
        while (normalizedPath.startsWith("/")) {
            normalizedPath = normalizedPath.substring(1);
        }
        return UI_ROOT + normalizedPath;
    }
}
