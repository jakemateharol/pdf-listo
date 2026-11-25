package pe.pdflisto.privacidad;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDMetadata;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotation;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDDocumentOutline;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineNode;
import org.apache.pdfbox.pdmodel.interactive.form.PDAcroForm;
import org.apache.pdfbox.pdmodel.interactive.form.PDField;
import org.apache.pdfbox.text.TextPosition;
import org.springframework.stereotype.Service;
import pe.pdflisto.pdf.HALectorTexto;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@Slf4j
public class HAVerificadorTachado {
    public record HAInforme(boolean haSeguro, int haDatosRevisados, int haAreasRevisadas, List<String> haProblemas) {
    }

    public HAInforme haVerificar(File haArchivo, String haContrasena, List<HAAreaTachado> haAreas, List<HADatoSensible> haDatos) {
        List<String> haProblemas = new ArrayList<>();
        try (PDDocument haPdf = Loader.loadPDF(haArchivo, haContrasena)) {
            List<HALectorTexto.HAPaginaTexto> haPaginas = HALectorTexto.haLeer(haPdf);
            haLetrasBajoRecuadros(haPaginas, haAreas, haProblemas);
            Map<String, String> haLugares = haLugaresConTexto(haPdf, haPaginas);
            Set<String> haRevisados = new LinkedHashSet<>();
            for (HADatoSensible haD : haDatos) {
                if (haD.haValor() == null || haD.haValor().length() < 3 || !haRevisados.add(haD.haTipo() + ":" + haD.haValor())) {
                    continue;
                }
                for (Map.Entry<String, String> haLugar : haLugares.entrySet()) {
                    if (haAparece(haD, haLugar.getValue())) {
                        haProblemas.add("«" + haD.haTexto() + "» (" + haD.haTipo() + ") todavía aparece en " + haLugar.getKey() + ".");
                    }
                }
            }
            return new HAInforme(haProblemas.isEmpty(), haRevisados.size(), haAreas.size(), haProblemas);
        } catch (IOException | RuntimeException haE) {
            log.warn("No se pudo verificar el tachado de {}", haArchivo, haE);
            haProblemas.add("No se pudo releer el resultado para verificarlo: " + haE.getMessage());
            return new HAInforme(false, 0, haAreas.size(), haProblemas);
        }
    }

    static boolean haAparece(HADatoSensible haD, String haContenido) {
        if (haD.haTipo().haNumerico()) {
            String haCompacto = haContenido.replaceAll("(?<=\\d)[\\s.\\-]+(?=\\d)", "");
            String haPrefijo = haD.haTipo() == HATipoDato.CELULAR ? "(?:51)?" : "";
            return java.util.regex.Pattern.compile("(?<!\\d)" + haPrefijo + java.util.regex.Pattern.quote(haD.haValor()) + "(?!\\d)")
                    .matcher(haCompacto).find();
        }
        return HADetectorDatos.haNormalizarValor(haD.haTipo(), haContenido).contains(haD.haValor());
    }

    private static void haLetrasBajoRecuadros(List<HALectorTexto.HAPaginaTexto> haPaginas, List<HAAreaTachado> haAreas,
                                            List<String> haProblemas) {
        for (HALectorTexto.HAPaginaTexto haP : haPaginas) {
            List<HAAreaTachado> haEnPagina = haAreas.stream().filter(haA -> haA.haPagina() == haP.haIndice()).toList();
            if (haEnPagina.isEmpty()) {
                continue;
            }
            StringBuilder haQuedan = new StringBuilder();
            for (int haI = 0; haI < haP.haLetras().size(); haI++) {
                TextPosition haL = haP.haLetras().get(haI);
                if (haL != null && HATachar.haDentroDeAlguna(haL, haEnPagina)) {
                    haQuedan.append(haP.haTexto().charAt(haI));
                }
            }
            if (!haQuedan.isEmpty()) {
                haProblemas.add("Queda texto debajo del tachado en la página " + (haP.haIndice() + 1) + ": «"
                        + (haQuedan.length() > 30 ? haQuedan.substring(0, 30) + "…" : haQuedan) + "».");
            }
        }
    }

    private static Map<String, String> haLugaresConTexto(PDDocument haPdf, List<HALectorTexto.HAPaginaTexto> haPaginas) {
        Map<String, String> haLugares = new LinkedHashMap<>();
        for (HALectorTexto.HAPaginaTexto haP : haPaginas) {
            haLugares.put("el texto de la página " + (haP.haIndice() + 1), haP.haTexto());
        }
        int haN = 0;
        for (PDPage haPagina : haPdf.getPages()) {
            haN++;
            try {
                StringBuilder haSb = new StringBuilder();
                for (PDAnnotation haA : haPagina.getAnnotations()) {
                    haAgregar(haSb, haA.getContents());
                    haAgregar(haSb, haA.getCOSObject().getString(COSName.T));
                    haAgregar(haSb, haA.getCOSObject().getString(COSName.getPDFName("RC")));
                }
                if (!haSb.isEmpty()) {
                    haLugares.put("un comentario de la página " + haN, haSb.toString());
                }
            } catch (IOException haIgnorado) {
            }
        }
        PDAcroForm haForm = haPdf.getDocumentCatalog().getAcroForm(null);
        if (haForm != null) {
            StringBuilder haSb = new StringBuilder();
            for (PDField haF : haForm.getFieldTree()) {
                haAgregar(haSb, haF.getValueAsString());
                haAgregar(haSb, haF.getPartialName());
            }
            haLugares.put("un campo de formulario", haSb.toString());
        }
        PDDocumentOutline haIndice = haPdf.getDocumentCatalog().getDocumentOutline();
        if (haIndice != null) {
            StringBuilder haSb = new StringBuilder();
            haMarcadores(haIndice, haSb, 0);
            haLugares.put("los marcadores", haSb.toString());
        }
        PDDocumentInformation haInfo = haPdf.getDocumentInformation();
        StringBuilder haMeta = new StringBuilder();
        for (String haClave : haInfo.getMetadataKeys()) {
            haAgregar(haMeta, haInfo.getCustomMetadataValue(haClave));
        }
        haLugares.put("los metadatos del documento", haMeta.toString());
        PDMetadata haXmp = haPdf.getDocumentCatalog().getMetadata();
        if (haXmp != null) {
            try (InputStream haIn = haXmp.exportXMPMetadata()) {
                haLugares.put("los metadatos XMP", new String(haIn.readAllBytes(), StandardCharsets.UTF_8));
            } catch (IOException haIgnorado) {
            }
        }
        var haNombres = haPdf.getDocumentCatalog().getNames();
        if (haNombres != null && haNombres.getEmbeddedFiles() != null) {
            try {
                var haHijos = haNombres.getEmbeddedFiles().getNames();
                if (haHijos != null) {
                    haLugares.put("el nombre de un archivo adjunto", String.join(" ", haHijos.keySet()));
                }
            } catch (IOException haIgnorado) {
            }
        }
        return haLugares;
    }

    private static void haMarcadores(PDOutlineNode haNodo, StringBuilder haSb, int haProfundidad) {
        if (haProfundidad > 12) {
            return;
        }
        for (PDOutlineItem haItem : haNodo.children()) {
            haAgregar(haSb, haItem.getTitle());
            haMarcadores(haItem, haSb, haProfundidad + 1);
        }
    }

    private static void haAgregar(StringBuilder haSb, String haTexto) {
        if (haTexto != null && !haTexto.isBlank()) {
            haSb.append(haTexto).append('\n');
        }
    }
}
