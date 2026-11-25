package pe.pdflisto.operaciones;

import java.io.File;

@FunctionalInterface
public interface HAProcesoVersion {
    HAResultado haEjecutar(File haEntrada, String haContrasena, File haSalida, HAControl haControl);
}
