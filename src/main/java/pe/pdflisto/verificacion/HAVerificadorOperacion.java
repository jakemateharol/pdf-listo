package pe.pdflisto.verificacion;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.interactive.form.PDAcroForm;
import org.apache.pdfbox.pdmodel.interactive.form.PDCheckBox;
import org.apache.pdfbox.pdmodel.interactive.form.PDChoice;
import org.apache.pdfbox.pdmodel.interactive.form.PDField;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.stereotype.Service;
import pe.pdflisto.operaciones.HAArchivos;
import pe.pdflisto.operaciones.HACambiarTamano;
import pe.pdflisto.operaciones.HAUnir;
import pe.pdflisto.privacidad.HAAnalizadorPrivacidad;
import pe.pdflisto.verificacion.HAComprobacion.HAEstado;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@Slf4j
public class HAVerificadorOperacion {
    private static final int HA_MIN_TEXTO = 20;

    private final HAAnalizadorPrivacidad haAnalizador;

    public HAVerificadorOperacion(HAAnalizadorPrivacidad haAnalizador) {
        this.haAnalizador = haAnalizador;
    }

    public HAInformeVerificacion haVerificar(String haDescripcion, File haAntes, String haClaveAntes,
                                         File haDespues, String haClaveDespues, List<HAEsperado> haEsperado, String haDocumento) {
        List<HAComprobacion> haLista = new ArrayList<>();
        try (PDDocument haPrevio = Loader.loadPDF(haAntes, haClaveAntes);
             PDDocument haNuevo = Loader.loadPDF(haDespues, haClaveDespues)) {
            haLista.add(new HAComprobacion("Integridad", HAEstado.CUMPLE,
                    "El resultado se abre correctamente (" + haNuevo.getNumberOfPages() + " páginas)."));
            HAContexto haC = new HAContexto(haPrevio, haNuevo, haAntes, haDespues, haClaveDespues);
            boolean haHayPeso = false;
            for (HAEsperado haE : haEsperado) {
                try {
                    haLista.add(haComprobar(haE, haC));
                } catch (IOException | RuntimeException haEx) {
                    log.warn("No se pudo comprobar {} en {}", haE, haDespues, haEx);
                    haLista.add(new HAComprobacion(haNombre(haE), HAEstado.REVISAR,
                            "No se pudo comprobar: " + haEx.getMessage()));
                }
                haHayPeso |= haE instanceof HAEsperado.HAPeso;
            }
            if (!haHayPeso) {
                haLista.add(new HAComprobacion("Peso", HAEstado.INFO,
                        HAArchivos.haTamanoLegible(haAntes.length()) + " → " + HAArchivos.haTamanoLegible(haDespues.length()) + "."));
            }
        } catch (IOException | RuntimeException haE) {
            log.warn("No se pudo verificar {}", haDespues, haE);
            haLista.add(new HAComprobacion("Integridad", HAEstado.REVISAR,
                    "El resultado no se pudo abrir para verificarlo: " + haE.getMessage()));
        }
        return new HAInformeVerificacion(haDocumento, LocalDateTime.now(), null, List.of(haDescripcion), haLista);
    }

    private static final class HAContexto {
        final PDDocument haAntes;
        final PDDocument haDespues;
        final File haArchivoAntes;
        final File haArchivoDespues;
        final String haClaveDespues;
        private List<String> haTextoAntes;
        private List<String> haTextoDespues;

        HAContexto(PDDocument haAntes, PDDocument haDespues, File haArchivoAntes, File haArchivoDespues, String haClaveDespues) {
            this.haAntes = haAntes;
            this.haClaveDespues = haClaveDespues;
            this.haDespues = haDespues;
            this.haArchivoAntes = haArchivoAntes;
            this.haArchivoDespues = haArchivoDespues;
        }

        List<String> haTextoAntes() throws IOException {
            if (haTextoAntes == null) {
                haTextoAntes = HAVerificador.haTextosSinEspacios(haAntes);
            }
            return haTextoAntes;
        }

        List<String> haTextoDespues() throws IOException {
            if (haTextoDespues == null) {
                haTextoDespues = HAVerificador.haTextosSinEspacios(haDespues);
            }
            return haTextoDespues;
        }
    }

    private HAComprobacion haComprobar(HAEsperado haE, HAContexto haC) throws IOException {
        if (haE instanceof HAEsperado.HAMismasPaginas) {
            return haMismasPaginas(haC);
        } else if (haE instanceof HAEsperado.HAOrden haO) {
            return haOrden(haO, haC);
        } else if (haE instanceof HAEsperado.HAUnidas haU) {
            return haUnidas(haU, haC);
        } else if (haE instanceof HAEsperado.HARotacion haR) {
            return haRotacion(haR, haC);
        } else if (haE instanceof HAEsperado.HANumeracion haN) {
            return HAVerificador.haNumeracion(haC.haDespues, haN.haNumeracion());
        } else if (haE instanceof HAEsperado.HATexto haT) {
            return haTexto(haT, haC);
        } else if (haE instanceof HAEsperado.HAEncabezadoPie haEp) {
            return haEncabezadoPie(haEp, haC);
        } else if (haE instanceof HAEsperado.HAContenidoCambiado haCc) {
            return haContenidoCambiado(haCc, haC);
        } else if (haE instanceof HAEsperado.HAAnotaciones haA) {
            return haAnotaciones(haA, haC);
        } else if (haE instanceof HAEsperado.HATamano haT) {
            return haTamano(haT, haC);
        } else if (haE instanceof HAEsperado.HATextoBuscable) {
            return haTextoBuscable(haC);
        } else if (haE instanceof HAEsperado.HASinMetadatos haM) {
            return haSinMetadatos(haM, haC);
        } else if (haE instanceof HAEsperado.HAContrasena haK) {
            return haContrasena(haK, haC);
        } else if (haE instanceof HAEsperado.HAPeso haP) {
            return haPeso(haP, haC);
        } else if (haE instanceof HAEsperado.HAFormulario haF) {
            return haFormulario(haF, haC);
        } else if (haE instanceof HAEsperado.HATachado haT) {
            return new HAComprobacion("Tachado", HAEstado.INFO, "Se tacharon datos en " + haPaginasTexto(haT.haPaginas())
                    + ". Que no se puedan recuperar se comprueba aparte (mira el resultado en la pestaña Tachado).");
        }
        throw new IllegalArgumentException("Comprobación desconocida: " + haE);
    }

    private static String haNombre(HAEsperado haE) {
        if (haE instanceof HAEsperado.HATexto haT) {
            return haT.haNombre();
        }
        if (haE instanceof HAEsperado.HAContenidoCambiado haCc) {
            return haCc.haNombre();
        }
        return HA_NOMBRES.getOrDefault(haE.getClass().getSimpleName(), "Comprobación");
    }

    private static final Map<String, String> HA_NOMBRES = Map.ofEntries(
            Map.entry("MismasPaginas", "Número de páginas"), Map.entry("Orden", "Orden de páginas"),
            Map.entry("Unidas", "Unión"), Map.entry("Rotacion", "Rotación"), Map.entry("Numeracion", "Numeración"),
            Map.entry("EncabezadoPie", "Encabezado y pie"), Map.entry("Anotaciones", "Anotaciones"),
            Map.entry("Tamano", "Tamaño de página"), Map.entry("TextoBuscable", "Texto buscable"),
            Map.entry("SinMetadatos", "Metadatos"), Map.entry("Contrasena", "Contraseña"), Map.entry("Peso", "Peso"),
            Map.entry("Formulario", "Formulario"), Map.entry("Tachado", "Tachado"));

    private static HAComprobacion haMismasPaginas(HAContexto haC) {
        int haA = haC.haAntes.getNumberOfPages();
        int haD = haC.haDespues.getNumberOfPages();
        return haA == haD
                ? new HAComprobacion("Número de páginas", HAEstado.CUMPLE, "Sigue teniendo " + haD + " páginas.")
                : new HAComprobacion("Número de páginas", HAEstado.REVISAR, "Tenía " + haA + " páginas y ahora tiene " + haD + ".");
    }

    private static HAComprobacion haOrden(HAEsperado.HAOrden haO, HAContexto haC) throws IOException {
        int haD = haC.haDespues.getNumberOfPages();
        if (haD != haO.haOrden().size()) {
            return new HAComprobacion("Orden de páginas", HAEstado.REVISAR,
                    "Se esperaban " + haO.haOrden().size() + " páginas y el resultado tiene " + haD + ".");
        }
        List<Integer> haDistintas = new ArrayList<>();
        for (int haI = 0; haI < haD; haI++) {
            int haOrigen = haO.haOrden().get(haI);
            if (haOrigen < 0 || haOrigen >= haC.haAntes.getNumberOfPages()
                    || !Arrays.equals(haHuella(haC.haAntes.getPage(haOrigen)), haHuella(haC.haDespues.getPage(haI)))) {
                haDistintas.add(haI + 1);
            }
        }
        return haDistintas.isEmpty()
                ? new HAComprobacion("Orden de páginas", HAEstado.CUMPLE,
                    "Tiene " + haD + " páginas y cada una es la que corresponde (comparando su contenido).")
                : new HAComprobacion("Orden de páginas", HAEstado.REVISAR,
                    "No coincide el contenido esperado en " + (haDistintas.size() == 1 ? "la página " : "las páginas ")
                            + HAVerificador.haResumirLista(haDistintas) + ".");
    }

    private static HAComprobacion haUnidas(HAEsperado.HAUnidas haU, HAContexto haC) throws IOException {
        int haEsperadas = 0;
        for (HAUnir.HAFuente haF : haU.haFuentes()) {
            if (haF.haEsActual()) {
                haEsperadas += haC.haAntes.getNumberOfPages();
            } else {
                try (PDDocument haOtro = Loader.loadPDF(haF.haArchivo(), haF.haContrasena())) {
                    haEsperadas += haOtro.getNumberOfPages();
                }
            }
        }
        int haD = haC.haDespues.getNumberOfPages();
        return haD == haEsperadas
                ? new HAComprobacion("Unión", HAEstado.CUMPLE, "Tiene " + haD + " páginas: la suma de los "
                    + haU.haFuentes().size() + " PDF unidos.")
                : new HAComprobacion("Unión", HAEstado.REVISAR, "Tiene " + haD + " páginas; se esperaban " + haEsperadas
                    + " (la suma de los " + haU.haFuentes().size() + " PDF).");
    }

    private static HAComprobacion haRotacion(HAEsperado.HARotacion haR, HAContexto haC) {
        if (haC.haAntes.getNumberOfPages() != haC.haDespues.getNumberOfPages()) {
            return new HAComprobacion("Rotación", HAEstado.REVISAR, "Cambió la cantidad de páginas al rotar.");
        }
        List<Integer> haMal = new ArrayList<>();
        for (int haI = 0; haI < haC.haDespues.getNumberOfPages(); haI++) {
            int haEsperado = haGiro(haC.haAntes.getPage(haI)) + (haR.haPaginas().contains(haI) ? haR.haGrados() : 0);
            if (haNormalizar(haEsperado) != haGiro(haC.haDespues.getPage(haI))) {
                haMal.add(haI + 1);
            }
        }
        return haMal.isEmpty()
                ? new HAComprobacion("Rotación", HAEstado.CUMPLE, (haR.haPaginas().size() == 1 ? "La página " : "Las páginas ")
                    + HAVerificador.haResumirLista(haSumarUno(haR.haPaginas())) + (haR.haPaginas().size() == 1 ? " giró " : " giraron ")
                    + haR.haGrados() + "°; las demás no cambiaron.")
                : new HAComprobacion("Rotación", HAEstado.REVISAR, "El giro no es el esperado en "
                    + (haMal.size() == 1 ? "la página " : "las páginas ") + HAVerificador.haResumirLista(haMal) + ".");
    }

    private static HAComprobacion haTexto(HAEsperado.HATexto haT, HAContexto haC) throws IOException {
        List<String> haTextos = haC.haTextoDespues();
        List<Integer> haFaltan = new ArrayList<>();
        for (int haI : haPaginas(haT.haPaginas(), haTextos.size())) {
            String haPagina = haTextos.get(haI);
            for (String haBuscado : haT.haTextos()) {
                String haSinEspacios = haBuscado == null ? "" : haBuscado.replaceAll("\\s", "");
                if (!haSinEspacios.isEmpty() && !haPagina.contains(haSinEspacios)) {
                    haFaltan.add(haI + 1);
                    break;
                }
            }
        }
        String haQue = haT.haTextos().size() == 1 ? "«" + haT.haTextos().get(0) + "»" : "el texto agregado";
        return haFaltan.isEmpty()
                ? new HAComprobacion(haT.haNombre(), HAEstado.CUMPLE, haQue + " está en " + haPaginasTexto(haT.haPaginas())
                    + " (verificado leyendo el texto).")
                : new HAComprobacion(haT.haNombre(), HAEstado.REVISAR, "No se encontró " + haQue + " en "
                    + (haFaltan.size() == 1 ? "la página " : "las páginas ") + HAVerificador.haResumirLista(haFaltan) + ".");
    }

    private static HAComprobacion haEncabezadoPie(HAEsperado.HAEncabezadoPie haEp, HAContexto haC) throws IOException {
        List<String> haTextos = haC.haTextoDespues();
        int haTotal = haTextos.size();
        List<Integer> haFaltan = new ArrayList<>();
        boolean haAlgoLegible = false;
        for (int haI = Math.max(0, haEp.haDesde() - 1); haI < haTotal; haI++) {
            for (String haT : haEp.haTextos()) {
                if (haT == null || haT.isBlank()) {
                    continue;
                }
                String haConcreto = haT.replace("{pagina}", String.valueOf(haI + 1)).replace("{total}", String.valueOf(haTotal))
                        .replace("{archivo}", haEp.haArchivo() == null ? "" : haEp.haArchivo());
                for (String haParte : haConcreto.split("\\{fecha\\}")) {
                    String haBuscado = haParte.replaceAll("\\s", "");
                    if (haBuscado.isEmpty()) {
                        continue;
                    }
                    haAlgoLegible = true;
                    if (!haTextos.get(haI).contains(haBuscado)) {
                        if (haFaltan.isEmpty() || haFaltan.get(haFaltan.size() - 1) != haI + 1) {
                            haFaltan.add(haI + 1);
                        }
                    }
                }
            }
        }
        if (!haAlgoLegible) {
            return new HAComprobacion("Encabezado y pie", HAEstado.INFO, "Solo lleva la fecha (" + LocalDate.now()
                    + "); no se puede comprobar leyendo el texto.");
        }
        return haFaltan.isEmpty()
                ? new HAComprobacion("Encabezado y pie", HAEstado.CUMPLE, "Está en cada página desde la " + haEp.haDesde()
                    + ", con sus variables reemplazadas (verificado leyendo el texto).")
                : new HAComprobacion("Encabezado y pie", HAEstado.REVISAR, "No se encontró completo en "
                    + (haFaltan.size() == 1 ? "la página " : "las páginas ") + HAVerificador.haResumirLista(haFaltan) + ".");
    }

    private static HAComprobacion haContenidoCambiado(HAEsperado.HAContenidoCambiado haCc, HAContexto haC) throws IOException {
        if (haC.haAntes.getNumberOfPages() != haC.haDespues.getNumberOfPages()) {
            return new HAComprobacion(haCc.haNombre(), HAEstado.REVISAR, "Cambió la cantidad de páginas.");
        }
        List<Integer> haSinCambio = new ArrayList<>();
        for (int haI : haPaginas(haCc.haPaginas(), haC.haDespues.getNumberOfPages())) {
            if (Arrays.equals(haHuella(haC.haAntes.getPage(haI)), haHuella(haC.haDespues.getPage(haI)))) {
                haSinCambio.add(haI + 1);
            }
        }
        return haSinCambio.isEmpty()
                ? new HAComprobacion(haCc.haNombre(), HAEstado.CUMPLE, "Se agregó en " + haPaginasTexto(haCc.haPaginas()) + ".")
                : new HAComprobacion(haCc.haNombre(), HAEstado.REVISAR, "No cambió el contenido de "
                    + (haSinCambio.size() == 1 ? "la página " : "las páginas ") + HAVerificador.haResumirLista(haSinCambio) + ".");
    }

    private static HAComprobacion haAnotaciones(HAEsperado.HAAnotaciones haA, HAContexto haC) throws IOException {
        List<Integer> haFaltan = new ArrayList<>();
        int haTotal = 0;
        for (Map.Entry<Integer, Integer> haE : haA.haPorPagina().entrySet()) {
            int haI = haE.getKey();
            if (haI >= haC.haDespues.getNumberOfPages() || haI >= haC.haAntes.getNumberOfPages()) {
                haFaltan.add(haI + 1);
                continue;
            }
            int haAgregadas = haC.haDespues.getPage(haI).getAnnotations().size() - haC.haAntes.getPage(haI).getAnnotations().size();
            if (haAgregadas < haE.getValue()) {
                haFaltan.add(haI + 1);
            }
            haTotal += haE.getValue();
        }
        return haFaltan.isEmpty()
                ? new HAComprobacion("Anotaciones", HAEstado.CUMPLE, haTotal + (haTotal == 1 ? " anotación quedó" : " anotaciones quedaron")
                    + " guardada" + (haTotal == 1 ? "" : "s") + " en el PDF (se pueden ver y editar en otros lectores).")
                : new HAComprobacion("Anotaciones", HAEstado.REVISAR, "Faltan anotaciones en "
                    + (haFaltan.size() == 1 ? "la página " : "las páginas ") + HAVerificador.haResumirLista(haFaltan) + ".");
    }

    private static HAComprobacion haTamano(HAEsperado.HATamano haT, HAContexto haC) {
        List<Integer> haMal = new ArrayList<>();
        for (int haI = 0; haI < haC.haDespues.getNumberOfPages(); haI++) {
            float[] haVisto = haMedidaVista(haC.haDespues.getPage(haI));
            boolean haHorizontal = switch (haT.haOrientacion()) {
                case VERTICAL -> false;
                case HORIZONTAL -> true;
                case MANTENER -> {
                    float[] haAntes = haI < haC.haAntes.getNumberOfPages() ? haMedidaVista(haC.haAntes.getPage(haI)) : haVisto;
                    yield haAntes[0] > haAntes[1];
                }
            };
            float haAncho = haHorizontal ? haT.haPapel().haAlto() : haT.haPapel().haAncho();
            float haAlto = haHorizontal ? haT.haPapel().haAncho() : haT.haPapel().haAlto();
            if (Math.abs(haVisto[0] - haAncho) > 2 || Math.abs(haVisto[1] - haAlto) > 2) {
                haMal.add(haI + 1);
            }
        }
        String haPedido = haT.haPapel() + (haT.haOrientacion() == HACambiarTamano.HAOrientacion.MANTENER ? ""
                : " " + haT.haOrientacion().toString().toLowerCase());
        return haMal.isEmpty()
                ? new HAComprobacion("Tamaño de página", HAEstado.CUMPLE, "Todas las páginas miden " + haPedido + ".")
                : new HAComprobacion("Tamaño de página", HAEstado.REVISAR, "No miden " + haPedido + " "
                    + (haMal.size() == 1 ? "la página " : "las páginas ") + HAVerificador.haResumirLista(haMal) + ".");
    }

    private static HAComprobacion haTextoBuscable(HAContexto haC) throws IOException {
        List<String> haAntes = haC.haTextoAntes();
        List<String> haDespues = haC.haTextoDespues();
        if (haAntes.size() != haDespues.size()) {
            return new HAComprobacion("Texto buscable", HAEstado.REVISAR, "Cambió la cantidad de páginas.");
        }
        int haReconocidas = 0;
        List<Integer> haEnBlanco = new ArrayList<>();
        List<Integer> haFaltan = new ArrayList<>();
        PDFRenderer haDibujante = new PDFRenderer(haC.haDespues);
        for (int haI = 0; haI < haDespues.size(); haI++) {
            if (haAntes.get(haI).length() >= HA_MIN_TEXTO) {
                if (haDespues.get(haI).length() < HA_MIN_TEXTO) {
                    haFaltan.add(haI + 1);
                }
            } else if (haDespues.get(haI).length() >= HA_MIN_TEXTO) {
                haReconocidas++;
            } else if (haPareceEnBlanco(haDibujante, haI)) {
                haEnBlanco.add(haI + 1);
            } else {
                haFaltan.add(haI + 1);
            }
        }
        String haBlancas = haEnBlanco.isEmpty() ? "" : " Están en blanco (sin nada que leer): "
                + HAVerificador.haResumirLista(haEnBlanco) + ".";
        if (!haFaltan.isEmpty()) {
            return new HAComprobacion("Texto buscable", HAEstado.REVISAR, "Siguen sin texto "
                    + (haFaltan.size() == 1 ? "la página " : "las páginas ") + HAVerificador.haResumirLista(haFaltan)
                    + " (pueden ser fotos o estar muy borrosas)." + haBlancas);
        }
        return new HAComprobacion("Texto buscable", HAEstado.CUMPLE, haReconocidas == 0
                ? "Todas las páginas ya tenían texto; no hizo falta reconocer nada." + haBlancas
                : "Se reconoció texto en " + haReconocidas + (haReconocidas == 1 ? " página" : " páginas")
                    + ": ahora se puede buscar y copiar." + haBlancas);
    }

    private HAComprobacion haSinMetadatos(HAEsperado.HASinMetadatos haM, HAContexto haC) {
        HAAnalizadorPrivacidad.HAInventario haInv = haAnalizador.haInventario(haC.haArchivoDespues, haC.haClaveDespues);
        List<String> haQuedan = new ArrayList<>();
        if (haM.haInformacion() && !haInv.haInformacion().isEmpty()) {
            haQuedan.add("autor/programa/fechas (" + String.join(", ", haInv.haInformacion().keySet()) + ")");
        }
        if (haM.haXmp() && haInv.haXmp()) {
            haQuedan.add("XMP");
        }
        if (haM.haComentarios() && haInv.haComentarios() > 0) {
            haQuedan.add(haInv.haComentarios() + " comentario(s)");
        }
        if (haM.haAdjuntos() && !haInv.haAdjuntos().isEmpty()) {
            haQuedan.add(haInv.haAdjuntos().size() + " adjunto(s)");
        }
        if (haM.haJavascript() && haInv.haJavascript()) {
            haQuedan.add("JavaScript");
        }
        if (haInv.haRevisionesAnteriores() > 0) {
            haQuedan.add(haInv.haRevisionesAnteriores() + " versión(es) anterior(es)");
        }
        return haQuedan.isEmpty()
                ? new HAComprobacion("Metadatos", HAEstado.CUMPLE, "Releído el resultado: ya no tiene lo que se pidió quitar.")
                : new HAComprobacion("Metadatos", HAEstado.REVISAR, "Todavía tiene: " + String.join("; ", haQuedan) + ".");
    }

    private static HAComprobacion haContrasena(HAEsperado.HAContrasena haK, HAContexto haC) {
        boolean haCifrado = haC.haDespues.isEncrypted();
        if (haK.haProtegido()) {
            return haCifrado
                    ? new HAComprobacion("Contraseña", HAEstado.CUMPLE, "El resultado está protegido y se abre con la contraseña indicada.")
                    : new HAComprobacion("Contraseña", HAEstado.REVISAR, "El resultado NO quedó protegido.");
        }
        return haCifrado
                ? new HAComprobacion("Contraseña", HAEstado.REVISAR, "El resultado todavía tiene contraseña.")
                : new HAComprobacion("Contraseña", HAEstado.CUMPLE, "El resultado ya no tiene contraseña.");
    }

    private static HAComprobacion haPeso(HAEsperado.HAPeso haP, HAContexto haC) {
        long haAntes = haC.haArchivoAntes.length();
        long haDespues = haC.haArchivoDespues.length();
        String haCambio = HAArchivos.haTamanoLegible(haAntes) + " → " + HAArchivos.haTamanoLegible(haDespues);
        if (haP.haMaximo() != null) {
            return haDespues <= haP.haMaximo()
                    ? new HAComprobacion("Peso", HAEstado.CUMPLE, haCambio + "; se pidió menos de " + HAArchivos.haTamanoLegible(haP.haMaximo()) + ".")
                    : new HAComprobacion("Peso", HAEstado.REVISAR, haCambio + "; NO llega a menos de "
                        + HAArchivos.haTamanoLegible(haP.haMaximo()) + ".");
        }
        return haDespues <= haAntes
                ? new HAComprobacion("Peso", HAEstado.CUMPLE, haCambio + ".")
                : new HAComprobacion("Peso", HAEstado.REVISAR, haCambio + ": quedó más pesado que antes.");
    }

    private static HAComprobacion haFormulario(HAEsperado.HAFormulario haF, HAContexto haC) throws IOException {
        PDAcroForm haAntes = haC.haAntes.getDocumentCatalog().getAcroForm(null);
        PDAcroForm haDespues = haC.haDespues.getDocumentCatalog().getAcroForm(null);
        List<String> haMal = new ArrayList<>();
        if (haF.haAplanado()) {
            boolean haQuedanCampos = false;
            if (haDespues != null && haDespues.getFieldTree().iterator().hasNext()) {
                for (PDPage haP : haC.haDespues.getPages()) {
                    haQuedanCampos |= haTieneWidgets(haP);
                }
            }
            String haTodo = String.join("", haC.haTextoDespues());
            for (Map.Entry<String, String> haE : haF.haValores().entrySet()) {
                PDField haCampo = haAntes == null ? null : haAntes.getField(haE.getKey());
                if (haCampo == null || haCampo.isReadOnly() || haCampo instanceof PDCheckBox || haE.getValue() == null) {
                    continue;
                }
                String haBuscado = haE.getValue().replaceAll("\\s", "");
                if (!haBuscado.isEmpty() && !haTodo.contains(haBuscado)) {
                    haMal.add("«" + haCampo.getPartialName() + "»");
                }
            }
            if (haQuedanCampos) {
                return new HAComprobacion("Formulario", HAEstado.REVISAR, "Quedan campos editables: el formulario no se aplanó.");
            }
            return haMal.isEmpty()
                    ? new HAComprobacion("Formulario", HAEstado.CUMPLE, "Los valores quedaron escritos y fijos (ya no se pueden cambiar).")
                    : new HAComprobacion("Formulario", HAEstado.REVISAR, "No se ve el valor de " + String.join(", ", haMal) + ".");
        }
        if (haDespues == null) {
            return new HAComprobacion("Formulario", HAEstado.REVISAR, "El resultado ya no tiene formulario.");
        }
        for (Map.Entry<String, String> haE : haF.haValores().entrySet()) {
            PDField haCampo = haDespues.getField(haE.getKey());
            if (haCampo == null || haCampo.isReadOnly()) {
                continue;
            }
            String haV = haE.getValue() == null ? "" : haE.getValue();
            boolean haOk;
            if (haCampo instanceof PDCheckBox haCasilla) {
                haOk = haCasilla.isChecked() == Boolean.parseBoolean(haV);
            } else if (haCampo instanceof PDChoice haLista) {
                haOk = haV.isEmpty() ? haLista.getValue().isEmpty() : haLista.getValue().contains(haV);
            } else {
                haOk = Objects.equals(haCampo.getValueAsString().trim(), haV.trim());
            }
            if (!haOk) {
                haMal.add("«" + haCampo.getPartialName() + "»");
            }
        }
        return haMal.isEmpty()
                ? new HAComprobacion("Formulario", HAEstado.CUMPLE, "Releído el resultado: los " + haF.haValores().size()
                    + " campos tienen el valor escrito.")
                : new HAComprobacion("Formulario", HAEstado.REVISAR, "No tienen el valor escrito: " + String.join(", ", haMal) + ".");
    }

    private static boolean haTieneWidgets(PDPage haP) {
        try {
            return haP.getAnnotations().stream().anyMatch(haA -> "Widget".equals(haA.getSubtype()));
        } catch (IOException haE) {
            return false;
        }
    }

    static byte[] haHuella(PDPage haPagina) throws IOException {
        try {
            MessageDigest haMd = MessageDigest.getInstance("SHA-256");
            if (haPagina.hasContents()) {
                try (InputStream haIn = haPagina.getContents()) {
                    byte[] haBuffer = new byte[8192];
                    int haN;
                    while ((haN = haIn.read(haBuffer)) > 0) {
                        haMd.update(haBuffer, 0, haN);
                    }
                }
            }
            return haMd.digest();
        } catch (NoSuchAlgorithmException haE) {
            throw new IllegalStateException(haE);
        }
    }

    private static int haGiro(PDPage haP) {
        return haNormalizar(haP.getRotation());
    }

    private static int haNormalizar(int haGrados) {
        return ((haGrados % 360) + 360) % 360;
    }

    private static float[] haMedidaVista(PDPage haP) {
        PDRectangle haCaja = haP.getCropBox();
        int haG = haGiro(haP);
        boolean haGirada = haG == 90 || haG == 270;
        return haGirada ? new float[]{haCaja.getHeight(), haCaja.getWidth()} : new float[]{haCaja.getWidth(), haCaja.getHeight()};
    }

    private static boolean haPareceEnBlanco(PDFRenderer haDibujante, int haPagina) {
        try {
            BufferedImage haImg = haDibujante.renderImageWithDPI(haPagina, 20, ImageType.GRAY);
            int haOscuros = 0;
            int haTotal = haImg.getWidth() * haImg.getHeight();
            for (int haY = 0; haY < haImg.getHeight(); haY++) {
                for (int haX = 0; haX < haImg.getWidth(); haX++) {
                    if ((haImg.getRGB(haX, haY) & 0xFF) < 200) {
                        haOscuros++;
                    }
                }
            }
            return haOscuros <= haTotal * 0.003;
        } catch (IOException | RuntimeException haE) {
            return false;
        }
    }

    private static List<Integer> haPaginas(List<Integer> haPedidas, int haTotal) {
        if (haPedidas == null) {
            List<Integer> haTodas = new ArrayList<>();
            for (int haI = 0; haI < haTotal; haI++) {
                haTodas.add(haI);
            }
            return haTodas;
        }
        return haPedidas.stream().filter(haI -> haI >= 0 && haI < haTotal).toList();
    }

    private static String haPaginasTexto(List<Integer> haPaginas) {
        if (haPaginas == null) {
            return "todas las páginas";
        }
        return (haPaginas.size() == 1 ? "la página " : "las páginas ") + HAVerificador.haResumirLista(haSumarUno(haPaginas));
    }

    private static List<Integer> haSumarUno(List<Integer> haPaginas) {
        return haPaginas.stream().distinct().sorted().map(haI -> haI + 1).toList();
    }
}
