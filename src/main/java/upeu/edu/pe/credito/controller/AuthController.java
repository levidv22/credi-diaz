
package upeu.edu.pe.credito.controller;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class AuthController {

    @GetMapping("/login")
    public String mostrarLogin(Authentication authentication) {

        if (authentication != null && authentication.isAuthenticated()) {

            boolean esAdmin = authentication.getAuthorities()
                    .stream()
                    .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));

            return esAdmin
                    ? "redirect:/admin/dashboard"
                    : "redirect:/creditos/mis-creditos";
        }

        return "auth/login";
    }
}
