package pe.pdflisto.edicion;

import lombok.extern.slf4j.Slf4j;
import pe.pdflisto.pdf.HADocumentoPdf;

import java.io.File;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

@Slf4j
public class HASesionEdicion {
    public record HAVersion(File haArchivo, String haDescripcion, String haContrasena, boolean haProtegida, String haCodigo) {
        public HAVersion(File haArchivo, String haDescripcion, String haContrasena, boolean haProtegida) {
            this(haArchivo, haDescripcion, haContrasena, haProtegida, null);
        }

        public HAVersion(File haArchivo, String haDescripcion, String haContrasena) {
            this(haArchivo, haDescripcion, haContrasena, haContrasena != null, null);
        }

        public HAVersion(File haArchivo, String haDescripcion) {
            this(haArchivo, haDescripcion, null, false, null);
        }
    }

    private final File haOriginal;
    private boolean haRestringido;
    private boolean haDesbloqueado;
    private final Path haCarpeta;
    private final List<HAVersion> haVersiones = new ArrayList<>();
    private int haIndice;
    private int haIndiceGuardado;
    private int haContador;
    private HADocumentoPdf haVista;
    private boolean haAvisoContrasenaDado;
    private FileChannel haCanalBloqueo;
    private FileLock haBloqueo;

    public HASesionEdicion(HADocumentoPdf haDocumentoOriginal) {
        this(haDocumentoOriginal.haArchivo(), haDocumentoOriginal.haContrasena(), HACarpetaTrabajo.haNueva());
        this.haVista = haDocumentoOriginal;
        this.haRestringido = haDocumentoOriginal.haRestringido();
        haGuardarDiario();
    }

    HASesionEdicion(File haOriginal, String haContrasena, Path haCarpeta) {
        this.haOriginal = haOriginal;
        this.haCarpeta = haCarpeta;
        haVersiones.add(new HAVersion(haOriginal, "Original", haContrasena));
        haBloquear();
        haGuardarDiario();
    }

    public static HASesionEdicion haRecuperar(Path haCarpeta, HADiarioSesion.HADatos haDatos) {
        HASesionEdicion haS = new HASesionEdicion(new File(haDatos.haOriginal()), haCarpeta);
        haS.haVersiones.add(new HAVersion(new File(haDatos.haOriginal()), "Original", null, haDatos.haOriginalProtegido()));
        for (HADiarioSesion.HAVersionGuardada haV : haDatos.haVersiones()) {
            haS.haVersiones.add(new HAVersion(new File(haV.haArchivo()), haV.haDescripcion(), null, haV.haProtegida(), haV.haCodigo()));
        }
        haS.haIndice = haDatos.haIndice();
        haS.haIndiceGuardado = haDatos.haIndiceGuardado();
        haS.haContador = haDatos.haContador();
        haS.haRestringido = haDatos.haRestringido();
        haS.haBloquear();
        haS.haGuardarDiario();
        return haS;
    }

    private HASesionEdicion(File haOriginal, Path haCarpeta) {
        this.haOriginal = haOriginal;
        this.haCarpeta = haCarpeta;
    }

    public File haOriginal() { return haOriginal; }
    public String haNombre() { return haOriginal.getName(); }
    public HADocumentoPdf haVista() { return haVista; }
    public Path haCarpeta() { return haCarpeta; }

    public HADocumentoPdf haCambiarVista(HADocumentoPdf haNueva) {
        HADocumentoPdf haAnterior = haVista;
        haVista = haNueva;
        return haAnterior == haNueva ? null : haAnterior;
    }

    public File haArchivoActual() { return haVersiones.get(haIndice).haArchivo(); }

    public String haContrasenaActual() { return haVersiones.get(haIndice).haContrasena(); }

    public void haPonerContrasenaActual(String haContrasena) {
        HAVersion haV = haVersiones.get(haIndice);
        haVersiones.set(haIndice, new HAVersion(haV.haArchivo(), haV.haDescripcion(), haContrasena, true, haV.haCodigo()));
    }

    public boolean haRequierePropietario() { return haIndice == 0 && haRestringido && !haDesbloqueado; }

    public void haDesbloquear(String haContrasenaPropietario) {
        haVersiones.set(0, new HAVersion(haOriginal, "Original", haContrasenaPropietario));
        haDesbloqueado = true;
        haGuardarDiario();
    }

    public boolean haAvisoContrasenaDado() { return haAvisoContrasenaDado; }
    public void haMarcarAvisoContrasena() { haAvisoContrasenaDado = true; }

    public File haNuevoArchivo() {
        return haCarpeta.resolve("v" + (++haContador) + ".pdf").toFile();
    }

    public void haAgregar(HAVersion haVersion) {
        while (haVersiones.size() > haIndice + 1) {
            haBorrar(haVersiones.remove(haVersiones.size() - 1).haArchivo());
        }
        if (haIndiceGuardado > haIndice) {
            haIndiceGuardado = -1;
        }
        haVersiones.add(haVersion);
        haIndice++;
        haGuardarDiario();
    }

    public boolean haPuedeDeshacer() { return haIndice > 0; }
    public boolean haPuedeRehacer() { return haIndice < haVersiones.size() - 1; }

    public String haQueDeshace() { return haPuedeDeshacer() ? haVersiones.get(haIndice).haDescripcion() : null; }
    public String haQueRehace() { return haPuedeRehacer() ? haVersiones.get(haIndice + 1).haDescripcion() : null; }

    public void haDeshacer() {
        if (haPuedeDeshacer()) {
            haIndice--;
            haGuardarDiario();
        }
    }

    public void haRehacer() {
        if (haPuedeRehacer()) {
            haIndice++;
            haGuardarDiario();
        }
    }

    public boolean haHayCambios() { return haIndice != haIndiceGuardado; }

    public void haMarcarGuardado() {
        haIndiceGuardado = haIndice;
        haGuardarDiario();
    }

    public int haCantidadVersiones() { return haVersiones.size(); }

    public String haCodigosAplicados() {
        List<String> haCodigos = new ArrayList<>();
        for (int haI = 1; haI <= haIndice; haI++) {
            String haC = haVersiones.get(haI).haCodigo();
            if (haC != null && !haC.isBlank()) {
                haCodigos.add(haC);
            }
        }
        return haCodigos.isEmpty() ? null : String.join(";", haCodigos);
    }

    public void haCerrar() {
        if (haVista != null) {
            haVista.close();
        }
        haSoltarBloqueo();
        HACarpetaTrabajo.haBorrar(haCarpeta);
    }

    public void haLiberarSinBorrar() {
        if (haVista != null) {
            haVista.close();
        }
        haSoltarBloqueo();
    }

    private void haGuardarDiario() {
        List<HADiarioSesion.HAVersionGuardada> haGuardadas = new ArrayList<>();
        for (int haI = 1; haI < haVersiones.size(); haI++) {
            HAVersion haV = haVersiones.get(haI);
            haGuardadas.add(new HADiarioSesion.HAVersionGuardada(haV.haArchivo().getAbsolutePath(), haV.haDescripcion(), haV.haProtegida(), haV.haCodigo()));
        }
        HADiarioSesion.haGuardar(haCarpeta, new HADiarioSesion.HADatos(haOriginal.getAbsolutePath(), haRestringido,
                haVersiones.get(0).haProtegida(), haIndice, haIndiceGuardado, haContador, haGuardadas, System.currentTimeMillis()));
    }

    private void haBloquear() {
        try {
            Files.createDirectories(haCarpeta);
            haCanalBloqueo = FileChannel.open(haCarpeta.resolve(HARecuperacion.HA_BLOQUEO),
                    StandardOpenOption.CREATE, StandardOpenOption.WRITE);
            haBloqueo = haCanalBloqueo.tryLock();
        } catch (IOException | RuntimeException haE) {
            log.warn("No se pudo bloquear la carpeta de trabajo {}; la recuperación no estará disponible", haCarpeta, haE);
        }
    }

    private void haSoltarBloqueo() {
        try {
            if (haBloqueo != null) {
                haBloqueo.release();
            }
            if (haCanalBloqueo != null) {
                haCanalBloqueo.close();
            }
        } catch (IOException haIgnorado) {
        }
        haBloqueo = null;
        haCanalBloqueo = null;
    }

    private void haBorrar(File haArchivo) {
        if (!haArchivo.equals(haOriginal)) {
            try {
                Files.deleteIfExists(haArchivo.toPath());
            } catch (IOException haIgnorado) {
            }
        }
    }
}
