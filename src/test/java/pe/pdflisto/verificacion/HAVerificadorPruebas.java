package pe.pdflisto.verificacion;

public final class HAVerificadorPruebas {
    private HAVerificadorPruebas() {
    }

    public static HAComprobacion haPeso(long haBytes, HARequisitos haReq) {
        return HAVerificador.haPeso(haBytes, haReq);
    }
}
