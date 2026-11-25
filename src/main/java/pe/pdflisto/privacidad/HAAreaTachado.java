package pe.pdflisto.privacidad;

import pe.pdflisto.pdf.HAAreaTexto;

public record HAAreaTachado(int haPagina, HAAreaTexto haArea) {
    public HAAreaTachado haAmpliada(double haMargen) {
        return new HAAreaTachado(haPagina, new HAAreaTexto(haArea.haX() - haMargen, haArea.haY() - haMargen,
                haArea.haAncho() + 2 * haMargen, haArea.haAlto() + 2 * haMargen));
    }

    public boolean haContiene(double haX, double haY) {
        return haX >= haArea.haX() && haX <= haArea.haX() + haArea.haAncho() && haY >= haArea.haY() && haY <= haArea.haY() + haArea.haAlto();
    }
}
