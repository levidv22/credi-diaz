package upeu.edu.pe.credito.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import upeu.edu.pe.credito.entity.Amortizacion;
import upeu.edu.pe.credito.entity.Credito;
import upeu.edu.pe.credito.entity.EstadoPago;

import java.util.Collection;
import java.util.List;

public interface AmortizacionRepository extends JpaRepository<Amortizacion, Long> {
    List<Amortizacion> findByCreditoOrderByFechaSolicitudDesc(Credito credito);
    List<Amortizacion> findByEstadoInOrderByFechaSolicitudAsc(Collection<EstadoPago> estados);
    boolean existsByCreditoAndEstadoIn(Credito credito, Collection<EstadoPago> estados);
    long countByEstadoIn(Collection<EstadoPago> estados);
}
