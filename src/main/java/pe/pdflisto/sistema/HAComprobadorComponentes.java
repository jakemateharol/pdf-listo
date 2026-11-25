package pe.pdflisto.sistema;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.sourceforge.tess4j.util.LoadLibs;
import org.springframework.stereotype.Service;
import pe.pdflisto.config.HACarpetaDatos;
import pe.pdflisto.conversiones.HAConversorWord;
import pe.pdflisto.ocr.HADatosOcr;
import pe.pdflisto.operaciones.HAArchivos;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class HAComprobadorComponentes {
    static final List<String> HA_DLL_VISUAL_C = List.of("msvcp140.dll", "vcruntime140.dll", "vcruntime140_1.dll");
    private static final long HA_MB = 1024L * 1024;

    private final HAConversorWord haWord;

    public List<HAEstadoComponente> haComprobar() {
        List<HAEstadoComponente> haLista = new ArrayList<>();
        haLista.add(haJava());
        haLista.add(haCarpetaDatos());
        haLista.add(haVisualC());
        haLista.add(haOcr());
        haLista.add(haLibreOffice());
        return haLista;
    }

    HAEstadoComponente haJava() {
        String haVersion = "Java " + Runtime.version() + " (" + System.getProperty("java.vendor", "?") + ")";
        long haMaxima = Runtime.getRuntime().maxMemory();
        String haMemoria = "memoria máxima " + HAArchivos.haTamanoLegible(haMaxima);
        if (haMaxima < 700 * HA_MB) {
            return HAEstadoComponente.haAviso("Java", haVersion + ", " + haMemoria
                    + ". Es poca para PDF muy grandes; la versión instalada usa más memoria automáticamente.");
        }
        return HAEstadoComponente.haOk("Java", haVersion + ", " + haMemoria + ".");
    }

    HAEstadoComponente haCarpetaDatos() {
        Path haCarpeta = HACarpetaDatos.haRuta();
        try {
            Path haPrueba = Files.createTempFile(haCarpeta, "prueba", ".tmp");
            Files.delete(haPrueba);
        } catch (IOException | RuntimeException haE) {
            return HAEstadoComponente.haFalta("Carpeta de datos", haCarpeta + ": no se puede escribir (" + haE.getMessage()
                    + "). El historial, las recetas y la recuperación no funcionarán.");
        }
        long haLibre = haCarpeta.toFile().getUsableSpace();
        if (haLibre < HAEspacioDisco.HA_POCO) {
            return HAEstadoComponente.haAviso("Carpeta de datos", haCarpeta + ": queda poco espacio en el disco ("
                    + HAArchivos.haTamanoLegible(haLibre) + "). Las versiones temporales de PDF grandes podrían no caber.");
        }
        return HAEstadoComponente.haOk("Carpeta de datos", haCarpeta + " (libre en disco: " + HAArchivos.haTamanoLegible(haLibre) + ").");
    }

    public HAEstadoComponente haVisualC() {
        List<String> haFaltan = new ArrayList<>();
        for (String haDll : HA_DLL_VISUAL_C) {
            if (haBuscarDll(haDll, haCarpetasDll()).isEmpty()) {
                haFaltan.add(haDll);
            }
        }
        if (haFaltan.isEmpty()) {
            return HAEstadoComponente.haOk("Microsoft Visual C++", "Las librerías que necesita el OCR están presentes.");
        }
        return HAEstadoComponente.haAviso("Microsoft Visual C++", "Faltan " + String.join(", ", haFaltan)
                + ". El OCR podría no funcionar: instala «Microsoft Visual C++ Redistributable 2015-2022 (x64)», "
                + "gratuito, de Microsoft.");
    }

    HAEstadoComponente haOcr() {
        try {
            HADatosOcr.haCarpeta();
            String haVersion = LoadLibs.getTessAPIInstance().TessVersion();
            return HAEstadoComponente.haOk("OCR (Tesseract)", "Tesseract " + haVersion + " listo, idiomas español e inglés"
                    + (HADatosOcr.haHayOrientacion() ? "; detecta páginas giradas." : "; sin detección de páginas giradas (falta osd.traineddata)."));
        } catch (LinkageError | RuntimeException haE) {
            log.warn("El OCR no se pudo iniciar", haE);
            return HAEstadoComponente.haFalta("OCR (Tesseract)", "No se pudo iniciar el motor de OCR. Normalmente se "
                    + "soluciona instalando «Microsoft Visual C++ Redistributable 2015-2022 (x64)». Detalle: " + haE.getMessage());
        }
    }

    HAEstadoComponente haLibreOffice() {
        Optional<File> haSoffice = haWord.haLibreOffice();
        return haSoffice.map(haF -> HAEstadoComponente.haOk("LibreOffice (opcional)", "Instalado en " + haF.getParent()
                        + ". «Word a PDF» está disponible."))
                .orElseGet(() -> HAEstadoComponente.haAviso("LibreOffice (opcional)", "No está instalado: «Word a PDF» "
                        + "queda desactivado. Todo lo demás funciona. Se puede instalar gratis desde libreoffice.org."));
    }

    static List<Path> haCarpetasDll() {
        List<Path> haCarpetas = new ArrayList<>();
        String haExe = System.getProperty("jpackage.app-path");
        if (haExe != null) {
            Path haPadre = Path.of(haExe).getParent();
            if (haPadre != null) {
                haCarpetas.add(haPadre);
            }
        }
        String haJna = System.getProperty("jna.library.path");
        if (haJna != null) {
            for (String haP : haJna.split(File.pathSeparator)) {
                if (!haP.isBlank()) {
                    haCarpetas.add(Path.of(haP));
                }
            }
        }
        haCarpetas.add(Path.of(System.getProperty("java.home"), "bin"));
        String haWindir = System.getenv("SystemRoot");
        haCarpetas.add(Path.of(haWindir != null ? haWindir : "C:\\Windows", "System32"));
        return haCarpetas;
    }

    static Optional<Path> haBuscarDll(String haNombre, List<Path> haCarpetas) {
        return haCarpetas.stream().map(haC -> haC.resolve(haNombre)).filter(Files::isRegularFile).findFirst();
    }
}
