package upeu.edu.pe.credito.dto;

import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class PerfilActualizacionDTO {
    private String nombreCompleto;
    private String dni;
    private String celular;
    private String domicilio;
    private String email;
}
