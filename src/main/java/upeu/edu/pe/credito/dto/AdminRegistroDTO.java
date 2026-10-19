package upeu.edu.pe.credito.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AdminRegistroDTO {
    private String nombreCompleto;
    private String dni;
    private String email;
    private String password;
}
