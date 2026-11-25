package pe.pdflisto.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import pe.pdflisto.operaciones.HAPosicion;
import pe.pdflisto.operaciones.HATamanoPapel;

@Entity
@Table(name = "perfil_entrega")
@Getter
@Setter
@NoArgsConstructor
public class HAPerfilEntrega {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String nombre;

    @Column(length = 500)
    private String descripcion;

    private Double pesoMaximoMb;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private HATamanoPapel tamano;

    private boolean numerar;

    @Column(length = 60)
    private String formatoNumero = "{n}";

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private HAPosicion posicionNumero = HAPosicion.ABAJO_CENTRO;

    private int desdeNumero = 1;

    private boolean textoBuscable;

    private boolean sinMetadatos;

    private boolean sinContrasena;

    private Integer maxPaginas;

    @Column(length = 200)
    private String patronNombre;

    public Long haPesoMaximoBytes() {
        return pesoMaximoMb == null ? null : (long) (pesoMaximoMb * 1024 * 1024);
    }

    @Override
    public String toString() {
        return nombre;
    }
}
