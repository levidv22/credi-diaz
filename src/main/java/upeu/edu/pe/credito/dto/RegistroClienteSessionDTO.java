package upeu.edu.pe.credito.dto;

import lombok.*;

import java.io.Serializable;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class RegistroClienteSessionDTO implements Serializable {
    private String nombreCompleto;
    private String dni;
    private String celular;
    private String domicilio;
    private String email;
    private String password;
    private String codigoPatrocinador;
    private byte[] fotoReciboLuzAgua;
    private byte[] fotoDniFrontal;
    private byte[] fotoDniReverso;
    private byte[] fotoFacial;
    private byte[] fotoPerfil;
    private byte[] fotoVivienda;
    private String reciboContentType;
    private String dniFrontalContentType;
    private String dniReversoContentType;
    private String facialContentType;
    private String perfilContentType;
    private String viviendaContentType;

    public UsuarioDTO toUsuarioDTO() {
        return new UsuarioDTO(nombreCompleto, dni, celular, domicilio, email, password, codigoPatrocinador);
    }
}
