package upeu.edu.pe.credito.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import upeu.edu.pe.credito.dto.AdminRegistroDTO;
import upeu.edu.pe.credito.dto.MorosoDTO;
import upeu.edu.pe.credito.entity.*;
import upeu.edu.pe.credito.repository.CreditoRepository;
import upeu.edu.pe.credito.repository.GarantiaReferidoRepository;
import upeu.edu.pe.credito.service.*;

import java.math.BigDecimal;
import java.util.ArrayList;
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

    public AdminController(UsuarioService usuarioService, CreditoRepository creditoRepository,
                           GarantiaReferidoRepository garantiaReferidoRepository, CreditoService creditoService,
                           RedGarantiaService redGarantiaService) {
        this.usuarioService = usuarioService; this.creditoRepository = creditoRepository;
        this.garantiaReferidoRepository = garantiaReferidoRepository; this.creditoService = creditoService; this.redGarantiaService = redGarantiaService;
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
        List<Credito> activos = creditoService.listarTodos().stream()
                .filter(c -> c.getEstado() == EstadoCredito.ACTIVO || c.getEstado() == EstadoCredito.EN_MORA)
                .sorted(Comparator.comparing((Credito c) -> {
                    CuotaCredito q = c.getProximaCuotaPendiente();
                    return q != null && q.getFechaVencimiento() != null ? q.getFechaVencimiento() : LocalDate.MAX;
                }).thenComparing(Credito::getId))
                .toList();
        List<Credito> pagados = creditoService.listarTodos().stream()
                .filter(c -> c.getEstado() == EstadoCredito.PAGADO)
                .sorted(Comparator.comparing(Credito::getFechaFin, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
        model.addAttribute("creditosActivos", activos);
        model.addAttribute("creditosPagados", pagados);
        model.addAttribute("totalActivos", activos.size());
        model.addAttribute("totalPagados", pagados.size());
        model.addAttribute("hoy", LocalDate.now());
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
}
