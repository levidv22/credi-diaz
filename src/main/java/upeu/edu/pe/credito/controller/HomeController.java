
package upeu.edu.pe.credito.controller;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {

    @GetMapping({"/", "/inicio"})
    public String inicio(Authentication authentication) {

        if (authentication == null || !authentication.isAuthenticated()) {
            return "auth/login";
        }

        boolean esAdmin = authentication.getAuthorities()
                .stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));

        if (esAdmin) {
            return "redirect:/admin/dashboard";
        }

        return "redirect:/creditos/mis-creditos";
    }
}
