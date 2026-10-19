package upeu.edu.pe.credito.service;

import upeu.edu.pe.credito.entity.*;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface CreditoService {
    Credito solicitarCredito(Long usuarioId, BigDecimal monto, ModalidadPago modalidad, int plazoMeses, MedioDesembolso medioDesembolso, String nombreBanco, String numeroCuenta, String cci, String nombreBilletera, String numeroBilletera);
    Credito aprobarCredito(Long creditoId, String comentarioAdmin);
    Credito rechazarCredito(Long creditoId, String comentarioAdmin);
    CuotaCredito pagarCuota(Long cuotaId, BigDecimal montoPagado, MetodoPago metodo, Long usuarioPagoId);
    Pago registrarComprobante(Long cuotaId, BigDecimal monto, MetodoPago metodo, Long usuarioId, byte[] comprobante, String contentType);
    Pago registrarPagoAdmin(Long cuotaId, BigDecimal monto, MetodoPago metodo, byte[] comprobante, String contentType, String comentario);
    Pago aprobarPago(Long pagoId, String comentario);
    Pago rechazarPago(Long pagoId, String comentario);
    BigDecimal calcularMontoAmortizacion(Long creditoId);
    Amortizacion registrarAmortizacionCliente(Long creditoId, BigDecimal monto, MetodoPago metodo, Long usuarioId, byte[] comprobante, String contentType);
    Amortizacion registrarAmortizacionAdmin(Long creditoId, BigDecimal monto, MetodoPago metodo, byte[] comprobante, String contentType, String comentario);
    Amortizacion aprobarAmortizacion(Long amortizacionId, String comentario);
    Amortizacion rechazarAmortizacion(Long amortizacionId, String comentario);
    List<Amortizacion> listarAmortizacionesPendientes();
    List<Amortizacion> listarAmortizacionesCredito(Long creditoId);
    Amortizacion obtenerAmortizacion(Long id);
    List<Pago> listarPagosPendientes();
    Pago obtenerPago(Long id);
    List<Pago> listarPagosCuota(Long cuotaId);
    Optional<Credito> findById(Long id);
    Optional<CuotaCredito> findCuotaById(Long id);
    List<Credito> listarPorUsuario(Long usuarioId);
    List<Credito> listarTodos();
    List<Credito> listarPendientesAprobacion();
}
