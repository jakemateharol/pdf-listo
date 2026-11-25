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
@Table(name = "historial")
@Getter
@Setter
@NoArgsConstructor
public class HARegistroHistorial {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private LocalDateTime fecha;

    @Column(length = 2048)
    private String origen;

    @Column(length = 2048)
    private String destino;

    @Column(nullable = false, length = 1000)
    private String descripcion;

    @Column(length = 4000)
    private String pasos;

    @Column(length = 1000)
    private String pedido;

    @Column(length = 4000)
    private String combinacion;

    @Column(length = 20)
    private String verificacion;
}
