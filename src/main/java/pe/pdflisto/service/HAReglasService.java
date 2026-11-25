package pe.pdflisto.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.pdflisto.exception.HAPdfListoException;
import pe.pdflisto.model.HAReglaDeteccion;
import pe.pdflisto.privacidad.HADetectorDatos;
import pe.pdflisto.repository.HAReglaDeteccionRepository;

import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

@Service
@RequiredArgsConstructor
@Slf4j
public class HAReglasService {
    private final HAReglaDeteccionRepository haRepositorio;

    @Transactional
    public List<HAReglaDeteccion> haTodas() {
        try {
            if (haRepositorio.count() == 0) {
                haRepositorio.saveAll(HADetectorDatos.haReglasIncorporadas());
            }
            return haRepositorio.findAll();
        } catch (RuntimeException haE) {
            log.warn("No se pudieron leer las reglas; se usan las incorporadas", haE);
            return HADetectorDatos.haReglasIncorporadas();
        }
    }

    public List<HAReglaDeteccion> haActivas() {
        return haTodas().stream().filter(HAReglaDeteccion::isActiva).toList();
    }

    @Transactional
    public void haGuardar(List<HAReglaDeteccion> haReglas) {
        haReglas.forEach(HAReglasService::haValidar);
        haRepositorio.saveAll(haReglas);
    }

    @Transactional
    public void haEliminar(HAReglaDeteccion haRegla) {
        if (haRegla.getModo() == HAReglaDeteccion.HAModo.INCORPORADA) {
            throw new HAPdfListoException("Las reglas incorporadas no se borran; puedes desactivarlas.");
        }
        if (haRegla.getId() != null) {
            haRepositorio.deleteById(haRegla.getId());
        }
    }

    public static void haValidar(HAReglaDeteccion haR) {
        if (haR.getModo() == HAReglaDeteccion.HAModo.INCORPORADA) {
            return;
        }
        if (haR.getNombre() == null || haR.getNombre().isBlank()) {
            throw new HAPdfListoException("Ponle un nombre a la regla.");
        }
        if (haR.getValor() == null || haR.getValor().trim().length() < 2) {
            throw new HAPdfListoException("La palabra o patrón debe tener al menos 2 caracteres.");
        }
        if (haR.getModo() == HAReglaDeteccion.HAModo.PATRON) {
            try {
                Pattern.compile(haR.getValor());
            } catch (PatternSyntaxException haE) {
                throw new HAPdfListoException("El patrón no es válido: " + haE.getDescription() + ".");
            }
        }
    }
}
