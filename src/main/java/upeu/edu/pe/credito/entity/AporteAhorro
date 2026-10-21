package upeu.edu.pe.credito.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "aportes_ahorro",
        indexes = {
                @Index(name = "idx_aporte_ahorro_estado", columnList = "estado"),
                @Index(name = "idx_aporte_ahorro_usuario", columnList = "usuario_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
public class AporteAhorro {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal monto = new BigDecimal("100.00");

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private MetodoPago metodo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private EstadoAporteAhorro estado =
            EstadoAporteAhorro.PENDIENTE_APROBACION;

    @Lob
    @Basic(fetch = FetchType.LAZY)
    @Column(name = "comprobante", columnDefinition = "LONGBLOB")
    private byte[] comprobante;

    @Column(name = "comprobante_content_type", length = 100)
    private String comprobanteContentType;

    @Column(name = "fecha_solicitud", nullable = false)
    private LocalDateTime fechaSolicitud = LocalDateTime.now();

    @Column(name = "fecha_revision")
    private LocalDateTime fechaRevision;

    @Column(name = "comentario_admin", length = 500)
    private String comentarioAdmin;
}
