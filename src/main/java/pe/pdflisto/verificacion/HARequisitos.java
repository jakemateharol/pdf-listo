package pe.pdflisto.verificacion;

public record HARequisitos(Long haPesoMaximo, Integer haPaginasEsperadas, HANumeracion haNumeracion, String haMarcaDeAgua,
                         long haPesoOriginal, boolean haSinMetadatos, boolean haTextoBuscable, boolean haComprimir,
                         java.util.List<HAPesoPaso> haPesosPorPaso) {
    public HARequisitos {
        haPesosPorPaso = haPesosPorPaso == null ? java.util.List.of() : java.util.List.copyOf(haPesosPorPaso);
    }

    public HARequisitos(Long haPesoMaximo, Integer haPaginasEsperadas, HANumeracion haNumeracion, String haMarcaDeAgua,
                      long haPesoOriginal, boolean haSinMetadatos, boolean haTextoBuscable) {
        this(haPesoMaximo, haPaginasEsperadas, haNumeracion, haMarcaDeAgua, haPesoOriginal, haSinMetadatos, haTextoBuscable,
                haPesoMaximo != null, java.util.List.of());
    }

    public HARequisitos haConPesosPorPaso(java.util.List<HAPesoPaso> haPesos) {
        return new HARequisitos(haPesoMaximo, haPaginasEsperadas, haNumeracion, haMarcaDeAgua, haPesoOriginal, haSinMetadatos,
                haTextoBuscable, haComprimir, haPesos);
    }

    public record HANumeracion(String haFormato, int haDesde, int haPrimerNumero, boolean haArriba) {
    }

    public static HARequisitos haNinguno(long haPesoActual) {
        return new HARequisitos(null, null, null, null, haPesoActual, false, false);
    }
}
