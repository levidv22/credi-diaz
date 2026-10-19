package upeu.edu.pe.credito.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

@Entity
@Table(name = "cuotas_credito")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class CuotaCredito {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "credito_id", nullable = false)
    private Credito credito;

    private int numeroCuota;

    @Column(precision = 15, scale = 2, nullable = false)
    private BigDecimal montoCuota = BigDecimal.ZERO;

    @Column(precision = 15, scale = 2, nullable = false)
    private BigDecimal montoInteres = BigDecimal.ZERO;

    @Column(precision = 15, scale = 2, nullable = false)
    private BigDecimal montoCapital = BigDecimal.ZERO;

    private LocalDate fechaVencimiento;
    private LocalDate fechaPagoReal;
    private boolean pagado = false;

    private int diasMora = 0;

    @Column(precision = 15, scale = 2, nullable = false)
    private BigDecimal penalizacionMoraAcumulada = BigDecimal.ZERO;

    /**
     * Mora documental: 1% diario sobre el importe de la cuota vencida.
     * En mensual simple, las cuotas de interés generan 1% de ese interés;
     * la última cuota (capital + interés) genera 1% del total de esa cuota.
     */
    public BigDecimal getTarifaMoraDiaria() {
        if (montoCuota == null) return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        return montoCuota.multiply(new BigDecimal("0.01")).setScale(2, RoundingMode.HALF_UP);
    }

    public BigDecimal getMontoTotalAPagar() {
        if (pagado) return montoCuota.setScale(2, RoundingMode.HALF_UP);
        return montoCuota.add(penalizacionMoraAcumulada).setScale(2, RoundingMode.HALF_UP);
    }

    public String getDetalleMoraCliente() {
        if (diasMora <= 0) return "Cuota al día - Sin penalizaciones.";
        return String.format("Atraso de %d día(s). Penalización acumulada: S/ %.2f (1%% diario de la cuota: S/ %.2f)",
                diasMora, penalizacionMoraAcumulada, getTarifaMoraDiaria());
    }
}
