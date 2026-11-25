package pe.pdflisto.exception;

public class HAPdfListoException extends RuntimeException {
    public HAPdfListoException(String haMensaje) {
        super(haMensaje);
    }

    public HAPdfListoException(String haMensaje, Throwable haCausa) {
        super(haMensaje, haCausa);
    }
}
