package upeu.edu.pe.credito.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import upeu.edu.pe.credito.entity.*;
import java.util.List;

public interface CreditoRepository extends JpaRepository<Credito, Long> {
    List<Credito> findByUsuario(Usuario usuario);
    List<Credito> findByUsuarioAndEstado(Usuario usuario, EstadoCredito estado);
    List<Credito> findByEstado(EstadoCredito estado);
    List<Credito> findByUsuarioAndCreditoPromocionalTrue(Usuario usuario);
}
