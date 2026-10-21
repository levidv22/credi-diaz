package upeu.edu.pe.credito.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import upeu.edu.pe.credito.entity.AporteAhorro;
import upeu.edu.pe.credito.entity.EstadoAporteAhorro;
import upeu.edu.pe.credito.entity.Usuario;

import java.util.List;
import java.util.Optional;

public interface AporteAhorroRepository
        extends JpaRepository<AporteAhorro, Long> {

    List<AporteAhorro> findAllByOrderByFechaSolicitudDesc();

    List<AporteAhorro> findByEstadoOrderByFechaSolicitudAsc(
            EstadoAporteAhorro estado);

    List<AporteAhorro> findByUsuarioOrderByFechaSolicitudDesc(
            Usuario usuario);

    Optional<AporteAhorro> findByIdAndUsuario(
            Long id, Usuario usuario);

    boolean existsByUsuarioAndEstado(
            Usuario usuario, EstadoAporteAhorro estado);
}
