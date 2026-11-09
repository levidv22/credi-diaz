package upeu.edu.pe.credito.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "solicitudes_reembolso_ahorro",
        indexes = {
                @Index(name = "idx_reembolso_ahorro_usuario", columnList = "usuario_id"),
                @Index(name = "idx_reembolso_ahorro_estado", columnList = "estado")
        })
@Getter
@Setter
@NoArgsConstructor
public class SolicitudReembolsoAhorro {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal monto = new BigDecimal("100.00");

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MetodoReembolsoAhorro metodo;

    @Column(name = "titular_cuenta", nullable = false, length = 150)
    private String titularCuenta;

    @Column(name = "numero_cuenta", length = 30)
    private String numeroCuenta;

    @Column(name = "cci", length = 25)
    private String cci;

    @Column(name = "numero_yape", length = 15)
    private String numeroYape;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoReembolsoAhorro estado = EstadoReembolsoAhorro.PENDIENTE;

    @Lob
    @Basic(fetch = FetchType.LAZY)
    @Column(name = "comprobante_reembolso", columnDefinition = "LONGBLOB")
    private byte[] comprobante;

    @Column(name = "comprobante_content_type", length = 100)
    private String comprobanteContentType;

    @Column(name = "fecha_solicitud", nullable = false)
    private LocalDateTime fechaSolicitud = LocalDateTime.now();

    @Column(name = "fecha_revision")
    private LocalDateTime fechaRevision;

    @Column(name = "motivo_rechazo", length = 500)
    private String motivoRechazo;
}
