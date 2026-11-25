package pe.pdflisto.interprete;

import lombok.RequiredArgsConstructor;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.springframework.stereotype.Service;
import pe.pdflisto.exception.HAPdfListoException;
import pe.pdflisto.operaciones.HACompresor;
import pe.pdflisto.operaciones.HAControl;
import pe.pdflisto.operaciones.HAMarcaDeAgua;
import pe.pdflisto.operaciones.HAMotorPdf;
import pe.pdflisto.operaciones.HANumerar;
import pe.pdflisto.operaciones.HAOperacionPdf;
import pe.pdflisto.operaciones.HAOrdenPaginas;
import pe.pdflisto.operaciones.HARecursos;
import pe.pdflisto.operaciones.HAReorganizar;
import pe.pdflisto.operaciones.HAResultado;
import pe.pdflisto.operaciones.HARotar;
import pe.pdflisto.operaciones.HAUnir;
import pe.pdflisto.verificacion.HARequisitos;

import java.awt.Color;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntFunction;

@Service
@RequiredArgsConstructor
public class HAEjecutorPlan {
    private final HAMotorPdf haMotor;
    private final HACompresor haCompresor;

    public HAResultado haEjecutar(List<HAPaso> haPasos, File haEntrada, String haContrasena, File haSalida, List<HAUnir.HAFuente> haOtros) {
        return haEjecutar(haPasos, haEntrada, haContrasena, haSalida, haOtros, HAControl.haNinguno());
    }

    public HAResultado haEjecutar(List<HAPaso> haPasos, File haEntrada, String haContrasena, File haSalida, List<HAUnir.HAFuente> haOtros,
                              HAControl haControl) {
        return haEjecutar(haPasos, haEntrada, haContrasena, haSalida, haOtros, haControl, null);
    }

    public HAResultado haEjecutar(List<HAPaso> haPasos, File haEntrada, String haContrasena, File haSalida, List<HAUnir.HAFuente> haOtros,
                              HAControl haControl, List<pe.pdflisto.verificacion.HAPesoPaso> haPesos) {
        if (haPasos.isEmpty()) {
            throw new HAPdfListoException("El plan no tiene pasos.");
        }
        File haActual = haEntrada;
        String haClave = haContrasena;
        List<File> haIntermedios = new ArrayList<>();
        List<String> haNotas = new ArrayList<>();
        boolean haHuboCambios = false;
        try {
            for (int haI = 0; haI < haPasos.size(); haI++) {
                HAPaso haPaso = haPasos.get(haI);
                File haDestino = new File(haSalida.getParentFile(), "paso" + (haI + 1) + "-" + haSalida.getName());
                haIntermedios.add(haDestino);
                HAResultado haR;
                try {
                    haControl.haAvance(haI, haPasos.size(), "Paso " + (haI + 1) + " de " + haPasos.size() + ": " + haPaso.haDescripcion());
                    haR = haEjecutarPaso(haPaso, haActual, haClave, haDestino, haOtros, haControl.haTramo(haI, haPasos.size()));
                } catch (pe.pdflisto.operaciones.HACanceladoException haE) {
                    throw haE;
                } catch (HAPdfListoException haE) {
                    throw new HAPdfListoException("Paso " + (haI + 1) + " («" + haPaso.haDescripcion() + "»): " + haE.getMessage(), haE);
                }
                if (haR.haDetalle() != null) {
                    haNotas.add(haR.haDetalle());
                }
                if (haPesos != null) {
                    long haAntes = haActual.length();
                    haPesos.add(new pe.pdflisto.verificacion.HAPesoPaso(haPaso.haDescripcion(), haAntes,
                            haR.haSinCambios() ? haAntes : haR.haArchivo().length(), haPaso instanceof HAPaso.HAComprimir));
                }
                if (!haR.haSinCambios()) {
                    haActual = haR.haArchivo();
                    haClave = haR.haContrasena();
                    haHuboCambios = true;
                }
            }
            if (!haHuboCambios) {
                return HAResultado.haDe(haEntrada, 0).haConDetalle(true, false,
                        haNotas.isEmpty() ? "El plan no produjo cambios." : String.join(" ", haNotas));
            }
            Files.move(haActual.toPath(), haSalida.toPath(), StandardCopyOption.REPLACE_EXISTING);
            int haPaginas;
            try (PDDocument haD = Loader.loadPDF(haSalida, haClave)) {
                haPaginas = haD.getNumberOfPages();
            }
            return new HAResultado(haSalida, haPaginas, haSalida.length(), false, true, haNotas.isEmpty() ? null : String.join(" ", haNotas), haClave);
        } catch (IOException haE) {
            throw new HAPdfListoException("No se pudo completar el plan: " + haE.getMessage(), haE);
        } finally {
            for (File haF : haIntermedios) {
                if (!haF.equals(haSalida)) {
                    try {
                        Files.deleteIfExists(haF.toPath());
                    } catch (IOException haIgnorado) {
                    }
                }
            }
        }
    }

    private HAResultado haEjecutarPaso(HAPaso haPaso, File haEntrada, String haClave, File haDestino, List<HAUnir.HAFuente> haOtros,
                                   HAControl haControl) {
        if (haPaso instanceof HAPaso.HAComprimir haC) {
            return haC.haObjetivoBytes() != null
                    ? haCompresor.haHastaObjetivo(haEntrada, haClave, haC.haObjetivoBytes(), haDestino, haControl)
                    : haCompresor.haComprimir(haEntrada, haClave, haC.haNivel(), haDestino, haControl);
        }
        return haMotor.haEjecutar(haEntrada, haClave, haOperacion(haPaso, haOtros), haDestino, haControl);
    }

    private static HAOperacionPdf haOperacion(HAPaso haPaso, List<HAUnir.HAFuente> haOtros) {
        if (haPaso instanceof HAPaso.HAUnir) {
            if (haOtros.isEmpty()) {
                throw new HAPdfListoException("No hay otros PDF abiertos para unir.");
            }
            List<HAUnir.HAFuente> haFuentes = new ArrayList<>();
            haFuentes.add(new HAUnir.HAFuente("actual", null, null, true));
            haFuentes.addAll(haOtros);
            return new HAUnir(haFuentes);
        }
        if (haPaso instanceof HAPaso.HARotar haR) {
            return haSegunPaginas(haR.haDescripcion(), haTotal -> new HARotar(HAPaginasPedido.haResolver(haR.haPaginas(), haTotal), haR.haGrados()));
        }
        if (haPaso instanceof HAPaso.HAEliminar haE) {
            return haSegunPaginas(haE.haDescripcion(), haTotal -> new HAReorganizar(
                    HAOrdenPaginas.haEliminar(haTotal, HAPaginasPedido.haResolver(haE.haPaginas(), haTotal)), haE.haDescripcion()));
        }
        if (haPaso instanceof HAPaso.HAMarcaAgua haM) {
            return new HAMarcaDeAgua(haM.haTexto(), null, new Color(110, 110, 110), 0.25f, haM.haDiagonal(), null);
        }
        if (haPaso instanceof HAPaso.HANumerar haN) {
            return new HANumerar(haN.haFormato(), haN.haPosicion(), haN.haDesde(), 1, 11f);
        }
        if (haPaso instanceof HAPaso.HATamano haT) {
            return new pe.pdflisto.operaciones.HACambiarTamano(haT.haPapel(), haT.haOrientacion());
        }
        if (haPaso instanceof HAPaso.HAQuitarClave) {
            return new pe.pdflisto.privacidad.HAQuitarContrasena();
        }
        if (haPaso instanceof HAPaso.HAOcr) {
            return new pe.pdflisto.ocr.HAReconocerTexto(pe.pdflisto.ocr.HAReconocerTexto.HAOpciones.haBasicas());
        }
        if (haPaso instanceof HAPaso.HALimpiarMetadatos) {
            return new pe.pdflisto.privacidad.HALimpiarMetadatos(pe.pdflisto.privacidad.HALimpiarMetadatos.HAOpciones.haTodo());
        }
        throw new IllegalArgumentException("Paso desconocido: " + haPaso);
    }

    private static HAOperacionPdf haSegunPaginas(String haDescripcion, IntFunction<HAOperacionPdf> haCrear) {
        return new HAOperacionPdf() {
            @Override
            public String haDescripcion() {
                return haDescripcion;
            }

            @Override
            public PDDocument haAplicar(PDDocument haDocumento, HARecursos haRecursos) throws IOException {
                return haCrear.apply(haDocumento.getNumberOfPages()).haAplicar(haDocumento, haRecursos);
            }
        };
    }

    public HARequisitos haRequisitos(List<HAPaso> haPasos, int haPaginas, int haPaginasOtros, long haPesoOriginal) {
        Integer haTotal = haPaginas;
        Long haPesoMaximo = null;
        boolean haComprimir = false;
        HARequisitos.HANumeracion haNumeracion = null;
        String haMarca = null;
        boolean haSinMetadatos = false;
        boolean haBuscable = false;
        for (HAPaso haP : haPasos) {
            if (haP instanceof HAPaso.HAUnir) {
                haTotal = haTotal == null ? null : haTotal + haPaginasOtros;
            } else if (haP instanceof HAPaso.HAEliminar haE && haTotal != null) {
                try {
                    haTotal = haTotal - HAPaginasPedido.haResolver(haE.haPaginas(), haTotal).size();
                } catch (HAPdfListoException haEx) {
                    haTotal = null;
                }
            } else if (haP instanceof HAPaso.HANumerar haN) {
                haNumeracion = new HARequisitos.HANumeracion(haN.haFormato(), haN.haDesde(), 1, haN.haPosicion().haArriba());
            } else if (haP instanceof HAPaso.HAMarcaAgua haM) {
                haMarca = haM.haTexto();
            } else if (haP instanceof HAPaso.HAComprimir haC) {
                haPesoMaximo = haC.haObjetivoBytes();
                haComprimir = true;
            } else if (haP instanceof HAPaso.HALimpiarMetadatos) {
                haSinMetadatos = true;
            } else if (haP instanceof HAPaso.HAOcr) {
                haBuscable = true;
            }
        }
        return new HARequisitos(haPesoMaximo, haTotal, haNumeracion, haMarca, haPesoOriginal, haSinMetadatos, haBuscable, haComprimir, List.of());
    }
}
