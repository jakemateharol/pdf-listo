package pe.pdflisto.entrega;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.pdflisto.exception.HAPdfListoException;
import pe.pdflisto.model.HAPerfilEntrega;
import pe.pdflisto.operaciones.HAPosicion;
import pe.pdflisto.operaciones.HATamanoPapel;
import pe.pdflisto.repository.HAPerfilEntregaRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class HAPerfilesService {
    private final HAPerfilEntregaRepository haRepositorio;

    @Transactional
    public List<HAPerfilEntrega> haTodos() {
        try {
            if (haRepositorio.count() == 0) {
                haRepositorio.saveAll(haPredeterminados());
            }
            return haRepositorio.findAll();
        } catch (RuntimeException haE) {
            log.warn("No se pudieron leer los perfiles; se usan los predeterminados", haE);
            return haPredeterminados();
        }
    }

    @Transactional
    public HAPerfilEntrega haGuardar(HAPerfilEntrega haP) {
        haValidar(haP);
        return haRepositorio.save(haP);
    }

    @Transactional
    public void haEliminar(HAPerfilEntrega haP) {
        if (haP.getId() != null) {
            haRepositorio.deleteById(haP.getId());
        }
    }

    public static void haValidar(HAPerfilEntrega haP) {
        if (haP.getNombre() == null || haP.getNombre().isBlank()) {
            throw new HAPdfListoException("Ponle un nombre al perfil.");
        }
        if (haP.getPesoMaximoMb() != null && haP.getPesoMaximoMb() <= 0) {
            throw new HAPdfListoException("El peso máximo debe ser mayor que 0.");
        }
        if (haP.getMaxPaginas() != null && haP.getMaxPaginas() < 1) {
            throw new HAPdfListoException("El máximo de páginas debe ser 1 o más.");
        }
        if (haP.isNumerar() && (haP.getFormatoNumero() == null || !haP.getFormatoNumero().contains("{n}"))) {
            throw new HAPdfListoException("El formato de numeración debe incluir {n}.");
        }
    }

    public static List<HAPerfilEntrega> haPredeterminados() {
        HAPerfilEntrega haTesis = new HAPerfilEntrega();
        haTesis.setNombre("Tesis (universidad)");
        haTesis.setDescripcion("A4, páginas numeradas abajo a la derecha, texto buscable, sin contraseña, máximo 20 MB.");
        haTesis.setPesoMaximoMb(20.0);
        haTesis.setTamano(HATamanoPapel.A4);
        haTesis.setNumerar(true);
        haTesis.setFormatoNumero("{n}");
        haTesis.setPosicionNumero(HAPosicion.ABAJO_DERECHA);
        haTesis.setTextoBuscable(true);
        haTesis.setSinContrasena(true);
        haTesis.setPatronNombre("Tesis - {nombre}");

        HAPerfilEntrega haTramite = new HAPerfilEntrega();
        haTramite.setNombre("Trámite (mesa de partes virtual)");
        haTramite.setDescripcion("A4, texto buscable, sin contraseña, máximo 5 MB. Revisa el límite exacto de la entidad.");
        haTramite.setPesoMaximoMb(5.0);
        haTramite.setTamano(HATamanoPapel.A4);
        haTramite.setTextoBuscable(true);
        haTramite.setSinContrasena(true);
        haTramite.setPatronNombre("{nombre} - {fecha}");

        HAPerfilEntrega haLicitacion = new HAPerfilEntrega();
        haLicitacion.setNombre("Licitación (propuesta foliada)");
        haLicitacion.setDescripcion("«Folio N» arriba a la derecha en cada página, texto buscable, sin metadatos ni "
                + "contraseña, máximo 10 MB.");
        haLicitacion.setPesoMaximoMb(10.0);
        haLicitacion.setNumerar(true);
        haLicitacion.setFormatoNumero("Folio {n}");
        haLicitacion.setPosicionNumero(HAPosicion.ARRIBA_DERECHA);
        haLicitacion.setTextoBuscable(true);
        haLicitacion.setSinMetadatos(true);
        haLicitacion.setSinContrasena(true);
        haLicitacion.setPatronNombre("Propuesta - {nombre}");
        return List.of(haTesis, haTramite, haLicitacion);
    }
}
