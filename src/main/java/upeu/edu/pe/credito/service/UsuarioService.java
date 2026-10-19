package upeu.edu.pe.credito.service;

import org.springframework.web.multipart.MultipartFile;
import upeu.edu.pe.credito.dto.*;
import upeu.edu.pe.credito.entity.Usuario;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface UsuarioService {
    Usuario registrarAdminInicial(AdminRegistroDTO dto);
    Usuario registrarCliente(UsuarioDTO dto, MultipartFile fotoReciboLuzAgua, MultipartFile fotoDniFrontal,
                             MultipartFile fotoDniReverso, MultipartFile fotoFacial, MultipartFile fotoPerfil,
                             MultipartFile fotoVivienda);
    Usuario registrarClienteConBytes(UsuarioDTO dto, byte[] recibo, String reciboType, byte[] dniFrontal, String dniFrontalType,
                                     byte[] dniReverso, String dniReversoType, byte[] facial, String facialType,
                                     byte[] perfil, String perfilType, byte[] vivienda, String viviendaType);
    Usuario actualizarPerfil(Long usuarioId, PerfilActualizacionDTO dto,
                             MultipartFile fotoReciboLuzAgua, MultipartFile fotoDniFrontal,
                             MultipartFile fotoDniReverso, MultipartFile fotoFacial,
                             MultipartFile fotoPerfil, MultipartFile fotoVivienda);
    Usuario evaluarAumentoLimite(Long usuarioId, BigDecimal nuevoLimite);
    Usuario procesarIngresoAhorro(Long usuarioId, BigDecimal monto);
    Optional<Usuario> findById(Long id);
    Optional<Usuario> findByDni(String dni);
    Optional<Usuario> findByEmail(String email);
    List<Usuario> listarTodos();
    Usuario marcarComoNoHabido(Long usuarioId);
    Usuario save(Usuario usuario);
    void actualizarActivacionesVencidas();
}
