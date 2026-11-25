package pe.pdflisto.privacidad;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import pe.pdflisto.exception.HAPdfListoException;
import pe.pdflisto.operaciones.HAOperacionPdf;
import pe.pdflisto.operaciones.HARecursos;

import java.io.IOException;
import java.security.SecureRandom;

public record HAProteger(String haContrasenaAbrir, String haContrasenaPropietario,
                       boolean haImprimir, boolean haCopiar, boolean haModificar) implements HAOperacionPdf {
    @Override
    public String haDescripcion() {
        return haContrasenaAbrir.isEmpty() ? "Poner permisos" : "Poner contraseña";
    }

    @Override
    public PDDocument haAplicar(PDDocument haDocumento, HARecursos haRecursos) throws IOException {
        if (haContrasenaAbrir.isEmpty() && haImprimir && haCopiar && haModificar) {
            throw new HAPdfListoException("Escribe una contraseña para abrir o quita algún permiso; si no, no hay nada que proteger.");
        }
        AccessPermission haPermisos = new AccessPermission();
        haPermisos.setCanPrint(haImprimir);
        haPermisos.setCanPrintFaithful(haImprimir);
        haPermisos.setCanExtractContent(haCopiar);
        haPermisos.setCanExtractForAccessibility(true);
        haPermisos.setCanModify(haModificar);
        haPermisos.setCanAssembleDocument(haModificar);
        haPermisos.setCanModifyAnnotations(haModificar);
        haPermisos.setCanFillInForm(true);
        String haPropietario = haContrasenaPropietario == null || haContrasenaPropietario.isEmpty()
                ? haAleatoria() : haContrasenaPropietario;
        StandardProtectionPolicy haPolitica = new StandardProtectionPolicy(haPropietario, haContrasenaAbrir, haPermisos);
        haPolitica.setEncryptionKeyLength(256);
        haPolitica.setPreferAES(true);
        haDocumento.setAllSecurityToBeRemoved(false);
        haDocumento.protect(haPolitica);
        return haDocumento;
    }

    @Override
    public boolean haConservaSeguridad() {
        return true;
    }

    @Override
    public String haContrasenaResultado() {
        return haContrasenaAbrir;
    }

    private static String haAleatoria() {
        SecureRandom haAzar = new SecureRandom();
        StringBuilder haSb = new StringBuilder();
        String haLetras = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789";
        for (int haI = 0; haI < 32; haI++) {
            haSb.append(haLetras.charAt(haAzar.nextInt(haLetras.length())));
        }
        return haSb.toString();
    }

    @Override
    public java.util.List<pe.pdflisto.verificacion.HAEsperado> haEsperado() {
        return java.util.List.of(new pe.pdflisto.verificacion.HAEsperado.HAMismasPaginas(), new pe.pdflisto.verificacion.HAEsperado.HAContrasena(true));
    }
}
