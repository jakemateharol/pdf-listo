package pe.pdflisto.ui;

import javafx.scene.image.Image;
import pe.pdflisto.pdf.HADocumentoPdf;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public final class HAMiniaturas {
    private final Map<HADocumentoPdf, Map<Integer, Image>> haMemoria = new ConcurrentHashMap<>();

    public Image haEnMemoria(HADocumentoPdf haDocumento, int haPagina) {
        Map<Integer, Image> haDelDocumento = haMemoria.get(haDocumento);
        return haDelDocumento == null ? null : haDelDocumento.get(haPagina);
    }

    public CompletableFuture<Image> haObtener(HADocumentoPdf haDocumento, int haPagina, double haAnchoPx) {
        Image haGuardada = haEnMemoria(haDocumento, haPagina);
        if (haGuardada != null) {
            return CompletableFuture.completedFuture(haGuardada);
        }
        float haDpi = (float) Math.max(6, Math.min(150, haAnchoPx * 72 / haDocumento.haTamano(haPagina).haAncho()));
        return haDocumento.haRenderizar(haPagina, haDpi, HADocumentoPdf.HAPrioridad.BAJA)
                .thenApply(HAImagenes::haAFx)
                .thenApply(haImg -> {
                    haMemoria.computeIfAbsent(haDocumento, haD -> new ConcurrentHashMap<>()).put(haPagina, haImg);
                    return haImg;
                });
    }

    public void haOlvidar(HADocumentoPdf haDocumento) {
        haMemoria.remove(haDocumento);
    }
}
