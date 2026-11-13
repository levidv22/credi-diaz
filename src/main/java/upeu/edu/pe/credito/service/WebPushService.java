package upeu.edu.pe.credito.service;

import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import upeu.edu.pe.credito.entity.*;
import upeu.edu.pe.credito.repository.NotificacionRepository;
import upeu.edu.pe.credito.repository.SuscripcionPushRepository;
import upeu.edu.pe.credito.repository.UsuarioRepository;

import java.util.List;

@Service
public class NotificacionServiceImpl implements NotificacionService {

    private static final Logger log =
            LoggerFactory.getLogger(NotificacionServiceImpl.class);

    private final NotificacionRepository notificacionRepository;
    private final UsuarioRepository usuarioRepository;
    private final SuscripcionPushRepository suscripcionRepository;
    private final WebPushService webPushService;

    public NotificacionServiceImpl(
            NotificacionRepository notificacionRepository,
            UsuarioRepository usuarioRepository,
            SuscripcionPushRepository suscripcionRepository,
            WebPushService webPushService) {

        this.notificacionRepository = notificacionRepository;
        this.usuarioRepository = usuarioRepository;
        this.suscripcionRepository = suscripcionRepository;
        this.webPushService = webPushService;
    }

    @Override
    @Transactional
    public Notificacion crear(
            Usuario destinatario,
            String titulo,
            String mensaje,
            String url) {

        if (destinatario == null) {
            throw new IllegalArgumentException(
                    "El destinatario de la notificación es obligatorio."
            );
        }

        Notificacion notificacion = notificacionRepository.save(
                new Notificacion(destinatario, titulo, mensaje, url)
        );

        log.info(
                "Notificación interna creada. Usuario ID: {}. Título: {}",
                destinatario.getId(),
                titulo
        );

        try {
            List<SuscripcionPush> suscripciones =
                    suscripcionRepository.findByUsuario(destinatario);

            log.info(
                    "Se encontraron {} suscripciones Push para el usuario ID: {}",
                    suscripciones.size(),
                    destinatario.getId()
            );

            for (SuscripcionPush suscripcion : suscripciones) {
                webPushService.enviar(
                        suscripcion,
                        titulo,
                        mensaje,
                        url
                );
            }

        } catch (Exception e) {
            log.error(
                    "Error procesando las suscripciones Push del usuario ID: {}",
                    destinatario.getId(),
                    e
            );
        }

        return notificacion;
    }

    @Override
    @Transactional
    public void notificarAdministradores(
            String titulo,
            String mensaje,
            String url) {

        List<Usuario> administradores =
                usuarioRepository.findAll().stream()
                        .filter(u -> u.getRol() == Rol.ADMIN)
                        .toList();

        log.info(
                "Enviando notificación a {} administradores.",
                administradores.size()
        );

        administradores.forEach(admin ->
                crear(admin, titulo, mensaje, url)
        );
    }

    @Override
    public Page<Notificacion> listar(Usuario usuario, int pagina) {
        return notificacionRepository.findByUsuarioOrderByFechaCreacionDesc(
                usuario,
                PageRequest.of(
                        Math.max(0, pagina),
                        20,
                        Sort.by(Sort.Direction.DESC, "fechaCreacion")
                )
        );
    }

    @Override
    public long contarNoLeidas(Usuario usuario) {
        return notificacionRepository.countByUsuarioAndLeidaFalse(usuario);
    }

    @Override
    @Transactional
    public void marcarLeida(Long id, Usuario usuario) {

        Notificacion n = notificacionRepository.findByIdAndUsuario(id, usuario)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Notificación no encontrada."
                ));

        n.setLeida(true);
        notificacionRepository.save(n);
    }

    @Override
    @Transactional
    public void marcarTodasLeidas(Usuario usuario) {

        notificacionRepository.findByUsuarioOrderByFechaCreacionDesc(
                        usuario,
                        PageRequest.of(0, 1000)
                )
                .forEach(n -> n.setLeida(true));
    }
}
