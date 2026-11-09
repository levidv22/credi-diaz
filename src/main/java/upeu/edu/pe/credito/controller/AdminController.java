package upeu.edu.pe.credito.controller;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import upeu.edu.pe.credito.dto.AdminRegistroDTO;
import upeu.edu.pe.credito.dto.MorosoDTO;
import upeu.edu.pe.credito.dto.RedUsuarioDTO;
import upeu.edu.pe.credito.entity.*;
import upeu.edu.pe.credito.repository.BonificacionMlmRepository;
import upeu.edu.pe.credito.repository.CreditoRepository;
import upeu.edu.pe.credito.repository.GarantiaReferidoRepository;
import upeu.edu.pe.credito.repository.UsuarioRepository;
import upeu.edu.pe.credito.service.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Comparator;
import java.time.LocalDate;

@Controller
@RequestMapping("/admin")
public class AdminController {
    private final UsuarioService usuarioService;
    private final CreditoRepository creditoRepository;
    private final GarantiaReferidoRepository garantiaReferidoRepository;
    private final CreditoService creditoService;
    private final RedGarantiaService redGarantiaService;
    private final AporteAhorroService aporteAhorroService;
    private final ReembolsoAhorroService reembolsoAhorroService;
    private final UsuarioRepository usuarioRepository;
    private final BonificacionMlmRepository bonificacionMlmRepository;
    private final PasswordEncoder passwordEncoder;

    public AdminController(UsuarioService usuarioService, CreditoRepository creditoRepository,
                           GarantiaReferidoRepository garantiaReferidoRepository, CreditoService creditoService,
                           RedGarantiaService redGarantiaService, AporteAhorroService aporteAhorroService,
                           ReembolsoAhorroService reembolsoAhorroService,
                           UsuarioRepository usuarioRepository,
                           BonificacionMlmRepository bonificacionMlmRepository,
                           PasswordEncoder passwordEncoder) {
        this.usuarioService = usuarioService; this.creditoRepository = creditoRepository;
        this.garantiaReferidoRepository = garantiaReferidoRepository; this.creditoService = creditoService; this.redGarantiaService = redGarantiaService; this.aporteAhorroService = aporteAhorroService; this.reembolsoAhorroService = reembolsoAhorroService;
        this.usuarioRepository = usuarioRepository;
        this.bonificacionMlmRepository = bonificacionMlmRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @GetMapping("/registro")
    public String mostrarRegistroAdmin(Model model) {
        if (!model.containsAttribute("adminRegistroDTO")) model.addAttribute("adminRegistroDTO", new AdminRegistroDTO());
        return "admin/registro-inicial";
    }

    @PostMapping("/registro-inicial")
    public String registrarAdmin(@ModelAttribute AdminRegistroDTO dto, RedirectAttributes ra) {
        try { usuarioService.registrarAdminInicial(dto); ra.addFlashAttribute("mensajeExito", "Administrador creado correctamente. Inicie sesión."); return "redirect:/login"; }
        catch (IllegalArgumentException | IllegalStateException e) { ra.addFlashAttribute("mensajeError", e.getMessage()); return "redirect:/admin/registro"; }
    }

    @GetMapping("/dashboard")
    @PreAuthorize("hasRole('ADMIN')")
    public String dashboard(Model model) {

        // Obtener todos los créditos una sola vez.
        List<Credito> todosLosCreditos = creditoService.listarTodos().stream()
                .filter(c -> c.getModalidad() == ModalidadPago.SEMANAL_COMPUESTO
                        || c.getModalidad() == ModalidadPago.MENSUAL_SIMPLE)
                .toList();

        // Fecha actual de Perú.
        LocalDate hoy = LocalDate.now(
                java.time.ZoneId.of("America/Lima")
        );

        // Créditos activos y créditos actualmente en mora.
        List<Credito> activos = todosLosCreditos.stream()
                .filter(c ->
                        c.getEstado() == EstadoCredito.ACTIVO
                                || c.getEstado() == EstadoCredito.EN_MORA
                )
                // Ordenar por fecha de vencimiento.
                .sorted(Comparator.comparing((Credito c) -> {
                    CuotaCredito cuota = c.getProximaCuotaPendiente();

                    return cuota != null && cuota.getFechaVencimiento() != null
                            ? cuota.getFechaVencimiento()
                            : LocalDate.MAX;

                }).thenComparing(Credito::getId))
                .toList();

        // Créditos completamente pagados.
        List<Credito> pagados = todosLosCreditos.stream()
                .filter(c -> c.getEstado() == EstadoCredito.PAGADO)
                .sorted(
                        Comparator.comparing(
                                Credito::getFechaFin,
                                Comparator.nullsLast(Comparator.reverseOrder())
                        )
                )
                .toList();

        // Contadores de la cartera activa.
        long vencenHoy = activos.stream()
                .filter(c -> {
                    CuotaCredito cuota = c.getProximaCuotaPendiente();

                    return cuota != null
                            && cuota.getFechaVencimiento() != null
                            && cuota.getFechaVencimiento().isEqual(hoy);
                })
                .count();

        long proximosAVencer = activos.stream()
                .filter(c -> {
                    CuotaCredito cuota = c.getProximaCuotaPendiente();

                    return cuota != null
                            && cuota.getFechaVencimiento() != null
                            && cuota.getFechaVencimiento().isAfter(hoy);
                })
                .count();

        long morosos = activos.stream()
                .filter(c -> {
                    CuotaCredito cuota = c.getProximaCuotaPendiente();

                    return cuota != null
                            && cuota.getFechaVencimiento() != null
                            && cuota.getFechaVencimiento().isBefore(hoy);
                })
                .count();

        // Información para la vista.
        model.addAttribute("creditosActivos", activos);
        model.addAttribute("creditosPagados", pagados);

        model.addAttribute("totalActivos", activos.size());
        model.addAttribute("totalPagados", pagados.size());

        model.addAttribute("vencenHoy", vencenHoy);
        model.addAttribute("proximosAVencer", proximosAVencer);
        model.addAttribute("totalMorosos", morosos);

        model.addAttribute("hoy", hoy);

        return "admin/dashboard";
    }

    @GetMapping("/creditos/{id}/cuota/{cuotaId}/pagar")
    @PreAuthorize("hasRole('ADMIN')")
    public String formularioPagoAdmin(@PathVariable Long id, @PathVariable Long cuotaId, Model model, RedirectAttributes ra) {
        try {
            Credito credito = creditoService.findById(id).orElseThrow(() -> new IllegalArgumentException("Crédito no encontrado."));
            CuotaCredito cuota = creditoService.findCuotaById(cuotaId).orElseThrow(() -> new IllegalArgumentException("Cuota no encontrada."));
            if (!cuota.getCredito().getId().equals(credito.getId())) throw new IllegalArgumentException("La cuota no pertenece al crédito.");
            model.addAttribute("credito", credito);
            model.addAttribute("cuota", cuota);
            model.addAttribute("metodosPago", List.of(MetodoPago.EFECTIVO, MetodoPago.YAPE, MetodoPago.PLIN, MetodoPago.TRANSFERENCIA));
            return "admin/pago-cuota";
        } catch (Exception e) {
            ra.addFlashAttribute("mensajeError", e.getMessage());
            return "redirect:/admin/dashboard";
        }
    }

    @PostMapping(value="/creditos/{id}/cuota/{cuotaId}/pagar", consumes="multipart/form-data")
    @PreAuthorize("hasRole('ADMIN')")
    public String pagarCuotaAdmin(@PathVariable Long id, @PathVariable Long cuotaId, @RequestParam BigDecimal monto,
                                  @RequestParam MetodoPago metodo, @RequestParam(required=false) MultipartFile comprobante,
                                  @RequestParam(required=false) String comentario, RedirectAttributes ra) {
        try {
            CuotaCredito cuota = creditoService.findCuotaById(cuotaId)
                    .orElseThrow(() -> new IllegalArgumentException("Cuota no encontrada."));
            if (!cuota.getCredito().getId().equals(id)) {
                throw new IllegalArgumentException("La cuota no pertenece al crédito indicado.");
            }
            byte[] bytes = comprobante != null && !comprobante.isEmpty() ? comprobante.getBytes() : null;
            String contentType = comprobante != null && !comprobante.isEmpty() ? comprobante.getContentType() : null;
            creditoService.registrarPagoAdmin(cuotaId, monto, metodo, bytes, contentType, comentario);
            ra.addFlashAttribute("mensajeExito", "Cuota pagada correctamente por ADMIN.");
        } catch (Exception e) {
            ra.addFlashAttribute("mensajeError", e.getMessage());
            return "redirect:/admin/creditos/" + id + "/cuota/" + cuotaId + "/pagar";
        }
        return "redirect:/admin/dashboard";
    }

    @GetMapping("/creditos/{id}/amortizar")
    @PreAuthorize("hasRole('ADMIN')")
    public String formularioAmortizacionAdmin(@PathVariable Long id, Model model, RedirectAttributes ra) {
        try {
            Credito credito = creditoService.findById(id).orElseThrow(() -> new IllegalArgumentException("Crédito no encontrado."));
            BigDecimal monto = creditoService.calcularMontoAmortizacion(id);
            model.addAttribute("credito", credito);
            model.addAttribute("montoAmortizacion", monto);
            model.addAttribute("metodosPago", List.of(MetodoPago.EFECTIVO, MetodoPago.YAPE, MetodoPago.PLIN, MetodoPago.TRANSFERENCIA));
            return "admin/amortizar-credito";
        } catch (Exception e) {
            ra.addFlashAttribute("mensajeError", e.getMessage());
            return "redirect:/admin/dashboard";
        }
    }

    @PostMapping(value="/creditos/{id}/amortizar", consumes="multipart/form-data")
    @PreAuthorize("hasRole('ADMIN')")
    public String amortizarAdmin(@PathVariable Long id, @RequestParam BigDecimal monto,
                                 @RequestParam MetodoPago metodo,
                                 @RequestParam(required=false) MultipartFile comprobante,
                                 @RequestParam(required=false) String comentario,
                                 RedirectAttributes ra) {
        try {
            byte[] bytes = comprobante != null && !comprobante.isEmpty() ? comprobante.getBytes() : null;
            String contentType = comprobante != null && !comprobante.isEmpty() ? comprobante.getContentType() : null;
            creditoService.registrarAmortizacionAdmin(id, monto, metodo, bytes, contentType, comentario);
            ra.addFlashAttribute("mensajeExito", "Amortización registrada por ADMIN y crédito liquidado correctamente.");
        } catch (Exception e) {
            ra.addFlashAttribute("mensajeError", e.getMessage());
            return "redirect:/admin/creditos/" + id + "/amortizar";
        }
        return "redirect:/admin/dashboard";
    }

    @GetMapping("/usuarios")
    @PreAuthorize("hasRole('ADMIN')")
    public String listarUsuarios(Model model) { model.addAttribute("usuarios", usuarioService.listarTodos()); return "admin/usuarios"; }

    @PostMapping("/usuarios/{id}/editar")
    @PreAuthorize("hasRole('ADMIN')")
    public String editarUsuario(
            @PathVariable Long id,
            @RequestParam String nombreCompleto,
            @RequestParam String dni,
            @RequestParam String celular,
            @RequestParam String domicilio,
            @RequestParam String email,
            @RequestParam BigDecimal puntos,
            @RequestParam(required = false, defaultValue = "") String nuevaPassword,
            RedirectAttributes ra) {

        try {
            Usuario usuario = usuarioRepository.findById(id)
                    .orElseThrow(() ->
                            new IllegalArgumentException("Usuario no encontrado."));

            if (usuario.getRol() != Rol.CLIENTE) {
                throw new IllegalArgumentException(
                        "Solo se pueden editar los datos de los clientes desde esta sección."
                );
            }

            // Validaciones de datos personales.
            if (nombreCompleto == null || nombreCompleto.isBlank()) {
                throw new IllegalArgumentException("El nombre completo es obligatorio.");
            }

            if (dni == null || !dni.trim().matches("\\d{8}")) {
                throw new IllegalArgumentException("El DNI debe contener exactamente 8 dígitos.");
            }

            if (celular == null || celular.isBlank()) {
                throw new IllegalArgumentException("El celular es obligatorio.");
            }

            if (domicilio == null || domicilio.isBlank()) {
                throw new IllegalArgumentException("El domicilio es obligatorio.");
            }

            if (email == null || !email.trim().matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
                throw new IllegalArgumentException("El correo electrónico no es válido.");
            }

            // Evitar DNI duplicados.
            usuarioRepository.findByDni(dni.trim()).ifPresent(existente -> {
                if (!existente.getId().equals(id)) {
                    throw new IllegalArgumentException("El DNI ya pertenece a otro usuario.");
                }
            });

            // Evitar correos duplicados.
            usuarioRepository.findByEmail(email.trim().toLowerCase()).ifPresent(existente -> {
                if (!existente.getId().equals(id)) {
                    throw new IllegalArgumentException("El correo ya pertenece a otro usuario.");
                }
            });

            // Validar puntos.
            if (puntos == null || puntos.signum() < 0) {
                throw new IllegalArgumentException(
                        "Los puntos deben ser un número mayor o igual a cero."
                );
            }

            BigDecimal puntosNormalizados =
                    puntos.setScale(3, RoundingMode.HALF_UP);

            if (puntosNormalizados.precision()
                    - puntosNormalizados.scale() > 12) {
                throw new IllegalArgumentException(
                        "El valor de puntos supera el límite permitido."
                );
            }

            // Actualizar datos personales.
            usuario.setNombreCompleto(nombreCompleto.trim());
            usuario.setDni(dni.trim());
            usuario.setCelular(celular.trim());
            usuario.setDomicilio(domicilio.trim());
            usuario.setEmail(email.trim().toLowerCase());

            // Actualizar puntos.
            usuario.setPuntosAcumulados(puntosNormalizados);

            // Solo cambiar la contraseña si el ADMIN escribió una nueva.
            if (nuevaPassword != null && !nuevaPassword.isBlank()) {
                if (nuevaPassword.length() < 8) {
                    throw new IllegalArgumentException(
                            "La nueva contraseña debe tener al menos 8 caracteres."
                    );
                }

                usuario.setPassword(passwordEncoder.encode(nuevaPassword));
            }

            usuarioRepository.save(usuario);

            ra.addFlashAttribute(
                    "mensajeExito",
                    "Los datos de " + usuario.getNombreCompleto()
                            + " se actualizaron correctamente."
            );

        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("mensajeError", e.getMessage());
        }

        return "redirect:/admin/usuarios";
    }

    @PostMapping("/usuarios/aprobar-limite")
    @PreAuthorize("hasRole('ADMIN')")
    public String aprobarLimite(@RequestParam Long usuarioId, @RequestParam BigDecimal nuevoLimite, RedirectAttributes ra) {
        try { usuarioService.evaluarAumentoLimite(usuarioId, nuevoLimite); ra.addFlashAttribute("mensajeExito", "Límite actualizado."); }
        catch (IllegalArgumentException | IllegalStateException e) { ra.addFlashAttribute("mensajeError", e.getMessage()); }
        return "redirect:/admin/usuarios";
    }

    @GetMapping("/morosos")
    @PreAuthorize("hasRole('ADMIN')")
    public String listarMorosos(Model model) {
        List<MorosoDTO> reporte = new ArrayList<>();
        creditoRepository.findByEstado(EstadoCredito.EN_MORA).forEach(c -> {
            GarantiaReferido g = garantiaReferidoRepository.findByCredito(c).orElse(null);
            BigDecimal saldo = c.getCuotas().stream().filter(q -> !q.isPagado()).map(CuotaCredito::getMontoTotalAPagar).reduce(BigDecimal.ZERO, BigDecimal::add);
            reporte.add(new MorosoDTO(c, g, saldo));
        });
        model.addAttribute("morosos", reporte); return "admin/morosos";
    }

    @PostMapping("/usuarios/{id}/marcar-no-habido")
    @PreAuthorize("hasRole('ADMIN')")
    public String marcarNoHabido(@PathVariable Long id, RedirectAttributes ra) {
        try { usuarioService.marcarComoNoHabido(id); ra.addFlashAttribute("mensajeExito", "Usuario marcado como NO HABIDO y garantía elevada al 100%."); }
        catch (IllegalArgumentException | IllegalStateException e) { ra.addFlashAttribute("mensajeError", e.getMessage()); }
        return "redirect:/admin/morosos";
    }

    @PostMapping("/garantias/{id}/cobrar")
    @PreAuthorize("hasRole('ADMIN')")
    public String cobrarGarantia(@PathVariable Long id, RedirectAttributes ra) {
        try {
            GarantiaReferido g = garantiaReferidoRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Garantía no encontrada."));
            redGarantiaService.listarNotificacionesPendientesPorGarante(g.getGarante().getId()).stream()
                    .filter(n -> n.getGarantia() != null && n.getGarantia().getId().equals(id)).findFirst()
                    .ifPresentOrElse(n -> redGarantiaService.cobrarGarantiaAGarante(n.getId(), g.getGarante().getId()),
                            () -> { throw new IllegalStateException("No existe una notificación pendiente para esta garantía."); });
            ra.addFlashAttribute("mensajeExito", "Cobro de garantía procesado.");
        } catch (IllegalArgumentException | IllegalStateException e) { ra.addFlashAttribute("mensajeError", e.getMessage()); }
        return "redirect:/admin/morosos";
    }

    @GetMapping("/aportes-ahorro")
    @PreAuthorize("hasRole('ADMIN')")
    public String aportesAhorro(Model model) {

        model.addAttribute(
                "aportes",
                aporteAhorroService.listarTodos()
        );

        return "admin/aportes-ahorro";
    }

    @PostMapping("/aportes-ahorro/{id}/aprobar")
    @PreAuthorize("hasRole('ADMIN')")
    public String aprobarAporteAhorro(
            @PathVariable Long id,
            RedirectAttributes ra) {

        try {
            aporteAhorroService.aprobar(id);
            ra.addFlashAttribute(
                    "mensajeExito",
                    "Aporte aprobado y ahorro acreditado correctamente.");
        } catch (IllegalArgumentException | IllegalStateException e) {
            ra.addFlashAttribute("mensajeError", e.getMessage());
        }

        return "redirect:/admin/aportes-ahorro";
    }

    @PostMapping("/aportes-ahorro/{id}/rechazar")
    @PreAuthorize("hasRole('ADMIN')")
    public String rechazarAporteAhorro(
            @PathVariable Long id,
            @RequestParam("comentario") String comentario,
            RedirectAttributes ra) {

        try {
            aporteAhorroService.rechazar(id, comentario);
            ra.addFlashAttribute(
                    "mensajeExito",
                    "El aporte fue rechazado.");
        } catch (IllegalArgumentException | IllegalStateException e) {
            ra.addFlashAttribute("mensajeError", e.getMessage());
        }

        return "redirect:/admin/aportes-ahorro";
    }

    @GetMapping("/reembolsos-ahorro")
    @PreAuthorize("hasRole('ADMIN')")
    public String reembolsosAhorro(Model model) {
        model.addAttribute("reembolsos", reembolsoAhorroService.listarTodos());
        return "admin/reembolsos-ahorro";
    }

    @PostMapping(value = "/reembolsos-ahorro/{id}/aprobar", consumes = "multipart/form-data")
    @PreAuthorize("hasRole('ADMIN')")
    public String aprobarReembolsoAhorro(
            @PathVariable Long id,
            @RequestParam(value = "comprobante", required = false) MultipartFile comprobante,
            RedirectAttributes ra) {
        try {
            reembolsoAhorroService.aprobar(id, comprobante);
            ra.addFlashAttribute("mensajeExito",
                    "Reembolso aprobado. Se descontaron S/ 100.00 del ahorro del cliente.");
        } catch (IllegalArgumentException | IllegalStateException e) {
            ra.addFlashAttribute("mensajeError", e.getMessage());
        }
        return "redirect:/admin/reembolsos-ahorro";
    }

    @PostMapping("/reembolsos-ahorro/{id}/rechazar")
    @PreAuthorize("hasRole('ADMIN')")
    public String rechazarReembolsoAhorro(
            @PathVariable Long id,
            @RequestParam("motivo") String motivo,
            RedirectAttributes ra) {
        try {
            reembolsoAhorroService.rechazar(id, motivo);
            ra.addFlashAttribute("mensajeExito", "La solicitud de reembolso fue rechazada.");
        } catch (IllegalArgumentException | IllegalStateException e) {
            ra.addFlashAttribute("mensajeError", e.getMessage());
        }
        return "redirect:/admin/reembolsos-ahorro";
    }

    /**
     * Devuelve el comprobante directamente al navegador para mostrarlo
     * dentro de la vista de reembolsos (sin descargarlo ni abrir otra página).
     */

    @PostMapping("/aportes-ahorro/{id}/eliminar-rechazado")
    @PreAuthorize("hasRole('ADMIN')")
    public String eliminarAporteRechazado(@PathVariable Long id, RedirectAttributes ra) {
        try {
            aporteAhorroService.eliminarRechazado(id);
            ra.addFlashAttribute("mensajeExito", "El aporte rechazado fue eliminado.");
        } catch (IllegalArgumentException | IllegalStateException e) {
            ra.addFlashAttribute("mensajeError", e.getMessage());
        }
        return "redirect:/admin/aportes-ahorro";
    }

    @PostMapping("/aportes-ahorro/rechazados/eliminar-todos")
    @PreAuthorize("hasRole('ADMIN')")
    public String eliminarTodosLosAportesRechazados(RedirectAttributes ra) {
        try {
            int cantidad = aporteAhorroService.eliminarTodosLosRechazados();
            ra.addFlashAttribute("mensajeExito", cantidad == 0
                    ? "No hay aportes rechazados para eliminar."
                    : "Se eliminaron " + cantidad + " aportes rechazados.");
        } catch (IllegalArgumentException | IllegalStateException e) {
            ra.addFlashAttribute("mensajeError", e.getMessage());
        }
        return "redirect:/admin/aportes-ahorro";
    }

    @PostMapping("/reembolsos-ahorro/{id}/eliminar-rechazado")
    @PreAuthorize("hasRole('ADMIN')")
    public String eliminarReembolsoRechazado(@PathVariable Long id, RedirectAttributes ra) {
        try {
            reembolsoAhorroService.eliminarRechazado(id);
            ra.addFlashAttribute("mensajeExito", "La solicitud de reembolso rechazada fue eliminada.");
        } catch (IllegalArgumentException | IllegalStateException e) {
            ra.addFlashAttribute("mensajeError", e.getMessage());
        }
        return "redirect:/admin/reembolsos-ahorro";
    }

    @PostMapping("/reembolsos-ahorro/rechazados/eliminar-todos")
    @PreAuthorize("hasRole('ADMIN')")
    public String eliminarTodosLosReembolsosRechazados(RedirectAttributes ra) {
        try {
            int cantidad = reembolsoAhorroService.eliminarTodosLosRechazados();
            ra.addFlashAttribute("mensajeExito", cantidad == 0
                    ? "No hay solicitudes de reembolso rechazadas para eliminar."
                    : "Se eliminaron " + cantidad + " solicitudes de reembolso rechazadas.");
        } catch (IllegalArgumentException | IllegalStateException e) {
            ra.addFlashAttribute("mensajeError", e.getMessage());
        }
        return "redirect:/admin/reembolsos-ahorro";
    }

    @GetMapping("/reembolsos-ahorro/{id}/comprobante")
    @PreAuthorize("hasRole('ADMIN')")
    public org.springframework.http.ResponseEntity<byte[]> comprobanteReembolso(
            @PathVariable Long id) {

        SolicitudReembolsoAhorro solicitud;
        try {
            solicitud = reembolsoAhorroService.buscarPorId(id);
        } catch (IllegalArgumentException | java.util.NoSuchElementException e) {
            return org.springframework.http.ResponseEntity.notFound().build();
        }

        byte[] archivo = solicitud.getComprobante();
        String tipoGuardado = solicitud.getComprobanteContentType();

        if (archivo == null || archivo.length == 0) {
            return org.springframework.http.ResponseEntity.notFound().build();
        }

        // Normalizar el MIME type guardado en la base de datos.
        org.springframework.http.MediaType mediaType;
        try {
            mediaType = (tipoGuardado == null || tipoGuardado.isBlank())
                    ? org.springframework.http.MediaType.APPLICATION_OCTET_STREAM
                    : org.springframework.http.MediaType.parseMediaType(tipoGuardado.trim());
        } catch (org.springframework.http.InvalidMediaTypeException e) {
            mediaType = org.springframework.http.MediaType.APPLICATION_OCTET_STREAM;
        }

        // Mantener el nombre con extensión para que el navegador reconozca el formato.
        String extension = ".bin";
        if (org.springframework.http.MediaType.IMAGE_JPEG.includes(mediaType)) {
            extension = ".jpg";
        } else if (org.springframework.http.MediaType.IMAGE_PNG.includes(mediaType)) {
            extension = ".png";
        } else if (org.springframework.http.MediaType.IMAGE_GIF.includes(mediaType)) {
            extension = ".gif";
        } else if ("image/webp".equalsIgnoreCase(mediaType.toString())) {
            extension = ".webp";
        } else if (org.springframework.http.MediaType.APPLICATION_PDF.includes(mediaType)) {
            extension = ".pdf";
        }

        return org.springframework.http.ResponseEntity.ok()
                .header(
                        org.springframework.http.HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline()
                                .filename("comprobante-reembolso-" + id + extension)
                                .build()
                                .toString()
                )
                .header("X-Content-Type-Options", "nosniff")
                .header(org.springframework.http.HttpHeaders.CACHE_CONTROL, "private, no-cache, no-store, must-revalidate")
                .contentType(mediaType)
                .contentLength(archivo.length)
                .body(archivo);
    }

    @PostMapping("/usuarios/{id}/puntos")
    @PreAuthorize("hasRole('ADMIN')")
    public String actualizarPuntos(
            @PathVariable Long id,
            @RequestParam BigDecimal puntos,
            RedirectAttributes ra
    ) {
        try {
            Usuario usuario = usuarioRepository.findById(id)
                    .orElseThrow(() ->
                            new IllegalArgumentException(
                                    "Usuario no encontrado."
                            )
                    );

            if (puntos == null || puntos.signum() < 0) {
                throw new IllegalArgumentException(
                        "Los puntos deben ser un número mayor o igual a cero."
                );
            }

            BigDecimal puntosNormalizados =
                    puntos.setScale(3, RoundingMode.HALF_UP);

            // La columna puntos_acumulados admite DECIMAL(15,3).
            if (puntosNormalizados.precision()
                    - puntosNormalizados.scale() > 12) {
                throw new IllegalArgumentException(
                        "El valor de puntos supera el límite permitido."
                );
            }

            usuario.setPuntosAcumulados(puntosNormalizados);
            usuarioRepository.save(usuario);

            ra.addFlashAttribute(
                    "mensajeExito",
                    "Puntos de " + usuario.getNombreCompleto()
                            + " actualizados correctamente."
            );

        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("mensajeError", e.getMessage());
        }

        return "redirect:/admin/usuarios";
    }


    /*
     * ÁRBOL MLM: ADMINISTRADOR PRINCIPAL Y REFERIDOS DIRECTOS
     */
    @GetMapping("/red/raiz")
    @ResponseBody
    @PreAuthorize("hasRole('ADMIN')")
    public RedUsuarioDTO obtenerRaizRed() {

        Usuario raiz = usuarioRepository
                .findFirstByRolOrderByIdAsc(Rol.ADMIN)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "No existe un usuario administrador."
                        )
                );

        // Solo cargamos el primer nivel de la red.
        List<RedUsuarioDTO> hijos = usuarioRepository
                .findByPatrocinadorOrderByNombreCompletoAsc(raiz)
                .stream()
                .map(this::convertirUsuarioRed)
                .toList();

        return convertirUsuarioRed(raiz, hijos);
    }


    /*
     * ÁRBOL MLM: CARGAR LOS REFERIDOS DE UN USUARIO
     * Se llama únicamente cuando el administrador despliega ese nodo.
     */
    @GetMapping("/red/{id}/hijos")
    @ResponseBody
    @PreAuthorize("hasRole('ADMIN')")
    public List<RedUsuarioDTO> obtenerHijosRed(@PathVariable Long id) {

        Usuario patrocinador = usuarioRepository.findById(id)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Usuario no encontrado."
                        )
                );

        return usuarioRepository
                .findByPatrocinadorOrderByNombreCompletoAsc(patrocinador)
                .stream()
                .map(this::convertirUsuarioRed)
                .toList();
    }


    /*
     * CONVERSIÓN SEGURA A DTO
     */
    private RedUsuarioDTO convertirUsuarioRed(Usuario usuario) {

        return convertirUsuarioRed(
                usuario,
                Collections.emptyList()
        );
    }


    private RedUsuarioDTO convertirUsuarioRed(
            Usuario usuario,
            List<RedUsuarioDTO> hijos
    ) {
        BigDecimal puntos = usuario.getPuntosAcumulados() == null
                ? BigDecimal.ZERO
                : usuario.getPuntosAcumulados();

        BigDecimal totalBonos =
                bonificacionMlmRepository.sumarBonosPorBeneficiario(
                        usuario.getId()
                );

        if (totalBonos == null) {
            totalBonos = BigDecimal.ZERO;
        }

        long cantidadDirectos =
                usuarioRepository.countByPatrocinador(usuario);

        return new RedUsuarioDTO(
                usuario.getId(),
                usuario.getNombreCompleto(),
                usuario.getRango() == null
                        ? "SIN RANGO"
                        : usuario.getRango().name(),
                puntos,
                totalBonos,
                cantidadDirectos,
                cantidadDirectos > 0,
                hijos
        );
    }
}
