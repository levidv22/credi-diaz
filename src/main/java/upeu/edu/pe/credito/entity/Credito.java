package upeu.edu.pe.credito.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "creditos")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class Credito {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @Column(precision = 15, scale = 2, nullable = false)
    private BigDecimal montoPrestado;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ModalidadPago modalidad;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private EstadoCredito estado;

    private int numeroCuotas;

    /** Datos de créditos rápidos asignados mediante promociones Yape. */
    private boolean creditoPromocional = false;
    private int plazoPromocionalDias;
    @Column(precision = 5, scale = 2)
    private BigDecimal tasaPromocionalPorcentaje;

    @Column(precision = 15, scale = 2)
    private BigDecimal montoCuota;

    @Column(precision = 15, scale = 2)
    private BigDecimal interesTotalCalculado;

    private LocalDate fechaSolicitud;
    private LocalDate fechaInicio;
    private LocalDate fechaFin;
    private boolean contratoGarantiaAceptado = false;
    private String comentarioAdmin;
    private LocalDate fechaAprobacion;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private MedioDesembolso medioDesembolso;

    @Column(length = 100)
    private String nombreBanco;

    @Column(length = 40)
    private String numeroCuenta;

    @Column(length = 40)
    private String cci;

    @Column(length = 80)
    private String nombreBilletera;

    @Column(length = 30)
    private String numeroBilletera;

    @OneToMany(mappedBy = "credito", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("numeroCuota ASC")
    private List<CuotaCredito> cuotas = new ArrayList<>();
    /** Próxima cuota pendiente, siempre en orden cronológico. */
    @Transient
    public CuotaCredito getProximaCuotaPendiente() {
        return cuotas == null ? null : cuotas.stream()
                .filter(q -> !q.isPagado())
                .min(java.util.Comparator.comparing(CuotaCredito::getNumeroCuota))
                .orElse(null);
    }

    @Transient
    public long getDiasParaVencimiento() {
        CuotaCredito q = getProximaCuotaPendiente();
        return q == null || q.getFechaVencimiento() == null ? 0 :
                ChronoUnit.DAYS.between(LocalDate.now(), q.getFechaVencimiento());
    }

    @Transient
    public String getEstadoVencimiento() {
        CuotaCredito q = getProximaCuotaPendiente();
        if (q == null) return "Crédito pagado";
        long d = getDiasParaVencimiento();
        if (d > 0) return "Vence en " + d + " día" + (d == 1 ? "" : "s");
        if (d == 0) return "Vence hoy";
        long atraso = Math.abs(d);
        return "Atrasada " + atraso + " día" + (atraso == 1 ? "" : "s");
    }

    @Transient
    public boolean isCuotaAtrasada() {
        return getDiasParaVencimiento() < 0;
    }

}
