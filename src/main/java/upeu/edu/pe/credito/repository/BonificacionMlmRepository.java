
package upeu.edu.pe.credito.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import upeu.edu.pe.credito.entity.BonificacionMlm;
import upeu.edu.pe.credito.entity.Credito;
import upeu.edu.pe.credito.entity.Usuario;

import java.math.BigDecimal;
import java.util.List;

public interface BonificacionMlmRepository
        extends JpaRepository<BonificacionMlm, Long> {

    List<BonificacionMlm> findByBeneficiarioOrderByFechaAcreditacionDesc(
            Usuario beneficiario
    );

    boolean existsByCredito(Credito credito);

    // Nuevo: total histórico de bonos de un beneficiario.
    @Query("""
        SELECT COALESCE(SUM(b.importe), 0)
        FROM BonificacionMlm b
        WHERE b.beneficiario.id = :usuarioId
        """)
    BigDecimal sumarBonosPorBeneficiario(
            @Param("usuarioId") Long usuarioId
    );
}
