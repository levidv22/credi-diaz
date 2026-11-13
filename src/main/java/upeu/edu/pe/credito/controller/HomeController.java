package upeu.edu.pe.credito.controller;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {

    @GetMapping({"/", "/inicio"})
    public String inicio(Authentication authentication) {

        boolean usuarioAutenticado =
                authentication != null
                && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken);

        if (!usuarioAutenticado) {
            return "auth/login";
        }

        boolean esAdmin = authentication.getAuthorities()
                .stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));

        return esAdmin
                ? "redirect:/admin/dashboard"
                : "redirect:/creditos/mis-creditos";
    }
}
