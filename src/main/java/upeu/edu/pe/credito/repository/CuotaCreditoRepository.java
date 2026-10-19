package upeu.edu.pe.credito.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import upeu.edu.pe.credito.entity.*;
import java.time.LocalDate;
import java.util.List;

public interface CuotaCreditoRepository extends JpaRepository<CuotaCredito, Long> {
    List<CuotaCredito> findByCreditoAndPagadoFalseOrderByNumeroCuotaAsc(Credito credito);
    List<CuotaCredito> findByPagadoFalseAndFechaVencimientoBefore(LocalDate fecha);
}
