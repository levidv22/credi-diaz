package upeu.edu.pe.credito.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "bonificaciones_mlm")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class BonificacionMlm {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "beneficiario_id", nullable = false)
    private Usuario beneficiario;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "credito_id", nullable = false)
    private Credito credito;

    @Column(nullable = false, length = 30)
    private String tipo; // DIRECTA / UNINIVEL

    @Column(nullable = false)
    private int nivel;

    @Column(nullable = false, precision = 6, scale = 3)
    private BigDecimal porcentaje;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal importe;

    @Column(nullable = false, length = 20)
    private String modalidadInteres; // SIMPLE / COMPUESTO

    @Column(nullable = false)
    private LocalDateTime fechaAcreditacion = LocalDateTime.now();

    @Column(nullable = false, length = 500)
    private String detalle;
}
