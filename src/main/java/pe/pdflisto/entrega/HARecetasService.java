package pe.pdflisto.entrega;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.pdflisto.config.HAConfiguracion;
import pe.pdflisto.exception.HAPdfListoException;
import pe.pdflisto.model.HAReceta;
import pe.pdflisto.repository.HARecetaRepository;
import pe.pdflisto.service.HAHistorialService;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class HARecetasService {
    public static final int HA_REPETICIONES = 3;

    private final HARecetaRepository haRepositorio;
    private final HAHistorialService haHistorial;
    private final HAConfiguracion haConfiguracion;
    private final List<Runnable> haOyentes = new ArrayList<>();

    public void haAlCambiar(Runnable haOyente) {
        haOyentes.add(haOyente);
    }

    public List<HAReceta> haTodas() {
        try {
            return haRepositorio.findAllByOrderByUsosDescNombreAsc();
        } catch (RuntimeException haE) {
            log.warn("No se pudieron leer las recetas", haE);
            return List.of();
        }
    }

    @Transactional
    public HAReceta haGuardar(String haNombre, String haCodigo, String haResumen) {
        if (haNombre == null || haNombre.isBlank()) {
            throw new HAPdfListoException("Ponle un nombre a la receta.");
        }
        if (haCodigo == null || haCodigo.isBlank()) {
            throw new HAPdfListoException("La receta no tiene pasos.");
        }
        HAReceta haR = new HAReceta();
        haR.setNombre(haNombre.trim());
        haR.setCodigo(haCodigo);
        haR.setResumen(haResumen);
        haR.setCreada(LocalDateTime.now());
        HAReceta haGuardada = haRepositorio.save(haR);
        haAvisar();
        return haGuardada;
    }

    @Transactional
    public void haEliminar(HAReceta haR) {
        haRepositorio.deleteById(haR.getId());
        haAvisar();
    }

    @Transactional
    public void haRegistrarUso(HAReceta haR) {
        haRepositorio.findById(haR.getId()).ifPresent(haX -> {
            haX.setUsos(haX.getUsos() + 1);
            haRepositorio.save(haX);
        });
    }

    public Optional<String> haSugerencia(String haCombinacion) {
        if (!haDebeOfrecer(haCombinacion, haHistorial.haContarCombinacion(haCombinacion),
                haExiste(haCombinacion), haConfiguracion.haRecetaRechazada(haCombinacion))) {
            return Optional.empty();
        }
        return Optional.of(haCombinacion);
    }

    public static boolean haDebeOfrecer(String haCombinacion, long haVecesHecha, boolean haYaEsReceta, boolean haRechazada) {
        return haCombinacion != null && haCombinacion.contains(";") && haVecesHecha >= HA_REPETICIONES && !haYaEsReceta && !haRechazada;
    }

    public void haRechazar(String haCombinacion) {
        haConfiguracion.haRechazarReceta(haCombinacion);
    }

    private boolean haExiste(String haCodigo) {
        try {
            return haRepositorio.existsByCodigo(haCodigo);
        } catch (RuntimeException haE) {
            return false;
        }
    }

    private void haAvisar() {
        haOyentes.forEach(Runnable::run);
    }
}
