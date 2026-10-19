package upeu.edu.pe.credito.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import upeu.edu.pe.credito.entity.Pago;
import upeu.edu.pe.credito.entity.CuotaCredito;
import java.util.List;
import upeu.edu.pe.credito.entity.EstadoPago;

public interface PagoRepository extends JpaRepository<Pago, Long> {
    List<Pago> findByCuotaOrderByFechaPagoDesc(CuotaCredito cuota);
    List<Pago> findByEstadoOrderByFechaPagoAsc(EstadoPago estado);
    List<Pago> findByEstadoInOrderByFechaPagoAsc(java.util.Collection<EstadoPago> estados);
    boolean existsByCuotaAndEstadoIn(CuotaCredito cuota, java.util.Collection<EstadoPago> estados);
}
