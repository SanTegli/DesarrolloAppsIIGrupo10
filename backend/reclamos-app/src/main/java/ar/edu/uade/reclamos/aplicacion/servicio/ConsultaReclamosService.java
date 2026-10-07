package ar.edu.uade.reclamos.aplicacion.servicio;

import ar.edu.uade.reclamos.aplicacion.seguridad.PermisosReclamo;
import ar.edu.uade.reclamos.comun.excepcion.AccesoDenegadoException;
import ar.edu.uade.reclamos.comun.excepcion.RecursoNoEncontradoException;
import ar.edu.uade.reclamos.dominio.modelo.*;
import ar.edu.uade.reclamos.dominio.repositorio.*;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ConsultaReclamosService {
    private final UsuarioRepository usuarios;
    private final ReclamoRepository reclamos;
    private final NotificacionRepository notificaciones;
    private final PermisosReclamo permisos;

    public ConsultaReclamosService(UsuarioRepository usuarios, ReclamoRepository reclamos,
                                    NotificacionRepository notificaciones, PermisosReclamo permisos) {
        this.usuarios = usuarios;
        this.reclamos = reclamos;
        this.notificaciones = notificaciones;
        this.permisos = permisos;
    }

    public List<Reclamo> listar(Long usuarioId, EstadoReclamo estado, Long areaId, Long ciudadanoId) {
        Usuario usuario = usuario(usuarioId);
        permisos.validarFiltros(usuario, areaId, ciudadanoId);
        List<Reclamo> visibles = switch (usuario) {
            case Ciudadano ciudadano -> reclamos.buscarPorCiudadano(ciudadano.getId());
            case AgenteMunicipal agente -> reclamos.buscarPorArea(agente.getArea().getId());
            case Administrador administrador -> areaId != null ? reclamos.buscarPorArea(areaId)
                    : ciudadanoId != null ? reclamos.buscarPorCiudadano(ciudadanoId) : reclamos.buscarTodos();
            default -> throw new AccesoDenegadoException("Tipo de usuario no permitido.");
        };
        return visibles.stream()
                .filter(reclamo -> estado == null || reclamo.getEstado() == estado)
                .filter(reclamo -> areaId == null || reclamo.getArea() != null
                        && Objects.equals(reclamo.getArea().getId(), areaId))
                .filter(reclamo -> ciudadanoId == null
                        || Objects.equals(reclamo.getCiudadano().getId(), ciudadanoId))
                .toList();
    }

    public Reclamo buscarPorNumero(Long usuarioId, String numero) {
        Usuario usuario = usuario(usuarioId);
        Reclamo reclamo = reclamo(numero);
        permisos.validarConsulta(usuario, reclamo);
        return reclamo;
    }

    public List<Notificacion> consultarNotificaciones(Long usuarioId, String numero) {
        buscarPorNumero(usuarioId, numero);
        return notificaciones.buscarPorReclamo(numero);
    }

    /** Bandeja de avisos: todo lo que se le notificó al usuario, del aviso más nuevo al más viejo. */
    public List<Notificacion> consultarAvisosDelUsuario(Long usuarioId) {
        Usuario usuario = usuario(usuarioId);
        return notificaciones.buscarPorDestinatario(usuario.getId());
    }

    public Set<EstadoReclamo> accionesDisponibles(Long usuarioId, String numero) {
        Usuario usuario = usuario(usuarioId);
        Reclamo reclamo = reclamo(numero);
        permisos.validarConsulta(usuario, reclamo);
        return permisos.accionesDisponibles(usuario, reclamo);
    }

    private Usuario usuario(Long id) {
        return usuarios.buscarPorId(id).orElseThrow(() -> new RecursoNoEncontradoException("usuario", id));
    }

    private Reclamo reclamo(String numero) {
        return reclamos.buscarPorNumero(numero)
                .orElseThrow(() -> new RecursoNoEncontradoException("reclamo", numero));
    }
}
