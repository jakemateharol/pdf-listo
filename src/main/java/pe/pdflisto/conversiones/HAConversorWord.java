package pe.pdflisto.conversiones;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import pe.pdflisto.exception.HAPdfListoException;
import pe.pdflisto.operaciones.HAControl;
import pe.pdflisto.pdf.HAEscrituraSegura;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class HAConversorWord {
    public static final String HA_MENSAJE_FALTA = "Para convertir Word a PDF instala LibreOffice (gratis, en "
            + "libreoffice.org) y vuelve a abrir PDF Listo.";
    private static final long HA_LIMITE_SEGUNDOS = 180;

    public Optional<File> haLibreOffice() {
        List<File> haCandidatos = new ArrayList<>();
        for (String haBase : new String[]{System.getenv("ProgramFiles"), System.getenv("ProgramFiles(x86)"),
                "C:\\Program Files", "C:\\Program Files (x86)"}) {
            if (haBase != null) {
                haCandidatos.add(new File(haBase, "LibreOffice\\program\\soffice.exe"));
            }
        }
        String haPath = System.getenv("PATH");
        if (haPath != null) {
            for (String haDir : haPath.split(File.pathSeparator)) {
                haCandidatos.add(new File(haDir, "soffice.exe"));
            }
        }
        return haCandidatos.stream().filter(File::isFile).findFirst();
    }

    public boolean haDisponible() {
        return haLibreOffice().isPresent();
    }

    public File haConvertir(File haDocumento, File haDestino, HAControl haControl) {
        File haSoffice = haLibreOffice().orElseThrow(() -> new HAPdfListoException(HA_MENSAJE_FALTA));
        Path haTemporal = null;
        try {
            haTemporal = Files.createTempDirectory("pdflisto-word");
            Path haPerfil = haTemporal.resolve("perfil");
            List<String> haComando = haComando(haSoffice, haDocumento, haTemporal, haPerfil);
            haControl.haMensaje("Convirtiendo «" + haDocumento.getName() + "» con LibreOffice…");
            Process haProceso = new ProcessBuilder(haComando).redirectErrorStream(true)
                    .redirectOutput(haTemporal.resolve("salida.txt").toFile()).start();
            long haLimite = System.currentTimeMillis() + HA_LIMITE_SEGUNDOS * 1000;
            while (!haProceso.waitFor(500, TimeUnit.MILLISECONDS)) {
                if (haControl.haCancelado() || System.currentTimeMillis() > haLimite) {
                    haProceso.destroyForcibly();
                    haControl.haComprobar();
                    throw new HAPdfListoException("LibreOffice tardó demasiado en convertir el documento.");
                }
            }
            String haNombrePdf = haDocumento.getName().replaceFirst("\\.[^.]+$", "") + ".pdf";
            Path haPdf = haTemporal.resolve(haNombrePdf);
            if (haProceso.exitValue() != 0 || !Files.isRegularFile(haPdf)) {
                log.warn("LibreOffice no convirtio {}: {}", haDocumento, Files.readString(haTemporal.resolve("salida.txt")));
                throw new HAPdfListoException("LibreOffice no pudo convertir «" + haDocumento.getName() + "». Revisa que se abra bien en Word o LibreOffice.");
            }
            try (var haD = org.apache.pdfbox.Loader.loadPDF(haPdf.toFile())) {
                HAEscrituraSegura.haVerificar(haPdf.toFile(), null, haD.getNumberOfPages());
            }
            Files.move(haPdf, haDestino.toPath(), StandardCopyOption.REPLACE_EXISTING);
            return haDestino;
        } catch (IOException haE) {
            throw new HAPdfListoException("No se pudo convertir el documento: " + haE.getMessage(), haE);
        } catch (InterruptedException haE) {
            Thread.currentThread().interrupt();
            throw new HAPdfListoException("Conversión interrumpida.");
        } finally {
            if (haTemporal != null) {
                pe.pdflisto.edicion.HACarpetaTrabajo.haBorrar(haTemporal);
            }
        }
    }

    static List<String> haComando(File haSoffice, File haDocumento, Path haCarpetaSalida, Path haPerfil) {
        return List.of(haSoffice.getAbsolutePath(), "-env:UserInstallation=" + haPerfil.toUri(), "--headless", "--norestore",
                "--convert-to", "pdf", "--outdir", haCarpetaSalida.toString(), haDocumento.getAbsolutePath());
    }
}
