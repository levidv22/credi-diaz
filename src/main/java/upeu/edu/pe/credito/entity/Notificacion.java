package upeu.edu.pe.credito.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "notificaciones",
        indexes = {
                @Index(name = "idx_notificacion_usuario_fecha",
                        columnList = "usuario_id,fechaCreacion")
        })
@Getter
@Setter
@NoArgsConstructor
public class Notificacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @Column(nullable = false, length = 120)
    private String titulo;

    @Column(nullable = false, length = 1000)
    private String mensaje;

    @Column(length = 500)
    private String url;

    @Column(nullable = false)
    private boolean leida = false;

    @Column(nullable = false)
    private LocalDateTime fechaCreacion = LocalDateTime.now();

    public Notificacion(Usuario usuario, String titulo,
                        String mensaje, String url) {
        this.usuario = usuario;
        this.titulo = titulo;
        this.mensaje = mensaje;
        this.url = url;
        this.leida = false;
        this.fechaCreacion = LocalDateTime.now();
    }
}
