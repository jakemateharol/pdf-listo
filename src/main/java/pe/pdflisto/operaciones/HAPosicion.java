package pe.pdflisto.operaciones;

public enum HAPosicion {
    ABAJO_CENTRO("Abajo, al centro", false, 0),
    ABAJO_DERECHA("Abajo, a la derecha", false, 1),
    ABAJO_IZQUIERDA("Abajo, a la izquierda", false, -1),
    ARRIBA_CENTRO("Arriba, al centro", true, 0),
    ARRIBA_DERECHA("Arriba, a la derecha", true, 1),
    ARRIBA_IZQUIERDA("Arriba, a la izquierda", true, -1);

    private final String haTexto;
    private final boolean haArriba;
    private final int haHorizontal;

    HAPosicion(String haTexto, boolean haArriba, int haHorizontal) {
        this.haTexto = haTexto;
        this.haArriba = haArriba;
        this.haHorizontal = haHorizontal;
    }

    public boolean haArriba() { return haArriba; }
    public int haHorizontal() { return haHorizontal; }

    @Override
    public String toString() {
        return haTexto;
    }
}
