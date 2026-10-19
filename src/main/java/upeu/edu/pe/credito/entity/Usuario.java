package upeu.edu.pe.credito.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Entity
@Table(name = "usuarios")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class Usuario {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String nombreCompleto;

    @Column(unique = true, nullable = false, length = 8)
    private String dni;

    @Column(length = 20)
    private String celular;

    @Column(length = 255)
    private String domicilio;

    @Column(unique = true, nullable = false, length = 150)
    private String email;

    @Column(nullable = false)
    private String password;

    @Column(unique = true, nullable = false, length = 20)
    private String codigoReferido;

    // KYC: imágenes almacenadas directamente en la base de datos como BLOB.
    @Lob @Basic(fetch = FetchType.LAZY) @Column(name = "kyc_recibo", columnDefinition = "LONGBLOB")
    private byte[] fotoReciboLuzAgua;
    @Lob @Basic(fetch = FetchType.LAZY) @Column(name = "kyc_dni_frontal", columnDefinition = "LONGBLOB")
    private byte[] fotoDniFrontal;
    @Lob @Basic(fetch = FetchType.LAZY) @Column(name = "kyc_dni_reverso", columnDefinition = "LONGBLOB")
    private byte[] fotoDniReverso;
    @Lob @Basic(fetch = FetchType.LAZY) @Column(name = "kyc_facial", columnDefinition = "LONGBLOB")
    private byte[] fotoFacial;
    @Lob @Basic(fetch = FetchType.LAZY) @Column(name = "kyc_perfil", columnDefinition = "LONGBLOB")
    private byte[] fotoPerfil;
    @Lob @Basic(fetch = FetchType.LAZY) @Column(name = "kyc_vivienda", columnDefinition = "LONGBLOB")
    private byte[] fotoVivienda;
    @Column(length = 100) private String tipoReciboKyc;
    @Column(length = 100) private String tipoDniFrontalKyc;
    @Column(length = 100) private String tipoDniReversoKyc;
    @Column(length = 100) private String tipoFacialKyc;
    @Column(length = 100) private String tipoPerfilKyc;
    @Column(length = 100) private String tipoViviendaKyc;
    private LocalDate fechaActualizacionDatos;
    private LocalDate fechaActualizacionDocumentos;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Rol rol;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private EstadoUsuario estado;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "patrocinador_id")
    private Usuario patrocinador;

    @OneToMany(mappedBy = "patrocinador")
    private List<Usuario> referidosDirectos;

    private int limiteDirectos = 3;
    private int scoreCrediticio = 100;
    private int conteoReferidosMorosos = 0;

    @Column(precision = 15, scale = 2, nullable = false)
    private BigDecimal limiteCreditoPermitido = new BigDecimal("100.00");

    @Column(precision = 15, scale = 2, nullable = false)
    private BigDecimal saldoAhorro = BigDecimal.ZERO;

    @Column(precision = 15, scale = 2, nullable = false)
    private BigDecimal saldoBonos = BigDecimal.ZERO;

    @Column(precision = 15, scale = 2, nullable = false)
    private BigDecimal saldoDisponible = BigDecimal.ZERO;

    @Column(name = "puntos_acumulados", precision = 15, scale = 3, nullable = false)
    private BigDecimal puntosAcumulados = BigDecimal.ZERO;

    private boolean activoPorAhorro = false;
    private LocalDate fechaVencimientoAhorro;

    // Activación/membresía
    private LocalDate fechaActivacionMembresia;
    private LocalDate fechaVencimientoMembresia;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RangoMlm rango = RangoMlm.MIEMBRO;
    @Column(name = "fecha_ultimo_credito_como_lider")
    private LocalDate fechaUltimoCreditoComoLider;
    @Column(name = "monto_ultimo_credito_como_lider", precision = 15, scale = 2)
    private BigDecimal montoUltimoCreditoComoLider;
}
