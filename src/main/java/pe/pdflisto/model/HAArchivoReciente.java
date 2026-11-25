package pe.pdflisto.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "archivo_reciente")
@Getter
@Setter
@NoArgsConstructor
public class HAArchivoReciente {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 2048)
    private String ruta;

    @Column(nullable = false)
    private String nombre;

    private int paginas;

    @Column(nullable = false)
    private LocalDateTime abiertoEn;
}
