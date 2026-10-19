package upeu.edu.pe.credito.dto;

import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class UsuarioDTO {
    private String nombreCompleto;
    private String dni;
    private String celular;
    private String domicilio;
    private String email;
    private String password;
    private String codigoPatrocinador;
}
