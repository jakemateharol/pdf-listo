package pe.pdflisto.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.pdflisto.model.HAArchivoReciente;
import pe.pdflisto.repository.HAArchivoRecienteRepository;

import java.io.File;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class HAArchivosRecientesService {
    private static final int HA_A_MOSTRAR = 10;
    private static final int HA_A_GUARDAR = 30;

    private final HAArchivoRecienteRepository haRepositorio;

    @Transactional
    public void haRegistrar(File haArchivo, int haPaginas) {
        String haRuta = haArchivo.getAbsolutePath();
        HAArchivoReciente haReciente = haRepositorio.findByRuta(haRuta).orElseGet(HAArchivoReciente::new);
        haReciente.setRuta(haRuta);
        haReciente.setNombre(haArchivo.getName());
        haReciente.setPaginas(haPaginas);
        haReciente.setAbiertoEn(LocalDateTime.now());
        haRepositorio.save(haReciente);

        List<HAArchivoReciente> haTodos = haRepositorio.findAllByOrderByAbiertoEnDesc();
        if (haTodos.size() > HA_A_GUARDAR) {
            haRepositorio.deleteAll(haTodos.subList(HA_A_GUARDAR, haTodos.size()));
        }
    }

    @Transactional(readOnly = true)
    public List<HAArchivoReciente> haRecientes() {
        return haRepositorio.findAllByOrderByAbiertoEnDesc().stream()
                .filter(haR -> new File(haR.getRuta()).isFile())
                .limit(HA_A_MOSTRAR)
                .toList();
    }

    @Transactional
    public void haQuitar(String haRuta) {
        haRepositorio.findByRuta(haRuta).ifPresent(haRepositorio::delete);
    }
}
