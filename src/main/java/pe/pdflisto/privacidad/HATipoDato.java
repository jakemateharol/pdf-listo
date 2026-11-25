package pe.pdflisto.privacidad;

public enum HATipoDato {
    DNI("DNI", true),
    RUC("RUC", true),
    CELULAR("Celular", true),
    CORREO("Correo", false),
    CUENTA("Cuenta bancaria", true),
    CCI("CCI", true),
    DIRECCION("Dirección", false),
    PERSONALIZADO("Personalizado", false),
    NOMBRE("Nombre de persona (sugerencia)", false);

    private final String haEtiqueta;
    private final boolean haNumerico;

    HATipoDato(String haEtiqueta, boolean haNumerico) {
        this.haEtiqueta = haEtiqueta;
        this.haNumerico = haNumerico;
    }

    public boolean haNumerico() {
        return haNumerico;
    }

    @Override
    public String toString() {
        return haEtiqueta;
    }
}
