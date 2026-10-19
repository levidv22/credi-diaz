package upeu.edu.pe.credito.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import upeu.edu.pe.credito.entity.*;
import java.time.LocalDate;
import java.util.List;

public interface NotificacionMoraRepository extends JpaRepository<NotificacionMora, Long> {
    List<NotificacionMora> findByGaranteAndResueltoFalse(Usuario garante);
    List<NotificacionMora> findByResueltoFalseAndFechaLimitePagoBefore(LocalDate fecha);
}
