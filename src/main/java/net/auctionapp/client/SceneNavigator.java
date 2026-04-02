package net.auctionapp.client;

import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;
import java.net.URL;
import java.util.Objects;

public final class SceneNavigator {
    private SceneNavigator() {
    }

    public static void switchScene(Node source, String fxmlPath) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(fxmlPath, "fxmlPath");

        URL resource = SceneNavigator.class.getResource(fxmlPath);
        if (resource == null) {
            throw new IllegalStateException("Missing FXML resource: " + fxmlPath);
        }

        Parent root = loadRoot(resource, fxmlPath);
        Scene scene = source.getScene();
        if (scene == null) {
            throw new IllegalStateException("Cannot switch scene because the source node is not attached to a scene.");
        }

        Stage stage = (Stage) scene.getWindow();
        if (stage == null) {
            throw new IllegalStateException("Cannot switch scene because the window is not available.");
        }

        scene.setRoot(root);
        stage.show();
    }

    private static Parent loadRoot(URL resource, String fxmlPath) {
        try {
            return FXMLLoader.load(resource);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to load FXML: " + fxmlPath, e);
        }
    }
}
