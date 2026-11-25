package pe.pdflisto.operaciones;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

@Service
@RequiredArgsConstructor
public class HACompresor {
    private final HAMotorPdf haMotor;

    public HAResultado haComprimir(File haEntrada, String haContrasena, HANivelCompresion haNivel, File haSalida) {
        return haComprimir(haEntrada, haContrasena, haNivel, haSalida, HAControl.haNinguno());
    }

    public HAResultado haComprimir(File haEntrada, String haContrasena, HANivelCompresion haNivel, File haSalida, HAControl haControl) {
        long haAntes = haEntrada.length();
        HAResultado haR = haMotor.haEjecutar(haEntrada, haContrasena, new HAComprimir(haNivel), haSalida, haControl);
        return haInformar(haR, haAntes, haNivel, -1);
    }

    public HAResultado haHastaObjetivo(File haEntrada, String haContrasena, long haObjetivoBytes, File haSalida) {
        return haHastaObjetivo(haEntrada, haContrasena, haObjetivoBytes, haSalida, HAControl.haNinguno());
    }

    public HAResultado haHastaObjetivo(File haEntrada, String haContrasena, long haObjetivoBytes, File haSalida, HAControl haControl) {
        long haAntes = haEntrada.length();
        if (haAntes <= haObjetivoBytes) {
            return HAResultado.haDe(haEntrada, 0).haConDetalle(true, true, "El PDF ya pesa " + HAArchivos.haTamanoLegible(haAntes)
                    + ", que es menos de " + HAArchivos.haTamanoLegible(haObjetivoBytes) + ". No hace falta comprimir.");
        }
        File haMejor = null;
        HANivelCompresion haMejorNivel = null;
        HAResultado haMejorResultado = null;
        try {
            HANivelCompresion[] haNiveles = HANivelCompresion.values();
            for (int haK = 0; haK < haNiveles.length; haK++) {
                HANivelCompresion haNivel = haNiveles[haK];
                File haIntento = new File(haSalida.getParentFile(), "intento-" + haNivel.name() + "-" + haSalida.getName());
                HAResultado haR = haMotor.haEjecutar(haEntrada, haContrasena, new HAComprimir(haNivel), haIntento, haControl.haTramo(haK, haNiveles.length));
                if (haMejorResultado == null || haR.haBytes() < haMejorResultado.haBytes()) {
                    haBorrar(haMejor);
                    haMejor = haIntento;
                    haMejorNivel = haNivel;
                    haMejorResultado = haR;
                } else {
                    haBorrar(haIntento);
                }
                if (haMejorResultado.haBytes() <= haObjetivoBytes) {
                    break;
                }
            }
            Files.move(haMejor.toPath(), haSalida.toPath(), StandardCopyOption.REPLACE_EXISTING);
            haMejor = null;
        } catch (IOException haE) {
            throw new pe.pdflisto.exception.HAPdfListoException("No se pudo completar la compresión: " + haE.getMessage(), haE);
        } finally {
            haBorrar(haMejor);
        }
        HAResultado haFinal_ = new HAResultado(haSalida, haMejorResultado.haPaginas(), haSalida.length(), false, true, null, null)
                .haConEsperado(java.util.List.of(new pe.pdflisto.verificacion.HAEsperado.HAMismasPaginas(),
                        new pe.pdflisto.verificacion.HAEsperado.HAPeso(haObjetivoBytes)));
        return haInformar(haFinal_, haAntes, haMejorNivel, haObjetivoBytes);
    }

    private static HAResultado haInformar(HAResultado haR, long haAntes, HANivelCompresion haNivel, long haObjetivo) {
        long haDespues = haR.haBytes();
        String haDe = HAArchivos.haTamanoLegible(haAntes) + " → " + HAArchivos.haTamanoLegible(haDespues);
        if (haDespues >= haAntes * 0.99) {
            String haExtra = haObjetivo > 0 ? " No es posible llegar a " + HAArchivos.haTamanoLegible(haObjetivo)
                    + " sin perder el texto; prueba dividiendo el PDF." : "";
            return haR.haConDetalle(true, false, "Este PDF ya estaba optimizado: no se pudo reducir más (" + haDe + ")." + haExtra);
        }
        long haAhorro = Math.round(100.0 * (haAntes - haDespues) / haAntes);
        if (haObjetivo > 0 && haDespues > haObjetivo) {
            return haR.haConDetalle(false, false, "No se logró bajar de " + HAArchivos.haTamanoLegible(haObjetivo)
                    + ": lo mínimo fue " + haDe + " (−" + haAhorro + " %, nivel " + haNivel.toString().toLowerCase()
                    + "). Se aplicó igual; puedes deshacerlo con Ctrl+Z.");
        }
        return haR.haConDetalle(false, true, "Compresión " + haNivel.toString().toLowerCase() + ": " + haDe
                + " (−" + haAhorro + " %)." + (haObjetivo > 0 ? " Cumple: pesa menos de " + HAArchivos.haTamanoLegible(haObjetivo) + "." : ""));
    }

    private static void haBorrar(File haF) {
        if (haF != null) {
            try {
                Files.deleteIfExists(haF.toPath());
            } catch (IOException haIgnorado) {
            }
        }
    }

}
