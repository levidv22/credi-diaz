package upeu.edu.pe.credito.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import upeu.edu.pe.credito.entity.Notificacion;
import upeu.edu.pe.credito.entity.Usuario;

public interface NotificacionRepository
        extends JpaRepository<Notificacion, Long> {

    Page<Notificacion> findByUsuarioOrderByFechaCreacionDesc(
            Usuario usuario, Pageable pageable);

    long countByUsuarioAndLeidaFalse(Usuario usuario);

    java.util.Optional<Notificacion> findByIdAndUsuario(
            Long id, Usuario usuario);
}
