package pe.pdflisto.entrega;

import lombok.RequiredArgsConstructor;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.springframework.stereotype.Service;
import pe.pdflisto.interprete.HAPaso;
import pe.pdflisto.model.HAPerfilEntrega;
import pe.pdflisto.operaciones.HACambiarTamano;
import pe.pdflisto.operaciones.HANivelCompresion;
import pe.pdflisto.pdf.HACargaPdf;
import pe.pdflisto.pdf.HATamanoPagina;
import pe.pdflisto.verificacion.HAComprobacion;
import pe.pdflisto.verificacion.HAComprobacion.HAEstado;
import pe.pdflisto.verificacion.HAInformeVerificacion;
import pe.pdflisto.verificacion.HARequisitos;
import pe.pdflisto.verificacion.HAVerificador;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class HAVerificadorPerfil {
    private final HAVerificador haVerificador;

    public record HACorreccion(List<HAPaso> haPasos, List<String> haAvisos) {
    }

    public HAInformeVerificacion haValidar(File haArchivo, String haClave, HAPerfilEntrega haP, String haNombre) {
        HAInformeVerificacion haBase = haVerificador.haVerificar(haArchivo, haClave, haRequisitos(haP, haArchivo.length()), haNombre,
                "Perfil «" + haP.getNombre() + "»", List.of());
        List<HAComprobacion> haLista = new ArrayList<>(haBase.haComprobaciones());
        if (haLista.stream().anyMatch(haC -> haC.haNombre().equals("Integridad") && haC.haEstado() == HAEstado.REVISAR)) {
            return haBase;
        }
        try (PDDocument haPdf = HACargaPdf.haAbrir(haArchivo, haClave)) {
            if (haP.getTamano() != null) {
                List<Integer> haDistintas = new ArrayList<>();
                for (int haI = 0; haI < haPdf.getNumberOfPages(); haI++) {
                    HATamanoPagina haT = HATamanoPagina.haDe(haPdf.getPage(haI));
                    if (!haP.getTamano().haCoincide(haT.haAncho(), haT.haAlto())) {
                        haDistintas.add(haI + 1);
                    }
                }
                haPoner(haLista, haDistintas.isEmpty()
                        ? new HAComprobacion("Tamaño de página", HAEstado.CUMPLE, "Todas las páginas son " + haP.getTamano() + ".")
                        : new HAComprobacion("Tamaño de página", HAEstado.REVISAR, "No son " + haP.getTamano() + " las páginas "
                            + haResumir(haDistintas) + "."));
            }
            if (haP.isSinContrasena()) {
                haPoner(haLista, haPdf.isEncrypted()
                        ? new HAComprobacion("Contraseña", HAEstado.REVISAR, "Tiene contraseña: el perfil pide que se pueda abrir sin ella.")
                        : new HAComprobacion("Contraseña", HAEstado.CUMPLE, "Se abre sin contraseña."));
            }
            if (haP.getMaxPaginas() != null) {
                int haN = haPdf.getNumberOfPages();
                haPoner(haLista, haN <= haP.getMaxPaginas()
                        ? new HAComprobacion("Máximo de páginas", HAEstado.CUMPLE, haN + " páginas (máximo " + haP.getMaxPaginas() + ").")
                        : new HAComprobacion("Máximo de páginas", HAEstado.REVISAR, haN + " páginas: sobran " + (haN - haP.getMaxPaginas())
                            + " (máximo " + haP.getMaxPaginas() + ")."));
            }
        } catch (IOException haE) {
            haPoner(haLista, new HAComprobacion("Integridad", HAEstado.REVISAR, "No se pudo revisar el archivo: " + haE.getMessage()));
        }
        long haRevisar = haLista.stream().filter(haC -> haC.haEstado() == HAEstado.REVISAR).count();
        haPoner(haLista, haRevisar == 0
                ? new HAComprobacion("Perfil de entrega", HAEstado.CUMPLE, "Cumple todo lo que pide «" + haP.getNombre() + "».")
                : new HAComprobacion("Perfil de entrega", HAEstado.REVISAR, "No cumple " + haRevisar + (haRevisar == 1 ? " requisito" : " requisitos")
                    + " de «" + haP.getNombre() + "». Pulsa «Corregir» para ver el plan de arreglos."));
        return new HAInformeVerificacion(haBase.haDocumento(), haBase.haFecha(), haBase.haPedido(), haBase.haPasos(), haLista);
    }

    public HARequisitos haRequisitos(HAPerfilEntrega haP, long haPesoActual) {
        HARequisitos.HANumeracion haN = haP.isNumerar()
                ? new HARequisitos.HANumeracion(haP.getFormatoNumero(), Math.max(1, haP.getDesdeNumero()), 1, haP.getPosicionNumero().haArriba())
                : null;
        return new HARequisitos(haP.haPesoMaximoBytes(), null, haN, null, haPesoActual, haP.isSinMetadatos(), haP.isTextoBuscable());
    }

    public HACorreccion haCorrecciones(HAInformeVerificacion haInforme, HAPerfilEntrega haP) {
        List<HAPaso> haPasos = new ArrayList<>();
        List<String> haAvisos = new ArrayList<>();
        for (HAComprobacion haC : haInforme.haComprobaciones()) {
            if (haC.haEstado() != HAEstado.REVISAR) {
                continue;
            }
            switch (haC.haNombre()) {
                case "Tamaño de página" -> haPasos.add(new HAPaso.HATamano(haP.getTamano(), HACambiarTamano.HAOrientacion.MANTENER));
                case "Texto buscable" -> haPasos.add(new HAPaso.HAOcr());
                case "Numeración" -> {
                    haPasos.add(new HAPaso.HANumerar(haP.getFormatoNumero(), haP.getPosicionNumero(), Math.max(1, haP.getDesdeNumero())));
                    haAvisos.add("Si el documento ya tenía otra numeración, quedarán dos: quita ese paso si no lo quieres.");
                }
                case "Metadatos" -> haPasos.add(new HAPaso.HALimpiarMetadatos());
                case "Contraseña" -> haPasos.add(new HAPaso.HAQuitarClave());
                case "Máximo de páginas" -> haAvisos.add("Hay más páginas de las permitidas: elimina las que sobren a mano (no se puede adivinar cuáles).");
                case "Integridad" -> haAvisos.add("El archivo está dañado: no se puede corregir automáticamente.");
                default -> { }
            }
        }
        if (haP.haPesoMaximoBytes() != null && (!haPasos.isEmpty()
                || haInforme.haComprobaciones().stream().anyMatch(haC -> haC.haNombre().equals("Peso") && haC.haEstado() == HAEstado.REVISAR))) {
            haPasos.add(new HAPaso.HAComprimir(HANivelCompresion.MEDIA, haP.haPesoMaximoBytes()));
        }
        haPasos.sort(Comparator.comparingInt(haX -> haX.haAccion().ordinal()));
        return new HACorreccion(haPasos, haAvisos);
    }

    private static void haPoner(List<HAComprobacion> haLista, HAComprobacion haNueva) {
        for (int haI = 0; haI < haLista.size(); haI++) {
            if (haLista.get(haI).haNombre().equals(haNueva.haNombre())) {
                haLista.set(haI, haNueva);
                return;
            }
        }
        haLista.add(haNueva);
    }

    private static String haResumir(List<Integer> haN) {
        return haN.size() <= 8 ? haN.toString().replaceAll("[\\[\\]]", "")
                : haN.subList(0, 8).toString().replaceAll("[\\[\\]]", "") + "… (" + haN.size() + " en total)";
    }
}
