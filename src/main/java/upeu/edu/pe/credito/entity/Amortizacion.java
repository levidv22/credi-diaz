package upeu.edu.pe.credito.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "amortizaciones")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class Amortizacion {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "credito_id", nullable = false)
    private Credito credito;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @Column(precision = 15, scale = 2, nullable = false)
    private BigDecimal monto;

    private LocalDateTime fechaSolicitud;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MetodoPago metodo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrigenPago origen;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private EstadoPago estado = EstadoPago.PENDIENTE_APROBACION;

    @Lob @Basic(fetch = FetchType.LAZY)
    @Column(name = "comprobante", columnDefinition = "LONGBLOB")
    private byte[] comprobante;

    @Column(length = 100)
    private String comprobanteContentType;

    @Column(length = 500)
    private String resultadoVerificacion;

    private LocalDateTime fechaRevision;

    @Column(length = 500)
    private String comentarioAdmin;
}
