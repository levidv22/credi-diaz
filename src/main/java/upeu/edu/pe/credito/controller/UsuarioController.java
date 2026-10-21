package upeu.edu.pe.credito.controller;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import upeu.edu.pe.credito.dto.*;
import upeu.edu.pe.credito.entity.*;
import upeu.edu.pe.credito.service.*;
import upeu.edu.pe.credito.repository.BonificacionMlmRepository;

import java.security.Principal;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import java.math.BigDecimal;
import java.io.IOException;
import org.springframework.web.bind.annotation.SessionAttributes;
import org.springframework.web.bind.annotation.SessionAttribute;
import org.springframework.web.bind.support.SessionStatus;

@Controller
@RequestMapping("/usuarios")
@SessionAttributes("registro")
public class UsuarioController {
    private final UsuarioService usuarioService;
    private final RedGarantiaService redGarantiaService;
    private final BonificacionMlmRepository bonificacionMlmRepository;
    private final AporteAhorroService aporteAhorroService;

    public UsuarioController(UsuarioService usuarioService, RedGarantiaService redGarantiaService,
                             BonificacionMlmRepository bonificacionMlmRepository, AporteAhorroService aporteAhorroService) {
        this.usuarioService = usuarioService;
        this.redGarantiaService = redGarantiaService;
        this.bonificacionMlmRepository = bonificacionMlmRepository;
        this.aporteAhorroService = aporteAhorroService;
    }

    @GetMapping("/puntos")
    public String verPuntosYBonificaciones(Principal principal, Model model) {
        Usuario usuario = obtenerUsuarioPrincipal(principal);
        model.addAttribute("usuario", usuario);
        model.addAttribute("puntos", usuario.getPuntosAcumulados() == null ? BigDecimal.ZERO : usuario.getPuntosAcumulados());
        model.addAttribute("bonificaciones", bonificacionMlmRepository.findByBeneficiarioOrderByFechaAcreditacionDesc(usuario));
        model.addAttribute("rangoCalculado", calcularRangoPuntos(usuario.getPuntosAcumulados()));
        model.addAttribute("activoEsteMes", usuario.getEstado() == EstadoUsuario.ACTIVO
                && (usuario.getFechaVencimientoMembresia() == null
                    || !usuario.getFechaVencimientoMembresia().isBefore(java.time.LocalDate.now())));
        return "usuario/puntos";
    }

    private String calcularRangoPuntos(BigDecimal puntos) {
        BigDecimal p = puntos == null ? BigDecimal.ZERO : puntos;
        if (p.compareTo(new BigDecimal("200000")) >= 0) return "Diamante Imperial";
        if (p.compareTo(new BigDecimal("100000")) >= 0) return "Diamante Negro";
        if (p.compareTo(new BigDecimal("50000")) >= 0) return "Diamante Azul";
        if (p.compareTo(new BigDecimal("20000")) >= 0) return "Diamante";
        if (p.compareTo(new BigDecimal("10000")) >= 0) return "Zafiro";
        if (p.compareTo(new BigDecimal("5000")) >= 0) return "Oro";
        if (p.compareTo(new BigDecimal("2000")) >= 0) return "Plata";
        if (p.compareTo(new BigDecimal("500")) >= 0) return "Bronce";
        if (p.compareTo(new BigDecimal("200")) >= 0) return "Maestro";
        if (p.compareTo(new BigDecimal("100")) >= 0) return "Líder";
        return "Miembro";
    }

    @GetMapping("/register")
    public String mostrarFormularioRegistro(Model model) {
        if (!model.containsAttribute("registro")) model.addAttribute("registro", new RegistroClienteSessionDTO());
        model.addAttribute("paso", 1);
        return "auth/registro";
    }

    @PostMapping("/registro/paso/1")
    public String registroPaso1(@ModelAttribute("registro") RegistroClienteSessionDTO r, RedirectAttributes ra, Model model) {
        try { if (r.getNombreCompleto()==null || r.getNombreCompleto().isBlank() || r.getDni()==null || !r.getDni().matches("\\d{8}")) throw new IllegalArgumentException("Ingrese nombre completo y un DNI válido de 8 dígitos.");
            model.addAttribute("paso",2); return "auth/registro"; } catch (Exception e) { ra.addFlashAttribute("mensajeError",e.getMessage()); return "redirect:/usuarios/register"; }
    }

    @PostMapping("/registro/paso/2")
    public String registroPaso2(@ModelAttribute("registro") RegistroClienteSessionDTO r, Model model) {
        if (r.getCelular()==null || r.getCelular().isBlank() || r.getDomicilio()==null || r.getDomicilio().isBlank()) { model.addAttribute("mensajeError","Complete celular y domicilio."); model.addAttribute("paso",2); return "auth/registro"; }
        model.addAttribute("paso",3); return "auth/registro";
    }

    @PostMapping("/registro/paso/3")
    public String registroPaso3(@ModelAttribute("registro") RegistroClienteSessionDTO r, Model model) {
        if (r.getEmail()==null || r.getEmail().isBlank() || r.getPassword()==null || r.getPassword().length()<8 || r.getCodigoPatrocinador()==null || r.getCodigoPatrocinador().isBlank()) { model.addAttribute("mensajeError","Complete correo, contraseña de mínimo 8 caracteres y código de patrocinador."); model.addAttribute("paso",3); return "auth/registro"; }
        model.addAttribute("paso",4); return "auth/registro";
    }

    @PostMapping(value = "/registro/paso/4", consumes = "multipart/form-data")
    public String registroPaso4(
            @SessionAttribute("registro") RegistroClienteSessionDTO r,
            @ModelAttribute RegistroDocumentosDTO documentos,
            Model model) {

        try {

            validarImagen(
                    documentos.getFotoReciboLuzAgua(),
                    "Recibo de luz/agua"
            );

            validarImagen(
                    documentos.getFotoDniFrontal(),
                    "DNI frontal"
            );

            validarImagen(
                    documentos.getFotoDniReverso(),
                    "DNI reverso"
            );

            validarImagen(
                    documentos.getFotoFacial(),
                    "Foto facial"
            );

            validarImagen(
                    documentos.getFotoPerfil(),
                    "Foto de perfil"
            );

            validarImagen(
                    documentos.getFotoVivienda(),
                    "Foto de vivienda"
            );

            // =========================
            // RECIBO
            // =========================
            r.setFotoReciboLuzAgua(
                    documentos.getFotoReciboLuzAgua().getBytes()
            );

            r.setReciboContentType(
                    documentos.getFotoReciboLuzAgua().getContentType()
            );

            // =========================
            // DNI FRONTAL
            // =========================
            r.setFotoDniFrontal(
                    documentos.getFotoDniFrontal().getBytes()
            );

            r.setDniFrontalContentType(
                    documentos.getFotoDniFrontal().getContentType()
            );

            // =========================
            // DNI REVERSO
            // =========================
            r.setFotoDniReverso(
                    documentos.getFotoDniReverso().getBytes()
            );

            r.setDniReversoContentType(
                    documentos.getFotoDniReverso().getContentType()
            );

            // =========================
            // FOTO FACIAL
            // =========================
            r.setFotoFacial(
                    documentos.getFotoFacial().getBytes()
            );

            r.setFacialContentType(
                    documentos.getFotoFacial().getContentType()
            );

            // =========================
            // FOTO PERFIL
            // =========================
            r.setFotoPerfil(
                    documentos.getFotoPerfil().getBytes()
            );

            r.setPerfilContentType(
                    documentos.getFotoPerfil().getContentType()
            );

            // =========================
            // FOTO VIVIENDA
            // =========================
            r.setFotoVivienda(
                    documentos.getFotoVivienda().getBytes()
            );

            r.setViviendaContentType(
                    documentos.getFotoVivienda().getContentType()
            );

            model.addAttribute("paso", 5);

            return "auth/registro";

        } catch (IOException e) {

            model.addAttribute(
                    "mensajeError",
                    "No se pudieron procesar las imágenes."
            );

            model.addAttribute("paso", 4);

            return "auth/registro";
        }
    }

    private void validarImagen(
            MultipartFile archivo,
            String nombre) {

        if (archivo == null || archivo.isEmpty()) {
            throw new IllegalArgumentException(
                    "Debe seleccionar: " + nombre
            );
        }

        String tipo = archivo.getContentType();

        if (tipo == null ||
                !(tipo.equals("image/jpeg")
                        || tipo.equals("image/png")
                        )) {

            throw new IllegalArgumentException(
                    "El archivo de " + nombre +
                            " debe ser una imagen JPG, PNG."
            );
        }
    }

    @PostMapping("/registro/finalizar")
    public String registrarCliente(@ModelAttribute("registro") RegistroClienteSessionDTO r, RedirectAttributes redirectAttributes, SessionStatus status) {
        try { usuarioService.registrarClienteConBytes(r.toUsuarioDTO(), r.getFotoReciboLuzAgua(), r.getReciboContentType(), r.getFotoDniFrontal(), r.getDniFrontalContentType(), r.getFotoDniReverso(), r.getDniReversoContentType(), r.getFotoFacial(), r.getFacialContentType(), r.getFotoPerfil(), r.getPerfilContentType(), r.getFotoVivienda(), r.getViviendaContentType());
            status.setComplete(); redirectAttributes.addFlashAttribute("mensajeExito", "Usuario registrado correctamente. Ya puede iniciar sesión."); return "redirect:/login";
        } catch (IllegalArgumentException | IllegalStateException e) { redirectAttributes.addFlashAttribute("mensajeError", e.getMessage()); return "redirect:/usuarios/register"; }
    }

    @GetMapping("/perfil")
    public String verPerfil(@RequestParam(value = "usuarioId", required = false) Long usuarioId,
                            Principal principal, Model model) {
        Usuario autenticado = obtenerUsuarioPrincipal(principal);
        Usuario visualizado = autenticado;
        if (usuarioId != null) {
            if (autenticado.getRol() != Rol.ADMIN) throw new IllegalStateException("No tiene autorización para visualizar otro perfil.");
            visualizado = usuarioService.findById(usuarioId).orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado."));
        }
        PerfilUsuarioResponseDTO dto = mapPerfil(visualizado);
        model.addAttribute("perfil", dto);
        model.addAttribute("actualizacion", new PerfilActualizacionDTO(visualizado.getNombreCompleto(), visualizado.getDni(), visualizado.getCelular(), visualizado.getDomicilio(), visualizado.getEmail()));
        model.addAttribute("actualizacionObligatoria", requiereActualizacionAnual(visualizado));
        return "usuario/perfil";
    }

    @PostMapping("/perfil/actualizar")
    public String actualizarPerfil(@ModelAttribute("actualizacion") PerfilActualizacionDTO dto,
                                   @RequestParam(value = "fotoReciboLuzAgua", required = false) MultipartFile fotoReciboLuzAgua,
                                   @RequestParam(value = "fotoDniFrontal", required = false) MultipartFile fotoDniFrontal,
                                   @RequestParam(value = "fotoDniReverso", required = false) MultipartFile fotoDniReverso,
                                   @RequestParam(value = "fotoFacial", required = false) MultipartFile fotoFacial,
                                   @RequestParam(value = "fotoPerfil", required = false) MultipartFile fotoPerfil,
                                   @RequestParam(value = "fotoVivienda", required = false) MultipartFile fotoVivienda,
                                   Principal principal, RedirectAttributes redirectAttributes) {
        try {
            Usuario u = obtenerUsuarioPrincipal(principal);
            Usuario actualizado = usuarioService.actualizarPerfil(u.getId(), dto, fotoReciboLuzAgua, fotoDniFrontal, fotoDniReverso, fotoFacial, fotoPerfil, fotoVivienda);
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && !actualizado.getDni().equals(auth.getName())) {
                SecurityContextHolder.getContext().setAuthentication(
                        new UsernamePasswordAuthenticationToken(actualizado.getDni(), auth.getCredentials(), auth.getAuthorities()));
            }
            redirectAttributes.addFlashAttribute("mensajeExito", "Perfil y documentación actualizados correctamente.");
        } catch (IllegalArgumentException | IllegalStateException e) {
            redirectAttributes.addFlashAttribute("mensajeError", e.getMessage());
        }
        return "redirect:/usuarios/perfil";
    }

    @PostMapping("/ahorro/activar")
    public String activarAhorro(
            @RequestParam("metodo") MetodoPago metodo,
            @RequestParam("comprobante") MultipartFile comprobante,
            Principal principal,
            RedirectAttributes ra) {

        try {
            Usuario usuario = obtenerUsuarioPrincipal(principal);

            aporteAhorroService.registrar(
                    usuario.getId(),
                    metodo,
                    comprobante);

            ra.addFlashAttribute(
                    "mensajeExito",
                    "Tu comprobante fue enviado. El aporte está pendiente de revisión administrativa.");

        } catch (IllegalArgumentException | IllegalStateException e) {
            ra.addFlashAttribute("mensajeError", e.getMessage());
        }

        return "redirect:/usuarios/perfil";
    }

    @GetMapping("/ahorro/solicitud/{id}/comprobante")
    public ResponseEntity<byte[]> verComprobanteAhorro(
            @PathVariable Long id,
            Principal principal) {

        Usuario usuario = obtenerUsuarioPrincipal(principal);

        AporteAhorro aporte = aporteAhorroService.buscarPorId(id);

        boolean esPropietario = aporte.getUsuario().getId()
                .equals(usuario.getId());

        boolean esAdmin = usuario.getRol() == Rol.ADMIN;

        if (!esPropietario && !esAdmin) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        byte[] archivo = aporte.getComprobante();
        String tipo = aporte.getComprobanteContentType();

        if (archivo == null || archivo.length == 0 || tipo == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok()
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=\"comprobante-ahorro-" + id + "\""
                )
                .contentType(MediaType.parseMediaType(tipo))
                .body(archivo);
    }

    @GetMapping("/referidos")
    public String verReferidos(Principal principal, Model model) {
        Usuario usuario = obtenerUsuarioPrincipal(principal);
        model.addAttribute("usuario", usuario);
        model.addAttribute("referidos", usuario.getReferidosDirectos() == null ? java.util.List.of() : usuario.getReferidosDirectos());
        model.addAttribute("beneficioLimite", usuario.getRol() == Rol.ADMIN
                ? "El administrador gestiona la red de referidos sin el límite de 3 directos aplicado a los clientes."
                : "Cada cliente puede registrar como máximo 3 referidos directos. El límite disponible se muestra en tu resumen.");
        model.addAttribute("beneficioBono", "Los créditos de tus referidos pueden generar bonos cuando se cumplan las condiciones de la modalidad y pago.");
        model.addAttribute("beneficioGarantia", "Como patrocinador, puedes aceptar o rechazar el respaldo de garantía de los créditos de tus referidos.");
        model.addAttribute("garantiasPendientes", redGarantiaService.listarGarantiasPendientesPorGarante(usuario.getId()));
        return "usuario/referidos";
    }

    @PostMapping("/garantias/{id}/aceptar")
    public String aceptarGarantia(@PathVariable Long id, Principal principal, RedirectAttributes redirectAttributes) {
        try {
            redGarantiaService.aceptarGarantia(id, obtenerUsuarioPrincipal(principal).getId());
            redirectAttributes.addFlashAttribute("mensajeExito", "Garantía aceptada. El ADMIN ya puede continuar con la evaluación del crédito.");
        } catch (IllegalArgumentException | IllegalStateException e) {
            redirectAttributes.addFlashAttribute("mensajeError", e.getMessage());
        }
        return "redirect:/usuarios/referidos";
    }

    @PostMapping("/garantias/{id}/rechazar")
    public String rechazarGarantia(@PathVariable Long id, Principal principal, RedirectAttributes redirectAttributes) {
        try {
            redGarantiaService.rechazarGarantia(id, obtenerUsuarioPrincipal(principal).getId());
            redirectAttributes.addFlashAttribute("mensajeExito", "Garantía rechazada. El ADMIN podrá ver el resultado en la solicitud del crédito.");
        } catch (IllegalArgumentException | IllegalStateException e) {
            redirectAttributes.addFlashAttribute("mensajeError", e.getMessage());
        }
        return "redirect:/usuarios/referidos";
    }

    @GetMapping("/notificaciones-mora")
    public String verNotificacionesMora(Principal principal, Model model) {
        Usuario usuario = obtenerUsuarioPrincipal(principal);
        model.addAttribute("notificaciones", redGarantiaService.listarNotificacionesPendientesPorGarante(usuario.getId()));
        return "usuario/notificaciones-mora";
    }

    @PostMapping("/notificaciones-mora/{id}/pagar")
    public String pagarGarantia(@PathVariable Long id, Principal principal, RedirectAttributes redirectAttributes) {
        try {
            redGarantiaService.cobrarGarantiaAGarante(id, obtenerUsuarioPrincipal(principal).getId());
            redirectAttributes.addFlashAttribute("mensajeExito", "La cobertura de garantía fue procesada.");
        } catch (IllegalArgumentException | IllegalStateException e) {
            redirectAttributes.addFlashAttribute("mensajeError", e.getMessage());
        }
        return "redirect:/usuarios/notificaciones-mora";
    }

    private boolean requiereActualizacionAnual(Usuario usuario) {
        java.time.LocalDate hoy = java.time.LocalDate.now();
        return usuario.getFechaActualizacionDatos() == null
                || usuario.getFechaActualizacionDatos().plusYears(1).isBefore(hoy)
                || usuario.getFechaActualizacionDocumentos() == null
                || usuario.getFechaActualizacionDocumentos().plusYears(1).isBefore(hoy);
    }

    private PerfilUsuarioResponseDTO mapPerfil(Usuario u) {

        PerfilUsuarioResponseDTO d = new PerfilUsuarioResponseDTO();

        d.setId(u.getId());
        d.setNombreCompleto(u.getNombreCompleto());
        d.setDni(u.getDni());
        d.setCelular(u.getCelular());
        d.setDomicilio(u.getDomicilio());
        d.setEmail(u.getEmail());
        d.setCodigoReferido(u.getCodigoReferido());

        d.setFotoReciboLuzAgua(u.getFotoReciboLuzAgua());
        d.setFotoDniFrontal(u.getFotoDniFrontal());
        d.setFotoDniReverso(u.getFotoDniReverso());
        d.setFotoFacial(u.getFotoFacial());
        d.setFotoPerfil(u.getFotoPerfil());
        d.setFotoVivienda(u.getFotoVivienda());

        d.setFechaActualizacionDatos(u.getFechaActualizacionDatos());
        d.setFechaActualizacionDocumentos(u.getFechaActualizacionDocumentos());

        d.setEstado(u.getEstado());
        d.setScoreCrediticio(u.getScoreCrediticio());

        d.setLimiteCreditoPermitido(u.getLimiteCreditoPermitido());
        d.setSaldoAhorro(u.getSaldoAhorro());
        d.setSaldoBonos(u.getSaldoBonos());
        d.setSaldoDisponible(u.getSaldoDisponible());
        d.setActivoPorAhorro(u.isActivoPorAhorro());

        d.setFechaVencimientoAhorro(u.getFechaVencimientoAhorro());
        d.setFechaActivacionMembresia(u.getFechaActivacionMembresia());
        d.setFechaVencimientoMembresia(u.getFechaVencimientoMembresia());

        d.setRango(u.getRango());

        if (u.getPatrocinador() != null) {
            d.setPatrocinadorNombre(
                    u.getPatrocinador().getNombreCompleto()
            );
        }

        d.setLimiteDirectos(u.getLimiteDirectos());
        d.setReferidosDirectos(u.getReferidosDirectos());

        return d;
    }

    private Usuario obtenerUsuarioPrincipal(Principal principal) {

        if (principal == null) {
            throw new IllegalStateException(
                    "Sesión no válida o expirada."
            );
        }

        return usuarioService
                .findByDni(principal.getName().trim())
                .orElseThrow(
                        () -> new IllegalArgumentException(
                                "Usuario de sesión no encontrado."
                        )
                );
    }
}
