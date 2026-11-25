package pe.pdflisto.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.pdflisto.model.HARegistroHistorial;
import pe.pdflisto.repository.HAHistorialRepository;

import java.io.File;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class HAHistorialService {
    private final HAHistorialRepository haRepositorio;

    @Transactional
    public HARegistroHistorial haRegistrar(File haOrigen, String haDescripcion, String haPasos, File haDestino, String haPedido) {
        return haRegistrar(haOrigen, haDescripcion, haPasos, haDestino, haPedido, null);
    }

    @Transactional
    public HARegistroHistorial haRegistrar(File haOrigen, String haDescripcion, String haPasos, File haDestino, String haPedido,
                                       String haCombinacion) {
        try {
            HARegistroHistorial haR = new HARegistroHistorial();
            haR.setFecha(LocalDateTime.now());
            haR.setOrigen(haOrigen == null ? null : haOrigen.getAbsolutePath());
            haR.setDestino(haDestino == null ? null : haDestino.getAbsolutePath());
            haR.setDescripcion(haRecortar(haDescripcion, 1000));
            haR.setPasos(haRecortar(haPasos, 4000));
            haR.setPedido(haRecortar(haPedido, 1000));
            haR.setCombinacion(haCombinacion == null || haCombinacion.length() > 4000 ? null : haCombinacion);
            return haRepositorio.save(haR);
        } catch (RuntimeException haE) {
            log.warn("No se pudo guardar en el historial: {}", haDescripcion, haE);
            return null;
        }
    }

    @Transactional
    public void haAnotarVerificacion(Long haId, boolean haCumple) {
        if (haId == null) {
            return;
        }
        try {
            haRepositorio.findById(haId).ifPresent(haR -> {
                haR.setVerificacion(haCumple ? "Cumple" : "Revisar");
                haRepositorio.save(haR);
            });
        } catch (RuntimeException haE) {
            log.warn("No se pudo anotar la verificación en el historial", haE);
        }
    }

    @Transactional(readOnly = true)
    public long haContarCombinacion(String haCombinacion) {
        try {
            return haCombinacion == null ? 0 : haRepositorio.countByCombinacion(haCombinacion);
        } catch (RuntimeException haE) {
            log.warn("No se pudo consultar el historial", haE);
            return 0;
        }
    }

    @Transactional(readOnly = true)
    public List<HARegistroHistorial> haRecientes() {
        try {
            return haRepositorio.findTop100ByOrderByFechaDescIdDesc();
        } catch (RuntimeException haE) {
            log.warn("No se pudo leer el historial", haE);
            return List.of();
        }
    }

    private static String haRecortar(String haTexto, int haMax) {
        if (haTexto == null) {
            return null;
        }
        return haTexto.length() <= haMax ? haTexto : haTexto.substring(0, haMax - 1) + "…";
    }
}
