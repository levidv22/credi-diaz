package upeu.edu.pe.credito.controller;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import upeu.edu.pe.credito.entity.Rol;
import upeu.edu.pe.credito.entity.Usuario;
import upeu.edu.pe.credito.service.UsuarioService;

import java.security.Principal;

@RestController
@RequestMapping("/archivos/kyc")
public class KycFileController {

    private final UsuarioService usuarioService;

    public KycFileController(UsuarioService usuarioService) {
        this.usuarioService = usuarioService;
    }

    @GetMapping("/{usuarioId}/{tipo}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<byte[]> ver(
            @PathVariable Long usuarioId,
            @PathVariable String tipo,
            Principal principal) {

        if (principal == null) return ResponseEntity.status(401).build();

        Usuario actual = usuarioService
                .findByDni(principal.getName().trim())
                .orElseThrow(() -> new IllegalStateException(
                        "Usuario autenticado no encontrado."
                ));

        Usuario objetivo = usuarioService
                .findById(usuarioId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Usuario de KYC no encontrado."
                ));

        if (actual.getRol() != Rol.ADMIN
                && !actual.getId().equals(usuarioId)) {

            return ResponseEntity
                    .status(403)
                    .build();
        }

        byte[] data;
        String contentType;

        switch (tipo) {

            case "recibo" -> {
                data = objetivo.getFotoReciboLuzAgua();
                contentType = objetivo.getTipoReciboKyc();
            }

            case "dni-frontal" -> {
                data = objetivo.getFotoDniFrontal();
                contentType = objetivo.getTipoDniFrontalKyc();
            }

            case "dni-reverso" -> {
                data = objetivo.getFotoDniReverso();
                contentType = objetivo.getTipoDniReversoKyc();
            }

            case "facial" -> {
                data = objetivo.getFotoFacial();
                contentType = objetivo.getTipoFacialKyc();
            }

            case "perfil" -> {
                data = objetivo.getFotoPerfil();
                contentType = objetivo.getTipoPerfilKyc();
            }

            case "vivienda" -> {
                data = objetivo.getFotoVivienda();
                contentType = objetivo.getTipoViviendaKyc();
            }

            default -> {
                return ResponseEntity
                        .badRequest()
                        .build();
            }
        }

        if (data == null || data.length == 0) {
            return ResponseEntity
                    .notFound()
                    .build();
        }

        MediaType mediaType;

        try {
            mediaType = MediaType.parseMediaType(
                    contentType != null && !contentType.isBlank()
                            ? contentType
                            : MediaType.IMAGE_JPEG_VALUE
            );
        } catch (IllegalArgumentException e) {
            mediaType = MediaType.IMAGE_JPEG;
        }

        return ResponseEntity
                .ok()
                .contentType(mediaType)
                .body(data);
    }
}
