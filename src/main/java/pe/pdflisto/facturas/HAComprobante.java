package pe.pdflisto.facturas;

import java.util.ArrayList;
import java.util.List;

public final class HAComprobante {
    public enum HAEstado {
        CUMPLE("Cumple"),
        NO_COMPROBABLE("No se puede comprobar"),
        REVISAR("Revisar"),
        DUPLICADO("Duplicado"),
        SIN_QR("Sin QR");

        private final String haTexto;

        HAEstado(String haTexto) {
            this.haTexto = haTexto;
        }

        @Override
        public String toString() {
            return haTexto;
        }
    }

    private final String haArchivo;
    private final int haPagina;
    private final HADatosQr haDatos;
    private final String haTextoQr;
    private final String haTextoPagina;
    private final List<String> haObservaciones = new ArrayList<>();
    private HAEstado haEstado;
    private long haFechaArchivo;
    private int haOrdenCarga;

    public HAComprobante(String haArchivo, int haPagina, HADatosQr haDatos, String haTextoQr, String haTextoPagina) {
        this.haArchivo = haArchivo;
        this.haPagina = haPagina;
        this.haDatos = haDatos;
        this.haTextoQr = haTextoQr;
        this.haTextoPagina = haTextoPagina;
        this.haEstado = haDatos == null ? HAEstado.SIN_QR : HAEstado.CUMPLE;
    }

    public static HAComprobante haSinQr(String haArchivo, int haPagina, String haMotivo) {
        HAComprobante haC = new HAComprobante(haArchivo, haPagina, null, null, null);
        haC.haObservaciones.add(haMotivo);
        return haC;
    }

    public String haArchivo() { return haArchivo; }
    public int haPagina() { return haPagina; }
    public HADatosQr haDatos() { return haDatos; }
    public String haTextoQr() { return haTextoQr; }
    String haTextoPagina() { return haTextoPagina; }
    public HAEstado haEstado() { return haEstado; }
    public List<String> haObservaciones() { return haObservaciones; }

    void haObservar(String haTexto, HAEstado haNuevo) {
        haObservaciones.add(haTexto);
        if (haNuevo.ordinal() > haEstado.ordinal() && haEstado != HAEstado.SIN_QR) {
            haEstado = haNuevo;
        }
    }

    void haAnotar(String haTexto) {
        haObservaciones.add(haTexto);
    }

    void haOrigen(long haFechaArchivo, int haOrdenCarga) {
        this.haFechaArchivo = haFechaArchivo;
        this.haOrdenCarga = haOrdenCarga;
    }

    long haFechaArchivo() { return haFechaArchivo; }
    int haOrdenCarga() { return haOrdenCarga; }

    public String haObservacionesTexto() {
        return String.join(" ", haObservaciones);
    }
}
