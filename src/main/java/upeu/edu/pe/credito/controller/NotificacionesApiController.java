package upeu.edu.pe.credito.controller;

import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import upeu.edu.pe.credito.dto.SuscripcionPushDTO;
import upeu.edu.pe.credito.entity.*;
import upeu.edu.pe.credito.repository.SuscripcionPushRepository;
import upeu.edu.pe.credito.service.NotificacionService;
import upeu.edu.pe.credito.service.UsuarioService;

import java.util.Map;

@RestController
@RequestMapping("/api/notificaciones")
public class NotificacionesApiController {

    private final UsuarioService usuarioService;
    private final NotificacionService notificacionService;
    private final SuscripcionPushRepository suscripcionRepository;

    public NotificacionesApiController(
            UsuarioService usuarioService,
            NotificacionService notificacionService,
            SuscripcionPushRepository suscripcionRepository) {
        this.usuarioService = usuarioService;
        this.notificacionService = notificacionService;
        this.suscripcionRepository = suscripcionRepository;
    }

    private Usuario usuarioActual(Authentication auth) {
        return usuarioService.findByDni(auth.getName().trim())
                .orElseThrow(() -> new IllegalStateException(
                        "Usuario autenticado no encontrado."));
    }

    @GetMapping
    public Map<String, Object> listar(
            Authentication auth,
            @RequestParam(defaultValue = "0") int pagina) {

        Usuario usuario = usuarioActual(auth);
        Page<Notificacion> datos = notificacionService.listar(usuario, pagina);

        return Map.of(
                "contenido", datos.getContent().stream().map(n -> Map.of(
                        "id", n.getId(),
                        "titulo", n.getTitulo(),
                        "mensaje", n.getMensaje(),
                        "url", n.getUrl() == null ? "/" : n.getUrl(),
                        "leida", n.isLeida(),
                        "fecha", n.getFechaCreacion().toString()
                )).toList(),
                "noLeidas", notificacionService.contarNoLeidas(usuario),
                "pagina", datos.getNumber(),
                "totalPaginas", datos.getTotalPages()
        );
    }

    @PostMapping("/{id}/leida")
    public ResponseEntity<Void> marcarLeida(
            Authentication auth,
            @PathVariable Long id) {
        notificacionService.marcarLeida(id, usuarioActual(auth));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/leidas")
    public ResponseEntity<Void> marcarTodas(Authentication auth) {
        notificacionService.marcarTodasLeidas(usuarioActual(auth));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/suscripcion")
    @Transactional
    public ResponseEntity<Void> registrarSuscripcion(
            Authentication auth,
            @RequestBody SuscripcionPushDTO dto) {

        if (dto == null || dto.endpoint() == null || dto.endpoint().isBlank()
                || dto.keys() == null || dto.keys().p256dh() == null
                || dto.keys().auth() == null) {
            return ResponseEntity.badRequest().build();
        }

        Usuario usuario = usuarioActual(auth);

        SuscripcionPush suscripcion = suscripcionRepository
                .findByEndpoint(dto.endpoint())
                .orElseGet(SuscripcionPush::new);

        suscripcion.setUsuario(usuario);
        suscripcion.setEndpoint(dto.endpoint());
        suscripcion.setClavePublica(dto.keys().p256dh());
        suscripcion.setClaveAuth(dto.keys().auth());

        suscripcionRepository.save(suscripcion);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/suscripcion")
    @Transactional
    public ResponseEntity<Void> eliminarSuscripcion(
            Authentication auth,
            @RequestBody Map<String, String> body) {

        String endpoint = body.get("endpoint");
        if (endpoint == null || endpoint.isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        Usuario usuario = usuarioActual(auth);

        suscripcionRepository.findByEndpoint(endpoint).ifPresent(s -> {
            if (s.getUsuario().getId().equals(usuario.getId())) {
                suscripcionRepository.delete(s);
            }
        });

        return ResponseEntity.noContent().build();
    }
}
