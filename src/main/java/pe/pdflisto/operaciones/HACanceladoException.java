package pe.pdflisto.operaciones;

import pe.pdflisto.exception.HAPdfListoException;

public class HACanceladoException extends HAPdfListoException {
    public HACanceladoException() {
        super("Operación cancelada. El documento no cambió.");
    }
}
