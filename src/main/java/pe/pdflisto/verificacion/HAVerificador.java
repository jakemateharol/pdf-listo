package pe.pdflisto.verificacion;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;
import pe.pdflisto.operaciones.HAArchivos;
import pe.pdflisto.pdf.HALectorTexto;
import pe.pdflisto.privacidad.HADatoSensible;
import pe.pdflisto.privacidad.HADetectorDatos;
import pe.pdflisto.privacidad.HATipoDato;
import pe.pdflisto.verificacion.HAComprobacion.HAEstado;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

@Service
@Slf4j
public class HAVerificador {
    private final HADetectorDatos haDetector;

    public HAVerificador(HADetectorDatos haDetector) {
        this.haDetector = haDetector;
    }

    public HAInformeVerificacion haVerificar(File haArchivo, String haContrasena, HARequisitos haReq,
                                         String haDocumento, String haPedido, List<String> haPasos) {
        List<HAComprobacion> haLista = new ArrayList<>();
        try (PDDocument haPdf = Loader.loadPDF(haArchivo, haContrasena)) {
            int haPaginas = haPdf.getNumberOfPages();
            haLista.add(new HAComprobacion("Integridad", HAEstado.CUMPLE, "El archivo se abre correctamente (" + haPaginas + " páginas)."));
            haLista.add(haPeso(haArchivo.length(), haReq));
            haLista.add(haPaginas(haPaginas, haReq));
            List<String> haTextos = haTextosSinEspacios(haPdf);
            haLista.add(haNumeracion(haPdf, haReq));
            haLista.add(haMarcaDeAgua(haTextos, haReq));
            haLista.add(haTextoBuscable(haTextos, haReq.haTextoBuscable()));
            haLista.add(haDatosSensibles(haPdf));
            haLista.add(haMetadatos(haPdf, haReq.haSinMetadatos()));
            haLista.add(new HAComprobacion("Contraseña", HAEstado.INFO,
                    haPdf.isEncrypted() ? "El archivo está protegido con contraseña." : "El archivo no tiene contraseña."));
            haLista.add(new HAComprobacion("Perfil de entrega", HAEstado.NO_APLICA,
                    "Para revisar requisitos de tesis, trámite o licitación usa la pestaña Entrega → Validar."));
        } catch (IOException | RuntimeException haE) {
            log.warn("No se pudo verificar {}", haArchivo, haE);
            haLista.add(new HAComprobacion("Integridad", HAEstado.REVISAR,
                    "El resultado no se pudo abrir para verificarlo: " + haE.getMessage()));
        }
        return new HAInformeVerificacion(haDocumento, LocalDateTime.now(), haPedido, haPasos == null ? List.of() : haPasos, haLista);
    }

    static HAComprobacion haPeso(long haBytes, HARequisitos haReq) {
        String haCambio = haReq.haPesoOriginal() > 0 && haReq.haPesoOriginal() != haBytes
                ? " (antes: " + HAArchivos.haTamanoLegible(haReq.haPesoOriginal()) + ")" : "";
        boolean haPesaMas = haReq.haPesoOriginal() > 0 && haBytes > haReq.haPesoOriginal();
        if (haReq.haComprimir() && haPesaMas) {
            String haLimite = haReq.haPesoMaximo() == null ? ""
                    : (haBytes <= haReq.haPesoMaximo() ? " Está por debajo del límite pedido (" : " Además supera el límite pedido (")
                    + HAArchivos.haTamanoLegible(haReq.haPesoMaximo()) + ").";
            return new HAComprobacion("Peso", HAEstado.REVISAR, "Se pidió comprimir, pero el resultado pesa "
                    + HAArchivos.haTamanoLegible(haBytes) + " y el original " + HAArchivos.haTamanoLegible(haReq.haPesoOriginal())
                    + ". " + haExplicarAumento(haReq.haPesosPorPaso()) + haLimite);
        }
        if (haReq.haPesoMaximo() != null) {
            boolean haOk = haBytes <= haReq.haPesoMaximo();
            return new HAComprobacion("Peso", haOk ? HAEstado.CUMPLE : HAEstado.REVISAR,
                    "Pesa " + HAArchivos.haTamanoLegible(haBytes) + haCambio + "; se pidió menos de "
                            + HAArchivos.haTamanoLegible(haReq.haPesoMaximo()) + ".");
        }
        if (haReq.haComprimir() && haReq.haPesoOriginal() > 0) {
            if (haBytes < haReq.haPesoOriginal()) {
                long haPorcentaje = Math.round(100.0 * (haReq.haPesoOriginal() - haBytes) / haReq.haPesoOriginal());
                return new HAComprobacion("Peso", HAEstado.CUMPLE, "Pesa " + HAArchivos.haTamanoLegible(haBytes) + haCambio
                        + ": se redujo " + haPorcentaje + " %.");
            }
            return new HAComprobacion("Peso", HAEstado.INFO, "Pesa lo mismo que antes ("
                    + HAArchivos.haTamanoLegible(haBytes) + "): el PDF ya estaba optimizado y no se pudo reducir más.");
        }
        return new HAComprobacion("Peso", HAEstado.INFO, "Pesa " + HAArchivos.haTamanoLegible(haBytes) + haCambio + ".");
    }

    static String haExplicarAumento(List<HAPesoPaso> haPesos) {
        if (haPesos.isEmpty()) {
            return "comprimir debía reducir el peso; revisa qué pasos agregaron contenido.";
        }
        List<String> haPartes = new ArrayList<>();
        boolean haCompresionSinEfecto = haPesos.stream().anyMatch(haP -> haP.haCompresion() && haP.haDiferencia() >= 0);
        if (haCompresionSinEfecto) {
            haPartes.add("no se pudo reducir: el PDF ya estaba optimizado");
        }
        List<String> haAgregaron = new ArrayList<>();
        for (HAPesoPaso haP : haPesos) {
            if (!haP.haCompresion() && haP.haDiferencia() > 0) {
                haAgregaron.add(haP.haDescripcion() + " (+" + HAArchivos.haTamanoLegible(haP.haDiferencia()) + ")");
            }
        }
        if (!haAgregaron.isEmpty()) {
            haPartes.add("agregaron peso: " + String.join(", ", haAgregaron));
        }
        haPesos.stream().filter(haP -> haP.haCompresion() && haP.haDiferencia() < 0).findFirst().ifPresent(haP ->
                haPartes.add("la compresión quitó " + HAArchivos.haTamanoLegible(-haP.haDiferencia()) + ", pero no alcanzó"));
        String haTexto = String.join("; ", haPartes) + ".";
        return Character.toUpperCase(haTexto.charAt(0)) + haTexto.substring(1);
    }

    private static HAComprobacion haPaginas(int haPaginas, HARequisitos haReq) {
        if (haReq.haPaginasEsperadas() == null) {
            return new HAComprobacion("Número de páginas", HAEstado.INFO, "Tiene " + haPaginas + " páginas.");
        }
        boolean haOk = haPaginas == haReq.haPaginasEsperadas();
        return new HAComprobacion("Número de páginas", haOk ? HAEstado.CUMPLE : HAEstado.REVISAR,
                "Tiene " + haPaginas + "; se esperaban " + haReq.haPaginasEsperadas() + ".");
    }

    private static HAComprobacion haNumeracion(PDDocument haPdf, HARequisitos haReq) throws IOException {
        return haNumeracion(haPdf, haReq.haNumeracion());
    }

    static HAComprobacion haNumeracion(PDDocument haPdf, HARequisitos.HANumeracion haN) throws IOException {
        if (haN == null) {
            return new HAComprobacion("Numeración", HAEstado.NO_APLICA, "No se pidió numerar.");
        }
        List<String> haMargenes = HATextoEnMargen.haLeer(haPdf, haN.haArriba());
        int haTotal = haMargenes.size();
        int haUltimo = haN.haPrimerNumero() + (haTotal - haN.haDesde());
        List<Integer> haFaltan = new ArrayList<>();
        for (int haI = haN.haDesde() - 1; haI < haTotal; haI++) {
            int haNumero = haN.haPrimerNumero() + (haI - (haN.haDesde() - 1));
            String haEsperado = haN.haFormato().replace("{n}", String.valueOf(haNumero))
                    .replace("{total}", String.valueOf(haUltimo)).replaceAll("\\s", "");
            Pattern haExacto = Pattern.compile("(?<!\\d)" + Pattern.quote(haEsperado) + "(?!\\d)");
            if (!haExacto.matcher(haMargenes.get(haI)).find()) {
                haFaltan.add(haI + 1);
            }
        }
        String haDonde = haN.haArriba() ? "margen superior" : "margen inferior";
        if (haFaltan.isEmpty()) {
            return new HAComprobacion("Numeración", HAEstado.CUMPLE, "Cada página desde la " + haN.haDesde()
                    + " tiene su número correcto en el " + haDonde + " (verificado leyendo el texto).");
        }
        return new HAComprobacion("Numeración", HAEstado.REVISAR, "No se encontró el número correcto en el " + haDonde + " de "
                + (haFaltan.size() == 1 ? "la página " : "las páginas ") + haResumirLista(haFaltan) + ".");
    }

    private static HAComprobacion haMarcaDeAgua(List<String> haTextos, HARequisitos haReq) {
        if (haReq.haMarcaDeAgua() == null) {
            return new HAComprobacion("Marca de agua", HAEstado.NO_APLICA, "No se pidió marca de agua.");
        }
        String haBuscado = haReq.haMarcaDeAgua().replaceAll("\\s", "");
        List<Integer> haFaltan = new ArrayList<>();
        for (int haI = 0; haI < haTextos.size(); haI++) {
            if (!haTextos.get(haI).contains(haBuscado)) {
                haFaltan.add(haI + 1);
            }
        }
        return haFaltan.isEmpty()
                ? new HAComprobacion("Marca de agua", HAEstado.CUMPLE, "«" + haReq.haMarcaDeAgua() + "» está en todas las páginas.")
                : new HAComprobacion("Marca de agua", HAEstado.REVISAR, "Falta en las páginas " + haResumirLista(haFaltan) + ".");
    }

    private static HAComprobacion haTextoBuscable(List<String> haTextos, boolean haExigido) {
        long haConTexto = haTextos.stream().filter(haT -> haT.length() >= 20).count();
        if (haExigido) {
            List<Integer> haFaltan = new ArrayList<>();
            for (int haI = 0; haI < haTextos.size(); haI++) {
                if (haTextos.get(haI).length() < 20) {
                    haFaltan.add(haI + 1);
                }
            }
            return haFaltan.isEmpty()
                    ? new HAComprobacion("Texto buscable", HAEstado.CUMPLE, "Todas las páginas tienen texto: se pueden buscar y copiar.")
                    : new HAComprobacion("Texto buscable", HAEstado.REVISAR, "Siguen sin texto las páginas " + haResumirLista(haFaltan)
                        + " (pueden estar en blanco, ser fotos o estar muy borrosas).");
        }
        if (haConTexto == 0) {
            return new HAComprobacion("Texto buscable", HAEstado.REVISAR,
                    "No tiene texto seleccionable (parece escaneado): no se podrá buscar ni copiar. "
                            + "Usa Herramientas → Reconocer texto (OCR).");
        }
        if (haConTexto < haTextos.size()) {
            return new HAComprobacion("Texto buscable", HAEstado.INFO,
                    haConTexto + " de " + haTextos.size() + " páginas tienen texto; las demás pueden ser imágenes o estar en blanco.");
        }
        return new HAComprobacion("Texto buscable", HAEstado.CUMPLE, "Todas las páginas tienen texto que se puede buscar y copiar.");
    }

    private HAComprobacion haDatosSensibles(PDDocument haPdf) throws IOException {
        List<HADatoSensible> haDatos = haDetector.haDetectar(HALectorTexto.haLeer(haPdf), haDetector.haReglasActivas()).stream()
                .filter(HADatoSensible::haProbable).toList();
        if (haDatos.isEmpty()) {
            return new HAComprobacion("Datos sensibles", HAEstado.CUMPLE, "No se detectaron DNI, RUC, celulares, correos ni cuentas.");
        }
        java.util.Map<HATipoDato, Long> haCuenta = new java.util.TreeMap<>();
        haDatos.forEach(haD -> haCuenta.merge(haD.haTipo(), 1L, Long::sum));
        StringBuilder haSb = new StringBuilder();
        haCuenta.forEach((haT, haN) -> haSb.append(haSb.isEmpty() ? "" : ", ").append(haN).append(" ").append(haT));
        return new HAComprobacion("Datos sensibles", HAEstado.INFO, "Contiene " + haSb
                + ". Si no deben ir en el archivo, táchalos en la pestaña Tachado.");
    }

    private static HAComprobacion haMetadatos(PDDocument haPdf, boolean haSePidioLimpiar) {
        PDDocumentInformation haInfo = haPdf.getDocumentInformation();
        List<String> haHay = new ArrayList<>();
        haAgregar(haHay, "autor", haInfo.getAuthor());
        haAgregar(haHay, "título", haInfo.getTitle());
        haAgregar(haHay, "asunto", haInfo.getSubject());
        haAgregar(haHay, "palabras clave", haInfo.getKeywords());
        haAgregar(haHay, "programa", haInfo.getCreator());
        haAgregar(haHay, "productor", haInfo.getProducer());
        if (haInfo.getCreationDate() != null) {
            haHay.add("fecha de creación");
        }
        if (haPdf.getDocumentCatalog().getMetadata() != null) {
            haHay.add("metadatos XMP");
        }
        if (haHay.isEmpty()) {
            return new HAComprobacion("Metadatos", HAEstado.CUMPLE, "No contiene metadatos.");
        }
        return new HAComprobacion("Metadatos", haSePidioLimpiar ? HAEstado.REVISAR : HAEstado.INFO, "Contiene: "
                + String.join("; ", haHay) + (haSePidioLimpiar ? "." : ". Puedes limpiarlos en la pestaña Tachado."));
    }

    private static void haAgregar(List<String> haLista, String haNombre, String haValor) {
        if (haValor != null && !haValor.isBlank()) {
            String haV = haValor.trim();
            haLista.add(haNombre + " «" + (haV.length() > 40 ? haV.substring(0, 40) + "…" : haV) + "»");
        }
    }

    static List<String> haTextosSinEspacios(PDDocument haPdf) throws IOException {
        PDFTextStripper haLector = new PDFTextStripper();
        List<String> haTextos = new ArrayList<>();
        for (int haI = 1; haI <= haPdf.getNumberOfPages(); haI++) {
            haLector.setStartPage(haI);
            haLector.setEndPage(haI);
            haTextos.add(haLector.getText(haPdf).replaceAll("\\s", ""));
        }
        return haTextos;
    }

    static String haResumirLista(List<Integer> haNumeros) {
        if (haNumeros.size() <= 8) {
            return haNumeros.toString().replaceAll("[\\[\\]]", "");
        }
        return haNumeros.subList(0, 8).toString().replaceAll("[\\[\\]]", "") + "… (" + haNumeros.size() + " en total)";
    }
}
