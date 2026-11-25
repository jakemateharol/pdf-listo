package pe.pdflisto.operaciones;

public enum HANivelCompresion {
    SUAVE("Suave", 3000, 0.85f, "Casi sin diferencia visible. Ideal para documentos que se imprimirán."),
    MEDIA("Media", 1800, 0.70f, "Buen equilibrio: se lee perfecto en pantalla y en impresión normal."),
    FUERTE("Fuerte", 1200, 0.50f, "El más liviano. Las fotos pierden detalle; el texto sigue nítido.");

    private final String haNombre;
    private final int haLadoMaximo;
    private final float haCalidadJpeg;
    private final String haExplicacion;

    HANivelCompresion(String haNombre, int haLadoMaximo, float haCalidadJpeg, String haExplicacion) {
        this.haNombre = haNombre;
        this.haLadoMaximo = haLadoMaximo;
        this.haCalidadJpeg = haCalidadJpeg;
        this.haExplicacion = haExplicacion;
    }

    public int haLadoMaximo() { return haLadoMaximo; }
    public float haCalidadJpeg() { return haCalidadJpeg; }
    public String haExplicacion() { return haExplicacion; }

    @Override
    public String toString() {
        return haNombre;
    }
}
