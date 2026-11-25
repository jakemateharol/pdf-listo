package pe.pdflisto.privacidad;

import lombok.RequiredArgsConstructor;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.graphics.optionalcontent.PDOptionalContentGroup;
import org.apache.pdfbox.pdmodel.graphics.optionalcontent.PDOptionalContentProperties;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotation;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationLink;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationWidget;
import org.apache.pdfbox.pdmodel.interactive.form.PDAcroForm;
import org.apache.pdfbox.pdmodel.interactive.form.PDField;
import org.apache.pdfbox.pdmodel.interactive.form.PDTerminalField;
import org.springframework.stereotype.Service;
import pe.pdflisto.exception.HAPdfListoException;
import pe.pdflisto.pdf.HALectorTexto;
import pe.pdflisto.verificacion.HAComprobacion;
import pe.pdflisto.verificacion.HAComprobacion.HAEstado;
import pe.pdflisto.verificacion.HAInformeVerificacion;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class HAAnalizadorPrivacidad {
    private final HADetectorDatos haDetector;

    public record HAInventario(Map<String, String> haInformacion, boolean haXmp, int haComentarios, List<String> haAdjuntos,
                             int haCapas, int haCapasOcultas, boolean haJavascript, int haCamposConValor,
                             int haRevisionesAnteriores, boolean haCifrado) {
        public boolean haHayAlgoQueLimpiar() {
            return !haInformacion.isEmpty() || haXmp || haComentarios > 0 || !haAdjuntos.isEmpty() || haJavascript
                    || haRevisionesAnteriores > 0;
        }
    }

    public HAInventario haInventario(File haArchivo, String haContrasena) {
        try (PDDocument haPdf = Loader.loadPDF(haArchivo, haContrasena)) {
            return haInventario(haPdf, haArchivo);
        } catch (IOException haE) {
            throw new HAPdfListoException("No se pudo analizar el PDF.", haE);
        }
    }

    public HAInformeVerificacion haInforme(File haArchivo, String haContrasena, String haNombre) {
        try (PDDocument haPdf = Loader.loadPDF(haArchivo, haContrasena)) {
            HAInventario haInv = haInventario(haPdf, haArchivo);
            List<HADatoSensible> haDatos = haDetector.haDetectar(HALectorTexto.haLeer(haPdf), haDetector.haReglasActivas());
            List<HAComprobacion> haLista = new ArrayList<>();

            List<HADatoSensible> haProbables = haDatos.stream().filter(HADatoSensible::haProbable).toList();
            haLista.add(haProbables.isEmpty()
                    ? new HAComprobacion("Datos sensibles", HAEstado.CUMPLE, haDatos.isEmpty()
                        ? "No se detectaron DNI, RUC, celulares, correos, cuentas ni direcciones."
                        : "Solo hay " + haDatos.size() + " posibles datos dudosos; revísalos en la pestaña Tachado.")
                    : new HAComprobacion("Datos sensibles", HAEstado.REVISAR, "Contiene " + haResumen(haProbables)
                        + ". Puedes tacharlos en la pestaña Tachado."));
            if (haInv.haInformacion().isEmpty() && !haInv.haXmp()) {
                haLista.add(new HAComprobacion("Metadatos", HAEstado.CUMPLE, "No tiene autor, programa ni fechas guardadas."));
            } else {
                String haDetalle = haInv.haInformacion().entrySet().stream()
                        .map(haE -> haE.getKey() + " «" + haE.getValue() + "»").collect(Collectors.joining("; "));
                haLista.add(new HAComprobacion("Metadatos", HAEstado.REVISAR,
                        (haDetalle.isEmpty() ? "" : haDetalle + ". ") + (haInv.haXmp() ? "Tiene metadatos XMP. " : "")
                                + "Se ven en Propiedades del archivo."));
            }
            haLista.add(haInv.haComentarios() == 0
                    ? new HAComprobacion("Comentarios", HAEstado.CUMPLE, "No tiene comentarios ni anotaciones.")
                    : new HAComprobacion("Comentarios", HAEstado.REVISAR, haInv.haComentarios()
                        + " comentarios o anotaciones (pueden incluir notas internas)."));
            haLista.add(haInv.haAdjuntos().isEmpty()
                    ? new HAComprobacion("Archivos adjuntos", HAEstado.CUMPLE, "No lleva archivos adjuntos.")
                    : new HAComprobacion("Archivos adjuntos", HAEstado.REVISAR, "Lleva " + haInv.haAdjuntos().size()
                        + " adjunto(s): " + String.join(", ", haInv.haAdjuntos()) + "."));
            if (haInv.haCapas() == 0) {
                haLista.add(new HAComprobacion("Capas", HAEstado.CUMPLE, "No tiene capas."));
            } else {
                haLista.add(new HAComprobacion("Capas", haInv.haCapasOcultas() > 0 ? HAEstado.REVISAR : HAEstado.INFO,
                        haInv.haCapas() + " capa(s)" + (haInv.haCapasOcultas() > 0 ? ", " + haInv.haCapasOcultas()
                                + " oculta(s): su contenido no se ve pero sigue en el archivo. Para eliminarlo de verdad, "
                                + "tacha esas páginas (se convierten en imagen)." : ".")));
            }
            haLista.add(haInv.haJavascript()
                    ? new HAComprobacion("JavaScript", HAEstado.REVISAR, "Contiene código que se ejecuta al abrirlo.")
                    : new HAComprobacion("JavaScript", HAEstado.CUMPLE, "No contiene JavaScript."));
            haLista.add(new HAComprobacion("Formularios", haInv.haCamposConValor() > 0 ? HAEstado.INFO : HAEstado.CUMPLE,
                    haInv.haCamposConValor() > 0 ? haInv.haCamposConValor() + " campo(s) de formulario con datos llenados."
                            : "No hay campos de formulario con datos."));
            haLista.add(haInv.haRevisionesAnteriores() > 0
                    ? new HAComprobacion("Historial de edición", HAEstado.REVISAR, "Guarda " + haInv.haRevisionesAnteriores()
                        + " versión(es) anterior(es) dentro del archivo: alguien podría recuperar lo que se borró antes. "
                        + "Se eliminan al limpiar metadatos.")
                    : new HAComprobacion("Historial de edición", HAEstado.CUMPLE, "No guarda versiones anteriores."));
            haLista.add(new HAComprobacion("Contraseña", HAEstado.INFO,
                    haInv.haCifrado() ? "Está protegido con contraseña." : "No tiene contraseña: cualquiera puede abrirlo."));
            return new HAInformeVerificacion(haNombre, LocalDateTime.now(), null, List.of(), haLista);
        } catch (IOException haE) {
            throw new HAPdfListoException("No se pudo analizar el PDF.", haE);
        }
    }

    static HAInventario haInventario(PDDocument haPdf, File haArchivo) {
        Map<String, String> haInfo = new LinkedHashMap<>();
        PDDocumentInformation haDi = haPdf.getDocumentInformation();
        haPoner(haInfo, "Autor", haDi.getAuthor());
        haPoner(haInfo, "Título", haDi.getTitle());
        haPoner(haInfo, "Asunto", haDi.getSubject());
        haPoner(haInfo, "Palabras clave", haDi.getKeywords());
        haPoner(haInfo, "Programa", haDi.getCreator());
        haPoner(haInfo, "Productor", haDi.getProducer());
        if (haDi.getCreationDate() != null) {
            haInfo.put("Fecha de creación", String.format("%tF", haDi.getCreationDate()));
        }
        if (haDi.getModificationDate() != null) {
            haInfo.put("Fecha de modificación", String.format("%tF", haDi.getModificationDate()));
        }
        boolean haXmp = haPdf.getDocumentCatalog().getMetadata() != null;
        int haComentarios = 0;
        List<String> haAdjuntos = new ArrayList<>();
        for (PDPage haP : haPdf.getPages()) {
            haXmp |= haP.getMetadata() != null;
            try {
                for (PDAnnotation haA : haP.getAnnotations()) {
                    if ("FileAttachment".equals(haA.getSubtype())) {
                        haAdjuntos.add("adjunto en una anotación");
                    } else if (!(haA instanceof PDAnnotationLink) && !(haA instanceof PDAnnotationWidget)) {
                        haComentarios++;
                    }
                }
            } catch (IOException haIgnorado) {
            }
        }
        var haNombres = haPdf.getDocumentCatalog().getNames();
        boolean haJavascript = haPdf.getDocumentCatalog().getCOSObject().containsKey(COSName.AA);
        if (haPdf.getDocumentCatalog().getCOSObject().getDictionaryObject(COSName.OPEN_ACTION) instanceof COSDictionary haAccion
                && COSName.JAVA_SCRIPT.equals(haAccion.getCOSName(COSName.S))) {
            haJavascript = true;
        }
        if (haNombres != null) {
            try {
                if (haNombres.getEmbeddedFiles() != null && haNombres.getEmbeddedFiles().getNames() != null) {
                    haAdjuntos.addAll(new TreeMap<>(haNombres.getEmbeddedFiles().getNames()).keySet());
                }
                if (haNombres.getEmbeddedFiles() != null && haNombres.getEmbeddedFiles().getKids() != null) {
                    haAdjuntos.add("(más adjuntos)");
                }
            } catch (IOException haIgnorado) {
                haAdjuntos.add("(adjuntos ilegibles)");
            }
            haJavascript |= haNombres.getJavaScript() != null;
        }
        int haCapas = 0;
        int haOcultas = 0;
        PDOptionalContentProperties haOc = haPdf.getDocumentCatalog().getOCProperties();
        if (haOc != null) {
            for (PDOptionalContentGroup haG : haOc.getOptionalContentGroups()) {
                haCapas++;
                if (!haOc.isGroupEnabled(haG)) {
                    haOcultas++;
                }
            }
        }
        int haCampos = 0;
        PDAcroForm haForm = haPdf.getDocumentCatalog().getAcroForm(null);
        if (haForm != null) {
            for (PDField haF : haForm.getFieldTree()) {
                if (haF instanceof PDTerminalField && haF.getValueAsString() != null && !haF.getValueAsString().isBlank()) {
                    haCampos++;
                }
            }
        }
        return new HAInventario(haInfo, haXmp, haComentarios, haAdjuntos, haCapas, haOcultas, haJavascript, haCampos,
                haRevisionesAnteriores(haArchivo), haPdf.isEncrypted());
    }

    static int haRevisionesAnteriores(File haArchivo) {
        try (InputStream haIn = Files.newInputStream(haArchivo.toPath())) {
            byte[] haDatos = haIn.readAllBytes();
            String haTexto = new String(haDatos, StandardCharsets.ISO_8859_1);
            int haFinales = 0;
            for (int haI = haTexto.indexOf("%%EOF"); haI >= 0; haI = haTexto.indexOf("%%EOF", haI + 5)) {
                haFinales++;
            }
            boolean haLinealizado = haTexto.substring(0, Math.min(2048, haTexto.length())).contains("/Linearized");
            return Math.max(0, haFinales - 1 - (haLinealizado ? 1 : 0));
        } catch (IOException | OutOfMemoryError haE) {
            return 0;
        }
    }

    private static void haPoner(Map<String, String> haM, String haClave, String haValor) {
        if (haValor != null && !haValor.isBlank()) {
            String haV = haValor.trim();
            haM.put(haClave, haV.length() > 50 ? haV.substring(0, 50) + "…" : haV);
        }
    }

    static String haResumen(List<HADatoSensible> haDatos) {
        Map<HATipoDato, Long> haCuenta = haDatos.stream()
                .collect(Collectors.groupingBy(HADatoSensible::haTipo, TreeMap::new, Collectors.counting()));
        return haCuenta.entrySet().stream().map(haE -> haE.getValue() + " " + haE.getKey())
                .collect(Collectors.joining(", "));
    }
}
