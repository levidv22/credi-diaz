package upeu.edu.pe.credito.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import upeu.edu.pe.credito.dto.*;
import upeu.edu.pe.credito.entity.*;
import upeu.edu.pe.credito.repository.*;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Random;

@Service
public class UsuarioServiceImpl implements UsuarioService {
    private static final BigDecimal CIEN = new BigDecimal("100.00");
    private final UsuarioRepository usuarioRepository;
    private final CreditoRepository creditoRepository;
    private final GarantiaReferidoRepository garantiaReferidoRepository;
    private final NotificacionMoraRepository notificacionMoraRepository;
    private final PasswordEncoder passwordEncoder;

    public UsuarioServiceImpl(UsuarioRepository usuarioRepository,
                              CreditoRepository creditoRepository,
                              GarantiaReferidoRepository garantiaReferidoRepository,
                              NotificacionMoraRepository notificacionMoraRepository,
                              PasswordEncoder passwordEncoder) {
        this.usuarioRepository = usuarioRepository;
        this.creditoRepository = creditoRepository;
        this.garantiaReferidoRepository = garantiaReferidoRepository;
        this.notificacionMoraRepository = notificacionMoraRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public Usuario registrarAdminInicial(AdminRegistroDTO dto) {
        if (usuarioRepository.existsByRol(Rol.ADMIN)) {
            throw new IllegalStateException("Ya existe un administrador. El registro inicial ya fue realizado.");
        }
        validarTexto(dto.getNombreCompleto(), "Nombre completo");
        validarDni(dto.getDni());
        validarEmail(dto.getEmail());
        validarPassword(dto.getPassword());
        if (usuarioRepository.findByDni(dto.getDni()).isPresent()) throw new IllegalArgumentException("El DNI ya se encuentra registrado.");
        if (usuarioRepository.findByEmail(dto.getEmail()).isPresent()) throw new IllegalArgumentException("El email ya se encuentra registrado.");

        Usuario admin = new Usuario();
        admin.setNombreCompleto(dto.getNombreCompleto().trim());
        admin.setDni(dto.getDni().trim());
        admin.setEmail(dto.getEmail().trim().toLowerCase());
        admin.setPassword(passwordEncoder.encode(dto.getPassword()));
        admin.setRol(Rol.ADMIN);
        admin.setEstado(EstadoUsuario.ACTIVO);
        admin.setCodigoReferido(generarCodigoUnico(dto.getNombreCompleto()));
        admin.setLimiteDirectos(100);
        admin.setScoreCrediticio(0);
        admin.setLimiteCreditoPermitido(BigDecimal.ZERO);
        admin.setSaldoAhorro(BigDecimal.ZERO);
        admin.setSaldoBonos(BigDecimal.ZERO);
        admin.setSaldoDisponible(BigDecimal.ZERO);
        admin.setRango(RangoMlm.LIDER);
        return usuarioRepository.save(admin);
    }

    @Override
    @Transactional
    public Usuario registrarCliente(UsuarioDTO dto, MultipartFile fotoReciboLuzAgua,
                                    MultipartFile fotoDniFrontal, MultipartFile fotoDniReverso,
                                    MultipartFile fotoFacial, MultipartFile fotoPerfil,
                                    MultipartFile fotoVivienda) {
        validarTexto(dto.getNombreCompleto(), "Nombre completo");
        validarDni(dto.getDni());
        validarTexto(dto.getCelular(), "Celular");
        validarTexto(dto.getDomicilio(), "Domicilio");
        validarEmail(dto.getEmail());
        validarPassword(dto.getPassword());
        if (usuarioRepository.findByDni(dto.getDni()).isPresent()) throw new IllegalArgumentException("El DNI ya se encuentra registrado.");
        if (usuarioRepository.findByEmail(dto.getEmail().trim().toLowerCase()).isPresent()) throw new IllegalArgumentException("El email ya se encuentra registrado.");
        if (dto.getCodigoPatrocinador() == null || dto.getCodigoPatrocinador().isBlank()) throw new IllegalArgumentException("Es obligatorio indicar un código de patrocinador.");

        Usuario patrocinador = usuarioRepository.findByCodigoReferido(dto.getCodigoPatrocinador().trim().toUpperCase())
                .orElseThrow(() -> new IllegalArgumentException("El patrocinador no existe."));
        if (patrocinador.getEstado() != EstadoUsuario.ACTIVO) throw new IllegalStateException("El patrocinador no se encuentra activo.");
        if (patrocinador.getRol() == Rol.CLIENTE && usuarioRepository.countByPatrocinador(patrocinador) >= 3) {
            throw new IllegalStateException("El patrocinador ya alcanzó el máximo de 3 referidos directos.");
        }
        if (patrocinador.getRol() == Rol.CLIENTE && patrocinador.getLimiteDirectos() <= 0) throw new IllegalStateException("El patrocinador ya alcanzó su límite de 3 referidos directos.");

        validarImagenObligatoria(fotoReciboLuzAgua, "recibo de luz/agua");
        validarImagenObligatoria(fotoDniFrontal, "DNI frontal");
        validarImagenObligatoria(fotoDniReverso, "DNI reverso");
        validarImagenObligatoria(fotoFacial, "foto facial");
        validarImagenObligatoria(fotoPerfil, "foto de perfil");
        validarImagenObligatoria(fotoVivienda, "foto de vivienda");

        Usuario cliente = new Usuario();
        cliente.setNombreCompleto(dto.getNombreCompleto().trim());
        cliente.setDni(dto.getDni().trim());
        cliente.setCelular(dto.getCelular().trim());
        cliente.setDomicilio(dto.getDomicilio().trim());
        cliente.setEmail(dto.getEmail().trim().toLowerCase());
        cliente.setPassword(passwordEncoder.encode(dto.getPassword()));
        try {
            cliente.setFotoReciboLuzAgua(fotoReciboLuzAgua.getBytes()); cliente.setTipoReciboKyc(fotoReciboLuzAgua.getContentType());
            cliente.setFotoDniFrontal(fotoDniFrontal.getBytes()); cliente.setTipoDniFrontalKyc(fotoDniFrontal.getContentType());
            cliente.setFotoDniReverso(fotoDniReverso.getBytes()); cliente.setTipoDniReversoKyc(fotoDniReverso.getContentType());
            cliente.setFotoFacial(fotoFacial.getBytes()); cliente.setTipoFacialKyc(fotoFacial.getContentType());
            cliente.setFotoPerfil(fotoPerfil.getBytes()); cliente.setTipoPerfilKyc(fotoPerfil.getContentType());
            cliente.setFotoVivienda(fotoVivienda.getBytes()); cliente.setTipoViviendaKyc(fotoVivienda.getContentType());
        } catch (IOException e) { throw new IllegalStateException("No se pudieron leer las imágenes KYC.", e); }
        cliente.setFechaActualizacionDatos(LocalDate.now());
        cliente.setFechaActualizacionDocumentos(LocalDate.now());
        cliente.setRol(Rol.CLIENTE);
        cliente.setEstado(EstadoUsuario.INACTIVO);
        cliente.setPatrocinador(patrocinador);
        cliente.setCodigoReferido(generarCodigoUnico(dto.getNombreCompleto()));
        cliente.setLimiteDirectos(3);
        cliente.setScoreCrediticio(100);
        cliente.setConteoReferidosMorosos(0);
        cliente.setLimiteCreditoPermitido(CIEN);
        cliente.setSaldoAhorro(BigDecimal.ZERO);
        cliente.setSaldoBonos(BigDecimal.ZERO);
        cliente.setSaldoDisponible(BigDecimal.ZERO);
        cliente.setActivoPorAhorro(false);
        cliente.setRango(RangoMlm.MIEMBRO);

        Usuario guardado = usuarioRepository.save(cliente);
        // La rama directa se consume desde el momento en que el patrocinador incorpora al cliente.
        if (patrocinador.getRol() == Rol.CLIENTE) {
            patrocinador.setLimiteDirectos(Math.max(0, 3 - (int) usuarioRepository.countByPatrocinador(patrocinador)));
            usuarioRepository.save(patrocinador);
        }
        return guardado;
    }

    @Override
    @Transactional
    public Usuario registrarClienteConBytes(UsuarioDTO dto, byte[] recibo, String reciboType, byte[] dniFrontal, String dniFrontalType,
                                            byte[] dniReverso, String dniReversoType, byte[] facial, String facialType,
                                            byte[] perfil, String perfilType, byte[] vivienda, String viviendaType) {
        validarTexto(dto.getNombreCompleto(), "Nombre completo"); validarDni(dto.getDni()); validarTexto(dto.getCelular(), "Celular");
        validarTexto(dto.getDomicilio(), "Domicilio"); validarEmail(dto.getEmail()); validarPassword(dto.getPassword());
        if (usuarioRepository.findByDni(dto.getDni()).isPresent()) throw new IllegalArgumentException("El DNI ya se encuentra registrado.");
        if (usuarioRepository.findByEmail(dto.getEmail().trim().toLowerCase()).isPresent()) throw new IllegalArgumentException("El email ya se encuentra registrado.");
        Usuario patrocinador = usuarioRepository.findByCodigoReferido(dto.getCodigoPatrocinador().trim().toUpperCase())
                .orElseThrow(() -> new IllegalArgumentException("El patrocinador no existe."));
        if (patrocinador.getEstado() != EstadoUsuario.ACTIVO) throw new IllegalStateException("El patrocinador no se encuentra activo.");
        if (patrocinador.getRol() == Rol.CLIENTE && usuarioRepository.countByPatrocinador(patrocinador) >= 3) {
            throw new IllegalStateException("El patrocinador ya alcanzó el máximo de 3 referidos directos.");
        }
        if (patrocinador.getRol() == Rol.CLIENTE && patrocinador.getLimiteDirectos() <= 0) throw new IllegalStateException("El patrocinador ya alcanzó su límite de 3 referidos directos.");
        validarBlob(recibo, reciboType, "recibo de luz/agua"); validarBlob(dniFrontal, dniFrontalType, "DNI frontal");
        validarBlob(dniReverso, dniReversoType, "DNI reverso"); validarBlob(facial, facialType, "foto facial");
        validarBlob(perfil, perfilType, "foto de perfil"); validarBlob(vivienda, viviendaType, "foto de vivienda");
        Usuario c = new Usuario(); c.setNombreCompleto(dto.getNombreCompleto().trim()); c.setDni(dto.getDni().trim()); c.setCelular(dto.getCelular().trim());
        c.setDomicilio(dto.getDomicilio().trim()); c.setEmail(dto.getEmail().trim().toLowerCase()); c.setPassword(passwordEncoder.encode(dto.getPassword()));
        c.setFotoReciboLuzAgua(recibo); c.setTipoReciboKyc(reciboType); c.setFotoDniFrontal(dniFrontal); c.setTipoDniFrontalKyc(dniFrontalType);
        c.setFotoDniReverso(dniReverso); c.setTipoDniReversoKyc(dniReversoType); c.setFotoFacial(facial); c.setTipoFacialKyc(facialType);
        c.setFotoPerfil(perfil); c.setTipoPerfilKyc(perfilType); c.setFotoVivienda(vivienda); c.setTipoViviendaKyc(viviendaType);
        c.setFechaActualizacionDatos(LocalDate.now()); c.setFechaActualizacionDocumentos(LocalDate.now()); c.setRol(Rol.CLIENTE); c.setEstado(EstadoUsuario.INACTIVO);
        c.setPatrocinador(patrocinador); c.setCodigoReferido(generarCodigoUnico(dto.getNombreCompleto())); c.setLimiteDirectos(3); c.setScoreCrediticio(100);
        c.setConteoReferidosMorosos(0); c.setLimiteCreditoPermitido(CIEN); c.setSaldoAhorro(BigDecimal.ZERO); c.setSaldoBonos(BigDecimal.ZERO); c.setSaldoDisponible(BigDecimal.ZERO); c.setActivoPorAhorro(false); c.setRango(RangoMlm.MIEMBRO);
        Usuario guardado=usuarioRepository.save(c);
        if (patrocinador.getRol()==Rol.CLIENTE) { patrocinador.setLimiteDirectos(Math.max(0, 3 - (int) usuarioRepository.countByPatrocinador(patrocinador))); usuarioRepository.save(patrocinador); }
        return guardado;
    }

    @Override
    @Transactional
    public Usuario actualizarPerfil(Long usuarioId, PerfilActualizacionDTO dto,
                                    MultipartFile fotoReciboLuzAgua, MultipartFile fotoDniFrontal,
                                    MultipartFile fotoDniReverso, MultipartFile fotoFacial,
                                    MultipartFile fotoPerfil, MultipartFile fotoVivienda) {
        Usuario usuario = usuarioRepository.findById(usuarioId).orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado."));
        validarTexto(dto.getNombreCompleto(), "Nombre completo");
        validarDniActualizacion(dto.getDni());
        validarTexto(dto.getCelular(), "Celular");
        validarTexto(dto.getDomicilio(), "Domicilio");
        validarEmail(dto.getEmail());
        usuarioRepository.findByDni(dto.getDni().trim()).ifPresent(existente -> {
            if (!existente.getId().equals(usuarioId)) throw new IllegalArgumentException("El DNI ya pertenece a otro usuario.");
        });
        usuarioRepository.findByEmail(dto.getEmail().trim().toLowerCase()).ifPresent(existente -> {
            if (!existente.getId().equals(usuarioId)) throw new IllegalArgumentException("El email ya pertenece a otro usuario.");
        });
        boolean actualizacionAnualObligatoria = requiereActualizacionAnual(usuario);
        usuario.setNombreCompleto(dto.getNombreCompleto().trim());
        usuario.setDni(dto.getDni().trim());
        usuario.setCelular(dto.getCelular().trim());
        usuario.setDomicilio(dto.getDomicilio().trim());
        usuario.setEmail(dto.getEmail().trim().toLowerCase());
        usuario.setFechaActualizacionDatos(LocalDate.now());

        if (actualizacionAnualObligatoria) {
            if (!archivoValido(fotoReciboLuzAgua) || !archivoValido(fotoDniFrontal) || !archivoValido(fotoDniReverso)
                    || !archivoValido(fotoFacial) || !archivoValido(fotoPerfil) || !archivoValido(fotoVivienda)) {
                throw new IllegalArgumentException("La actualización anual es obligatoria. Debe volver a subir las 6 imágenes de validación.");
            }
        }

        boolean actualizoDocumento = false;
        if (archivoValido(fotoReciboLuzAgua)) { usuario.setFotoReciboLuzAgua(bytes(fotoReciboLuzAgua)); usuario.setTipoReciboKyc(fotoReciboLuzAgua.getContentType()); actualizoDocumento = true; }
        if (archivoValido(fotoDniFrontal)) { usuario.setFotoDniFrontal(bytes(fotoDniFrontal)); usuario.setTipoDniFrontalKyc(fotoDniFrontal.getContentType()); actualizoDocumento = true; }
        if (archivoValido(fotoDniReverso)) { usuario.setFotoDniReverso(bytes(fotoDniReverso)); usuario.setTipoDniReversoKyc(fotoDniReverso.getContentType()); actualizoDocumento = true; }
        if (archivoValido(fotoFacial)) { usuario.setFotoFacial(bytes(fotoFacial)); usuario.setTipoFacialKyc(fotoFacial.getContentType()); actualizoDocumento = true; }
        if (archivoValido(fotoPerfil)) { usuario.setFotoPerfil(bytes(fotoPerfil)); usuario.setTipoPerfilKyc(fotoPerfil.getContentType()); actualizoDocumento = true; }
        if (archivoValido(fotoVivienda)) { usuario.setFotoVivienda(bytes(fotoVivienda)); usuario.setTipoViviendaKyc(fotoVivienda.getContentType()); actualizoDocumento = true; }
        if (actualizoDocumento) usuario.setFechaActualizacionDocumentos(LocalDate.now());
        return usuarioRepository.save(usuario);
    }

    private boolean requiereActualizacionAnual(Usuario usuario) {
        LocalDate hoy = LocalDate.now();
        return usuario.getFechaActualizacionDatos() == null
                || usuario.getFechaActualizacionDatos().plusYears(1).isBefore(hoy)
                || usuario.getFechaActualizacionDocumentos() == null
                || usuario.getFechaActualizacionDocumentos().plusYears(1).isBefore(hoy);
    }

    private void validarDniActualizacion(String dni) {
        if (dni == null || !dni.trim().matches("\\d{8}")) {
            throw new IllegalArgumentException("El DNI debe contener exactamente 8 dígitos.");
        }
    }

    @Override
    @Transactional
    public Usuario marcarComoNoHabido(Long usuarioId) {
        Usuario deudor = usuarioRepository.findById(usuarioId).orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado."));
        deudor.setEstado(EstadoUsuario.NO_HABIDO);
        usuarioRepository.save(deudor);
        garantiaReferidoRepository.findByDeudor(deudor).forEach(garantia -> {
            if (garantia.getEstado() == EstadoGarantia.ACTIVA || garantia.getEstado() == EstadoGarantia.EJECUTADA) {
                garantia.setPorcentajeResponsabilidad(BigDecimal.ONE);
                if (garantia.getCredito() != null) {
                    BigDecimal saldo = garantia.getCredito().getCuotas().stream().filter(q -> !q.isPagado())
                            .map(CuotaCredito::getMontoTotalAPagar).reduce(BigDecimal.ZERO, BigDecimal::add);
                    garantia.setMontoDeudaGarantizada(saldo);
                    notificacionMoraRepository.findByGaranteAndResueltoFalse(garantia.getGarante()).forEach(n -> {
                        n.setMontoAPagar(saldo);
                        notificacionMoraRepository.save(n);
                    });
                }
                garantiaReferidoRepository.save(garantia);
            }
        });
        return deudor;
    }

    @Override @Transactional
    public Usuario evaluarAumentoLimite(Long usuarioId, BigDecimal nuevoLimite) {
        Usuario usuario = usuarioRepository.findById(usuarioId).orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado."));
        validarMultiploCien(nuevoLimite, "El límite debe ser múltiplo de S/ 100.");
        if (nuevoLimite.compareTo(CIEN) < 0) throw new IllegalArgumentException("El límite mínimo es S/ 100.");
        usuario.setLimiteCreditoPermitido(nuevoLimite);
        return usuarioRepository.save(usuario);
    }

    @Override @Transactional
    public Usuario procesarIngresoAhorro(Long usuarioId, BigDecimal monto) {
        validarPositivo(monto, "El ahorro");
        Usuario usuario = usuarioRepository.findById(usuarioId).orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado."));
        usuario.setSaldoAhorro(usuario.getSaldoAhorro().add(monto));
        if (usuario.getSaldoAhorro().compareTo(CIEN) >= 0) {
            activarPorAhorro(usuario);
        }
        return usuarioRepository.save(usuario);
    }

    private void activarPorAhorro(Usuario usuario) {
        LocalDate hoy = LocalDate.now();
        usuario.setActivoPorAhorro(true);
        usuario.setEstado(EstadoUsuario.ACTIVO);
        usuario.setFechaVencimientoAhorro(hoy.plusYears(1));
        usuario.setFechaActivacionMembresia(hoy);
        usuario.setFechaVencimientoMembresia(hoy.plusYears(1));
    }

    @Override
    @Scheduled(cron = "0 10 1 * * ?")
    @Transactional
    public void actualizarActivacionesVencidas() {

        LocalDate hoy = LocalDate.now();

        usuarioRepository.findAll().forEach(usuario -> {

            // Solo procesamos clientes.
            if (usuario.getRol() != Rol.CLIENTE) {
                return;
            }

            LocalDate vencimientoMembresia =
                    usuario.getFechaVencimientoMembresia();

            // Si no tiene fecha de vencimiento, no hay nada que procesar.
            if (vencimientoMembresia == null) {
                return;
            }

            // La membresía todavía está vigente.
            if (!vencimientoMembresia.isBefore(hoy)) {
                return;
            }

            /*
             * 1. VERIFICAR SI TIENE UN CRÉDITO EN CURSO
             */
            boolean creditoEnCurso =
                    !creditoRepository
                            .findByUsuarioAndEstado(
                                    usuario,
                                    EstadoCredito.ACTIVO
                            )
                            .isEmpty()
                            ||
                            !creditoRepository
                                    .findByUsuarioAndEstado(
                                            usuario,
                                            EstadoCredito.EN_MORA
                                    )
                                    .isEmpty();

            /*
             * 2. VERIFICAR SI SU AHORRO DE ACTIVACIÓN
             *    TODAVÍA ES VÁLIDO.
             */
            boolean ahorroVigente =
                    usuario.isActivoPorAhorro()
                            && usuario.getFechaVencimientoAhorro() != null
                            && !usuario.getFechaVencimientoAhorro().isBefore(hoy);

            /*
             * 3. VERIFICAR REQUISITO DEL LÍDER.
             *
             * El documento establece que un líder debe solicitar
             * como mínimo S/1,000 durante el año de membresía.
             */
            boolean liderCumplio = false;

            if (usuario.getRango() == RangoMlm.LIDER
                    && usuario.getFechaUltimoCreditoComoLider() != null
                    && usuario.getMontoUltimoCreditoComoLider() != null
                    && usuario.getFechaActivacionMembresia() != null) {

                LocalDate fechaCredito =
                        usuario.getFechaUltimoCreditoComoLider();

                BigDecimal montoCredito =
                        usuario.getMontoUltimoCreditoComoLider();

                boolean creditoDentroDelPeriodo =
                        !fechaCredito.isBefore(
                                usuario.getFechaActivacionMembresia()
                        )
                                &&
                                !fechaCredito.isAfter(
                                        vencimientoMembresia
                                );

                boolean montoMinimoCumplido =
                        montoCredito.compareTo(
                                new BigDecimal("1000.00")
                        ) >= 0;

                liderCumplio =
                        creditoDentroDelPeriodo
                                && montoMinimoCumplido;
            }

            /*
             * 4. SI TIENE CRÉDITO ACTIVO,
             *    SU ACTIVACIÓN CONTINÚA HASTA QUE TERMINE.
             */
            if (creditoEnCurso) {

                creditoRepository
                        .findByUsuarioAndEstado(
                                usuario,
                                EstadoCredito.ACTIVO
                        )
                        .stream()
                        .findFirst()
                        .ifPresent(credito -> {

                            if (credito.getFechaFin() != null) {
                                usuario.setFechaVencimientoMembresia(
                                        credito.getFechaFin()
                                );
                            }
                        });

            }

            /*
             * 5. SI TIENE AHORRO VIGENTE O ES LÍDER
             *    Y CUMPLIÓ EL REQUISITO, RENOVAMOS.
             */
            else if (ahorroVigente || liderCumplio) {

                usuario.setEstado(EstadoUsuario.ACTIVO);

                usuario.setFechaActivacionMembresia(hoy);

                usuario.setFechaVencimientoMembresia(
                        hoy.plusYears(1)
                );

            }

            /*
             * 6. SI NO CUMPLIÓ NINGÚN REQUISITO,
             *    PASA A INACTIVO.
             */
            else {

                /*
                 * Si todavía posee ahorro disponible,
                 * lo pasamos a saldo disponible.
                 */
                if (usuario.getSaldoAhorro() != null
                        && usuario.getSaldoAhorro()
                        .compareTo(BigDecimal.ZERO) > 0) {

                    usuario.setSaldoDisponible(
                            usuario.getSaldoDisponible()
                                    .add(usuario.getSaldoAhorro())
                    );

                    usuario.setSaldoAhorro(
                            BigDecimal.ZERO
                    );
                }

                usuario.setEstado(
                        EstadoUsuario.INACTIVO
                );

                usuario.setActivoPorAhorro(false);

                usuario.setFechaActivacionMembresia(null);

                usuario.setFechaVencimientoMembresia(null);
            }

            usuarioRepository.save(usuario);
        });
    }

    @Override public Optional<Usuario> findById(Long id) { return usuarioRepository.findById(id); }
    @Override public Optional<Usuario> findByDni(String dni) { return usuarioRepository.findByDni(dni); }
    @Override public Optional<Usuario> findByEmail(String email) { return usuarioRepository.findByEmail(email); }
    @Override public List<Usuario> listarTodos() { return usuarioRepository.findAll(); }
    @Override @Transactional public Usuario save(Usuario usuario) { return usuarioRepository.save(usuario); }

    private String generarCodigoUnico(String nombreCompleto) {
        String prefijo = nombreCompleto == null ? "USER" : nombreCompleto.replaceAll("[^a-zA-Z]", "").toUpperCase();
        prefijo = prefijo.length() > 4 ? prefijo.substring(0, 4) : (prefijo.isEmpty() ? "USER" : prefijo);
        String codigo;
        Random random = new Random();
        do { codigo = prefijo + (1000 + random.nextInt(9000)); }
        while (usuarioRepository.findByCodigoReferido(codigo).isPresent());
        return codigo;
    }

    private byte[] bytes(MultipartFile f) { try { return f.getBytes(); } catch (IOException e) { throw new IllegalStateException("No se pudo leer la imagen.", e); } }

    private void validarBlob(byte[] data, String type, String nombre) {
        if (data == null || data.length == 0) throw new IllegalArgumentException("Debe adjuntar " + nombre + ".");
        if (data.length > 5 * 1024 * 1024) throw new IllegalArgumentException("Cada imagen no puede superar 5 MB.");
        if (type == null || !type.matches("image/(jpeg|png|webp)")) throw new IllegalArgumentException("El archivo de " + nombre + " no es una imagen válida.");
    }

    private boolean archivoValido(MultipartFile f) { return f != null && !f.isEmpty(); }
    private void validarImagenObligatoria(MultipartFile f, String nombre) { if (!archivoValido(f)) throw new IllegalArgumentException("Debe adjuntar " + nombre + "."); }
    private void validarTexto(String value, String campo) { if (value == null || value.isBlank()) throw new IllegalArgumentException("El campo " + campo + " es obligatorio."); }
    private void validarDni(String dni) { if (dni == null || !dni.matches("\\d{8}")) throw new IllegalArgumentException("El DNI debe contener exactamente 8 dígitos."); }
    private void validarEmail(String email) { if (email == null || !email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) throw new IllegalArgumentException("El correo electrónico no es válido."); }
    private void validarPassword(String password) { if (password == null || password.length() < 8) throw new IllegalArgumentException("La contraseña debe tener al menos 8 caracteres."); }
    private void validarPositivo(BigDecimal value, String campo) { if (value == null || value.compareTo(BigDecimal.ZERO) <= 0) throw new IllegalArgumentException(campo + " debe ser mayor a cero."); }
    private void validarMultiploCien(BigDecimal value, String mensaje) { if (value == null || value.compareTo(BigDecimal.ZERO) <= 0 || value.remainder(CIEN).compareTo(BigDecimal.ZERO) != 0) throw new IllegalArgumentException(mensaje); }
}
