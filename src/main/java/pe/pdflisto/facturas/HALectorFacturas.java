package pe.pdflisto.facturas;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.NotFoundException;
import com.google.zxing.Result;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.multi.qrcode.QRCodeMultiReader;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;
import pe.pdflisto.operaciones.HAControl;
import pe.pdflisto.pdf.HACargaPdf;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
@Slf4j
public class HALectorFacturas {
    private static final Map<DecodeHintType, Object> HA_PISTAS = new EnumMap<>(DecodeHintType.class);

    static {
        HA_PISTAS.put(DecodeHintType.POSSIBLE_FORMATS, List.of(BarcodeFormat.QR_CODE));
        HA_PISTAS.put(DecodeHintType.TRY_HARDER, Boolean.TRUE);
        HA_PISTAS.put(DecodeHintType.CHARACTER_SET, "UTF-8");
    }

    public record HAFuente(File haArchivo, String haNombre) {
    }

    public List<HAComprobante> haLeer(List<File> haArchivos, HAControl haControl) {
        return haLeerFuentes(haArchivos.stream().map(haF -> new HAFuente(haF, haF.getName())).toList(), haControl);
    }

    public List<HAComprobante> haLeerFuentes(List<HAFuente> haFuentes, HAControl haControl) {
        List<HAComprobante> haResultado = new ArrayList<>();
        for (int haI = 0; haI < haFuentes.size(); haI++) {
            HAFuente haF = haFuentes.get(haI);
            haControl.haAvance(haI, haFuentes.size(), "Leyendo " + haF.haNombre() + " (" + (haI + 1) + " de " + haFuentes.size() + ")");
            List<HAComprobante> haDelArchivo = haLeerArchivo(haF.haArchivo(), haF.haNombre(), haControl.haTramo(haI, haFuentes.size()));
            for (HAComprobante haC : haDelArchivo) {
                haC.haOrigen(haF.haArchivo().lastModified(), haI);
            }
            haResultado.addAll(haDelArchivo);
        }
        HAValidadorComprobantes.haValidar(haResultado, LocalDate.now());
        return haResultado;
    }

    private List<HAComprobante> haLeerArchivo(File haArchivo, String haNombre, HAControl haControl) {
        List<HAComprobante> haLista = new ArrayList<>();
        try (PDDocument haPdf = HACargaPdf.haAbrir(haArchivo, null)) {
            PDFRenderer haDibujante = new PDFRenderer(haPdf);
            PDFTextStripper haLector = new PDFTextStripper();
            int haTotal = haPdf.getNumberOfPages();
            for (int haP = 0; haP < haTotal; haP++) {
                haControl.haAvance(haP, haTotal, "Buscando el QR en " + haNombre + ": página " + (haP + 1) + " de " + haTotal);
                Set<String> haQrs = haBuscarQr(haDibujante, haP);
                if (haQrs.isEmpty()) {
                    continue;
                }
                haLector.setStartPage(haP + 1);
                haLector.setEndPage(haP + 1);
                String haTexto = haLector.getText(haPdf);
                for (String haQr : haQrs) {
                    Optional<HADatosQr> haDatos = HAParserQrSunat.haParsear(haQr);
                    if (haDatos.isPresent()) {
                        haLista.add(new HAComprobante(haNombre, haP + 1, haDatos.get(), haQr, haTexto));
                    } else {
                        haLista.add(HAComprobante.haSinQr(haNombre, haP + 1, "Hay un código QR, pero no es de un comprobante de SUNAT."));
                    }
                }
            }
            if (haLista.isEmpty()) {
                haLista.add(HAComprobante.haSinQr(haNombre, 0, "No se encontró ningún código QR. Puede ser un comprobante sin QR "
                        + "(su lectura llega en la versión 1.1) o un QR muy borroso."));
            }
        } catch (InvalidPasswordException haE) {
            haLista.add(HAComprobante.haSinQr(haNombre, 0, "Tiene contraseña: ábrelo en PDF Listo, quita la contraseña y vuelve a intentarlo."));
        } catch (IOException | RuntimeException haE) {
            if (haE instanceof pe.pdflisto.operaciones.HACanceladoException haC) {
                throw haC;
            }
            log.warn("No se pudo leer {}", haArchivo, haE);
            haLista.add(HAComprobante.haSinQr(haNombre, 0, "No se pudo abrir: " + pe.pdflisto.pdf.HADiagnosticoPdf.haExplicar(haArchivo)));
        }
        return haLista;
    }

    private static Set<String> haBuscarQr(PDFRenderer haDibujante, int haPagina) throws IOException {
        Set<String> haEncontrados = new LinkedHashSet<>();
        for (float haDpi : new float[]{200f, 300f}) {
            BufferedImage haImg = haDibujante.renderImageWithDPI(haPagina, haDpi, ImageType.RGB);
            haEncontrados.addAll(haDecodificar(haImg));
            if (!haEncontrados.isEmpty()) {
                break;
            }
        }
        return haEncontrados;
    }

    static List<String> haDecodificar(BufferedImage haImagen) {
        List<String> haTextos = new ArrayList<>();
        try {
            BinaryBitmap haMapa = new BinaryBitmap(new HybridBinarizer(new BufferedImageLuminanceSource(haImagen)));
            for (Result haR : new QRCodeMultiReader().decodeMultiple(haMapa, HA_PISTAS)) {
                if (haR.getText() != null && !haR.getText().isBlank()) {
                    haTextos.add(haR.getText().trim());
                }
            }
        } catch (NotFoundException haE) {
        }
        return haTextos;
    }
}
