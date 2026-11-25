package pe.pdflisto.pdf;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;

import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public final class HADocumentoPdf implements AutoCloseable {
    public enum HAPrioridad { ALTA, NORMAL, BAJA }

    private final File haArchivo;
    private final String haContrasena;
    private final PDDocument haPdf;
    private final PDFRenderer haDibujante;
    private final List<HATamanoPagina> haTamanos;
    private final long haBytes;
    private final boolean haCifrado;
    private final boolean haRestringido;
    private final ThreadPoolExecutor haHilo;
    private final AtomicLong haOrden = new AtomicLong();
    private volatile boolean haCerrado;

    HADocumentoPdf(File haArchivo, String haContrasena, PDDocument haPdf) {
        this.haArchivo = haArchivo;
        this.haContrasena = haContrasena;
        this.haPdf = haPdf;
        this.haDibujante = new PDFRenderer(haPdf);
        this.haBytes = haArchivo.length();
        this.haCifrado = haPdf.isEncrypted();
        this.haRestringido = haPdf.isEncrypted() && !haPdf.getCurrentAccessPermission().isOwnerPermission();
        List<HATamanoPagina> haLista = new ArrayList<>();
        for (PDPage haPagina : haPdf.getPages()) {
            haLista.add(HATamanoPagina.haDe(haPagina));
        }
        this.haTamanos = List.copyOf(haLista);
        this.haHilo = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS, new PriorityBlockingQueue<>(), haR -> {
            Thread haT = new Thread(haR, "pdf-" + haArchivo.getName());
            haT.setDaemon(true);
            return haT;
        });
    }

    public File haArchivo() { return haArchivo; }
    public String haNombre() { return haArchivo.getName(); }
    public int haPaginas() { return haTamanos.size(); }
    public long haBytes() { return haBytes; }
    public boolean haCifrado() { return haCifrado; }
    public boolean haRestringido() { return haRestringido; }
    public String haContrasena() { return haContrasena; }

    public HATamanoPagina haTamano(int haIndice) {
        return haTamanos.get(haIndice);
    }

    public CompletableFuture<BufferedImage> haRenderizar(int haIndice, float haDpi, HAPrioridad haPrioridad) {
        return haEnHilo(haPrioridad, () -> haDibujante.renderImageWithDPI(haIndice, haDpi, ImageType.RGB));
    }

    public CompletableFuture<List<HACoincidencia>> haBuscar(String haConsulta) {
        return haEnHilo(HAPrioridad.NORMAL, () -> HABuscadorTexto.haBuscar(haPdf, haConsulta));
    }

    public CompletableFuture<List<HAMarcador>> haMarcadores() {
        return haEnHilo(HAPrioridad.NORMAL, () -> HALectorMarcadores.haLeer(haPdf));
    }

    private final java.util.Map<Integer, HALectorTexto.HAPaginaTexto> haTextos = new java.util.concurrent.ConcurrentHashMap<>();

    public CompletableFuture<HALectorTexto.HAPaginaTexto> haTextoPagina(int haIndice) {
        HALectorTexto.HAPaginaTexto haGuardado = haTextos.get(haIndice);
        if (haGuardado != null) {
            return CompletableFuture.completedFuture(haGuardado);
        }
        return haEnHilo(HAPrioridad.ALTA, () -> {
            HALectorTexto.HAPaginaTexto haT = HALectorTexto.haLeerPagina(haPdf, haIndice);
            haTextos.put(haIndice, haT);
            return haT;
        });
    }

    public CompletableFuture<pe.pdflisto.formularios.HALectorFormulario.HAFormulario> haFormulario() {
        return haEnHilo(HAPrioridad.NORMAL, () -> pe.pdflisto.formularios.HALectorFormulario.haLeer(haPdf));
    }

    public CompletableFuture<List<Integer>> haPaginasSinTexto() {
        return haEnHilo(HAPrioridad.BAJA, () -> {
            org.apache.pdfbox.text.PDFTextStripper haLector = new org.apache.pdfbox.text.PDFTextStripper();
            List<Integer> haSinTexto = new ArrayList<>();
            for (int haI = 0; haI < haPaginas(); haI++) {
                haLector.setStartPage(haI + 1);
                haLector.setEndPage(haI + 1);
                if (haLector.getText(haPdf).replaceAll("\\s", "").length() < 20) {
                    haSinTexto.add(haI);
                }
            }
            return haSinTexto;
        });
    }

    public CompletableFuture<Boolean> haPareceEscaneado() {
        return haEnHilo(HAPrioridad.BAJA, () -> HABuscadorTexto.haContarLetras(haPdf, 5) < 20);
    }

    private <T> CompletableFuture<T> haEnHilo(HAPrioridad haPrioridad, Callable<T> haTrabajo) {
        CompletableFuture<T> haResultado = new CompletableFuture<>();
        if (haCerrado) {
            haResultado.completeExceptionally(new CancellationException("Documento cerrado"));
            return haResultado;
        }
        haHilo.execute(new HATarea(haPrioridad.ordinal(), haOrden.incrementAndGet(), () -> {
            if (haCerrado) {
                haResultado.completeExceptionally(new CancellationException("Documento cerrado"));
                return;
            }
            try {
                haResultado.complete(haTrabajo.call());
            } catch (Throwable haE) {
                haResultado.completeExceptionally(haE);
            }
        }));
        return haResultado;
    }

    private record HATarea(int haPrioridad, long haOrden, Runnable haAccion) implements Runnable, Comparable<HATarea> {
        @Override
        public void run() {
            haAccion.run();
        }

        @Override
        public int compareTo(HATarea haOtra) {
            int haC = Integer.compare(haPrioridad, haOtra.haPrioridad);
            return haC != 0 ? haC : Long.compare(haOrden, haOtra.haOrden);
        }
    }

    @Override
    public void close() {
        if (haCerrado) {
            return;
        }
        haCerrado = true;
        haHilo.getQueue().clear();
        haHilo.execute(new HATarea(-1, 0, () -> {
            try {
                haPdf.close();
            } catch (Exception haIgnorado) {
            }
        }));
        haHilo.shutdown();
    }
}
