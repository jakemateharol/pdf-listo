package pe.pdflisto.verificacion;

public record HAPesoPaso(String haDescripcion, long haAntes, long haDespues, boolean haCompresion) {
    public long haDiferencia() {
        return haDespues - haAntes;
    }
}
