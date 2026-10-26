package upeu.edu.pe.credito.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import upeu.edu.pe.credito.entity.SuscripcionPush;
import upeu.edu.pe.credito.entity.Usuario;

import java.util.List;
import java.util.Optional;

public interface SuscripcionPushRepository
        extends JpaRepository<SuscripcionPush, Long> {

    Optional<SuscripcionPush> findByEndpoint(String endpoint);

    List<SuscripcionPush> findByUsuario(Usuario usuario);

    void deleteByEndpoint(String endpoint);
}
