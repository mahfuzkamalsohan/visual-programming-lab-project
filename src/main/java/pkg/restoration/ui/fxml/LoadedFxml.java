package pkg.restoration.ui.fxml;

import javafx.scene.Parent;

public record LoadedFxml<T>(Parent root, T controller) {
}
