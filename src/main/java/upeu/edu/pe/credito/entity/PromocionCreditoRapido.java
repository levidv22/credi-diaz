package upeu.edu.pe.credito.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name="promociones_credito_rapido")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class PromocionCreditoRapido {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @Column(nullable=false, precision=15, scale=2) private BigDecimal monto;
 @Column(nullable=false) private LocalDate fechaExpiracion;
 @Column(nullable=false) private LocalDate fechaCreacion=LocalDate.now();
 @Column(nullable=false) private boolean activa=true;
 @ManyToMany(fetch=FetchType.LAZY)
 @JoinTable(name="promocion_credito_destinatarios", joinColumns=@JoinColumn(name="promocion_id"), inverseJoinColumns=@JoinColumn(name="usuario_id"))
 private List<Usuario> destinatarios=new ArrayList<>();
}
