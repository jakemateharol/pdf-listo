package pe.pdflisto.controller;

import pe.pdflisto.entrega.HARevisionEnvio;
import pe.pdflisto.operaciones.HACompresor;
import pe.pdflisto.operaciones.HAMotorPdf;
import pe.pdflisto.operaciones.HAProcesoVersion;

import java.util.List;
import java.util.Map;

public final class HAPanelListoParaEnviarPruebas {
    private HAPanelListoParaEnviarPruebas() {
    }

    public static List<Map.Entry<String, HAProcesoVersion>> haPasos(HAMotorPdf haMotor, HACompresor haCompresor,
                                                                   HARevisionEnvio.HARevision haR) {
        return new HAPanelListoParaEnviar(null, null, haMotor, haCompresor, null).haPasos(haR);
    }
}
