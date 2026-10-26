package upeu.edu.pe.credito.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import upeu.edu.pe.credito.entity.*;
import upeu.edu.pe.credito.repository.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class CreditoServiceImpl implements CreditoService {
    private static final BigDecimal CIEN = new BigDecimal("100.00");
    private static final BigDecimal DIEZ_POR_CIENTO = new BigDecimal("0.10");

    private final CreditoRepository creditoRepository;
    private final CuotaCreditoRepository cuotaCreditoRepository;
    private final UsuarioRepository usuarioRepository;
    private final GarantiaReferidoRepository garantiaReferidoRepository;
    private final PagoRepository pagoRepository;
    private final AmortizacionRepository amortizacionRepository;
    private final ComprobanteVerificacionService verificador;
    private final BonificacionMlmRepository bonificacionMlmRepository;

    public CreditoServiceImpl(CreditoRepository creditoRepository,
                              CuotaCreditoRepository cuotaCreditoRepository,
                              UsuarioRepository usuarioRepository,
                              GarantiaReferidoRepository garantiaReferidoRepository,
                              PagoRepository pagoRepository,
                              AmortizacionRepository amortizacionRepository,
                              ComprobanteVerificacionService verificador,
                              BonificacionMlmRepository bonificacionMlmRepository) {
        this.creditoRepository = creditoRepository;
        this.cuotaCreditoRepository = cuotaCreditoRepository;
        this.usuarioRepository = usuarioRepository;
        this.garantiaReferidoRepository = garantiaReferidoRepository;
        this.pagoRepository = pagoRepository;
        this.amortizacionRepository = amortizacionRepository;
        this.verificador = verificador;
        this.bonificacionMlmRepository = bonificacionMlmRepository;
    }

    @Override
    @Transactional
    public Credito solicitarCredito(Long usuarioId, BigDecimal monto, ModalidadPago modalidad, int plazoMeses,
                                    MedioDesembolso medioDesembolso,
                                    String nombreBanco, String numeroCuenta, String cci,
                                    String nombreBilletera, String numeroBilletera) {
        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado."));
        if (usuario.getEstado() == EstadoUsuario.ELIMINADO_POR_MORA || usuario.getEstado() == EstadoUsuario.NO_HABIDO) {
            throw new IllegalStateException("El usuario no puede solicitar créditos por su estado actual.");
        }
        validarMonto(monto);
        validarActualizacionAnual(usuario);
        validarSinCreditoEnCurso(usuario);
        validarCapacidadCredito(usuario, monto);
        validarDatosDesembolso(medioDesembolso, nombreBanco, numeroCuenta, cci, nombreBilletera, numeroBilletera);

        int cuotas = validarModalidad(usuario, modalidad, plazoMeses);
        Credito credito = new Credito();
        credito.setUsuario(usuario);
        credito.setMontoPrestado(monto.setScale(2, RoundingMode.HALF_UP));
        credito.setModalidad(modalidad);
        credito.setNumeroCuotas(cuotas);
        credito.setEstado(EstadoCredito.PENDIENTE_APROBACION);
        credito.setFechaSolicitud(LocalDate.now());
        // La documentación establece que la membresía se activa desde la solicitud,
        // y se mantiene hasta el fin estimado del contrato de crédito.
        LocalDate inicioSolicitud = LocalDate.now();
        boolean membresiaVigente = usuario.getFechaVencimientoMembresia() != null
                && !usuario.getFechaVencimientoMembresia().isBefore(inicioSolicitud);
        usuario.setEstado(EstadoUsuario.ACTIVO);
        if (!membresiaVigente || usuario.getFechaActivacionMembresia() == null) {
            usuario.setFechaActivacionMembresia(inicioSolicitud);
        }
        LocalDate finEstimado = modalidad == ModalidadPago.SEMANAL_COMPUESTO
                ? inicioSolicitud.plusWeeks(cuotas)
                : inicioSolicitud.plusMonths(cuotas);
        if (!membresiaVigente || usuario.getFechaVencimientoMembresia().isBefore(finEstimado)) {
            usuario.setFechaVencimientoMembresia(finEstimado);
        }
        usuarioRepository.save(usuario);
        credito.setMedioDesembolso(medioDesembolso);
        credito.setNombreBanco(limpiar(nombreBanco));
        credito.setNumeroCuenta(limpiar(numeroCuenta));
        credito.setCci(limpiar(cci));
        credito.setNombreBilletera(limpiar(nombreBilletera));
        credito.setNumeroBilletera(limpiar(numeroBilletera));
        Usuario patrocinador = usuario.getPatrocinador();
        if (patrocinador == null) {
            throw new IllegalStateException("El crédito no puede solicitarse sin un patrocinador válido.");
        }
        credito.setContratoGarantiaAceptado(patrocinador.getRol() == Rol.ADMIN);
        Credito guardado = creditoRepository.save(credito);
        crearGarantiaSolicitud(guardado);
        return guardado;
    }

    @Override
    @Transactional
    public Credito aprobarCredito(Long creditoId, String comentarioAdmin) {
        Credito credito = creditoRepository.findById(creditoId)
                .orElseThrow(() -> new IllegalArgumentException("Crédito no encontrado."));
        if (credito.getEstado() != EstadoCredito.PENDIENTE_APROBACION) {
            throw new IllegalStateException("Solo se pueden aprobar créditos pendientes.");
        }
        GarantiaReferido garantia = null;
        if (!credito.isCreditoPromocional() && credito.getModalidad() != ModalidadPago.YAPE_PROMOCIONAL) {
            garantia = garantiaReferidoRepository.findByCredito(credito)
                    .orElseGet(() -> crearGarantiaSolicitud(credito));
        }
        if (!credito.isCreditoPromocional() && credito.getUsuario().getPatrocinador() != null
                && credito.getUsuario().getPatrocinador().getRol() != Rol.ADMIN
                && garantia.getEstado() != EstadoGarantia.ACTIVA) {
            throw new IllegalStateException("No se puede activar el crédito hasta que el patrocinador acepte la garantía.");
        }
        credito.setContratoGarantiaAceptado(garantia != null && garantia.getEstado() == EstadoGarantia.ACTIVA);
        activarCredito(credito, comentarioAdmin);
        return creditoRepository.save(credito);
    }

    private void activarCredito(Credito credito, String comentarioAdmin) {
        LocalDate inicio = LocalDate.now();
        credito.setEstado(EstadoCredito.ACTIVO);
        credito.setFechaInicio(inicio);
        credito.setFechaAprobacion(inicio);
        credito.setComentarioAdmin(comentarioAdmin);
        generarEstructuraCuotas(credito, inicio);
        credito.setFechaFin(credito.getCuotas().get(credito.getCuotas().size() - 1).getFechaVencimiento());
        Usuario usuario = credito.getUsuario();
        usuario.setEstado(EstadoUsuario.ACTIVO);
        usuario.setFechaActivacionMembresia(inicio);
        usuario.setFechaVencimientoMembresia(usuario.getFechaVencimientoMembresia() == null || usuario.getFechaVencimientoMembresia().isBefore(credito.getFechaFin()) ? credito.getFechaFin() : usuario.getFechaVencimientoMembresia());
        if (usuario.getRango() == RangoMlm.LIDER && credito.getMontoPrestado().compareTo(new BigDecimal("1000.00")) >= 0) usuario.setFechaUltimoCreditoComoLider(inicio);
        acreditarPuntosRed(credito);
        usuarioRepository.save(usuario);
        creditoRepository.save(credito);
    }

    @Override
    @Transactional
    public Credito rechazarCredito(Long creditoId, String comentarioAdmin) {
        Credito credito = creditoRepository.findById(creditoId)
                .orElseThrow(() -> new IllegalArgumentException("Crédito no encontrado."));
        if (credito.getEstado() != EstadoCredito.PENDIENTE_APROBACION) throw new IllegalStateException("El crédito no está pendiente.");
        credito.setEstado(EstadoCredito.RECHAZADO);
        credito.setComentarioAdmin(comentarioAdmin);
        Usuario usuario = credito.getUsuario();
        if (usuario.getSaldoAhorro().compareTo(CIEN) < 0) usuario.setEstado(EstadoUsuario.INACTIVO);
        usuarioRepository.save(usuario);
        garantiaReferidoRepository.findByCredito(credito).ifPresent(g -> {
            if (g.getEstado() == EstadoGarantia.PENDIENTE_ACEPTACION || g.getEstado() == EstadoGarantia.ACTIVA) {
                g.setEstado(EstadoGarantia.RECHAZADA);
                g.setContratoFirmado(false);
                garantiaReferidoRepository.save(g);
            }
        });
        return creditoRepository.save(credito);
    }

    @Override
    @Transactional
    public CuotaCredito pagarCuota(Long cuotaId, BigDecimal montoPagado, MetodoPago metodo, Long usuarioPagoId) {
        CuotaCredito cuota = cuotaCreditoRepository.findById(cuotaId)
                .orElseThrow(() -> new IllegalArgumentException("Cuota no encontrada."));
        if (cuota.isPagado()) throw new IllegalStateException("La cuota ya se encuentra pagada.");

        Credito credito = cuota.getCredito();
        if (amortizacionRepository.existsByCreditoAndEstadoIn(credito, List.of(EstadoPago.PENDIENTE_APROBACION, EstadoPago.REVISION_MANUAL))) {
            throw new IllegalStateException("Existe una solicitud de amortización pendiente para este crédito.");
        }
        Usuario deudor = credito.getUsuario();
        if (usuarioPagoId == null || !deudor.getId().equals(usuarioPagoId)) {
            throw new IllegalStateException("Solo el titular del crédito puede registrar el pago de su cuota.");
        }
        CuotaCredito primeraPendiente = cuotaCreditoRepository.findByCreditoAndPagadoFalseOrderByNumeroCuotaAsc(credito)
                .stream().findFirst().orElseThrow();
        if (!primeraPendiente.getId().equals(cuotaId)) {
            throw new IllegalStateException("Las cuotas deben pagarse en orden. Primero debe pagar la cuota N° " + primeraPendiente.getNumeroCuota() + ".");
        }
        if (metodo == null || metodo == MetodoPago.GARANTIA) throw new IllegalArgumentException("Seleccione un método de pago válido para el deudor.");

        BigDecimal total = cuota.getMontoTotalAPagar().setScale(2, RoundingMode.HALF_UP);
        if (montoPagado == null || montoPagado.setScale(2, RoundingMode.HALF_UP).compareTo(total) != 0) {
            throw new IllegalArgumentException("El monto a pagar debe ser exactamente S/ " + total.toPlainString() + ".");
        }

        cuota.setPagado(true);
        cuota.setFechaPagoReal(LocalDate.now());
        cuotaCreditoRepository.save(cuota);

        Pago pago = new Pago();
        pago.setCuota(cuota);
        pago.setUsuario(deudor);
        pago.setMonto(total);
        pago.setFechaPago(LocalDateTime.now());
        pago.setMetodo(metodo);
        pago.setOrigen(OrigenPago.DEUDOR);
        pagoRepository.save(pago);

        Usuario garante = deudor.getPatrocinador();
        acreditarBonificacionesPorPago(credito, cuota);

        List<CuotaCredito> pendientes = cuotaCreditoRepository.findByCreditoAndPagadoFalseOrderByNumeroCuotaAsc(credito);
        boolean liquidado = pendientes.isEmpty();
        if (credito.getEstado() == EstadoCredito.EN_MORA && !liquidado && pendientes.stream().noneMatch(q -> q.getFechaVencimiento().isBefore(LocalDate.now()))) {
            credito.setEstado(EstadoCredito.ACTIVO);
        }

        if (liquidado) {
            credito.setEstado(EstadoCredito.PAGADO);
            deudor.setLimiteCreditoPermitido(deudor.getLimiteCreditoPermitido().add(CIEN));
            boolean tuvoMora = credito.getCuotas().stream().anyMatch(q -> q.getDiasMora() > 0 ||
                    (q.getFechaPagoReal() != null && q.getFechaPagoReal().isAfter(q.getFechaVencimiento())));
            if (garante != null && garante.getEstado() == EstadoUsuario.ACTIVO) {
                if (!tuvoMora) garante.setLimiteCreditoPermitido(garante.getLimiteCreditoPermitido().add(CIEN));
            }
            garantiaReferidoRepository.findByCredito(credito).ifPresent(g -> {
                g.setEstado(EstadoGarantia.LIQUIDADA);
                garantiaReferidoRepository.save(g);
            });
            if (deudor.getSaldoAhorro().compareTo(CIEN) < 0) {
                // Si la activación dependía exclusivamente del crédito, el contrato finaliza aquí.
                deudor.setFechaVencimientoMembresia(LocalDate.now());
            }
        }
        usuarioRepository.save(deudor);
        if (garante != null) usuarioRepository.save(garante);
        creditoRepository.save(credito);
        return cuota;
    }

    @Override
    @Transactional
    public Pago registrarComprobante(Long cuotaId, BigDecimal monto, MetodoPago metodo, Long usuarioId, byte[] comprobante, String contentType) {
        CuotaCredito cuota=cuotaCreditoRepository.findById(cuotaId).orElseThrow(() -> new IllegalArgumentException("Cuota no encontrada."));
        Credito credito=cuota.getCredito();
        if (amortizacionRepository.existsByCreditoAndEstadoIn(credito, List.of(EstadoPago.PENDIENTE_APROBACION, EstadoPago.REVISION_MANUAL))) throw new IllegalStateException("Existe una solicitud de amortización pendiente para este crédito.");
        Usuario deudor=credito.getUsuario();
        if (!deudor.getId().equals(usuarioId)) throw new IllegalStateException("Solo el titular puede registrar el pago.");
        if (cuota.isPagado()) throw new IllegalStateException("La cuota ya está pagada.");
        CuotaCredito primera=cuotaCreditoRepository.findByCreditoAndPagadoFalseOrderByNumeroCuotaAsc(credito).stream().findFirst().orElseThrow();
        if (!primera.getId().equals(cuotaId)) throw new IllegalStateException("Debe pagar primero la cuota N° " + primera.getNumeroCuota() + ".");
        if (metodo != MetodoPago.YAPE && metodo != MetodoPago.TRANSFERENCIA) throw new IllegalArgumentException("Para este proceso seleccione Yape o Transferencia BCP.");
        BigDecimal total=cuota.getMontoTotalAPagar().setScale(2,RoundingMode.HALF_UP);
        if (monto==null || monto.setScale(2,RoundingMode.HALF_UP).compareTo(total)!=0) throw new IllegalArgumentException("El monto debe ser exactamente S/ " + total.toPlainString() + ".");
        if (comprobante==null || comprobante.length==0 || comprobante.length>5*1024*1024) throw new IllegalArgumentException("Adjunte un comprobante de imagen de hasta 5 MB.");
        if (contentType==null || !contentType.matches("image/(jpeg|png|webp)")) throw new IllegalArgumentException("El comprobante debe ser JPG, PNG o WEBP.");
        if (pagoRepository.existsByCuotaAndEstadoIn(cuota, java.util.List.of(EstadoPago.PENDIENTE_APROBACION))) throw new IllegalStateException("Ya existe un comprobante pendiente de aprobación para esta cuota.");
        Pago pago=new Pago(); pago.setCuota(cuota); pago.setUsuario(deudor); pago.setMonto(total); pago.setFechaPago(LocalDateTime.now()); pago.setMetodo(metodo); pago.setOrigen(OrigenPago.DEUDOR); pago.setComprobante(comprobante); pago.setComprobanteContentType(contentType);
        ComprobanteVerificacionService.Resultado vr=verificador.verificar(comprobante,contentType,total,metodo);
        pago.setResultadoVerificacion(vr.detalle()); pago.setEstado(vr.valido()?EstadoPago.PENDIENTE_APROBACION:EstadoPago.REVISION_MANUAL);
        return pagoRepository.save(pago);
    }

    @Override
    @Transactional
    public Pago registrarPagoAdmin(Long cuotaId, BigDecimal monto, MetodoPago metodo, byte[] comprobante, String contentType, String comentario) {
        CuotaCredito cuota = cuotaCreditoRepository.findById(cuotaId)
                .orElseThrow(() -> new IllegalArgumentException("Cuota no encontrada."));
        if (cuota.isPagado()) throw new IllegalStateException("La cuota ya se encuentra pagada.");
        Credito credito = cuota.getCredito();
        if (amortizacionRepository.existsByCreditoAndEstadoIn(credito, List.of(EstadoPago.PENDIENTE_APROBACION, EstadoPago.REVISION_MANUAL))) {
            throw new IllegalStateException("Existe una solicitud de amortización pendiente para este crédito.");
        }
        if (credito.getEstado() != EstadoCredito.ACTIVO && credito.getEstado() != EstadoCredito.EN_MORA) {
            throw new IllegalStateException("Solo se puede registrar pago de una cuota de un crédito activo o en mora.");
        }
        CuotaCredito primera = cuotaCreditoRepository.findByCreditoAndPagadoFalseOrderByNumeroCuotaAsc(credito)
                .stream().findFirst().orElseThrow(() -> new IllegalStateException("No existen cuotas pendientes."));
        if (!primera.getId().equals(cuotaId)) {
            throw new IllegalStateException("Las cuotas deben pagarse en orden. Primero corresponde la cuota N° " + primera.getNumeroCuota() + ".");
        }
        if (metodo == null || metodo == MetodoPago.GARANTIA) {
            throw new IllegalArgumentException("Seleccione un método de pago válido.");
        }
        BigDecimal total = cuota.getMontoTotalAPagar().setScale(2, RoundingMode.HALF_UP);
        if (monto == null || monto.setScale(2, RoundingMode.HALF_UP).compareTo(total) != 0) {
            throw new IllegalArgumentException("El monto debe ser exactamente S/ " + total.toPlainString() + ".");
        }
        boolean efectivo = metodo == MetodoPago.EFECTIVO;
        if (!efectivo) {
            if (comprobante == null || comprobante.length == 0 || comprobante.length > 5 * 1024 * 1024) {
                throw new IllegalArgumentException("Para este método debe adjuntar un comprobante de imagen de hasta 5 MB.");
            }
            if (contentType == null || !contentType.matches("image/(jpeg|png|webp)")) {
                throw new IllegalArgumentException("El comprobante debe ser JPG, PNG o WEBP.");
            }
        } else {
            comprobante = null;
            contentType = null;
        }

        cuota.setPagado(true);
        cuota.setFechaPagoReal(LocalDate.now());
        cuotaCreditoRepository.save(cuota);

        Pago pago = new Pago();
        pago.setCuota(cuota);
        pago.setUsuario(credito.getUsuario());
        pago.setMonto(total);
        pago.setFechaPago(LocalDateTime.now());
        pago.setMetodo(metodo);
        pago.setOrigen(OrigenPago.ADMIN);
        pago.setEstado(EstadoPago.APROBADO);
        pago.setComprobante(comprobante);
        pago.setComprobanteContentType(contentType);
        pago.setComentarioAdmin(comentario);
        pago.setFechaRevision(LocalDateTime.now());
        pago.setResultadoVerificacion(efectivo ? "Pago registrado directamente por ADMIN en efectivo." : "Pago registrado directamente por ADMIN con comprobante.");
        aplicarConsecuenciasPagoAprobado(cuota);
        return pagoRepository.save(pago);
    }

    @Override
    @Transactional
    public Pago aprobarPago(Long pagoId, String comentario) {
        Pago pago=pagoRepository.findById(pagoId).orElseThrow(() -> new IllegalArgumentException("Pago no encontrado."));
        if (pago.getEstado()!=EstadoPago.PENDIENTE_APROBACION && pago.getEstado()!=EstadoPago.REVISION_MANUAL) throw new IllegalStateException("El pago no está pendiente.");
        CuotaCredito cuota=pago.getCuota(); if (cuota.isPagado()) throw new IllegalStateException("La cuota ya estaba pagada.");
        cuota.setPagado(true); cuota.setFechaPagoReal(LocalDate.now()); cuotaCreditoRepository.save(cuota); pago.setEstado(EstadoPago.APROBADO); pago.setFechaRevision(LocalDateTime.now()); pago.setComentarioAdmin(comentario);
        aplicarConsecuenciasPagoAprobado(cuota); return pagoRepository.save(pago);
    }

    @Override
    @Transactional
    public Pago rechazarPago(Long pagoId, String comentario) {
        Pago pago=pagoRepository.findById(pagoId).orElseThrow(() -> new IllegalArgumentException("Pago no encontrado."));
        if (pago.getEstado()!=EstadoPago.PENDIENTE_APROBACION && pago.getEstado()!=EstadoPago.REVISION_MANUAL) throw new IllegalStateException("El pago no está pendiente.");
        pago.setEstado(EstadoPago.RECHAZADO); pago.setFechaRevision(LocalDateTime.now()); pago.setComentarioAdmin(comentario); return pagoRepository.save(pago);
    }

    @Override public List<Pago> listarPagosPendientes() { return pagoRepository.findByEstadoInOrderByFechaPagoAsc(java.util.List.of(EstadoPago.PENDIENTE_APROBACION, EstadoPago.REVISION_MANUAL)); }
    @Override public Pago obtenerPago(Long id) { return pagoRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Pago no encontrado.")); }
    @Override public List<Pago> listarPagosCuota(Long cuotaId) { return cuotaCreditoRepository.findById(cuotaId).map(pagoRepository::findByCuotaOrderByFechaPagoDesc).orElseGet(List::of); }

    private void aplicarConsecuenciasPagoAprobado(CuotaCredito cuota) {
        Credito credito=cuota.getCredito(); Usuario deudor=credito.getUsuario(); Usuario garante=deudor.getPatrocinador();
        acreditarBonificacionesPorPago(credito, cuota);
        List<CuotaCredito> pendientes=cuotaCreditoRepository.findByCreditoAndPagadoFalseOrderByNumeroCuotaAsc(credito); boolean liquidado=pendientes.isEmpty();
        if (credito.getEstado()==EstadoCredito.EN_MORA && !liquidado && pendientes.stream().noneMatch(q->q.getFechaVencimiento().isBefore(LocalDate.now()))) credito.setEstado(EstadoCredito.ACTIVO);
        if (liquidado) {
            credito.setEstado(EstadoCredito.PAGADO); deudor.setLimiteCreditoPermitido(deudor.getLimiteCreditoPermitido().add(CIEN));
            boolean tuvoMora=credito.getCuotas().stream().anyMatch(q->q.getDiasMora()>0 || (q.getFechaPagoReal()!=null && q.getFechaPagoReal().isAfter(q.getFechaVencimiento())));
            if (garante!=null && garante.getEstado()==EstadoUsuario.ACTIVO) { if (!tuvoMora) garante.setLimiteCreditoPermitido(garante.getLimiteCreditoPermitido().add(CIEN)); }
            garantiaReferidoRepository.findByCredito(credito).ifPresent(g->{g.setEstado(EstadoGarantia.LIQUIDADA);garantiaReferidoRepository.save(g);});
            if (deudor.getSaldoAhorro().compareTo(CIEN)<0) deudor.setFechaVencimientoMembresia(LocalDate.now());
        }
        usuarioRepository.save(deudor); if(garante!=null) usuarioRepository.save(garante); creditoRepository.save(credito);
    }

    private boolean estaActivoParaMlm(Usuario usuario) {
        if (usuario == null || usuario.getRol() == Rol.ADMIN || usuario.getEstado() != EstadoUsuario.ACTIVO) return false;
        LocalDate hoy = LocalDate.now();
        // La membresía por crédito permanece activa durante la vigencia de su contrato.
        return usuario.getFechaVencimientoMembresia() == null
                || !usuario.getFechaVencimientoMembresia().isBefore(hoy);
    }

    private void acreditarPuntosRed(Credito credito) {

    BigDecimal puntos = credito.getMontoPrestado()
            .multiply(new BigDecimal("0.001"))
            .setScale(3, RoundingMode.HALF_UP);

    Usuario upline = credito.getUsuario().getPatrocinador();

    while (upline != null) {

        if (estaActivoParaMlm(upline)) {

            BigDecimal puntosActuales = upline.getPuntosAcumulados() == null
                    ? BigDecimal.ZERO
                    : upline.getPuntosAcumulados();

            upline.setPuntosAcumulados(
                    puntosActuales.add(puntos)
                            .setScale(3, RoundingMode.HALF_UP)
            );
            if (upline.getPuntosAcumulados()
                    .compareTo(new BigDecimal("100.000")) >= 0) {
                upline.setRango(RangoMlm.LIDER);
            }

            usuarioRepository.save(upline);
        }
        upline = upline.getPatrocinador();
    }
}

    /**
     * El bono se genera solo cuando el interés fue recibido:
     * mensual simple: 10% del interés de cada cuota mensual;
     * compuesto: al liquidarse el crédito, 10% del interés total.
     * El unilevel distribuye adicionalmente 5%, 2%, 1%, 1% y 1% a niveles 2-6.
     */
    private void acreditarBonificacionesPorPago(Credito credito, CuotaCredito cuotaPagada) {
        List<CuotaCredito> pendientes = cuotaCreditoRepository.findByCreditoAndPagadoFalseOrderByNumeroCuotaAsc(credito);
        boolean liquidacion = pendientes.isEmpty();
        BigDecimal baseInteres;
        if (credito.getModalidad() == ModalidadPago.MENSUAL_SIMPLE) {
            baseInteres = cuotaPagada.getMontoInteres() == null ? BigDecimal.ZERO : cuotaPagada.getMontoInteres();
        } else {
            if (!liquidacion) return;
            baseInteres = credito.getInteresTotalCalculado() == null ? BigDecimal.ZERO : credito.getInteresTotalCalculado();
        }
        if (baseInteres.signum() <= 0) return;

        Usuario beneficiario = credito.getUsuario().getPatrocinador();
        int nivel = 1;
        BigDecimal[] porcentajes = {
                new BigDecimal("0.10"), new BigDecimal("0.05"), new BigDecimal("0.02"),
                new BigDecimal("0.01"), new BigDecimal("0.01"), new BigDecimal("0.01")
        };
        while (beneficiario != null && nivel <= 6) {
            if (estaActivoParaMlm(beneficiario)) {
                BigDecimal porcentaje = porcentajes[nivel - 1];
                BigDecimal importe = baseInteres.multiply(porcentaje).setScale(2, RoundingMode.HALF_UP);
                if (importe.signum() > 0) {
                    acreditarBono(beneficiario, importe);
                    BonificacionMlm registro = new BonificacionMlm();
                    registro.setBeneficiario(beneficiario);
                    registro.setCredito(credito);
                    registro.setTipo(nivel == 1 ? "DIRECTA" : "UNINIVEL");
                    registro.setNivel(nivel);
                    registro.setPorcentaje(porcentaje.multiply(new BigDecimal("100")).setScale(2, RoundingMode.HALF_UP));
                    registro.setImporte(importe);
                    registro.setModalidadInteres(credito.getModalidad() == ModalidadPago.MENSUAL_SIMPLE ? "SIMPLE" : "COMPUESTO");
                    registro.setFechaAcreditacion(LocalDateTime.now());
                    registro.setDetalle(credito.getModalidad() == ModalidadPago.MENSUAL_SIMPLE
                            ? "Interés mensual recibido del crédito #" + credito.getId()
                            : "Crédito compuesto liquidado #" + credito.getId());
                    bonificacionMlmRepository.save(registro);
                    usuarioRepository.save(beneficiario);
                }
            }
            beneficiario = beneficiario.getPatrocinador();
            nivel++;
        }
    }

    private void acreditarBono(Usuario usuario, BigDecimal importe) {
        usuario.setSaldoBonos(usuario.getSaldoBonos().add(importe).setScale(2, RoundingMode.HALF_UP));
    }

    private void generarEstructuraCuotas(Credito credito, LocalDate inicio) {

        BigDecimal monto = credito.getMontoPrestado();
        int total = credito.getNumeroCuotas();

        // Crédito rápido promocional: plazo en días, interés fijo y sin amortización.
        if (credito.isCreditoPromocional() || credito.getModalidad() == ModalidadPago.YAPE_PROMOCIONAL) {
            List<CuotaCredito> cuotasPromo = credito.getCuotas();
            cuotasPromo.clear();
            BigDecimal interesTotal = credito.getInteresTotalCalculado() == null ? BigDecimal.ZERO : credito.getInteresTotalCalculado();
            BigDecimal totalPagar = monto.add(interesTotal).setScale(2, RoundingMode.HALF_UP);
            BigDecimal cuotaBase = totalPagar.divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP);
            BigDecimal capitalAcumulado = BigDecimal.ZERO;
            for (int i = 1; i <= total; i++) {
                CuotaCredito q = new CuotaCredito();
                q.setCredito(credito);
                q.setNumeroCuota(i);
                BigDecimal capital = i == total ? monto.subtract(capitalAcumulado).setScale(2, RoundingMode.HALF_UP)
                    : monto.divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP);
                capitalAcumulado = capitalAcumulado.add(capital);
                BigDecimal importe = i == total ? totalPagar.subtract(cuotaBase.multiply(BigDecimal.valueOf(total - 1))).setScale(2, RoundingMode.HALF_UP) : cuotaBase;
                q.setMontoCuota(importe);
                q.setMontoCapital(capital);
                q.setMontoInteres(importe.subtract(capital).setScale(2, RoundingMode.HALF_UP));
                int dias = total == 1 ? credito.getPlazoPromocionalDias() : i * 10;
                q.setFechaVencimiento(inicio.plusDays(dias));
                cuotasPromo.add(q);
            }
            credito.setInteresTotalCalculado(interesTotal);
            credito.setMontoCuota(cuotaBase);
            return;
        }

        // No reemplazar la colección administrada por Hibernate.
        List<CuotaCredito> cuotas = credito.getCuotas();
        cuotas.clear();

        if (credito.getModalidad() == ModalidadPago.SEMANAL_COMPUESTO) {

            BigDecimal tasa = tasaSemanal(credito.getUsuario(), total);
            BigDecimal interesTotal = monto.multiply(tasa).setScale(2, RoundingMode.HALF_UP);
            BigDecimal totalPagar = monto.add(interesTotal).setScale(2, RoundingMode.HALF_UP);
            BigDecimal cuotaBase = totalPagar.divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP);

            int[] pesosCapital = pesosCapitalSemanales(total);
            int sumaPesos = 0;
            for (int peso : pesosCapital) sumaPesos += peso;
            BigDecimal sumaCapital = BigDecimal.ZERO;

            for (int i = 1; i <= total; i++) {
                CuotaCredito q = new CuotaCredito();
                q.setCredito(credito);
                q.setNumeroCuota(i);
                q.setMontoCuota(cuotaBase);

                BigDecimal capital;
                if (i < total) {
                    capital = monto
                            .multiply(BigDecimal.valueOf(pesosCapital[i - 1]))
                            .divide(BigDecimal.valueOf(sumaPesos), 2, RoundingMode.HALF_UP);
                    sumaCapital = sumaCapital.add(capital);
                } else {
                    // Ajuste de centavos para que la suma del capital sea exactamente
                    // igual al principal prestado. Los porcentajes entregados por CREDI DIAZ
                    // se utilizan como pesos crecientes de distribución.
                    capital = monto.subtract(sumaCapital).setScale(2, RoundingMode.HALF_UP);
                }

                BigDecimal interes = cuotaBase.subtract(capital).setScale(2, RoundingMode.HALF_UP);
                q.setMontoCapital(capital);
                q.setMontoInteres(interes);
                q.setFechaVencimiento(inicio.plusWeeks(i));
                cuotas.add(q);
            }

            credito.setInteresTotalCalculado(interesTotal);
            credito.setMontoCuota(cuotaBase);

        } else {

            BigDecimal interesMensual = monto
                    .multiply(DIEZ_POR_CIENTO)
                    .setScale(2, RoundingMode.HALF_UP);

            BigDecimal interesTotal = interesMensual
                    .multiply(BigDecimal.valueOf(total))
                    .setScale(2, RoundingMode.HALF_UP);

            for (int i = 1; i <= total; i++) {
                CuotaCredito q = new CuotaCredito();
                q.setCredito(credito);
                q.setNumeroCuota(i);
                boolean ultima = i == total;
                q.setMontoInteres(interesMensual);
                q.setMontoCapital(ultima ? monto : BigDecimal.ZERO);
                q.setMontoCuota(ultima ? monto.add(interesMensual) : interesMensual);
                q.setFechaVencimiento(inicio.plusMonths(i));
                cuotas.add(q);
            }

            credito.setInteresTotalCalculado(interesTotal);
            credito.setMontoCuota(interesMensual);
        }
    }

    private GarantiaReferido crearGarantiaSolicitud(Credito credito) {
        return garantiaReferidoRepository.findByCredito(credito).orElseGet(() -> {
            Usuario deudor = credito.getUsuario();
            Usuario garante = deudor.getPatrocinador();
            if (garante == null) throw new IllegalStateException("El crédito no puede continuar sin patrocinador/garante.");

            GarantiaReferido garantia = new GarantiaReferido();
            garantia.setGarante(garante);
            garantia.setDeudor(deudor);
            garantia.setCredito(credito);
            garantia.setPorcentajeResponsabilidad(BigDecimal.ONE);
            garantia.setMontoDeudaGarantizada(BigDecimal.ZERO);
            boolean adminGarante = garante.getRol() == Rol.ADMIN;
            garantia.setContratoFirmado(adminGarante);
            garantia.setEstado(adminGarante ? EstadoGarantia.ACTIVA : EstadoGarantia.PENDIENTE_ACEPTACION);
            return garantiaReferidoRepository.save(garantia);
        });
    }

    @Override
    @Transactional
    public BigDecimal calcularMontoAmortizacion(Long creditoId) {
        Credito credito = creditoRepository.findById(creditoId)
                .orElseThrow(() -> new IllegalArgumentException("Crédito no encontrado."));
        validarCreditoAmortizable(credito);
        return credito.getCuotas().stream()
                .filter(q -> !q.isPagado())
                .map(CuotaCredito::getMontoCapital)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal calcularCapitalPendienteSinValidaciones(Credito credito) {
        return credito.getCuotas().stream()
                .filter(q -> !q.isPagado())
                .map(CuotaCredito::getMontoCapital)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private void validarCreditoAmortizable(Credito credito) {
        if (credito.isCreditoPromocional() || credito.getModalidad() == ModalidadPago.YAPE_PROMOCIONAL) {
            throw new IllegalStateException("Los créditos promocionales no permiten amortización.");
        }
        if (credito.getModalidad() != ModalidadPago.SEMANAL_COMPUESTO) {
            throw new IllegalStateException("La amortización solo está disponible para créditos con cuotas semanales.");
        }
        if (credito.getEstado() != EstadoCredito.ACTIVO && credito.getEstado() != EstadoCredito.EN_MORA) {
            throw new IllegalStateException("Solo se puede amortizar un crédito activo o en mora.");
        }
        if (credito.getCuotas() == null || credito.getCuotas().isEmpty()) {
            throw new IllegalStateException("El crédito todavía no tiene cuotas generadas.");
        }
        if (credito.getCuotas().stream().allMatch(CuotaCredito::isPagado)) {
            throw new IllegalStateException("El crédito ya se encuentra pagado.");
        }
        if (amortizacionRepository.existsByCreditoAndEstadoIn(credito, List.of(EstadoPago.PENDIENTE_APROBACION, EstadoPago.REVISION_MANUAL))) {
            throw new IllegalStateException("Ya existe una solicitud de amortización pendiente para este crédito.");
        }
        for (CuotaCredito cuota : credito.getCuotas()) {
            if (!cuota.isPagado() && pagoRepository.existsByCuotaAndEstadoIn(cuota, List.of(EstadoPago.PENDIENTE_APROBACION, EstadoPago.REVISION_MANUAL))) {
                throw new IllegalStateException("Existe un comprobante pendiente de aprobación para la cuota N° " + cuota.getNumeroCuota() + ".");
            }
        }

        CuotaCredito ultimaPagada = credito.getCuotas().stream()
                .filter(CuotaCredito::isPagado)
                .max(java.util.Comparator.comparing(CuotaCredito::getNumeroCuota))
                .orElse(null);

        if (ultimaPagada != null && ultimaPagada.getFechaVencimiento() != null
                && LocalDate.now().isAfter(ultimaPagada.getFechaVencimiento())
                && (ultimaPagada.getFechaPagoReal() == null || !ultimaPagada.getFechaPagoReal().equals(LocalDate.now()))) {
            CuotaCredito siguiente = credito.getCuotas().stream()
                    .filter(q -> !q.isPagado())
                    .min(java.util.Comparator.comparing(CuotaCredito::getNumeroCuota))
                    .orElse(null);
            if (siguiente != null) {
                throw new IllegalStateException("La fecha de amortización ya pasó para la cuota N° " + ultimaPagada.getNumeroCuota() + ". Debe pagar completa la cuota N° " + siguiente.getNumeroCuota() + " antes de amortizar las cuotas posteriores.");
            }
        }
    }

    @Override
    @Transactional
    public Amortizacion registrarAmortizacionCliente(Long creditoId, BigDecimal monto, MetodoPago metodo, Long usuarioId, byte[] comprobante, String contentType) {
        Credito credito = creditoRepository.findById(creditoId)
                .orElseThrow(() -> new IllegalArgumentException("Crédito no encontrado."));
        Usuario deudor = credito.getUsuario();
        if (!deudor.getId().equals(usuarioId)) throw new IllegalStateException("Solo el titular puede solicitar la amortización.");
        validarCreditoAmortizable(credito);
        if (metodo != MetodoPago.YAPE && metodo != MetodoPago.TRANSFERENCIA) {
            throw new IllegalArgumentException("Para amortizar seleccione Yape o Transferencia BCP.");
        }
        BigDecimal esperado = calcularMontoAmortizacion(creditoId);
        if (monto == null || monto.setScale(2, RoundingMode.HALF_UP).compareTo(esperado) != 0) {
            throw new IllegalArgumentException("El monto de amortización debe ser exactamente S/ " + esperado.toPlainString() + ".");
        }
        if (comprobante == null || comprobante.length == 0 || comprobante.length > 5 * 1024 * 1024) {
            throw new IllegalArgumentException("Adjunte un comprobante de imagen de hasta 5 MB.");
        }
        if (contentType == null || !contentType.matches("image/(jpeg|png|webp)")) {
            throw new IllegalArgumentException("El comprobante debe ser JPG, PNG o WEBP.");
        }

        Amortizacion a = new Amortizacion();
        a.setCredito(credito);
        a.setUsuario(deudor);
        a.setMonto(esperado);
        a.setFechaSolicitud(LocalDateTime.now());
        a.setMetodo(metodo);
        a.setOrigen(OrigenPago.DEUDOR);
        a.setComprobante(comprobante);
        a.setComprobanteContentType(contentType);
        ComprobanteVerificacionService.Resultado vr = verificador.verificar(comprobante, contentType, esperado, metodo);
        a.setResultadoVerificacion(vr.detalle());
        a.setEstado(vr.valido() ? EstadoPago.PENDIENTE_APROBACION : EstadoPago.REVISION_MANUAL);
        return amortizacionRepository.save(a);
    }

    @Override
    @Transactional
    public Amortizacion registrarAmortizacionAdmin(Long creditoId, BigDecimal monto, MetodoPago metodo, byte[] comprobante, String contentType, String comentario) {
        Credito credito = creditoRepository.findById(creditoId)
                .orElseThrow(() -> new IllegalArgumentException("Crédito no encontrado."));
        validarCreditoAmortizable(credito);
        if (metodo == null || metodo == MetodoPago.GARANTIA) throw new IllegalArgumentException("Seleccione un método de pago válido.");
        BigDecimal esperado = calcularMontoAmortizacion(creditoId);
        if (monto == null || monto.setScale(2, RoundingMode.HALF_UP).compareTo(esperado) != 0) {
            throw new IllegalArgumentException("El monto de amortización debe ser exactamente S/ " + esperado.toPlainString() + ".");
        }
        boolean efectivo = metodo == MetodoPago.EFECTIVO;
        if (!efectivo) {
            if (comprobante == null || comprobante.length == 0 || comprobante.length > 5 * 1024 * 1024) throw new IllegalArgumentException("Para este método debe adjuntar un comprobante de imagen de hasta 5 MB.");
            if (contentType == null || !contentType.matches("image/(jpeg|png|webp)")) throw new IllegalArgumentException("El comprobante debe ser JPG, PNG o WEBP.");
        } else {
            comprobante = null; contentType = null;
        }

        Amortizacion a = new Amortizacion();
        a.setCredito(credito);
        a.setUsuario(credito.getUsuario());
        a.setMonto(esperado);
        a.setFechaSolicitud(LocalDateTime.now());
        a.setMetodo(metodo);
        a.setOrigen(OrigenPago.ADMIN);
        a.setEstado(EstadoPago.APROBADO);
        a.setComprobante(comprobante);
        a.setComprobanteContentType(contentType);
        a.setComentarioAdmin(comentario);
        a.setFechaRevision(LocalDateTime.now());
        a.setResultadoVerificacion(efectivo ? "Amortización registrada directamente por ADMIN en efectivo." : "Amortización registrada directamente por ADMIN con comprobante.");
        liquidarPorAmortizacion(a);
        return amortizacionRepository.save(a);
    }

    @Override
    @Transactional
    public Amortizacion aprobarAmortizacion(Long amortizacionId, String comentario) {
        Amortizacion a = amortizacionRepository.findById(amortizacionId)
                .orElseThrow(() -> new IllegalArgumentException("Amortización no encontrada."));
        if (a.getEstado() != EstadoPago.PENDIENTE_APROBACION && a.getEstado() != EstadoPago.REVISION_MANUAL) throw new IllegalStateException("La amortización no está pendiente.");
        Credito credito = a.getCredito();
        if (credito.getEstado() != EstadoCredito.ACTIVO && credito.getEstado() != EstadoCredito.EN_MORA) throw new IllegalStateException("El crédito ya no está disponible para amortización.");
        // No llamar calcularMontoAmortizacion aquí: valida que no exista una solicitud pendiente,
        // y precisamente estamos aprobando esa solicitud. Solo recalculamos el capital pendiente.
        BigDecimal esperado = calcularCapitalPendienteSinValidaciones(credito);
        if (a.getMonto().compareTo(esperado) != 0) throw new IllegalStateException("El importe de la amortización ya no coincide con el saldo de capital pendiente.");
        a.setEstado(EstadoPago.APROBADO);
        a.setFechaRevision(LocalDateTime.now());
        a.setComentarioAdmin(comentario);
        liquidarPorAmortizacion(a);
        return amortizacionRepository.save(a);
    }

    @Override
    @Transactional
    public Amortizacion rechazarAmortizacion(Long amortizacionId, String comentario) {
        Amortizacion a = amortizacionRepository.findById(amortizacionId)
                .orElseThrow(() -> new IllegalArgumentException("Amortización no encontrada."));
        if (a.getEstado() != EstadoPago.PENDIENTE_APROBACION && a.getEstado() != EstadoPago.REVISION_MANUAL) throw new IllegalStateException("La amortización no está pendiente.");
        a.setEstado(EstadoPago.RECHAZADO);
        a.setFechaRevision(LocalDateTime.now());
        a.setComentarioAdmin(comentario);
        return amortizacionRepository.save(a);
    }

    private void liquidarPorAmortizacion(Amortizacion a) {
        Credito credito = a.getCredito();
        Usuario deudor = credito.getUsuario();
        for (CuotaCredito cuota : credito.getCuotas()) {
            if (cuota.isPagado()) continue;
            cuota.setPagado(true);
            cuota.setFechaPagoReal(LocalDate.now());
            cuotaCreditoRepository.save(cuota);
        }
        credito.setEstado(EstadoCredito.PAGADO);
        usuarioRepository.save(deudor);
        garantiaReferidoRepository.findByCredito(credito).ifPresent(g -> {
            g.setEstado(EstadoGarantia.LIQUIDADA);
            g.setMontoDeudaGarantizada(BigDecimal.ZERO);
            garantiaReferidoRepository.save(g);
        });
        deudor.setLimiteCreditoPermitido(deudor.getLimiteCreditoPermitido().add(CIEN));
        if (deudor.getSaldoAhorro().compareTo(CIEN) < 0) deudor.setFechaVencimientoMembresia(LocalDate.now());
        usuarioRepository.save(deudor);
        creditoRepository.save(credito);
    }

    @Override public List<Amortizacion> listarAmortizacionesPendientes() {
        return amortizacionRepository.findByEstadoInOrderByFechaSolicitudAsc(List.of(EstadoPago.PENDIENTE_APROBACION, EstadoPago.REVISION_MANUAL));
    }

    @Override public List<Amortizacion> listarAmortizacionesCredito(Long creditoId) {
        Credito credito = creditoRepository.findById(creditoId).orElseThrow(() -> new IllegalArgumentException("Crédito no encontrado."));
        return amortizacionRepository.findByCreditoOrderByFechaSolicitudDesc(credito);
    }

    @Override public Amortizacion obtenerAmortizacion(Long id) {
        return amortizacionRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Amortización no encontrada."));
    }

    private void validarCapacidadCredito(Usuario usuario, BigDecimal monto) {
        BigDecimal limite = usuario.getLimiteCreditoPermitido() == null ? BigDecimal.ZERO : usuario.getLimiteCreditoPermitido();
        if (monto.compareTo(limite) > 0) {
            throw new IllegalArgumentException("El monto solicitado supera tu límite actual de S/ "
                    + limite.setScale(2, RoundingMode.HALF_UP).toPlainString() + ".");
        }
    }

    private void validarDatosDesembolso(MedioDesembolso medio, String banco, String cuenta, String cci,
                                        String billetera, String numeroBilletera) {
        if (medio == null) throw new IllegalArgumentException("Seleccione cómo desea recibir el desembolso.");
        if (medio == MedioDesembolso.CUENTA_BANCARIA) {
            if (vacio(banco) || vacio(cuenta) || vacio(cci)) throw new IllegalArgumentException("Para una cuenta bancaria debe indicar banco, número de cuenta y CCI.");
            String b=banco.trim();
            int longitud=switch (b) { case "BCP" -> 14; case "BBVA" -> 18; case "Interbank" -> 13; case "Scotiabank" -> 10; case "Banco de la Nación" -> 11; case "Mibanco" -> 12; case "Banco Falabella" -> 12; default -> 0; };
            if (longitud==0) throw new IllegalArgumentException("Seleccione un banco válido.");
            if (!cuenta.matches("\\d{" + longitud + "}")) throw new IllegalArgumentException("La cuenta de " + b + " debe tener exactamente " + longitud + " dígitos.");
            if (!cci.matches("\\d{20}")) throw new IllegalArgumentException("El CCI debe contener exactamente 20 dígitos.");
        } else {
            if (vacio(billetera) || vacio(numeroBilletera)) throw new IllegalArgumentException("Para una billetera digital debe indicar billetera y número.");
            if (!(billetera.equals("Yape") || billetera.equals("Plin") || billetera.startsWith("Otros:"))) throw new IllegalArgumentException("Seleccione Yape, Plin u Otros.");
            if (billetera.startsWith("Otros:") && billetera.substring(6).isBlank()) throw new IllegalArgumentException("Indique el nombre de la billetera.");
            if (!numeroBilletera.matches("\\d{9}")) throw new IllegalArgumentException("El número de billetera debe contener 9 dígitos.");

        }
    }

    private boolean vacio(String valor) {
        return valor == null || valor.trim().isEmpty();
    }

    private String limpiar(String valor) {
        return vacio(valor) ? null : valor.trim();
    }

    private void validarActualizacionAnual(Usuario usuario) {
        LocalDate limite = LocalDate.now().minusYears(1);
        if (usuario.getFechaActualizacionDatos() == null || usuario.getFechaActualizacionDatos().isBefore(limite)) {
            throw new IllegalStateException("Debe actualizar sus datos personales antes de solicitar un nuevo crédito.");
        }
        if (usuario.getFechaActualizacionDocumentos() == null || usuario.getFechaActualizacionDocumentos().isBefore(limite)) {
            throw new IllegalStateException("Debe actualizar su documentación KYC antes de solicitar un nuevo crédito.");
        }
    }

    private void validarMonto(BigDecimal monto) {
        if (monto == null || monto.compareTo(BigDecimal.ZERO) <= 0 || monto.remainder(CIEN).compareTo(BigDecimal.ZERO) != 0)
            throw new IllegalArgumentException("El monto solicitado debe ser un múltiplo de S/ 100.");
    }

    private int validarModalidad(Usuario usuario, ModalidadPago modalidad, int plazoMeses) {
        if (modalidad == ModalidadPago.SEMANAL_COMPUESTO) {
            if (usuario.getRango() == RangoMlm.LIDER) {
                if (plazoMeses == 8 || plazoMeses == 10 || plazoMeses == 12) return plazoMeses;
                throw new IllegalArgumentException("La modalidad semanal de primer nivel solo permite 8, 10 o 12 semanas.");
            }
            if (usuario.getLimiteDirectos() <= 0) {
                if (plazoMeses == 5 || plazoMeses == 10 || plazoMeses == 12) return plazoMeses;
                throw new IllegalArgumentException("La modalidad semanal con 3 referidos directos solo permite 5, 10 o 12 semanas.");
            }
            if (plazoMeses == 6 || plazoMeses == 12 || plazoMeses == 20) return plazoMeses;
            throw new IllegalArgumentException("El pago semanal solo permite 6, 12 o 20 semanas.");
        }
        if (modalidad == ModalidadPago.MENSUAL_SIMPLE && plazoMeses >= 1 && plazoMeses <= 12) return plazoMeses;
        throw new IllegalArgumentException("El plazo mensual debe estar entre 1 y 12 meses.");
    }

    private BigDecimal tasaSemanal(Usuario usuario, int semanas) {
        // La tasa queda asociada al plazo, independientemente de la modalidad
        // que habilitó ese plazo para el cliente.
        return switch (semanas) {
            case 5 -> new BigDecimal("0.08");
            case 6 -> new BigDecimal("0.11");
            case 8 -> new BigDecimal("0.10");
            case 10 -> usuario.getRango() == RangoMlm.LIDER
                    ? new BigDecimal("0.12")
                    : usuario.getLimiteDirectos() <= 0 ? new BigDecimal("0.15") : BigDecimal.ZERO;
            case 12 -> usuario.getRango() == RangoMlm.LIDER
                    ? new BigDecimal("0.14")
                    : usuario.getLimiteDirectos() <= 0 ? new BigDecimal("0.17") : new BigDecimal("0.20");
            case 20 -> new BigDecimal("0.30");
            default -> throw new IllegalArgumentException("Plazo semanal no válido para la modalidad del cliente.");
        };
    }

    private int[] pesosCapitalSemanales(int semanas) {
        return switch (semanas) {
            case 5 -> new int[]{84, 88, 92, 96, 100};
            case 6 -> new int[]{83, 86, 89, 91, 94, 97};
            case 8 -> new int[]{78, 82, 86, 89, 92, 94, 96, 98};
            case 10 -> new int[]{74, 78, 82, 86, 89, 92, 94, 96, 98, 100};
            case 12 -> new int[]{70, 72, 75, 77, 80, 82, 84, 86, 89, 91, 94, 97};
            case 20 -> new int[]{59, 60, 62, 64, 66, 68, 70, 72, 74, 76, 78, 80, 82, 84, 86, 89, 91, 94, 96, 98};
            default -> throw new IllegalArgumentException("No existe una distribución de capital para este plazo semanal.");
        };
    }

    private void validarSinCreditoEnCurso(Usuario usuario) {
        boolean bloqueado = creditoRepository.findByUsuarioAndEstado(usuario, EstadoCredito.ACTIVO).size() > 0
                || creditoRepository.findByUsuarioAndEstado(usuario, EstadoCredito.EN_MORA).size() > 0
                || creditoRepository.findByUsuarioAndEstado(usuario, EstadoCredito.PENDIENTE_APROBACION).size() > 0;
        if (bloqueado) throw new IllegalStateException("El usuario ya tiene una solicitud o crédito en curso.");
    }

    @Override public Optional<Credito> findById(Long id) { return creditoRepository.findById(id); }
    @Override public Optional<CuotaCredito> findCuotaById(Long id) { return cuotaCreditoRepository.findById(id); }
    @Override public List<Credito> listarTodos() { return creditoRepository.findAll(); }

    @Override public List<Credito> listarPorUsuario(Long usuarioId) {
        Usuario usuario = usuarioRepository.findById(usuarioId).orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado."));
        return creditoRepository.findByUsuario(usuario);
    }
    @Override public List<Credito> listarPendientesAprobacion() { return creditoRepository.findByEstado(EstadoCredito.PENDIENTE_APROBACION); }
}
