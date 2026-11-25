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
import pe.pdflisto.privacidad.HATipoDato;

@Entity
@Table(name = "regla_deteccion")
@Getter
@Setter
@NoArgsConstructor
public class HAReglaDeteccion {
    public enum HAModo { INCORPORADA, PALABRA, PATRON }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String nombre;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private HATipoDato tipo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private HAModo modo;

    @Column(length = 500)
    private String valor;

    private boolean activa = true;

    public static HAReglaDeteccion haIncorporada(HATipoDato tipo, String nombre) {
        HAReglaDeteccion r = new HAReglaDeteccion();
        r.tipo = tipo;
        r.nombre = nombre;
        r.modo = HAModo.INCORPORADA;
        r.activa = true;
        return r;
    }

    public static HAReglaDeteccion haDelUsuario(String nombre, HAModo modo, String valor) {
        HAReglaDeteccion r = new HAReglaDeteccion();
        r.tipo = HATipoDato.PERSONALIZADO;
        r.nombre = nombre;
        r.modo = modo;
        r.valor = valor;
        r.activa = true;
        return r;
    }
}
