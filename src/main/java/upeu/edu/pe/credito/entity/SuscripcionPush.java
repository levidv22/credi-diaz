package upeu.edu.pe.credito.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "suscripciones_push",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_suscripcion_endpoint",
                columnNames = "endpoint"
        ),
        indexes = @Index(name = "idx_push_usuario", columnList = "usuario_id"))
@Getter
@Setter
@NoArgsConstructor
public class SuscripcionPush {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @Column(nullable = false, length = 2048)
    private String endpoint;

    @Column(nullable = false, length = 300)
    private String clavePublica;

    @Column(nullable = false, length = 200)
    private String claveAuth;

    @Column(nullable = false)
    private LocalDateTime fechaRegistro = LocalDateTime.now();
}
