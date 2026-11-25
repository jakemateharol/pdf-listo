package pe.pdflisto.entrega;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.springframework.stereotype.Service;
import pe.pdflisto.exception.HAPdfListoException;
import pe.pdflisto.operaciones.HAArchivos;
import pe.pdflisto.pdf.HACargaPdf;
import pe.pdflisto.pdf.HALectorTexto;
import pe.pdflisto.privacidad.HAAnalizadorPrivacidad;
import pe.pdflisto.privacidad.HADatoSensible;
import pe.pdflisto.privacidad.HADetectorDatos;
import pe.pdflisto.privacidad.HATipoDato;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

@Service
public class HARevisionEnvio {
    public static final long HA_PESO_MAXIMO = 10L * 1024 * 1024;
    static final Set<HATipoDato> HA_TIPOS_ROJOS = EnumSet.of(HATipoDato.DNI, HATipoDato.RUC, HATipoDato.CELULAR,
            HATipoDato.CORREO, HATipoDato.CUENTA, HATipoDato.CCI);

    public enum HANivel { ROJO, AMARILLO, VERDE }

    public record HAPunto(HANivel haNivel, String haTitulo, String haDetalle) {
    }

    public enum HAPasoArreglo { TACHAR, LIMPIAR_METADATOS, COMPRIMIR }

    public record HARevision(List<HAPunto> haPuntos, List<HADatoSensible> haDatos, List<String> haMetadatos, long haBytes,
                             List<HAPasoArreglo> haPlan) {
        public HANivel haNivel() {
            return haPuntos.stream().map(HAPunto::haNivel).min(Enum::compareTo).orElse(HANivel.VERDE);
        }

        public List<String> haDescripcionPlan() {
            List<String> haR = new ArrayList<>();
            for (HAPasoArreglo haP : haPlan) {
                haR.add(switch (haP) {
                    case TACHAR -> "Tachar " + haDatos.size() + (haDatos.size() == 1 ? " dato sensible" : " datos sensibles")
                            + " (se borran del archivo y se verifica que no queden; también limpia los metadatos)";
                    case LIMPIAR_METADATOS -> "Limpiar los metadatos (" + String.join(", ", haMetadatos) + ")";
                    case COMPRIMIR -> "Comprimir para que pese menos de " + HAArchivos.haTamanoLegible(HA_PESO_MAXIMO)
                            + " (ahora " + HAArchivos.haTamanoLegible(haBytes) + ")";
                });
            }
            return haR;
        }
    }

    private final HADetectorDatos haDetector;
    private final HAAnalizadorPrivacidad haAnalizador;

    public HARevisionEnvio(HADetectorDatos haDetector, HAAnalizadorPrivacidad haAnalizador) {
        this.haDetector = haDetector;
        this.haAnalizador = haAnalizador;
    }

    public HARevision haRevisar(File haArchivo, String haContrasena) {
        List<HAPunto> haRojos = new ArrayList<>();
        List<HAPunto> haAmarillos = new ArrayList<>();
        List<HAPunto> haVerdes = new ArrayList<>();

        List<HALectorTexto.HAPaginaTexto> haPaginas;
        int haTotalPaginas;
        try (PDDocument haPdf = HACargaPdf.haAbrir(haArchivo, haContrasena)) {
            haTotalPaginas = haPdf.getNumberOfPages();
            haPaginas = HALectorTexto.haLeer(haPdf);
        } catch (IOException haE) {
            throw new HAPdfListoException("No se pudo abrir el PDF para revisarlo: " + haE.getMessage(), haE);
        }
        haVerdes.add(new HAPunto(HANivel.VERDE, "Abre correctamente", haTotalPaginas + (haTotalPaginas == 1 ? " página." : " páginas.")));

        List<HADatoSensible> haDatos = haDetector.haDetectar(haPaginas, haDetector.haReglasActivas()).stream()
                .filter(haD -> haD.haProbable() && HA_TIPOS_ROJOS.contains(haD.haTipo())).toList();
        if (haDatos.isEmpty()) {
            haVerdes.add(new HAPunto(HANivel.VERDE, "Sin datos sensibles", "No se encontraron DNI, RUC, celulares, correos, "
                    + "cuentas ni CCI en el texto."));
        } else {
            Map<HATipoDato, Set<Integer>> haPorTipo = new LinkedHashMap<>();
            haDatos.forEach(haD -> haPorTipo.computeIfAbsent(haD.haTipo(), haK -> new TreeSet<>()).add(haD.haPagina() + 1));
            List<String> haPartes = new ArrayList<>();
            haPorTipo.forEach((haT, haP) -> haPartes.add(haT + " (" + (haP.size() == 1 ? "pág. " : "págs. ")
                    + String.join(", ", haP.stream().map(String::valueOf).toList()) + ")"));
            haRojos.add(new HAPunto(HANivel.ROJO, haDatos.size() + (haDatos.size() == 1 ? " dato sensible" : " datos sensibles"),
                    String.join(" · ", haPartes)));
        }

        long haBytes = haArchivo.length();
        if (haBytes > HA_PESO_MAXIMO) {
            haAmarillos.add(new HAPunto(HANivel.AMARILLO, "Pesa " + HAArchivos.haTamanoLegible(haBytes),
                    "Más de " + HAArchivos.haTamanoLegible(HA_PESO_MAXIMO) + ": muchos correos y trámites no lo aceptan."));
        } else {
            haVerdes.add(new HAPunto(HANivel.VERDE, "Peso adecuado", HAArchivos.haTamanoLegible(haBytes) + "."));
        }

        HAAnalizadorPrivacidad.HAInventario haInv = haAnalizador.haInventario(haArchivo, haContrasena);
        List<String> haMetadatos = new ArrayList<>(haInv.haInformacion().keySet());
        if (haInv.haXmp()) {
            haMetadatos.add("XMP");
        }
        if (haMetadatos.isEmpty()) {
            haVerdes.add(new HAPunto(HANivel.VERDE, "Sin metadatos", "No dice quién lo hizo ni con qué programa."));
        } else {
            haAmarillos.add(new HAPunto(HANivel.AMARILLO, "Tiene metadatos", String.join(", ", haMetadatos)
                    + " (por ejemplo, quién lo hizo y con qué programa)."));
        }

        long haSinTexto = haPaginas.stream().filter(haP -> haP.haTexto().replaceAll("\\s", "").length() < 20).count();
        if (haSinTexto > 0) {
            haAmarillos.add(new HAPunto(HANivel.AMARILLO, haSinTexto + (haSinTexto == 1 ? " página sin" : " páginas sin")
                    + " texto buscable", "Parece escaneado: usa Herramientas → Reconocer texto (OCR). "
                    + "Además, ahí no se pueden detectar datos sensibles."));
        } else {
            haVerdes.add(new HAPunto(HANivel.VERDE, "Texto buscable", "Todas las páginas tienen texto que se puede buscar y copiar."));
        }

        if (haContrasena != null) {
            haAmarillos.add(new HAPunto(HANivel.AMARILLO, "Tiene contraseña", "Quien lo reciba la necesitará para abrirlo."));
        } else {
            haVerdes.add(new HAPunto(HANivel.VERDE, "Sin contraseña", "Se abre sin pedir contraseña."));
        }

        List<HAPasoArreglo> haPlan = new ArrayList<>();
        if (!haDatos.isEmpty()) {
            haPlan.add(HAPasoArreglo.TACHAR);
        } else if (!haMetadatos.isEmpty()) {
            haPlan.add(HAPasoArreglo.LIMPIAR_METADATOS);
        }
        if (haBytes > HA_PESO_MAXIMO) {
            haPlan.add(HAPasoArreglo.COMPRIMIR);
        }
        List<HAPunto> haPuntos = new ArrayList<>(haRojos);
        haPuntos.addAll(haAmarillos);
        haPuntos.addAll(haVerdes);
        return new HARevision(haPuntos, haDatos, haMetadatos, haBytes, haPlan);
    }
}
