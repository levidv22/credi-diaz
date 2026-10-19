package upeu.edu.pe.credito.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import upeu.edu.pe.credito.entity.BonificacionMlm;
import upeu.edu.pe.credito.entity.Usuario;
import java.util.List;

public interface BonificacionMlmRepository extends JpaRepository<BonificacionMlm, Long> {
    List<BonificacionMlm> findByBeneficiarioOrderByFechaAcreditacionDesc(Usuario beneficiario);
}
