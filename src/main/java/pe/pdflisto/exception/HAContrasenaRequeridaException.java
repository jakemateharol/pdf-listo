package pe.pdflisto.exception;

public class HAContrasenaRequeridaException extends HAPdfListoException {
    private final boolean haIncorrecta;

    public HAContrasenaRequeridaException(boolean haIncorrecta) {
        super(haIncorrecta ? "La contraseña no es correcta." : "Este PDF está protegido con contraseña.");
        this.haIncorrecta = haIncorrecta;
    }

    public boolean isIncorrecta() {
        return haIncorrecta;
    }
}
