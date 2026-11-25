package pe.pdflisto.verificacion;

public record HAComprobacion(String haNombre, HAEstado haEstado, String haDetalle) {
    public enum HAEstado {
        CUMPLE("Cumple"),
        REVISAR("Revisar"),
        INFO("Info"),
        NO_APLICA("No aplica");

        private final String haTexto;

        HAEstado(String haTexto) {
            this.haTexto = haTexto;
        }

        @Override
        public String toString() {
            return haTexto;
        }
    }
}
