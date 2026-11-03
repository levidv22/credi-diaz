package upeu.edu.pe.credito.controller;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import upeu.edu.pe.credito.entity.Usuario;
import upeu.edu.pe.credito.service.TotpService;
import upeu.edu.pe.credito.service.UsuarioService;

import java.io.ByteArrayOutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.Base64;

@Controller
public class PasswordSecurityController {

    private static final String SESSION_PENDING_SECRET = "CREDI_DIAZ_TOTP_PENDING_SECRET";

    private final UsuarioService usuarioService;
    private final PasswordEncoder passwordEncoder;
    private final TotpService totpService;

    public PasswordSecurityController(UsuarioService usuarioService,
                                      PasswordEncoder passwordEncoder,
                                      TotpService totpService) {
        this.usuarioService = usuarioService;
        this.passwordEncoder = passwordEncoder;
        this.totpService = totpService;
    }

    @GetMapping("/usuarios/seguridad/contrasena")
    public String mostrarSeguridad(Principal principal, Model model) {
        Usuario usuario = usuarioAutenticado(principal);
        model.addAttribute("totpEnabled", usuario.isTotpEnabled());
        return "usuario/seguridad-contrasena";
    }

    @GetMapping("/usuarios/seguridad/2fa/configurar")
    public String mostrarConfiguracion2FA(Principal principal,
                                          HttpSession session,
                                          Model model,
                                          RedirectAttributes ra) {
        Usuario usuario = usuarioAutenticado(principal);
        if (usuario.isTotpEnabled()) {
            ra.addFlashAttribute("mensajeInfo", "Google Authenticator ya está activado en tu cuenta.");
            return "redirect:/usuarios/seguridad/contrasena";
        }

        String secret = (String) session.getAttribute(SESSION_PENDING_SECRET);
        if (secret == null || secret.isBlank()) {
            secret = totpService.generarSecreto();
            session.setAttribute(SESSION_PENDING_SECRET, secret);
        }

        String label = "CREDI-DIAZ:" + usuario.getDni();
        String otpauth = "otpauth://totp/"
                + URLEncoder.encode(label, StandardCharsets.UTF_8)
                + "?secret=" + secret
                + "&issuer=" + URLEncoder.encode("CREDI-DIAZ", StandardCharsets.UTF_8)
                + "&algorithm=SHA1&digits=6&period=30";

        try {
            QRCodeWriter writer = new QRCodeWriter();
            BitMatrix matrix = writer.encode(otpauth, BarcodeFormat.QR_CODE, 260, 260);
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(matrix, "PNG", output);
            String qr = Base64.getEncoder().encodeToString(output.toByteArray());

            model.addAttribute("qrDataUri", "data:image/png;base64," + qr);
            model.addAttribute("manualSecret", secret);
            return "usuario/configurar-2fa";
        } catch (Exception ex) {
            throw new IllegalStateException("No se pudo generar el código QR de Google Authenticator.", ex);
        }
    }

    @PostMapping("/usuarios/seguridad/2fa/activar")
    public String activar2FA(@RequestParam String codigo,
                             Principal principal,
                             HttpSession session,
                             RedirectAttributes ra) {
        Usuario usuario = usuarioAutenticado(principal);
        String pendingSecret = (String) session.getAttribute(SESSION_PENDING_SECRET);

        if (usuario.isTotpEnabled()) {
            ra.addFlashAttribute("mensajeInfo", "Google Authenticator ya está activado.");
            return "redirect:/usuarios/seguridad/contrasena";
        }
        if (pendingSecret == null || !totpService.validarCodigo(pendingSecret, codigo)) {
            ra.addFlashAttribute("mensajeError", "El código no es válido. Revisa Google Authenticator e inténtalo nuevamente.");
            return "redirect:/usuarios/seguridad/2fa/configurar";
        }

        usuario.setTotpSecret(pendingSecret);
        usuario.setTotpEnabled(true);
        usuarioService.save(usuario);
        session.removeAttribute(SESSION_PENDING_SECRET);

        ra.addFlashAttribute("mensajeExito", "Google Authenticator quedó activado correctamente.");
        return "redirect:/usuarios/seguridad/contrasena";
    }

    @PostMapping("/usuarios/seguridad/contrasena")
    public String cambiarContrasena(@RequestParam String contrasenaActual,
                                    @RequestParam String codigo,
                                    @RequestParam String nuevaContrasena,
                                    @RequestParam String confirmarContrasena,
                                    Principal principal,
                                    RedirectAttributes ra) {
        Usuario usuario = usuarioAutenticado(principal);

        if (!usuario.isTotpEnabled() || usuario.getTotpSecret() == null) {
            ra.addFlashAttribute("mensajeError", "Primero debes configurar Google Authenticator.");
            return "redirect:/usuarios/seguridad/2fa/configurar";
        }
        if (!passwordEncoder.matches(contrasenaActual, usuario.getPassword())) {
            ra.addFlashAttribute("mensajeError", "La contraseña actual es incorrecta.");
            return "redirect:/usuarios/seguridad/contrasena";
        }
        if (!totpService.validarCodigo(usuario.getTotpSecret(), codigo)) {
            ra.addFlashAttribute("mensajeError", "El código de Google Authenticator no es válido.");
            return "redirect:/usuarios/seguridad/contrasena";
        }

        String error = validarNuevaContrasena(nuevaContrasena, confirmarContrasena);
        if (error != null) {
            ra.addFlashAttribute("mensajeError", error);
            return "redirect:/usuarios/seguridad/contrasena";
        }

        usuario.setPassword(passwordEncoder.encode(nuevaContrasena));
        usuarioService.save(usuario);
        ra.addFlashAttribute("mensajeExito", "Tu contraseña se actualizó correctamente.");
        return "redirect:/usuarios/seguridad/contrasena";
    }

    @GetMapping("/recuperar-contrasena")
    public String mostrarRecuperacion() {
        return "auth/recuperar-contrasena";
    }

    @PostMapping("/recuperar-contrasena")
    public String recuperarContrasena(@RequestParam String dni,
                                      @RequestParam String codigo,
                                      @RequestParam String nuevaContrasena,
                                      @RequestParam String confirmarContrasena,
                                      RedirectAttributes ra) {
        String mensajeGenerico = "No se pudo completar la recuperación. Verifica tus datos, el código y que Google Authenticator esté configurado.";

        String error = validarNuevaContrasena(nuevaContrasena, confirmarContrasena);
        if (error != null) {
            ra.addFlashAttribute("mensajeError", error);
            return "redirect:/recuperar-contrasena";
        }

        Usuario usuario = usuarioService.findByDni(dni == null ? "" : dni.trim()).orElse(null);
        if (usuario == null || !usuario.isTotpEnabled()
                || usuario.getTotpSecret() == null
                || !totpService.validarCodigo(usuario.getTotpSecret(), codigo)) {
            ra.addFlashAttribute("mensajeError", mensajeGenerico);
            return "redirect:/recuperar-contrasena";
        }

        usuario.setPassword(passwordEncoder.encode(nuevaContrasena));
        usuarioService.save(usuario);

        ra.addFlashAttribute("mensajeExito", "Tu contraseña se recuperó correctamente. Ya puedes iniciar sesión.");
        return "redirect:/login?recuperada";
    }

    private Usuario usuarioAutenticado(Principal principal) {
        if (principal == null) {
            throw new IllegalStateException("Debes iniciar sesión para realizar esta operación.");
        }
        return usuarioService.findByDni(principal.getName().trim())
                .orElseThrow(() -> new IllegalStateException("No se encontró el usuario autenticado."));
    }

    private String validarNuevaContrasena(String nueva, String confirmacion) {
        if (nueva == null || nueva.length() < 10) {
            return "La nueva contraseña debe tener al menos 10 caracteres.";
        }
        if (!nueva.matches(".*[A-Z].*") || !nueva.matches(".*[a-z].*")
                || !nueva.matches(".*\\d.*")) {
            return "La contraseña debe incluir mayúsculas, minúsculas y números.";
        }
        if (!nueva.equals(confirmacion)) {
            return "Las contraseñas no coinciden.";
        }
        return null;
    }
}
