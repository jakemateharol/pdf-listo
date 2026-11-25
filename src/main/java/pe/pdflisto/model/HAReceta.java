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
@Table(name = "receta")
@Getter
@Setter
@NoArgsConstructor
public class HAReceta {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String nombre;

    @Column(nullable = false, length = 4000)
    private String codigo;

    @Column(length = 500)
    private String resumen;

    @Column(nullable = false)
    private LocalDateTime creada;

    private int usos;

    @Override
    public String toString() {
        return nombre;
    }
}
