package upeu.edu.pe.credito.service;

import org.springframework.web.multipart.MultipartFile;
import upeu.edu.pe.credito.entity.*;

import java.util.List;

public interface ReembolsoAhorroService {

    SolicitudReembolsoAhorro solicitar(
            Long usuarioId,
            MetodoReembolsoAhorro metodo,
            String titularCuenta,
            String numeroCuenta,
            String cci,
            String numeroYape);

    List<SolicitudReembolsoAhorro> listarTodos();

    List<SolicitudReembolsoAhorro> listarPendientes();

    boolean tieneSolicitudPendiente(Long usuarioId);

    SolicitudReembolsoAhorro buscarPorId(Long id);

    SolicitudReembolsoAhorro aprobar(Long solicitudId, MultipartFile comprobante);

    SolicitudReembolsoAhorro rechazar(Long solicitudId, String motivo);

    void eliminarRechazado(Long solicitudId);
    int eliminarTodosLosRechazados();
}
