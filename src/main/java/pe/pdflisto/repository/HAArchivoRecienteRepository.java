package pe.pdflisto.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pe.pdflisto.model.HAArchivoReciente;

import java.util.List;
import java.util.Optional;

public interface HAArchivoRecienteRepository extends JpaRepository<HAArchivoReciente, Long> {
    Optional<HAArchivoReciente> findByRuta(String haRuta);

    List<HAArchivoReciente> findAllByOrderByAbiertoEnDesc();
}
