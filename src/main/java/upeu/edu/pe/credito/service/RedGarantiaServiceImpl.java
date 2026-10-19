package upeu.edu.pe.credito.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import upeu.edu.pe.credito.entity.*;
import upeu.edu.pe.credito.repository.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

@Service
public class RedGarantiaServiceImpl implements RedGarantiaService {
    private final CreditoRepository creditoRepository;
    private final CuotaCreditoRepository cuotaCreditoRepository;
    private final GarantiaReferidoRepository garantiaReferidoRepository;
    private final NotificacionMoraRepository notificacionMoraRepository;
    private final UsuarioRepository usuarioRepository;
    private final PagoRepository pagoRepository;

    public RedGarantiaServiceImpl(CreditoRepository creditoRepository,
                                  CuotaCreditoRepository cuotaCreditoRepository,
                                  GarantiaReferidoRepository garantiaReferidoRepository,
                                  NotificacionMoraRepository notificacionMoraRepository,
                                  UsuarioRepository usuarioRepository,
                                  PagoRepository pagoRepository) {
        this.creditoRepository = creditoRepository;
        this.cuotaCreditoRepository = cuotaCreditoRepository;
        this.garantiaReferidoRepository = garantiaReferidoRepository;
        this.notificacionMoraRepository = notificacionMoraRepository;
        this.usuarioRepository = usuarioRepository;
        this.pagoRepository = pagoRepository;
    }

    @Override
    @Scheduled(cron = "0 0 1 * * ?")
    @Transactional
    public void ejecutarProcesoMoraDiario() {
        LocalDate hoy = LocalDate.now();
        List<CuotaCredito> vencidas = cuotaCreditoRepository.findByPagadoFalseAndFechaVencimientoBefore(hoy);
        for (CuotaCredito cuota : vencidas) procesarCuotaVencida(cuota, hoy);
        procesarNotificacionesVencidas(hoy);
    }

    private void procesarCuotaVencida(CuotaCredito cuota, LocalDate hoy) {
        Credito credito = cuota.getCredito();
        if (credito.getEstado() != EstadoCredito.ACTIVO && credito.getEstado() != EstadoCredito.EN_MORA) return;
        Usuario deudor = credito.getUsuario();
        int anterior = cuota.getDiasMora();
        int actual = (int) ChronoUnit.DAYS.between(cuota.getFechaVencimiento(), hoy);
        if (actual <= anterior) return;

        cuota.setDiasMora(actual);
        cuota.setPenalizacionMoraAcumulada(cuota.getTarifaMoraDiaria().multiply(BigDecimal.valueOf(actual)).setScale(2, RoundingMode.HALF_UP));
        cuotaCreditoRepository.save(cuota);

        int bloquesAntes = anterior / 3;
        int bloquesAhora = actual / 3;
        int bloquesNuevos = Math.max(0, bloquesAhora - bloquesAntes);
        if (bloquesNuevos > 0) {
            BigDecimal descuento = BigDecimal.valueOf(bloquesNuevos * 100L);
            deudor.setLimiteCreditoPermitido(deudor.getLimiteCreditoPermitido().subtract(descuento).max(BigDecimal.ZERO));
            usuarioRepository.save(deudor);
        }

        if (credito.getEstado() == EstadoCredito.ACTIVO) {
            credito.setEstado(EstadoCredito.EN_MORA);
            creditoRepository.save(credito);
            deudor.setEstado(EstadoUsuario.ELIMINADO_POR_MORA);
            usuarioRepository.save(deudor);
            ejecutarGarantia(credito, hoy);
        } else {
            actualizarNotificacionExistente(credito);
        }
    }

    private void ejecutarGarantia(Credito credito, LocalDate hoy) {
        Optional<GarantiaReferido> opt = garantiaReferidoRepository.findByCredito(credito);
        if (opt.isEmpty()) return;
        GarantiaReferido garantia = opt.get();
        if (garantia.getEstado() != EstadoGarantia.ACTIVA) return;

        garantia.setEstado(EstadoGarantia.EJECUTADA);
        garantia.setPorcentajeResponsabilidad(BigDecimal.ONE);
        BigDecimal saldo = saldoPendiente(credito);
        garantia.setMontoDeudaGarantizada(saldo);
        garantiaReferidoRepository.save(garantia);

        Usuario garante = garantia.getGarante();
        garante.setScoreCrediticio(Math.max(0, garante.getScoreCrediticio() - 20));
        garante.setLimiteDirectos(2);
        garante.setConteoReferidosMorosos(garante.getConteoReferidosMorosos() + 1);
        if (garante.getConteoReferidosMorosos() >= 3) garante.setEstado(EstadoUsuario.ELIMINADO_POR_MORA);
        usuarioRepository.save(garante);

        NotificacionMora n = new NotificacionMora();
        n.setGarante(garante);
        n.setGarantia(garantia);
        n.setMontoAPagar(saldo);
        n.setFechaNotificacion(hoy);
        n.setFechaLimitePago(hoy.plusDays(7));
        n.setResuelto(false);
        notificacionMoraRepository.save(n);
    }

    private void actualizarNotificacionExistente(Credito credito) {
        garantiaReferidoRepository.findByCredito(credito).ifPresent(garantia ->
                notificacionMoraRepository.findByGaranteAndResueltoFalse(garantia.getGarante()).stream()
                        .filter(n -> n.getGarantia() != null && n.getGarantia().getId().equals(garantia.getId()))
                        .findFirst().ifPresent(n -> {
                            n.setMontoAPagar(saldoPendiente(credito));
                            notificacionMoraRepository.save(n);
                        }));
    }

    private BigDecimal saldoPendiente(Credito credito) {
        return credito.getCuotas().stream().filter(q -> !q.isPagado()).map(CuotaCredito::getMontoTotalAPagar)
                .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
    }

    @Scheduled(cron = "0 15 1 * * ?")
    @Transactional
    @Override
    public void ejecutarVencimientoGarantias() {
        LocalDate hoy = LocalDate.now();
        procesarNotificacionesVencidas(hoy);
    }

    private void procesarNotificacionesVencidas(LocalDate hoy) {
        notificacionMoraRepository.findByResueltoFalseAndFechaLimitePagoBefore(hoy).forEach(n -> {
            Usuario garante = n.getGarante();
            garante.setEstado(EstadoUsuario.ELIMINADO_POR_MORA);
            usuarioRepository.save(garante);
        });
    }

    @Override
    @Transactional
    public NotificacionMora cobrarGarantiaAGarante(Long notificacionId, Long garanteId) {
        NotificacionMora n = notificacionMoraRepository.findById(notificacionId)
                .orElseThrow(() -> new IllegalArgumentException("Notificación de mora no encontrada."));
        if (n.isResuelto()) throw new IllegalStateException("La notificación ya fue resuelta.");
        if (!n.getGarante().getId().equals(garanteId)) throw new IllegalStateException("No tiene autorización para cubrir esta garantía.");

        Usuario garante = n.getGarante();
        BigDecimal pendiente = n.getMontoAPagar();
        boolean usoAhorro = false;

        BigDecimal desdeBonos = garante.getSaldoBonos().min(pendiente);
        garante.setSaldoBonos(garante.getSaldoBonos().subtract(desdeBonos));
        pendiente = pendiente.subtract(desdeBonos);

        if (pendiente.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal desdeAhorro = garante.getSaldoAhorro().min(pendiente);
            if (desdeAhorro.compareTo(BigDecimal.ZERO) > 0) usoAhorro = true;
            garante.setSaldoAhorro(garante.getSaldoAhorro().subtract(desdeAhorro));
            pendiente = pendiente.subtract(desdeAhorro);
        }

        if (usoAhorro) {
            // El documento indica que al descontarse el capital de ahorro el garante queda inactivo.
            garante.setEstado(EstadoUsuario.INACTIVO);
        }

        if (pendiente.compareTo(BigDecimal.ZERO) <= 0) {
            cubrirCreditoConGarantia(n.getGarantia());
            n.setMontoAPagar(BigDecimal.ZERO);
            n.setResuelto(true);
        } else {
            n.setMontoAPagar(pendiente.setScale(2, RoundingMode.HALF_UP));
            if (LocalDate.now().isAfter(n.getFechaLimitePago())) garante.setEstado(EstadoUsuario.ELIMINADO_POR_MORA);
        }
        usuarioRepository.save(garante);
        return notificacionMoraRepository.save(n);
    }

    private void cubrirCreditoConGarantia(GarantiaReferido garantia) {
        Credito credito = garantia.getCredito();
        for (CuotaCredito cuota : credito.getCuotas()) {
            if (cuota.isPagado()) continue;
            BigDecimal total = cuota.getMontoTotalAPagar();
            cuota.setPagado(true);
            cuota.setFechaPagoReal(LocalDate.now());
            cuotaCreditoRepository.save(cuota);
            Pago pago = new Pago();
            pago.setCuota(cuota);
            pago.setUsuario(garantia.getGarante());
            pago.setMonto(total);
            pago.setFechaPago(LocalDateTime.now());
            pago.setMetodo(MetodoPago.GARANTIA);
            pago.setOrigen(OrigenPago.GARANTE);
            pagoRepository.save(pago);
        }
        credito.setEstado(EstadoCredito.EJECUTADO_GARANTIA);
        creditoRepository.save(credito);
        garantia.setEstado(EstadoGarantia.LIQUIDADA);
        garantia.setMontoDeudaGarantizada(BigDecimal.ZERO);
        garantiaReferidoRepository.save(garantia);
    }

    @Override
    public List<NotificacionMora> listarNotificacionesPendientesPorGarante(Long garanteId) {
        Usuario garante = usuarioRepository.findById(garanteId).orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado."));
        return notificacionMoraRepository.findByGaranteAndResueltoFalse(garante);
    }

    @Override
    public List<GarantiaReferido> listarGarantiasPendientesPorGarante(Long garanteId) {
        Usuario garante = usuarioRepository.findById(garanteId)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado."));
        return garantiaReferidoRepository.findByGaranteAndEstado(garante, EstadoGarantia.PENDIENTE_ACEPTACION);
    }

    @Override
    @Transactional
    public GarantiaReferido aceptarGarantia(Long garantiaId, Long garanteId) {
        GarantiaReferido garantia = garantiaReferidoRepository.findById(garantiaId)
                .orElseThrow(() -> new IllegalArgumentException("Solicitud de garantía no encontrada."));
        if (!garantia.getGarante().getId().equals(garanteId)) {
            throw new IllegalStateException("No tiene autorización para responder esta garantía.");
        }
        if (garantia.getEstado() != EstadoGarantia.PENDIENTE_ACEPTACION) {
            throw new IllegalStateException("Esta solicitud de garantía ya fue procesada.");
        }
        if (garantia.getCredito().getEstado() != EstadoCredito.PENDIENTE_APROBACION) {
            throw new IllegalStateException("La solicitud de crédito ya no está pendiente de aprobación.");
        }
        garantia.setContratoFirmado(true);
        garantia.setEstado(EstadoGarantia.ACTIVA);
        garantia.getCredito().setContratoGarantiaAceptado(true);
        creditoRepository.save(garantia.getCredito());
        return garantiaReferidoRepository.save(garantia);
    }

    @Override
    @Transactional
    public GarantiaReferido rechazarGarantia(Long garantiaId, Long garanteId) {
        GarantiaReferido garantia = garantiaReferidoRepository.findById(garantiaId)
                .orElseThrow(() -> new IllegalArgumentException("Solicitud de garantía no encontrada."));
        if (!garantia.getGarante().getId().equals(garanteId)) {
            throw new IllegalStateException("No tiene autorización para responder esta garantía.");
        }
        if (garantia.getEstado() != EstadoGarantia.PENDIENTE_ACEPTACION) {
            throw new IllegalStateException("Esta solicitud de garantía ya fue procesada.");
        }
        garantia.setContratoFirmado(false);
        garantia.setEstado(EstadoGarantia.RECHAZADA);
        garantia.getCredito().setContratoGarantiaAceptado(false);
        creditoRepository.save(garantia.getCredito());
        return garantiaReferidoRepository.save(garantia);
    }
}
