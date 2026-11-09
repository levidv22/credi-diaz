package upeu.edu.pe.credito.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import upeu.edu.pe.credito.entity.EstadoReembolsoAhorro;
import upeu.edu.pe.credito.entity.SolicitudReembolsoAhorro;
import upeu.edu.pe.credito.entity.Usuario;

import java.util.List;
import java.util.Optional;

public interface SolicitudReembolsoAhorroRepository
        extends JpaRepository<SolicitudReembolsoAhorro, Long> {

    List<SolicitudReembolsoAhorro> findAllByOrderByFechaSolicitudDesc();

    List<SolicitudReembolsoAhorro> findByEstadoOrderByFechaSolicitudAsc(
            EstadoReembolsoAhorro estado);

    Optional<SolicitudReembolsoAhorro> findByIdAndUsuario(
            Long id, Usuario usuario);

    boolean existsByUsuarioAndEstado(
            Usuario usuario, EstadoReembolsoAhorro estado);
}
