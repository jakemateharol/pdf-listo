package pe.pdflisto.anotaciones;

import pe.pdflisto.pdf.HAAreaTexto;

import java.awt.Color;
import java.io.File;
import java.util.List;

public sealed interface HAAnotacion {
    int haPagina();

    String haDescripcion();

    enum HATipoMarcado { RESALTADO, SUBRAYADO, TACHADO }

    enum HATipoForma { RECTANGULO, ELIPSE, LINEA, FLECHA }

    record HAMarcado(HATipoMarcado haTipo, int haPagina, List<HAAreaTexto> haAreas, Color haColor, String haTexto) implements HAAnotacion {
        public String haDescripcion() {
            String haQue = switch (haTipo) {
                case RESALTADO -> "Resaltado";
                case SUBRAYADO -> "Subrayado";
                case TACHADO -> "Texto tachado";
            };
            return haQue + (haTexto == null || haTexto.isBlank() ? "" : " «" + haRecortar(haTexto) + "»");
        }
    }

    record HANota(int haPagina, double haX, double haY, String haTexto, Color haColor) implements HAAnotacion {
        public String haDescripcion() { return "Nota «" + haRecortar(haTexto) + "»"; }
    }

    record HAForma(HATipoForma haTipo, int haPagina, double haX1, double haY1, double haX2, double haY2, Color haColor, float haGrosor)
            implements HAAnotacion {
        public String haDescripcion() {
            return switch (haTipo) {
                case RECTANGULO -> "Rectángulo";
                case ELIPSE -> "Elipse";
                case LINEA -> "Línea";
                case FLECHA -> "Flecha";
            };
        }

        public HAAreaTexto haArea() {
            return new HAAreaTexto(Math.min(haX1, haX2), Math.min(haY1, haY2), Math.abs(haX2 - haX1), Math.abs(haY2 - haY1));
        }
    }

    record HATrazo(int haPagina, List<double[]> haPuntos, Color haColor, float haGrosor) implements HAAnotacion {
        public String haDescripcion() { return "Dibujo libre"; }
    }

    record HATexto(int haPagina, double haX, double haY, String haTexto, float haTamano, Color haColor) implements HAAnotacion {
        public String haDescripcion() { return "Texto «" + haRecortar(haTexto) + "»"; }
    }

    record HAImagen(int haPagina, HAAreaTexto haArea, File haArchivo) implements HAAnotacion {
        public String haDescripcion() { return "Imagen " + haArchivo.getName(); }
    }

    record HAFirma(int haPagina, HAAreaTexto haArea, File haImagen, String haNombre) implements HAAnotacion {
        public String haDescripcion() { return "Firma «" + haNombre + "»"; }
    }

    record HASello(int haPagina, HAAreaTexto haArea, String haTexto, Color haColor, String haFecha) implements HAAnotacion {
        public String haDescripcion() { return "Sello «" + haTexto + "»" + (haFecha != null ? " " + haFecha : ""); }
    }

    private static String haRecortar(String haT) {
        String haS = haT.replace('\n', ' ').trim();
        return haS.length() > 30 ? haS.substring(0, 30) + "…" : haS;
    }
}
