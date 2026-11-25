package pe.pdflisto.ui;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Control;
import javafx.scene.control.Labeled;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class HABarraAdaptable {
    public enum HANivel { COMPLETO, COMPACTO, MINIMO }

    public static final double HA_ANCHO_NOMBRE_ARCHIVO = 120;

    private final HBox haBarra;
    private final Region haZonaElastica;
    private final List<HAElemento> haElementos = new ArrayList<>();
    private final List<Consumer<HANivel>> haExtras = new ArrayList<>();
    private final double haEspacioNormal;
    private double[] haNecesario;
    private HANivel haNivel = HANivel.COMPLETO;
    private boolean haMedicionPendiente;
    private boolean haAjustando;
    private int haMediciones;

    public HABarraAdaptable(HBox haBarra, Region haZonaElastica) {
        this.haBarra = haBarra;
        this.haZonaElastica = haZonaElastica;
        this.haEspacioNormal = haBarra.getSpacing();
    }

    public HABarraAdaptable haSoloIconoDesde(Labeled haBoton, HANivel haDesde) {
        haElementos.add(new HAElemento(haBoton, haDesde, null, null));
        return this;
    }

    public HABarraAdaptable haTextoCorto(Labeled haBoton, String haCorto) {
        haElementos.add(new HAElemento(haBoton, HANivel.MINIMO, haCorto, HANivel.COMPACTO));
        return this;
    }

    public HABarraAdaptable haOcultarDesde(Node haNodo, HANivel haDesde) {
        haExtras.add(haN -> {
            boolean haVer = haN.ordinal() < haDesde.ordinal();
            haNodo.setVisible(haVer);
            haNodo.setManaged(haVer);
        });
        return this;
    }

    public HABarraAdaptable haAlCambiarNivel(Consumer<HANivel> haAjuste) {
        haExtras.add(haAjuste);
        return this;
    }

    public void haInstalar() {
        for (HAElemento haE : haElementos) {
            String haTextoCompleto = haE.haBoton.getText();
            if (haE.haBoton.getTooltip() == null && haTextoCompleto != null && !haTextoCompleto.isBlank()) {
                haE.haBoton.setTooltip(new Tooltip(haTextoCompleto));
            }
        }
        for (Node haN : haBarra.getChildren()) {
            if (haN != haZonaElastica && haN instanceof Region haR) {
                haR.setMinWidth(Region.USE_PREF_SIZE);
            }
            haN.managedProperty().addListener((haO, haA, haB) -> {
                if (!haAjustando) {
                    haPedirMedicion();
                }
            });
        }
        haBarra.widthProperty().addListener((haO, haA, haB) -> haElegir());
        haBarra.sceneProperty().addListener((haO, haA, haB) -> haPedirMedicion());
        haPedirMedicion();
    }

    public HANivel haNivel() {
        return haNivel;
    }

    int haMediciones() {
        return haMediciones;
    }

    public void haPedirMedicion() {
        if (haMedicionPendiente) {
            return;
        }
        haMedicionPendiente = true;
        Platform.runLater(() -> {
            haMedicionPendiente = false;
            haMedir();
            haElegir();
        });
    }

    public void haMedirAhora() {
        haMedir();
        haElegir();
    }

    private void haMedir() {
        if (haBarra.getScene() == null) {
            return;
        }
        haMediciones++;
        haAjustando = true;
        try {
            haBarra.applyCss();
            HANivel[] haNiveles = HANivel.values();
            double[] haAnchos = new double[haNiveles.length];
            for (int haI = 0; haI < haNiveles.length; haI++) {
                haAplicar(haNiveles[haI]);
                haAnchos[haI] = haAnchoNecesario();
            }
            haNecesario = haAnchos;
            haAplicar(haNivel);
        } finally {
            haAjustando = false;
        }
    }

    private void haElegir() {
        if (haNecesario == null || haBarra.getWidth() <= 0) {
            return;
        }
        HANivel haNuevo = haElegirNivel(haBarra.getWidth(), haNecesario);
        if (haNuevo != haNivel) {
            haNivel = haNuevo;
            haAjustando = true;
            try {
                haAplicar(haNuevo);
            } finally {
                haAjustando = false;
            }
        }
    }

    public static HANivel haElegirNivel(double haDisponible, double[] haNecesarioPorNivel) {
        HANivel[] haNiveles = HANivel.values();
        for (int haI = 0; haI < haNiveles.length && haI < haNecesarioPorNivel.length; haI++) {
            if (haNecesarioPorNivel[haI] <= haDisponible) {
                return haNiveles[haI];
            }
        }
        return haNiveles[haNiveles.length - 1];
    }

    private double haAnchoNecesario() {
        double haTotal = 0;
        int haVisibles = 0;
        for (Node haN : haBarra.getChildren()) {
            if (!haN.isManaged()) {
                continue;
            }
            haVisibles++;
            if (haN == haZonaElastica) {
                haTotal += Math.max(HA_ANCHO_NOMBRE_ARCHIVO, haZonaElastica.getMinWidth());
            } else if (haN instanceof Control haC && haC.getPrefWidth() > 0) {
                haTotal += haC.getPrefWidth();
            } else {
                haTotal += haN.prefWidth(-1);
            }
        }
        Insets haMargen = haBarra.getInsets();
        return haTotal + Math.max(0, haVisibles - 1) * haBarra.getSpacing() + haMargen.getLeft() + haMargen.getRight()
                + 4;
    }

    private void haAplicar(HANivel haN) {
        for (HAElemento haE : haElementos) {
            haE.haAplicar(haN);
        }
        haExtras.forEach(haX -> haX.accept(haN));
        haBarra.setSpacing(haN == HANivel.MINIMO ? Math.min(6, haEspacioNormal) : haEspacioNormal);
    }

    private static final class HAElemento {
        final Labeled haBoton;
        final HANivel haSoloIconoDesde;
        final String haTextoCorto;
        final HANivel haCortoDesde;
        final String haTextoCompleto;

        HAElemento(Labeled haBoton, HANivel haSoloIconoDesde, String haTextoCorto, HANivel haCortoDesde) {
            this.haBoton = haBoton;
            this.haSoloIconoDesde = haSoloIconoDesde;
            this.haTextoCorto = haTextoCorto;
            this.haCortoDesde = haCortoDesde;
            this.haTextoCompleto = haBoton.getText();
        }

        void haAplicar(HANivel haN) {
            boolean haSoloIcono = haN.ordinal() >= haSoloIconoDesde.ordinal();
            boolean haCorto = haCortoDesde != null && haN.ordinal() >= haCortoDesde.ordinal();
            haBoton.setText(haCorto ? haTextoCorto : haTextoCompleto);
            haBoton.setContentDisplay(haSoloIcono ? ContentDisplay.GRAPHIC_ONLY : ContentDisplay.LEFT);
            haBoton.requestLayout();
        }
    }
}
