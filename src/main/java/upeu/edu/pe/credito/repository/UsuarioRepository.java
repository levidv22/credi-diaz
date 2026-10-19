package upeu.edu.pe.credito.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import upeu.edu.pe.credito.entity.*;
import java.util.Optional;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {
    Optional<Usuario> findByDni(String dni);
    Optional<Usuario> findByEmail(String email);
    Optional<Usuario> findByCodigoReferido(String codigoReferido);
    long countByPatrocinadorAndEstado(Usuario patrocinador, EstadoUsuario estado);
    long countByPatrocinador(Usuario patrocinador);
    boolean existsByRol(Rol rol);
}
