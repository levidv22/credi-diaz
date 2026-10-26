package upeu.edu.pe.credito.service;

import org.springframework.data.domain.Page;
import upeu.edu.pe.credito.entity.Notificacion;
import upeu.edu.pe.credito.entity.Usuario;

public interface NotificacionService {

    Notificacion crear(
            Usuario destinatario,
            String titulo,
            String mensaje,
            String url
    );

    void notificarAdministradores(
            String titulo,
            String mensaje,
            String url
    );

    Page<Notificacion> listar(Usuario usuario, int pagina);

    long contarNoLeidas(Usuario usuario);

    void marcarLeida(Long id, Usuario usuario);

    void marcarTodasLeidas(Usuario usuario);
}
