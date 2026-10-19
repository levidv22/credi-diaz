package upeu.edu.pe.credito.dto;

import lombok.*;
import upeu.edu.pe.credito.entity.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class PerfilUsuarioResponseDTO {
    private Long id;
    private String nombreCompleto;
    private String dni;
    private String celular;
    private String domicilio;
    private String email;
    private String codigoReferido;
    private byte[] fotoReciboLuzAgua;
    private byte[] fotoDniFrontal;
    private byte[] fotoDniReverso;
    private byte[] fotoFacial;
    private byte[] fotoPerfil;
    private byte[] fotoVivienda;
    private LocalDate fechaActualizacionDatos;
    private LocalDate fechaActualizacionDocumentos;
    private EstadoUsuario estado;
    private int scoreCrediticio;
    private BigDecimal limiteCreditoPermitido;
    private BigDecimal saldoAhorro;
    private BigDecimal saldoBonos;
    private BigDecimal saldoDisponible;
    private boolean activoPorAhorro;
    private LocalDate fechaVencimientoAhorro;
    private LocalDate fechaActivacionMembresia;
    private LocalDate fechaVencimientoMembresia;
    private RangoMlm rango;
    private String patrocinadorNombre;
    private int limiteDirectos;
    private List<Usuario> referidosDirectos;
    private Double porcentajeScore;
}
