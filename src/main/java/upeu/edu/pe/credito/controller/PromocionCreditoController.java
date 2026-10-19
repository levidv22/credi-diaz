package upeu.edu.pe.credito.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import upeu.edu.pe.credito.entity.*;
import upeu.edu.pe.credito.repository.*;
import upeu.edu.pe.credito.service.UsuarioService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

@Controller
public class PromocionCreditoController {
 private final PromocionCreditoRapidoRepository promociones;
 private final UsuarioRepository usuarios;
 private final CreditoRepository creditos;
 private final UsuarioService usuarioService;
 public PromocionCreditoController(PromocionCreditoRapidoRepository promociones, UsuarioRepository usuarios, CreditoRepository creditos, UsuarioService usuarioService){this.promociones=promociones;this.usuarios=usuarios;this.creditos=creditos;this.usuarioService=usuarioService;}
 @GetMapping("/admin/promociones/credito-rapido") @PreAuthorize("hasRole('ADMIN')")
 public String admin(Model model){model.addAttribute("usuarios",usuarioService.listarTodos());model.addAttribute("promociones",promociones.findAll());return "admin/promociones-credito-rapido";}
 @PostMapping("/admin/promociones/credito-rapido") @PreAuthorize("hasRole('ADMIN')")
 public String crear(@RequestParam BigDecimal monto,@RequestParam LocalDate fechaExpiracion,@RequestParam(name="usuarioIds",required=false) List<Long> ids,RedirectAttributes ra){
  if(monto==null||monto.compareTo(BigDecimal.ZERO)<=0){ra.addFlashAttribute("mensajeError","El monto debe ser mayor que cero.");return "redirect:/admin/promociones/credito-rapido";}
  if(fechaExpiracion==null||fechaExpiracion.isBefore(LocalDate.now())){ra.addFlashAttribute("mensajeError","La fecha de expiración debe ser hoy o posterior.");return "redirect:/admin/promociones/credito-rapido";}
  if(ids==null||ids.isEmpty()){ra.addFlashAttribute("mensajeError","Selecciona al menos un cliente.");return "redirect:/admin/promociones/credito-rapido";}
  PromocionCreditoRapido p=new PromocionCreditoRapido();p.setMonto(monto.setScale(2,java.math.RoundingMode.HALF_UP));p.setFechaExpiracion(fechaExpiracion);p.setFechaCreacion(LocalDate.now());p.setActiva(true);p.setDestinatarios(usuarios.findAllById(ids));promociones.save(p);ra.addFlashAttribute("mensajeExito","Promoción enviada a "+p.getDestinatarios().size()+" clientes.");return "redirect:/admin/promociones/credito-rapido";
 }
 @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
 public String errorSolicitud(RuntimeException ex, RedirectAttributes ra) {
  ra.addFlashAttribute("mensajeError", ex.getMessage());
  return "redirect:/creditos/mis-creditos";
 }
 @GetMapping("/creditos/promocion-rapida")
 public String cliente(Authentication auth,Model model){Usuario u=usuarioActual(auth);List<PromocionCreditoRapido> disponibles=promociones.findByActivaTrueOrderByFechaCreacionDesc().stream().filter(p->!p.getFechaExpiracion().isBefore(LocalDate.now())&&p.getDestinatarios().stream().anyMatch(d->d.getId().equals(u.getId()))).toList();model.addAttribute("promocionesRapidas",disponibles);return "creditos/promocion-rapida";}
 @PostMapping("/creditos/promocion-rapida/{id}/solicitar")
 public String solicitar(@PathVariable Long id,
                         @RequestParam int opcion,
                         @RequestParam MedioDesembolso medioDesembolso,
                         @RequestParam(required=false) String nombreBanco,
                         @RequestParam(required=false) String numeroCuenta,
                         @RequestParam(required=false) String cci,
                         @RequestParam(required=false) String nombreBilletera,
                         @RequestParam(required=false) String numeroBilletera,
                         Authentication auth,RedirectAttributes ra){
  Usuario u=usuarioActual(auth);PromocionCreditoRapido p=promociones.findById(id).orElseThrow(()->new IllegalArgumentException("Promoción no encontrada."));
  if(!p.isActiva()||p.getFechaExpiracion().isBefore(LocalDate.now())||p.getDestinatarios().stream().noneMatch(d->d.getId().equals(u.getId())))throw new IllegalStateException("La promoción ya no está disponible para tu usuario.");
  if(!creditos.findByUsuarioAndCreditoPromocionalTrue(u).isEmpty())
   throw new IllegalStateException("Ya utilizaste una promoción de crédito rápido. No puedes solicitar otra.");
  if(creditos.findByUsuarioAndEstado(u,EstadoCredito.ACTIVO).size()>0)throw new IllegalStateException("Debes terminar tu crédito activo antes de solicitar otro.");
  if(medioDesembolso==MedioDesembolso.CUENTA_BANCARIA){
   if(nombreBanco==null||nombreBanco.isBlank()||numeroCuenta==null||numeroCuenta.isBlank())
    throw new IllegalArgumentException("Indica el banco y el número de cuenta donde deseas recibir el desembolso.");
   if("BCP".equalsIgnoreCase(nombreBanco) && (cci==null||cci.isBlank()))
    throw new IllegalArgumentException("Para BCP también debes indicar el CCI.");
  } else if(medioDesembolso==MedioDesembolso.BILLETERA_DIGITAL){
   if(nombreBilletera==null||nombreBilletera.isBlank()||numeroBilletera==null||numeroBilletera.isBlank())
    throw new IllegalArgumentException("Indica la billetera digital y su número.");
  }
  int cuotas;int dias;BigDecimal tasa;
  switch(opcion){case 1->{cuotas=1;dias=10;tasa=new BigDecimal("3.00");}case 2->{cuotas=1;dias=15;tasa=new BigDecimal("4.00");}case 3->{cuotas=2;dias=20;tasa=new BigDecimal("5.00");}case 4->{cuotas=1;dias=25;tasa=new BigDecimal("7.00");}case 5->{cuotas=4;dias=40;tasa=new BigDecimal("10.00");}default->throw new IllegalArgumentException("Selecciona un plazo válido.");}
  Credito c=new Credito();c.setUsuario(u);c.setMontoPrestado(p.getMonto());c.setModalidad(ModalidadPago.YAPE_PROMOCIONAL);c.setEstado(EstadoCredito.PENDIENTE_APROBACION);c.setFechaSolicitud(LocalDate.now());c.setNumeroCuotas(cuotas);c.setCreditoPromocional(true);c.setPlazoPromocionalDias(dias);c.setTasaPromocionalPorcentaje(tasa);
  c.setMedioDesembolso(medioDesembolso);
  c.setNombreBanco(limpiar(nombreBanco));
  c.setNumeroCuenta(limpiar(numeroCuenta));
  c.setCci(limpiar(cci));
  c.setNombreBilletera(limpiar(nombreBilletera));
  c.setNumeroBilletera(limpiar(numeroBilletera));
  c.setInteresTotalCalculado(p.getMonto().multiply(tasa).divide(new BigDecimal("100"),2,java.math.RoundingMode.HALF_UP));c.setComentarioAdmin("Solicitud de promoción #"+p.getId());creditos.save(c);ra.addFlashAttribute("mensajeExito","Solicitud de crédito promocional enviada al administrador.");return "redirect:/creditos/mis-creditos";
 }
 @GetMapping("/creditos/promocion-rapida/buscar") @ResponseBody
 @PreAuthorize("hasRole('ADMIN')") public List<Map<String,Object>> buscar(@RequestParam String q){String query=q.toLowerCase(Locale.ROOT);return usuarioService.listarTodos().stream().filter(u->u.getRol()!=Rol.ADMIN&&((u.getNombreCompleto()!=null&&u.getNombreCompleto().toLowerCase(Locale.ROOT).contains(query))||(u.getDni()!=null&&u.getDni().contains(query)))).limit(12).map(u->Map.<String,Object>of("id",u.getId(),"nombre",u.getNombreCompleto(),"dni",u.getDni())).toList();}
 private String limpiar(String valor){return valor==null?null:valor.trim();}
 private Usuario usuarioActual(Authentication auth){if(auth==null)throw new IllegalStateException("Inicia sesión.");return usuarios.findByDni(auth.getName().trim()).orElseThrow(()->new IllegalStateException("Usuario no encontrado."));}
}
