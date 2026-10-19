package upeu.edu.pe.credito.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "notificaciones_mora")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class NotificacionMora {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "garante_id", nullable = false)
    private Usuario garante;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "garantia_id", nullable = false, unique = true)
    private GarantiaReferido garantia;

    @Column(precision = 15, scale = 2, nullable = false)
    private BigDecimal montoAPagar = BigDecimal.ZERO;

    private LocalDate fechaNotificacion;
    private LocalDate fechaLimitePago;
    private boolean resuelto = false;
}
