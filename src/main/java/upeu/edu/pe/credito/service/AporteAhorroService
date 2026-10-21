package upeu.edu.pe.credito.service;

import org.springframework.web.multipart.MultipartFile;
import upeu.edu.pe.credito.entity.AporteAhorro;
import upeu.edu.pe.credito.entity.EstadoAporteAhorro;
import upeu.edu.pe.credito.entity.MetodoPago;
import upeu.edu.pe.credito.entity.Usuario;

import java.util.List;

public interface AporteAhorroService {

    AporteAhorro registrar(
            Long usuarioId,
            MetodoPago metodo,
            MultipartFile comprobante);

    List<AporteAhorro> listarPendientes();

    List<AporteAhorro> listarPorUsuario(Long usuarioId);

    AporteAhorro buscarPorId(Long id);

    AporteAhorro aprobar(Long aporteId);

    AporteAhorro rechazar(Long aporteId, String comentario);
}
