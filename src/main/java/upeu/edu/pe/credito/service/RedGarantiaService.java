package upeu.edu.pe.credito.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;
import upeu.edu.pe.credito.entity.GarantiaReferido;
import upeu.edu.pe.credito.entity.NotificacionMora;
import java.util.List;

public interface RedGarantiaService {
    void ejecutarProcesoMoraDiario();

    @Scheduled(cron = "0 15 1 * * ?")
    @Transactional
    void ejecutarVencimientoGarantias();

    NotificacionMora cobrarGarantiaAGarante(Long notificacionId, Long garanteId);
    List<NotificacionMora> listarNotificacionesPendientesPorGarante(Long garanteId);
    List<GarantiaReferido> listarGarantiasPendientesPorGarante(Long garanteId);
    GarantiaReferido aceptarGarantia(Long garantiaId, Long garanteId);
    GarantiaReferido rechazarGarantia(Long garantiaId, Long garanteId);
}
