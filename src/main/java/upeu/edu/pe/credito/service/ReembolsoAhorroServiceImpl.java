package upeu.edu.pe.credito.service;

import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import upeu.edu.pe.credito.entity.*;
import upeu.edu.pe.credito.repository.SolicitudReembolsoAhorroRepository;
import upeu.edu.pe.credito.repository.UsuarioRepository;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class ReembolsoAhorroServiceImpl implements ReembolsoAhorroService {

    private static final BigDecimal MONTO_REEMBOLSO = new BigDecimal("100.00");

    private final SolicitudReembolsoAhorroRepository reembolsoRepository;
    private final UsuarioRepository usuarioRepository;
    private final NotificacionService notificacionService;

    public ReembolsoAhorroServiceImpl(
            SolicitudReembolsoAhorroRepository reembolsoRepository,
            UsuarioRepository usuarioRepository,
            NotificacionService notificacionService) {
        this.reembolsoRepository = reembolsoRepository;
        this.usuarioRepository = usuarioRepository;
        this.notificacionService = notificacionService;
    }

    @Override
    @Transactional
    public SolicitudReembolsoAhorro solicitar(
            Long usuarioId,
            MetodoReembolsoAhorro metodo,
            String titularCuenta,
            String numeroCuenta,
            String cci,
            String numeroYape) {

        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado."));

        if (usuario.getRol() != Rol.CLIENTE) {
            throw new IllegalStateException("Solo los clientes pueden solicitar reembolsos.");
        }

        BigDecimal saldo = usuario.getSaldoAhorro() == null
                ? BigDecimal.ZERO : usuario.getSaldoAhorro();

        if (saldo.compareTo(MONTO_REEMBOLSO) < 0) {
            throw new IllegalStateException(
                    "Necesitas tener al menos S/ 100.00 de ahorro disponible para solicitar el reembolso.");
        }

        if (reembolsoRepository.existsByUsuarioAndEstado(
                usuario, EstadoReembolsoAhorro.PENDIENTE)) {
            throw new IllegalStateException(
                    "Ya tienes una solicitud de reembolso pendiente de revisión.");
        }

        if (metodo == null) {
            throw new IllegalArgumentException("Selecciona el método para recibir el reembolso.");
        }

        if (titularCuenta == null || titularCuenta.isBlank()) {
            throw new IllegalArgumentException("Ingresa el nombre del titular.");
        }

        SolicitudReembolsoAhorro solicitud = new SolicitudReembolsoAhorro();
        solicitud.setUsuario(usuario);
        solicitud.setMonto(MONTO_REEMBOLSO);
        solicitud.setMetodo(metodo);
        solicitud.setTitularCuenta(titularCuenta.trim());

        if (metodo == MetodoReembolsoAhorro.BCP) {
            String cuenta = soloDigitos(numeroCuenta);
            String codigoCci = soloDigitos(cci);

            if (cuenta.isBlank()) {
                throw new IllegalArgumentException("Ingresa tu número de cuenta BCP.");
            }
            if (!codigoCci.matches("\\d{20}")) {
                throw new IllegalArgumentException("El CCI debe contener exactamente 20 dígitos.");
            }

            solicitud.setNumeroCuenta(cuenta);
            solicitud.setCci(codigoCci);
        } else if (metodo == MetodoReembolsoAhorro.YAPE) {
            String celular = soloDigitos(numeroYape);
            if (!celular.matches("\\d{9}")) {
                throw new IllegalArgumentException("El número de Yape debe contener 9 dígitos.");
            }
            solicitud.setNumeroYape(celular);
        }

        solicitud.setEstado(EstadoReembolsoAhorro.PENDIENTE);
        solicitud.setFechaSolicitud(LocalDateTime.now());

        SolicitudReembolsoAhorro guardada = reembolsoRepository.save(solicitud);

        notificacionService.notificarAdministradores(
                "Nueva solicitud de reembolso de ahorro",
                usuario.getNombreCompleto() + " solicitó el reembolso de S/ 100.00.",
                "/admin/reembolsos-ahorro"
        );

        return guardada;
    }

    private String soloDigitos(String valor) {
        return valor == null ? "" : valor.replaceAll("\\D", "");
    }

    @Override
    public List<SolicitudReembolsoAhorro> listarTodos() {
        return reembolsoRepository.findAllByOrderByFechaSolicitudDesc();
    }

    @Override
    public List<SolicitudReembolsoAhorro> listarPendientes() {
        return reembolsoRepository.findByEstadoOrderByFechaSolicitudAsc(
                EstadoReembolsoAhorro.PENDIENTE);
    }

    @Override
    public SolicitudReembolsoAhorro buscarPorId(Long id) {
        return reembolsoRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Solicitud de reembolso no encontrada."));
    }

    @Override
    public boolean tieneSolicitudPendiente(Long usuarioId) {
        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado."));
        return reembolsoRepository.existsByUsuarioAndEstado(
                usuario, EstadoReembolsoAhorro.PENDIENTE);
    }

    @Override
    @Transactional
    public SolicitudReembolsoAhorro aprobar(
            Long solicitudId, MultipartFile comprobante) {

        SolicitudReembolsoAhorro solicitud = reembolsoRepository.findById(solicitudId)
                .orElseThrow(() -> new IllegalArgumentException("Solicitud de reembolso no encontrada."));

        if (solicitud.getEstado() != EstadoReembolsoAhorro.PENDIENTE) {
            throw new IllegalStateException("Esta solicitud ya fue revisada.");
        }

        Usuario usuario = usuarioRepository.findById(solicitud.getUsuario().getId())
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado."));

        BigDecimal saldoActual = usuario.getSaldoAhorro() == null
                ? BigDecimal.ZERO : usuario.getSaldoAhorro();

        if (saldoActual.compareTo(MONTO_REEMBOLSO) < 0) {
            throw new IllegalStateException(
                    "El saldo de ahorro ya no alcanza para procesar el reembolso de S/ 100.00.");
        }

        if (comprobante != null && !comprobante.isEmpty()) {
            if (comprobante.getSize() > 5 * 1024 * 1024) {
                throw new IllegalArgumentException("El comprobante no puede superar los 5 MB.");
            }

            String tipo = comprobante.getContentType();
            if (tipo == null || !(tipo.equalsIgnoreCase("image/jpeg")
                    || tipo.equalsIgnoreCase("image/png")
                    || tipo.equalsIgnoreCase("image/webp")
                    || tipo.equalsIgnoreCase("application/pdf"))) {
                throw new IllegalArgumentException(
                        "El comprobante debe ser JPG, PNG, WEBP o PDF.");
            }

            try {
                solicitud.setComprobante(comprobante.getBytes());
                solicitud.setComprobanteContentType(tipo);
            } catch (IOException e) {
                throw new IllegalStateException("No se pudo leer el comprobante adjunto.", e);
            }
        }

        BigDecimal saldoRestante = saldoActual.subtract(MONTO_REEMBOLSO);
        usuario.setSaldoAhorro(saldoRestante);

        /*
         * Si queda saldo, la activación por ahorro se conserva.
         * Si el saldo queda en cero, se revierte la activación por ahorro.
         */
        if (saldoRestante.compareTo(BigDecimal.ZERO) == 0) {
            usuario.setActivoPorAhorro(false);
            usuario.setFechaVencimientoAhorro(null);
            usuario.setFechaActivacionMembresia(null);
            usuario.setFechaVencimientoMembresia(null);

            usuario.setEstado(EstadoUsuario.INACTIVO);
        }

        solicitud.setEstado(EstadoReembolsoAhorro.APROBADO);
        solicitud.setFechaRevision(LocalDateTime.now());

        usuarioRepository.save(usuario);
        SolicitudReembolsoAhorro guardada = reembolsoRepository.save(solicitud);

        notificacionService.crear(
                usuario,
                "Reembolso de ahorro realizado",
                "El ADMIN confirmó el reembolso de S/ 100.00. Tu saldo de ahorro actual es S/ "
                        + saldoRestante.toPlainString() + ".",
                "/usuarios/perfil"
        );

        notificacionService.notificarAdministradores(
                "Reembolso de ahorro aprobado",
                "Se aprobó el reembolso #" + guardada.getId()
                        + " de " + usuario.getNombreCompleto()
                        + " por S/ 100.00.",
                "/admin/reembolsos-ahorro"
        );

        return guardada;
    }

    @Override
    @Transactional
    public SolicitudReembolsoAhorro rechazar(Long solicitudId, String motivo) {

        SolicitudReembolsoAhorro solicitud = reembolsoRepository.findById(solicitudId)
                .orElseThrow(() -> new IllegalArgumentException("Solicitud de reembolso no encontrada."));

        if (solicitud.getEstado() != EstadoReembolsoAhorro.PENDIENTE) {
            throw new IllegalStateException("Esta solicitud ya fue revisada.");
        }

        if (motivo == null || motivo.isBlank()) {
            throw new IllegalArgumentException("Debes indicar el motivo del rechazo.");
        }

        solicitud.setEstado(EstadoReembolsoAhorro.RECHAZADO);
        solicitud.setMotivoRechazo(motivo.trim());
        solicitud.setFechaRevision(LocalDateTime.now());

        SolicitudReembolsoAhorro guardada = reembolsoRepository.save(solicitud);

        notificacionService.crear(
                solicitud.getUsuario(),
                "Solicitud de reembolso rechazada",
                "Tu solicitud de reembolso de S/ 100.00 fue rechazada. Motivo: "
                        + solicitud.getMotivoRechazo(),
                "/usuarios/perfil"
        );

        notificacionService.notificarAdministradores(
                "Solicitud de reembolso rechazada",
                "Se rechazó la solicitud #" + guardada.getId()
                        + ". Motivo: " + guardada.getMotivoRechazo(),
                "/admin/reembolsos-ahorro"
        );

        return guardada;
    }

    @Override
    @Transactional
    public void eliminarRechazado(Long solicitudId) {
        SolicitudReembolsoAhorro solicitud = reembolsoRepository.findById(solicitudId)
                .orElseThrow(() -> new IllegalArgumentException("Solicitud de reembolso no encontrada."));
        if (solicitud.getEstado() != EstadoReembolsoAhorro.RECHAZADO) {
            throw new IllegalStateException("Solo se pueden eliminar solicitudes de reembolso rechazadas.");
        }
        reembolsoRepository.delete(solicitud);
    }

    @Override
    @Transactional
    public int eliminarTodosLosRechazados() {
        List<SolicitudReembolsoAhorro> rechazados = reembolsoRepository.findAllByOrderByFechaSolicitudDesc().stream()
                .filter(r -> r.getEstado() == EstadoReembolsoAhorro.RECHAZADO)
                .toList();
        reembolsoRepository.deleteAll(rechazados);
        return rechazados.size();
    }
}
