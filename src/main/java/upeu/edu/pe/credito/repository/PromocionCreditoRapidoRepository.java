package upeu.edu.pe.credito.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import upeu.edu.pe.credito.entity.PromocionCreditoRapido;
import java.util.List;

public interface PromocionCreditoRapidoRepository extends JpaRepository<PromocionCreditoRapido,Long> {
 List<PromocionCreditoRapido> findByActivaTrueOrderByFechaCreacionDesc();
}
