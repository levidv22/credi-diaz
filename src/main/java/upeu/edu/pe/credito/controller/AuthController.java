package upeu.edu.pe.credito.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class AuthController {

    @GetMapping("/login")
    public String mostrarLogin() {
        return "auth/login"; // Apunta a src/main/resources/templates/auth/login.html
    }
}
