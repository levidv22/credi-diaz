package upeu.edu.pe.credito.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "garantias_referido")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class GarantiaReferido {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "garante_id", nullable = false)
    private Usuario garante;

    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "deudor_id", nullable = false)
    private Usuario deudor;

    @OneToOne(fetch = FetchType.LAZY) @JoinColumn(name = "credito_id", nullable = false, unique = true)
    private Credito credito;

    @Column(precision = 5, scale = 4, nullable = false)
    private BigDecimal porcentajeResponsabilidad = BigDecimal.ONE;

    @Column(precision = 15, scale = 2, nullable = false)
    private BigDecimal montoDeudaGarantizada = BigDecimal.ZERO;

    private boolean contratoFirmado = false;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoGarantia estado;
}
