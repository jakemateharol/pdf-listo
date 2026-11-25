package pe.pdflisto.pdf;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDDocumentOutline;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineNode;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

final class HALectorMarcadores {
    private static final int HA_PROFUNDIDAD_MAXIMA = 12;
    private static final int HA_TOTAL_MAXIMO = 5000;

    private int haLeidos;

    private HALectorMarcadores() {
    }

    static List<HAMarcador> haLeer(PDDocument haPdf) {
        PDDocumentOutline haIndice = haPdf.getDocumentCatalog().getDocumentOutline();
        if (haIndice == null) {
            return List.of();
        }
        return new HALectorMarcadores().haHijos(haIndice, haPdf, 0);
    }

    private List<HAMarcador> haHijos(PDOutlineNode haNodo, PDDocument haPdf, int haProfundidad) {
        List<HAMarcador> haLista = new ArrayList<>();
        if (haProfundidad > HA_PROFUNDIDAD_MAXIMA) {
            return haLista;
        }
        for (PDOutlineItem haItem : haNodo.children()) {
            if (++haLeidos > HA_TOTAL_MAXIMO) {
                break;
            }
            int haPagina = -1;
            try {
                PDPage haDestino = haItem.findDestinationPage(haPdf);
                if (haDestino != null) {
                    haPagina = haPdf.getPages().indexOf(haDestino);
                }
            } catch (IOException | RuntimeException haE) {
            }
            String haTitulo = haItem.getTitle() == null || haItem.getTitle().isBlank() ? "(sin título)" : haItem.getTitle().trim();
            haLista.add(new HAMarcador(haTitulo, haPagina, haHijos(haItem, haPdf, haProfundidad + 1)));
        }
        return haLista;
    }
}
