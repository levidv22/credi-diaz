package upeu.edu.pe.credito.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import upeu.edu.pe.credito.entity.*;
import java.util.List;
import java.util.Optional;

public interface GarantiaReferidoRepository extends JpaRepository<GarantiaReferido, Long> {
    Optional<GarantiaReferido> findByDeudorAndEstado(Usuario deudor, EstadoGarantia estado);
    Optional<GarantiaReferido> findByCredito(Credito credito);
    List<GarantiaReferido> findByDeudor(Usuario deudor);
    List<GarantiaReferido> findByGarante(Usuario garante);
    List<GarantiaReferido> findByGaranteAndEstado(Usuario garante, EstadoGarantia estado);
    long countByEstado(EstadoGarantia estado);
}
