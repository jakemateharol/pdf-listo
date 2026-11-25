package pe.pdflisto.operaciones;

public enum HATamanoPapel {
    A4("A4", 595.28f, 841.89f),
    CARTA("Carta", 612f, 792f);

    private final String haNombre;
    private final float haAncho;
    private final float haAlto;

    HATamanoPapel(String haNombre, float haAncho, float haAlto) {
        this.haNombre = haNombre;
        this.haAncho = haAncho;
        this.haAlto = haAlto;
    }

    public float haAncho() { return haAncho; }
    public float haAlto() { return haAlto; }

    public boolean haCoincide(double haW, double haH) {
        return (Math.abs(haW - haAncho) < 3 && Math.abs(haH - haAlto) < 3) || (Math.abs(haW - haAlto) < 3 && Math.abs(haH - haAncho) < 3);
    }

    @Override
    public String toString() {
        return haNombre;
    }
}
