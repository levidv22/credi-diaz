package upeu.edu.pe.credito.service;

import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import upeu.edu.pe.credito.entity.*;
import upeu.edu.pe.credito.repository.AporteAhorroRepository;
import upeu.edu.pe.credito.repository.UsuarioRepository;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class AporteAhorroServiceImpl implements AporteAhorroService {

    private static final BigDecimal MONTO_FIJO =
            new BigDecimal("100.00");

    private final AporteAhorroRepository aporteRepository;
    private final UsuarioRepository usuarioRepository;

    public AporteAhorroServiceImpl(
            AporteAhorroRepository aporteRepository,
            UsuarioRepository usuarioRepository) {
        this.aporteRepository = aporteRepository;
        this.usuarioRepository = usuarioRepository;
    }

    @Override
    @Transactional
    public AporteAhorro registrar(
            Long usuarioId,
            MetodoPago metodo,
            MultipartFile comprobante) {

        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() ->
                        new IllegalArgumentException("Usuario no encontrado."));

        if (usuario.getRol() != Rol.CLIENTE) {
            throw new IllegalStateException(
                    "Solo los clientes pueden solicitar la activación por ahorro.");
        }

        if (usuario.isActivoPorAhorro()
                && usuario.getFechaVencimientoAhorro() != null
                && !usuario.getFechaVencimientoAhorro()
                .isBefore(LocalDate.now())) {
            throw new IllegalStateException(
                    "Tu activación por ahorro ya está vigente.");
        }

        boolean pendiente = aporteRepository.existsByUsuarioAndEstado(
                usuario, EstadoAporteAhorro.PENDIENTE_APROBACION);

        if (pendiente) {
            throw new IllegalStateException(
                    "Ya tienes un aporte pendiente de revisión.");
        }

        if (metodo != MetodoPago.YAPE
                && metodo != MetodoPago.PLIN
                && metodo != MetodoPago.TRANSFERENCIA) {
            throw new IllegalArgumentException(
                    "Selecciona un método de pago válido.");
        }

        if (comprobante == null || comprobante.isEmpty()) {
            throw new IllegalArgumentException(
                    "Debes adjuntar el comprobante de pago.");
        }

        if (comprobante.getSize() > 5 * 1024 * 1024) {
            throw new IllegalArgumentException(
                    "El comprobante no puede superar los 5 MB.");
        }

        String contentType = comprobante.getContentType();

        if (contentType == null ||
                !(contentType.equalsIgnoreCase("image/jpeg")
                        || contentType.equalsIgnoreCase("image/png")
                        || contentType.equalsIgnoreCase("image/webp")
                        || contentType.equalsIgnoreCase("application/pdf"))) {
            throw new IllegalArgumentException(
                    "Adjunta una imagen JPG, PNG, WEBP o un PDF.");
        }

        try {
            AporteAhorro aporte = new AporteAhorro();
            aporte.setUsuario(usuario);
            aporte.setMonto(MONTO_FIJO);
            aporte.setMetodo(metodo);
            aporte.setEstado(EstadoAporteAhorro.PENDIENTE_APROBACION);
            aporte.setComprobante(comprobante.getBytes());
            aporte.setComprobanteContentType(contentType);
            aporte.setFechaSolicitud(LocalDateTime.now());

            return aporteRepository.save(aporte);

        } catch (IOException e) {
            throw new IllegalStateException(
                    "No se pudo leer el comprobante. Inténtalo nuevamente.", e);
        }
    }

    @Override
    public List<AporteAhorro> listarPendientes() {
        return aporteRepository.findByEstadoOrderByFechaSolicitudAsc(
                EstadoAporteAhorro.PENDIENTE_APROBACION);
    }

    @Override
    public List<AporteAhorro> listarPorUsuario(Long usuarioId) {
        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() ->
                        new IllegalArgumentException("Usuario no encontrado."));

        return aporteRepository.findByUsuarioOrderByFechaSolicitudDesc(usuario);
    }

    @Override
    public AporteAhorro buscarPorId(Long id) {
        return aporteRepository.findById(id)
                .orElseThrow(() ->
                        new IllegalArgumentException("Aporte no encontrado."));
    }

    @Override
    @Transactional
    public AporteAhorro aprobar(Long aporteId) {
        AporteAhorro aporte = aporteRepository.findById(aporteId)
                .orElseThrow(() ->
                        new IllegalArgumentException("Aporte no encontrado."));

        if (aporte.getEstado() != EstadoAporteAhorro.PENDIENTE_APROBACION) {
            throw new IllegalStateException(
                    "Este aporte ya fue revisado.");
        }

        Usuario usuario = usuarioRepository.findById(
                aporte.getUsuario().getId()).orElseThrow(() ->
                new IllegalArgumentException("Usuario no encontrado."));

        /*
         * El estado se cambia dentro de la misma transacción que la
         * actualización del saldo. Si algo falla, se revierte todo.
         */
        aporte.setEstado(EstadoAporteAhorro.APROBADO);
        aporte.setFechaRevision(LocalDateTime.now());

        usuario.setSaldoAhorro(
                (usuario.getSaldoAhorro() == null
                        ? BigDecimal.ZERO : usuario.getSaldoAhorro())
                        .add(aporte.getMonto()));

        if (usuario.getSaldoAhorro().compareTo(MONTO_FIJO) >= 0) {
            LocalDate hoy = LocalDate.now();
            usuario.setActivoPorAhorro(true);
            usuario.setEstado(EstadoUsuario.ACTIVO);
            usuario.setFechaVencimientoAhorro(hoy.plusYears(1));
            usuario.setFechaActivacionMembresia(hoy);
            usuario.setFechaVencimientoMembresia(hoy.plusYears(1));
        }

        usuarioRepository.save(usuario);
        return aporteRepository.save(aporte);
    }

    @Override
    @Transactional
    public AporteAhorro rechazar(Long aporteId, String comentario) {
        AporteAhorro aporte = aporteRepository.findById(aporteId)
                .orElseThrow(() ->
                        new IllegalArgumentException("Aporte no encontrado."));

        if (aporte.getEstado() != EstadoAporteAhorro.PENDIENTE_APROBACION) {
            throw new IllegalStateException(
                    "Este aporte ya fue revisado.");
        }

        if (comentario == null || comentario.isBlank()) {
            throw new IllegalArgumentException(
                    "Indica el motivo del rechazo.");
        }

        aporte.setEstado(EstadoAporteAhorro.RECHAZADO);
        aporte.setFechaRevision(LocalDateTime.now());
        aporte.setComentarioAdmin(comentario.trim());

        return aporteRepository.save(aporte);
    }
}
