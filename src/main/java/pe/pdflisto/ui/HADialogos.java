package pe.pdflisto.ui;

import javafx.scene.control.Dialog;

import java.util.Optional;
import java.util.function.Consumer;

public final class HADialogos {
    public static volatile Consumer<Dialog<?>> haCapturador;

    private HADialogos() {
    }

    public static <T> Optional<T> haMostrar(Dialog<T> haDialogo) {
        Consumer<Dialog<?>> haC = haCapturador;
        if (haC != null) {
            haC.accept(haDialogo);
            return Optional.empty();
        }
        return haDialogo.showAndWait();
    }
}
