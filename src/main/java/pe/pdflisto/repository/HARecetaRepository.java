package pe.pdflisto.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pe.pdflisto.model.HAReceta;

import java.util.List;

public interface HARecetaRepository extends JpaRepository<HAReceta, Long> {
    boolean existsByCodigo(String haCodigo);

    List<HAReceta> findAllByOrderByUsosDescNombreAsc();
}
