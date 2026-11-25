package pe.pdflisto.operaciones;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class HADivisor {
    private final HAMotorPdf haMotor;

    public List<File> haDividir(File haEntrada, String haContrasena, List<HAPlanDivision.HAParte> haPartes, File haCarpeta) {
        return haDividir(haEntrada, haContrasena, haPartes, haCarpeta, HAControl.haNinguno());
    }

    public List<File> haDividir(File haEntrada, String haContrasena, List<HAPlanDivision.HAParte> haPartes, File haCarpeta,
                              HAControl haControl) {
        List<File> haCreados = new ArrayList<>();
        try {
            for (int haI = 0; haI < haPartes.size(); haI++) {
                HAPlanDivision.HAParte haParte = haPartes.get(haI);
                File haDestino = HAArchivos.haLibre(haCarpeta, haParte.haNombre(), ".pdf");
                haControl.haAvance(haI, haPartes.size(), "Creando parte " + (haI + 1) + " de " + haPartes.size() + "…");
                haMotor.haEjecutar(haEntrada, haContrasena, new HAReorganizar(haParte.haPaginas(), "Dividir"), haDestino,
                        haControl.haTramo(haI, haPartes.size()));
                haCreados.add(haDestino);
            }
        } catch (RuntimeException haE) {
            haCreados.forEach(File::delete);
            throw haE;
        }
        return haCreados;
    }

    public File haExtraer(File haEntrada, String haContrasena, List<Integer> haPaginas, File haDestino) {
        return haExtraer(haEntrada, haContrasena, haPaginas, haDestino, HAControl.haNinguno());
    }

    public File haExtraer(File haEntrada, String haContrasena, List<Integer> haPaginas, File haDestino, HAControl haControl) {
        haMotor.haEjecutar(haEntrada, haContrasena, new HAReorganizar(haPaginas, "Extraer páginas"), haDestino, haControl);
        return haDestino;
    }
}
