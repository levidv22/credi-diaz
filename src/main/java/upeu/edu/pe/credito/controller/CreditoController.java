package upeu.edu.pe.credito.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import upeu.edu.pe.credito.dto.SolicitudCreditoDTO;
import upeu.edu.pe.credito.entity.*;
import upeu.edu.pe.credito.service.CreditoService;
import upeu.edu.pe.credito.service.UsuarioService;
import upeu.edu.pe.credito.repository.GarantiaReferidoRepository;
import upeu.edu.pe.credito.repository.PromocionCreditoRapidoRepository;

import java.math.BigDecimal;
import java.security.Principal;
import java.util.List;

@Controller
@RequestMapping("/creditos")
public class CreditoController {
    private final CreditoService creditoService;
    private final UsuarioService usuarioService;
    private final GarantiaReferidoRepository garantiaReferidoRepository;
    private final PromocionCreditoRapidoRepository promocionesRapidas;

    public CreditoController(CreditoService creditoService, UsuarioService usuarioService, GarantiaReferidoRepository garantiaReferidoRepository, PromocionCreditoRapidoRepository promocionesRapidas) {
        this.creditoService = creditoService;
        this.usuarioService = usuarioService;
        this.garantiaReferidoRepository = garantiaReferidoRepository;
        this.promocionesRapidas = promocionesRapidas;
    }

    @GetMapping("/solicitar")
    public String mostrarFormularioSolicitud(Model model, Principal principal) {
        Usuario usuario = obtenerUsuarioPrincipal(principal);
        if (!model.containsAttribute("solicitud")) {
            SolicitudCreditoDTO dto = new SolicitudCreditoDTO();
            dto.setUsuarioId(usuario.getId());
            dto.setPlazoSemanas(12);
            dto.setPlazoMeses(1);
            model.addAttribute("solicitud", dto);
        }
        model.addAttribute("usuario", usuario);
        model.addAttribute("modalidadSemanalNivel", usuario.getRango() == RangoMlm.LIDER);
        model.addAttribute("modalidadSemanalTresDirectos", usuario.getRango() != RangoMlm.LIDER && usuario.getLimiteDirectos() <= 0);
        model.addAttribute("modalidades", ModalidadPago.values());
        model.addAttribute("mediosDesembolso", MedioDesembolso.values());
        model.addAttribute("whatsappAdmin", "51915092797");
        model.addAttribute("patrocinador", usuario.getPatrocinador());
        model.addAttribute("patrocinadorEsAdmin", usuario.getPatrocinador() != null && usuario.getPatrocinador().getRol() == Rol.ADMIN);
        return "creditos/solicitar";
    }

    @PostMapping("/solicitar")
    public String solicitarCredito(@ModelAttribute("solicitud") SolicitudCreditoDTO dto,
                                   Principal principal, RedirectAttributes ra, Model model) {
        Usuario usuario = obtenerUsuarioPrincipal(principal);
        try {
            if (!usuario.getId().equals(dto.getUsuarioId())) throw new IllegalStateException("No puede solicitar un crédito a nombre de otro usuario.");
            int plazoSeleccionado = dto.getModalidad() == ModalidadPago.SEMANAL_COMPUESTO
                    ? dto.getPlazoSemanas()
                    : dto.getPlazoMeses();
            Credito credito = creditoService.solicitarCredito(
                    usuario.getId(), dto.getMonto(), dto.getModalidad(), plazoSeleccionado,
                    dto.getMedioDesembolso(), dto.getNombreBanco(),
                    dto.getNumeroCuenta(), dto.getCci(), dto.getNombreBilletera(), dto.getNumeroBilletera());
            if (credito.getEstado() == EstadoCredito.PENDIENTE_APROBACION) {
                ra.addFlashAttribute("mensajeInfo", "La solicitud fue enviada a evaluación administrativa.");
                return "redirect:/creditos/mis-creditos";
            }
            ra.addFlashAttribute("mensajeExito", "Crédito aprobado y activado correctamente.");
            return "redirect:/creditos/detalle/" + credito.getId();
        } catch (IllegalArgumentException | IllegalStateException e) {
            model.addAttribute("mensajeError", e.getMessage());
            model.addAttribute("usuario", usuario);
            model.addAttribute("modalidadSemanalNivel", usuario.getRango() == RangoMlm.LIDER);
            model.addAttribute("modalidadSemanalTresDirectos", usuario.getRango() != RangoMlm.LIDER && usuario.getLimiteDirectos() <= 0);
            model.addAttribute("modalidades", ModalidadPago.values());
            model.addAttribute("mediosDesembolso", MedioDesembolso.values());
            model.addAttribute("whatsappAdmin", "51915092797");
            model.addAttribute("patrocinador", usuario.getPatrocinador());
            model.addAttribute("patrocinadorEsAdmin", usuario.getPatrocinador() != null && usuario.getPatrocinador().getRol() == Rol.ADMIN);
            return "creditos/solicitar";
        }
    }


    @GetMapping("/capacidad")
    @ResponseBody
    public java.util.Map<String, Object> capacidadActual(Principal principal) {
        Usuario usuario = obtenerUsuarioPrincipal(principal);
        return java.util.Map.of(
                "score", usuario.getScoreCrediticio(),
                "limite", usuario.getLimiteCreditoPermitido() == null ? BigDecimal.ZERO : usuario.getLimiteCreditoPermitido()
        );
    }

    @GetMapping("/detalle/{id}")
    public String verDetalle(@PathVariable Long id, Principal principal, Model model) {
        Credito credito = creditoService.findById(id).orElseThrow(() -> new IllegalArgumentException("Crédito no encontrado."));
        Usuario usuario = obtenerUsuarioPrincipal(principal);
        if (!credito.getUsuario().getId().equals(usuario.getId()) && usuario.getRol() != Rol.ADMIN) throw new IllegalStateException("No tiene autorización.");
        if (credito.getEstado() == EstadoCredito.PENDIENTE_APROBACION) {
            return "redirect:/creditos/mis-creditos?pendiente=" + credito.getId();
        }
        model.addAttribute("credito", credito);
        model.addAttribute("cuotas", credito.getCuotas());
        java.util.Map<Long, List<Pago>> pagosPorCuota = new java.util.HashMap<>();
        credito.getCuotas().forEach(q -> pagosPorCuota.put(q.getId(), creditoService.listarPagosCuota(q.getId())));
        model.addAttribute("pagosPorCuota", pagosPorCuota);
        model.addAttribute("metodosPago", MetodoPago.values());
        model.addAttribute("esAdmin", usuario.getRol() == Rol.ADMIN);
        List<Amortizacion> historialAmortizaciones = creditoService.listarAmortizacionesCredito(credito.getId());
        model.addAttribute("amortizaciones", historialAmortizaciones);
        boolean amortizacionPendiente = historialAmortizaciones.stream().anyMatch(a ->
                a.getEstado() == EstadoPago.PENDIENTE_APROBACION || a.getEstado() == EstadoPago.REVISION_MANUAL);
        model.addAttribute("amortizacionPendiente", amortizacionPendiente);
        if (credito.getEstado() == EstadoCredito.ACTIVO || credito.getEstado() == EstadoCredito.EN_MORA) {
            if (amortizacionPendiente) {
                // Evita mostrar el mensaje de validación como si fuera un error después de enviar la solicitud.
                model.addAttribute("montoAmortizacion", BigDecimal.ZERO);
            } else {
                try {
                    model.addAttribute("montoAmortizacion", creditoService.calcularMontoAmortizacion(credito.getId()));
                } catch (IllegalStateException e) {
                    model.addAttribute("montoAmortizacion", BigDecimal.ZERO);
                    model.addAttribute("mensajeAmortizacion", e.getMessage());
                }
            }
        }
        return "creditos/detalle";
    }

    @GetMapping("/mis-creditos")
    public String listarMisCreditos(Principal principal, Model model) {
        Usuario usuario = obtenerUsuarioPrincipal(principal);
        model.addAttribute("usuario", usuario);
        List<Credito> creditos = creditoService.listarPorUsuario(usuario.getId());
        model.addAttribute("creditos", creditos);
        boolean yaSolicitoCreditoRapido = creditos.stream().anyMatch(Credito::isCreditoPromocional);
        model.addAttribute("promocionesRapidas", yaSolicitoCreditoRapido ? java.util.List.of() :
                promocionesRapidas.findByActivaTrueOrderByFechaCreacionDesc().stream()
                .filter(p -> !p.getFechaExpiracion().isBefore(java.time.LocalDate.now())
                        && p.getDestinatarios().stream().anyMatch(d -> d.getId().equals(usuario.getId())))
                .toList());
        java.util.Set<Long> amortizacionesPendientes = new java.util.HashSet<>();
        for (Credito c : creditos) {
            boolean pendiente = creditoService.listarAmortizacionesCredito(c.getId()).stream().anyMatch(a ->
                    a.getEstado() == EstadoPago.PENDIENTE_APROBACION || a.getEstado() == EstadoPago.REVISION_MANUAL);
            if (pendiente) amortizacionesPendientes.add(c.getId());
        }
        model.addAttribute("creditosConAmortizacionPendiente", amortizacionesPendientes);
        return "creditos/lista";
    }

    @GetMapping("/cuotas/{id}/pagar")
    public String formularioPago(@PathVariable Long id, Principal principal, Model model, RedirectAttributes ra) {
        try {
            Usuario u=obtenerUsuarioPrincipal(principal); CuotaCredito cuota=creditoService.findCuotaById(id).orElseThrow(() -> new IllegalArgumentException("Cuota no encontrada."));
            if (!cuota.getCredito().getUsuario().getId().equals(u.getId())) throw new IllegalStateException("No tiene autorización.");
            boolean amortizacionPendiente = creditoService.listarAmortizacionesCredito(cuota.getCredito().getId()).stream().anyMatch(a ->
                    a.getEstado() == EstadoPago.PENDIENTE_APROBACION || a.getEstado() == EstadoPago.REVISION_MANUAL);
            if (amortizacionPendiente) throw new IllegalStateException("No puedes pagar cuotas mientras la amortización esté pendiente de aprobación del administrador.");
            model.addAttribute("cuota",cuota); model.addAttribute("metodosPago",java.util.List.of(MetodoPago.YAPE,MetodoPago.TRANSFERENCIA));
            model.addAttribute("yapeNumero","915 092 797"); model.addAttribute("yapeNombre","Eder Dia*"); model.addAttribute("bcpCuenta","43502664174002"); model.addAttribute("bcpCci","00243510266417400266"); model.addAttribute("bcpNombre","Diaz Culqui Eder Omar");
            return "creditos/pago";
        } catch(Exception e) { ra.addFlashAttribute("mensajeError",e.getMessage()); return "redirect:/creditos/mis-creditos"; }
    }

    @PostMapping(value="/cuotas/{id}/pagar", consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public String enviarPago(@PathVariable Long id, @RequestParam BigDecimal monto, @RequestParam MetodoPago metodo, @RequestParam MultipartFile comprobante, Principal principal, RedirectAttributes ra) {
        try {
            Pago pago=creditoService.registrarComprobante(id,monto,metodo,obtenerUsuarioPrincipal(principal).getId(),comprobante.getBytes(),comprobante.getContentType());
            if (pago.getEstado()==EstadoPago.REVISION_MANUAL) ra.addFlashAttribute("mensajeInfo","El comprobante no pudo validarse automáticamente y quedó marcado para revisión del ADMIN.");
            else ra.addFlashAttribute("mensajeInfo","Comprobante enviado. La cuota seguirá pendiente hasta la aprobación del ADMIN.");
            return "redirect:/creditos/detalle/"+pago.getCuota().getCredito().getId();
        } catch(Exception e) { ra.addFlashAttribute("mensajeError",e.getMessage()); return "redirect:/creditos/cuotas/"+id+"/pagar"; }
    }

    @GetMapping("/{id}/amortizar")
    public String formularioAmortizacion(@PathVariable Long id, Principal principal, Model model, RedirectAttributes ra) {
        try {
            Usuario usuario = obtenerUsuarioPrincipal(principal);
            Credito credito = creditoService.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException("Crédito no encontrado."));
            if (!credito.getUsuario().getId().equals(usuario.getId())) {
                throw new IllegalStateException("No tiene autorización.");
            }
            if (credito.getModalidad() != ModalidadPago.SEMANAL_COMPUESTO) {
                throw new IllegalStateException("La amortización solo está disponible para créditos con cuotas semanales.");
            }
            BigDecimal monto = creditoService.calcularMontoAmortizacion(id);
            model.addAttribute("credito", credito);
            model.addAttribute("montoAmortizacion", monto);
            model.addAttribute("metodosPago", List.of(MetodoPago.YAPE, MetodoPago.TRANSFERENCIA));
            model.addAttribute("yapeNumero","915 092 797");
            model.addAttribute("yapeNombre","Eder Dia*");
            model.addAttribute("bcpCuenta","43502664174002");
            model.addAttribute("bcpCci","00243510266417400266");
            model.addAttribute("bcpNombre","Diaz Culqui Eder Omar");
            return "creditos/amortizar";
        } catch (Exception e) {
            ra.addFlashAttribute("mensajeError", e.getMessage());
            return "redirect:/creditos/mis-creditos";
        }
    }

    @PostMapping(value="/{id}/amortizar", consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public String enviarAmortizacion(@PathVariable Long id, @RequestParam BigDecimal monto, @RequestParam MetodoPago metodo, @RequestParam MultipartFile comprobante, Principal principal, RedirectAttributes ra) {
        try {
            Amortizacion a = creditoService.registrarAmortizacionCliente(id, monto, metodo, obtenerUsuarioPrincipal(principal).getId(), comprobante.getBytes(), comprobante.getContentType());
            if (a.getEstado() == EstadoPago.REVISION_MANUAL) {
                ra.addFlashAttribute("mensajeInfo", "El comprobante de amortización quedó para revisión manual del ADMIN.");
            } else {
                ra.addFlashAttribute("mensajeInfo", "La solicitud de amortización fue enviada. El crédito se marcará como pagado únicamente cuando el ADMIN la apruebe.");
            }
            return "redirect:/creditos/detalle/" + id;
        } catch (Exception e) {
            ra.addFlashAttribute("mensajeError", e.getMessage());
            return "redirect:/creditos/" + id + "/amortizar";
        }
    }

    @GetMapping("/amortizaciones/{id}/comprobante")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<byte[]> comprobanteAmortizacion(@PathVariable Long id) {
        Amortizacion a = creditoService.obtenerAmortizacion(id);
        if (a.getComprobante() == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(a.getComprobanteContentType())).body(a.getComprobante());
    }

    @GetMapping("/amortizaciones/pendientes")
    @PreAuthorize("hasRole('ADMIN')")
    public String amortizacionesPendientes(Model model) {
        model.addAttribute("amortizacionesPendientes", creditoService.listarAmortizacionesPendientes());
        return "creditos/amortizaciones-pendientes";
    }

    @PostMapping("/amortizaciones/{id}/aprobar")
    @PreAuthorize("hasRole('ADMIN')")
    public String aprobarAmortizacion(@PathVariable Long id, @RequestParam(required=false) String comentario, RedirectAttributes ra) {
        try {
            creditoService.aprobarAmortizacion(id, comentario);
            ra.addFlashAttribute("mensajeExito", "Amortización aprobada y crédito liquidado correctamente.");
        } catch (Exception e) {
            ra.addFlashAttribute("mensajeError", e.getMessage());
        }
        return "redirect:/creditos/amortizaciones/pendientes";
    }

    @PostMapping("/amortizaciones/{id}/rechazar")
    @PreAuthorize("hasRole('ADMIN')")
    public String rechazarAmortizacion(@PathVariable Long id, @RequestParam(required=false) String comentario, RedirectAttributes ra) {
        try {
            creditoService.rechazarAmortizacion(id, comentario);
            ra.addFlashAttribute("mensajeExito", "Amortización rechazada. El cliente podrá presentar una nueva solicitud.");
        } catch (Exception e) {
            ra.addFlashAttribute("mensajeError", e.getMessage());
        }
        return "redirect:/creditos/amortizaciones/pendientes";
    }

    @GetMapping("/pagos/pendientes")
    @PreAuthorize("hasRole('ADMIN')")
    public String pagosPendientes(Model model) { model.addAttribute("pagosPendientes",creditoService.listarPagosPendientes()); return "creditos/pagos-pendientes"; }

    @PostMapping("/pagos/{id}/aprobar")
    @PreAuthorize("hasRole('ADMIN')")
    public String aprobarPago(@PathVariable Long id,@RequestParam(required=false) String comentario,RedirectAttributes ra) { try { creditoService.aprobarPago(id,comentario); ra.addFlashAttribute("mensajeExito","Pago aprobado y cuota marcada como pagada."); } catch(Exception e){ra.addFlashAttribute("mensajeError",e.getMessage());} return "redirect:/creditos/pagos/pendientes"; }

    @PostMapping("/pagos/{id}/rechazar")
    @PreAuthorize("hasRole('ADMIN')")
    public String rechazarPago(@PathVariable Long id,@RequestParam(required=false) String comentario,RedirectAttributes ra) { try { creditoService.rechazarPago(id,comentario); ra.addFlashAttribute("mensajeExito","Pago rechazado. El cliente podrá enviar un nuevo comprobante."); } catch(Exception e){ra.addFlashAttribute("mensajeError",e.getMessage());} return "redirect:/creditos/pagos/pendientes"; }

    @GetMapping("/pagos/{id}/comprobante")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<byte[]> comprobante(@PathVariable Long id) {
        Pago p=creditoService.obtenerPago(id);
        if(p.getComprobante()==null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(p.getComprobanteContentType())).body(p.getComprobante());
    }

    @GetMapping("/pendientes")
    @PreAuthorize("hasRole('ADMIN')")
    public String listarPendientes(Model model) {
        List<Credito> pendientes = creditoService.listarPendientesAprobacion();
        java.util.Map<Long, GarantiaReferido> garantiasPorCredito = new java.util.HashMap<>();
        pendientes.forEach(c -> garantiaReferidoRepository.findByCredito(c).ifPresent(g -> garantiasPorCredito.put(c.getId(), g)));
        model.addAttribute("creditosPendientes", pendientes);
        model.addAttribute("garantiasPorCredito", garantiasPorCredito);
        return "creditos/pendientes";
    }

    @PostMapping("/{id}/aprobar")
    @PreAuthorize("hasRole('ADMIN')")
    public String aprobar(@PathVariable Long id, @RequestParam(required = false) String comentario, RedirectAttributes ra) {
        try { creditoService.aprobarCredito(id, comentario); ra.addFlashAttribute("mensajeExito", "Crédito aprobado."); }
        catch (IllegalArgumentException | IllegalStateException e) { ra.addFlashAttribute("mensajeError", e.getMessage()); }
        return "redirect:/creditos/pendientes";
    }

    @PostMapping("/{id}/rechazar")
    @PreAuthorize("hasRole('ADMIN')")
    public String rechazar(@PathVariable Long id, @RequestParam(required = false) String comentario, RedirectAttributes ra) {
        try { creditoService.rechazarCredito(id, comentario); ra.addFlashAttribute("mensajeExito", "Crédito rechazado."); }
        catch (IllegalArgumentException | IllegalStateException e) { ra.addFlashAttribute("mensajeError", e.getMessage()); }
        return "redirect:/creditos/pendientes";
    }

    private Usuario obtenerUsuarioPrincipal(Principal p) {

        if (p == null) {
            throw new IllegalStateException(
                    "Sesión no válida o expirada."
            );
        }

        return usuarioService
                .findByDni(p.getName().trim())
                .orElseThrow(
                        () -> new IllegalArgumentException(
                                "Usuario de sesión no encontrado."
                        )
                );
    }
}
