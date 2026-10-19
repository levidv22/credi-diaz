package upeu.edu.pe.credito.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import upeu.edu.pe.credito.entity.*;
import upeu.edu.pe.credito.repository.AmortizacionRepository;
import upeu.edu.pe.credito.repository.CreditoRepository;
import upeu.edu.pe.credito.repository.GarantiaReferidoRepository;
import upeu.edu.pe.credito.repository.PagoRepository;
import upeu.edu.pe.credito.service.UsuarioService;

import java.security.Principal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/notificaciones")
public class NotificacionController {
    private final UsuarioService usuarioService;
    private final CreditoRepository creditoRepository;
    private final PagoRepository pagoRepository;
    private final AmortizacionRepository amortizacionRepository;
    private final GarantiaReferidoRepository garantiaReferidoRepository;

    public NotificacionController(UsuarioService usuarioService,
                                  CreditoRepository creditoRepository,
                                  PagoRepository pagoRepository,
                                  AmortizacionRepository amortizacionRepository,
                                  GarantiaReferidoRepository garantiaReferidoRepository) {
        this.usuarioService = usuarioService;
        this.creditoRepository = creditoRepository;
        this.pagoRepository = pagoRepository;
        this.amortizacionRepository = amortizacionRepository;
        this.garantiaReferidoRepository = garantiaReferidoRepository;
    }

    @GetMapping("/navegador")
    public Map<String, Object> navegador(Principal principal) {
        Usuario usuario = usuarioService.findByDni(principal.getName().trim())
                .orElseThrow(() -> new IllegalStateException("Usuario autenticado no encontrado."));

        if (usuario.getRol() == Rol.ADMIN) {
            long solicitudes = creditoRepository.findByEstado(EstadoCredito.PENDIENTE_APROBACION).size();
            long pagos = pagoRepository.findByEstadoInOrderByFechaPagoAsc(List.of(EstadoPago.PENDIENTE_APROBACION, EstadoPago.REVISION_MANUAL)).size();
            long amortizaciones = amortizacionRepository.countByEstadoIn(List.of(EstadoPago.PENDIENTE_APROBACION, EstadoPago.REVISION_MANUAL));
            long garantias = garantiaReferidoRepository.countByEstado(EstadoGarantia.PENDIENTE_ACEPTACION);
            return Map.of(
                    "rol", "ADMIN",
                    "solicitudes", solicitudes,
                    "pagos", pagos,
                    "amortizaciones", amortizaciones,
                    "garantias", garantias
            );
        }

        long garantias = garantiaReferidoRepository.findByGaranteAndEstado(usuario, EstadoGarantia.PENDIENTE_ACEPTACION).size();
        return Map.of(
                "rol", "CLIENTE",
                "garantias", garantias
        );
    }
}
