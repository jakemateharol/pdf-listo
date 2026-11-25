package pe.pdflisto.operaciones;

import pe.pdflisto.verificacion.HAEsperado;

import java.util.List;

import java.io.File;

public record HAResultado(File haArchivo, int haPaginas, long haBytes, boolean haSinCambios, boolean haCumple, String haDetalle,
                        String haContrasena, List<HAEsperado> haEsperado) {
    public HAResultado(File haArchivo, int haPaginas, long haBytes, boolean haSinCambios, boolean haCumple, String haDetalle,
                     String haContrasena) {
        this(haArchivo, haPaginas, haBytes, haSinCambios, haCumple, haDetalle, haContrasena, null);
    }

    public static HAResultado haDe(File haArchivo, int haPaginas) {
        return new HAResultado(haArchivo, haPaginas, haArchivo.length(), false, true, null, null);
    }

    public HAResultado haConDetalle(boolean haSinCambios, boolean haCumple, String haDetalle) {
        return new HAResultado(haArchivo, haPaginas, haBytes, haSinCambios, haCumple, haDetalle, haContrasena, haEsperado);
    }

    public HAResultado haConContrasena(String haClave) {
        return new HAResultado(haArchivo, haPaginas, haBytes, haSinCambios, haCumple, haDetalle, haClave, haEsperado);
    }

    public HAResultado haConEsperado(List<HAEsperado> haLista) {
        return new HAResultado(haArchivo, haPaginas, haBytes, haSinCambios, haCumple, haDetalle, haContrasena, haLista);
    }
}
