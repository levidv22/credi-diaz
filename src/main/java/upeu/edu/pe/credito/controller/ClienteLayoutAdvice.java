package upeu.edu.pe.credito.controller;

import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;
import upeu.edu.pe.credito.entity.Usuario;
import upeu.edu.pe.credito.service.UsuarioService;

import java.security.Principal;

@ControllerAdvice(assignableTypes = {
        UsuarioController.class,
        CreditoController.class
})
public class ClienteLayoutAdvice {

    private final UsuarioService usuarioService;

    public ClienteLayoutAdvice(UsuarioService usuarioService) {
        this.usuarioService = usuarioService;
    }

    @ModelAttribute
    public void agregarDatosClienteAlLayout(Principal principal, Model model) {
        if (principal == null) {
            return;
        }

        usuarioService.findByDni(principal.getName().trim()).ifPresent(usuario -> {
            model.addAttribute("usuarioLayout", usuario);
            model.addAttribute("nombreClienteLayout", primeraPalabra(usuario.getNombreCompleto()));

            String nombrePatrocinador = usuario.getPatrocinador() == null
                    ? "Sin patrocinador"
                    : primeraPalabra(usuario.getPatrocinador().getNombreCompleto());

            model.addAttribute("nombrePatrocinadorLayout", nombrePatrocinador);

            byte[] fotoPerfil = usuario.getFotoPerfil();
            model.addAttribute(
                    "tieneFotoPerfilLayout",
                    fotoPerfil != null && fotoPerfil.length > 0
            );
        });
    }

    private String primeraPalabra(String nombreCompleto) {
        if (nombreCompleto == null || nombreCompleto.isBlank()) {
            return "Cliente";
        }

        String nombreLimpio = nombreCompleto.trim();
        int separador = nombreLimpio.indexOf(' ');

        return separador >= 0
                ? nombreLimpio.substring(0, separador)
                : nombreLimpio;
    }
}
