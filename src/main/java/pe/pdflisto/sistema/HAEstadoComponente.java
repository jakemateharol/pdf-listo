package pe.pdflisto.sistema;

public record HAEstadoComponente(String haNombre, HANivel haNivel, String haDetalle) {
    public enum HANivel {
        OK, AVISO, FALTA
    }

    static HAEstadoComponente haOk(String haNombre, String haDetalle) {
        return new HAEstadoComponente(haNombre, HANivel.OK, haDetalle);
    }

    static HAEstadoComponente haAviso(String haNombre, String haDetalle) {
        return new HAEstadoComponente(haNombre, HANivel.AVISO, haDetalle);
    }

    static HAEstadoComponente haFalta(String haNombre, String haDetalle) {
        return new HAEstadoComponente(haNombre, HANivel.FALTA, haDetalle);
    }
}
