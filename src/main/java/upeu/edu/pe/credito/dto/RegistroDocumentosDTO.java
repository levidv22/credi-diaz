package upeu.edu.pe.credito.dto;

import lombok.*;
import org.springframework.web.multipart.MultipartFile;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RegistroDocumentosDTO {

    private MultipartFile fotoReciboLuzAgua;

    private MultipartFile fotoDniFrontal;

    private MultipartFile fotoDniReverso;

    private MultipartFile fotoFacial;

    private MultipartFile fotoPerfil;

    private MultipartFile fotoVivienda;
}