package pe.pdflisto.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pe.pdflisto.model.HARegistroHistorial;

import java.util.List;

public interface HAHistorialRepository extends JpaRepository<HARegistroHistorial, Long> {
    List<HARegistroHistorial> findTop100ByOrderByFechaDescIdDesc();

    long countByCombinacion(String haCombinacion);
}
