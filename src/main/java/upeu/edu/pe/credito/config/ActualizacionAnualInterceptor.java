package upeu.edu.pe.credito.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import upeu.edu.pe.credito.entity.Rol;
import upeu.edu.pe.credito.entity.Usuario;
import upeu.edu.pe.credito.service.UsuarioService;

import java.time.LocalDate;

@Component
public class ActualizacionAnualInterceptor implements HandlerInterceptor {
    private final UsuarioService usuarioService;

    public ActualizacionAnualInterceptor(UsuarioService usuarioService) {
        this.usuarioService = usuarioService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String uri = request.getRequestURI();
        if (uri.equals(request.getContextPath() + "/usuarios/perfil") || uri.equals(request.getContextPath() + "/usuarios/perfil/actualizar")
                || uri.equals(request.getContextPath() + "/usuarios/register") || uri.startsWith(request.getContextPath() + "/usuarios/registro/"))
            return true;
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) return true;
        Usuario u = usuarioService.findByDni(auth.getName().trim()).orElse(null);
        if (u == null || u.getRol() == Rol.ADMIN || !requiereActualizacion(u)) return true;
        response.sendRedirect(request.getContextPath() + "/usuarios/perfil?actualizacionObligatoria=true");
        return false;
    }

    private boolean requiereActualizacion(Usuario u) {
        LocalDate h = LocalDate.now();
        return u.getFechaActualizacionDatos() == null || u.getFechaActualizacionDatos().plusYears(1).isBefore(h)
                || u.getFechaActualizacionDocumentos() == null || u.getFechaActualizacionDocumentos().plusYears(1).isBefore(h);
    }
}
