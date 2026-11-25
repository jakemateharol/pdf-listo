package pe.pdflisto.operaciones;

import java.util.concurrent.atomic.AtomicBoolean;

public final class HAControl {
    @FunctionalInterface
    public interface HAOyente {
        void haAvance(double haFraccion, String haMensaje);
    }

    public static HAControl haNinguno() {
        return new HAControl(null);
    }

    private final HAOyente haOyente;
    private final AtomicBoolean haCancelado;
    private final double haInicio;
    private final double haAncho;

    public HAControl(HAOyente haOyente) {
        this(haOyente, new AtomicBoolean(false), 0, 1);
    }

    private HAControl(HAOyente haOyente, AtomicBoolean haCancelado, double haInicio, double haAncho) {
        this.haOyente = haOyente;
        this.haCancelado = haCancelado;
        this.haInicio = haInicio;
        this.haAncho = haAncho;
    }

    public void haCancelar() {
        haCancelado.set(true);
    }

    public boolean haCancelado() {
        return haCancelado.get();
    }

    public void haComprobar() {
        if (haCancelado.get()) {
            throw new HACanceladoException();
        }
    }

    public void haAvance(int haHecho, int haTotal, String haMensaje) {
        haComprobar();
        if (haOyente != null && haTotal > 0) {
            haOyente.haAvance(haInicio + haAncho * Math.min(1.0, (double) haHecho / haTotal), haMensaje);
        }
    }

    public void haMensaje(String haMensaje) {
        haComprobar();
        if (haOyente != null) {
            haOyente.haAvance(-1, haMensaje);
        }
    }

    public HAControl haTramo(int haI, int haN) {
        double haPaso = haAncho / Math.max(1, haN);
        return new HAControl(haOyente, haCancelado, haInicio + haPaso * haI, haPaso);
    }
}
