package ar.edu.uade.reclamos.aplicacion.servicio;

import ar.edu.uade.reclamos.aplicacion.seguridad.PermisosReclamo;
import ar.edu.uade.reclamos.comun.excepcion.RecursoNoEncontradoException;
import ar.edu.uade.reclamos.dominio.estrategia.EstrategiaPrioridad;
import ar.edu.uade.reclamos.dominio.evento.*;
import ar.edu.uade.reclamos.dominio.fabrica.ReclamoFactory;
import ar.edu.uade.reclamos.dominio.modelo.*;
import ar.edu.uade.reclamos.dominio.repositorio.*;
import java.time.Clock;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Casos de uso de escritura sobre un reclamo: crear, asignar y cambiar de estado.
 * Coordina fábrica, estrategia de prioridad, {@link ServicioAsignacion}, persistencia y eventos;
 * las reglas de transición y pertenencia las valida el dominio.
 */
@Service
public class ReclamoService {
    private final UsuarioRepository usuarios;
    private final CategoriaRepository categorias;
    private final BarrioRepository barrios;
    private final ReclamoRepository reclamos;
    private final ReclamoFactory factory;
    private final EstrategiaPrioridad prioridad;
    private final ServicioAsignacion asignacion;
    private final PermisosReclamo permisos;
    private final Clock reloj;
    private final PublicadorEventos eventos;

    public ReclamoService(UsuarioRepository usuarios, CategoriaRepository categorias, BarrioRepository barrios,
                          ReclamoRepository reclamos, ReclamoFactory factory,
                          EstrategiaPrioridad prioridad, ServicioAsignacion asignacion,
                          PermisosReclamo permisos, Clock reloj, PublicadorEventos eventos) {
        this.usuarios = usuarios;
        this.categorias = categorias;
        this.barrios = barrios;
        this.reclamos = reclamos;
        this.factory = factory;
        this.prioridad = prioridad;
        this.asignacion = asignacion;
        this.permisos = permisos;
        this.reloj = reloj;
        this.eventos = eventos;
    }

    @Transactional
    public Reclamo crear(Long usuarioId, Long categoriaId, Long barrioId, String descripcion, String direccion) {
        Ciudadano ciudadano = permisos.validarCreacion(usuario(usuarioId));
        Categoria categoria = categorias.buscarPorId(categoriaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("categoría", categoriaId));
        Barrio barrio = barrios.buscarPorId(barrioId)
                .orElseThrow(() -> new RecursoNoEncontradoException("barrio", barrioId));
        Reclamo reclamo = factory.crear(ciudadano, categoria, barrio, descripcion, direccion);
        reclamo.definirPrioridad(prioridad.calcular(reclamo));
        asignacion.asignarAutomaticamente(reclamo, categoriaId, barrioId, ahora());
        Reclamo guardado = reclamos.guardar(reclamo);
        eventos.publicar(new ReclamoCreado(guardado.getNumero(), ciudadano.getId(), categoriaId, barrioId,
                guardado.getPrioridad(), guardado.getFechaCreacion()));
        if (guardado.getArea() != null) {
            publicarAsignacion(guardado, null);
        }
        return guardado;
    }

    /** El dominio distingue asignación inicial y reasignación según el estado actual. */
    @Transactional
    public Reclamo asignar(Long usuarioId, String numero, Long areaId, String observacion) {
        Usuario actor = usuario(usuarioId);
        Reclamo reclamo = reclamo(numero);
        asignacion.asignarManualmente(reclamo, areaId, actor, observacion, ahora());
        Reclamo guardado = reclamos.guardar(reclamo);
        publicarAsignacion(guardado, actor);
        return guardado;
    }

    @Transactional
    public Reclamo cambiarEstado(Long usuarioId, String numero, EstadoReclamo estado, String observacion) {
        Usuario actor = usuario(usuarioId);
        Reclamo reclamo = reclamo(numero);
        EstadoReclamo anterior = reclamo.getEstado();
        LocalDateTime fecha = ahora();
        reclamo.cambiarEstado(estado, actor, observacion, fecha);
        Reclamo guardado = reclamos.guardar(reclamo);
        eventos.publicar(new EstadoReclamoCambiado(guardado.getNumero(), anterior, guardado.getEstado(),
                actor.getId(), fecha));
        if (guardado.getEstado() == EstadoReclamo.RESUELTO) {
            eventos.publicar(new ReclamoResuelto(guardado.getNumero(), guardado.getCiudadano().getId(),
                    actor.getId(), fecha));
        }
        return guardado;
    }

    @Transactional
    public Reclamo reasignar(Long usuarioId, String numero, Long areaId, String observacion) {
        return asignar(usuarioId, numero, areaId, observacion);
    }

    @Transactional
    public Reclamo tomar(Long usuarioId, String numero, String observacion) {
        return cambiarEstado(usuarioId, numero, EstadoReclamo.EN_PROCESO, observacion);
    }

    @Transactional
    public Reclamo resolver(Long usuarioId, String numero, String observacion) {
        return cambiarEstado(usuarioId, numero, EstadoReclamo.RESUELTO, observacion);
    }

    @Transactional
    public Reclamo cerrar(Long usuarioId, String numero, String observacion) {
        return cambiarEstado(usuarioId, numero, EstadoReclamo.CERRADO, observacion);
    }

    @Transactional
    public Reclamo reabrir(Long usuarioId, String numero, String observacion) {
        return cambiarEstado(usuarioId, numero, EstadoReclamo.EN_PROCESO, observacion);
    }

    @Transactional
    public Reclamo rechazar(Long usuarioId, String numero, String observacion) {
        return cambiarEstado(usuarioId, numero, EstadoReclamo.RECHAZADO, observacion);
    }

    @Transactional
    public Reclamo cancelar(Long usuarioId, String numero, String observacion) {
        return cambiarEstado(usuarioId, numero, EstadoReclamo.CANCELADO, observacion);
    }

    private Usuario usuario(Long id) {
        return usuarios.buscarPorId(id).orElseThrow(() -> new RecursoNoEncontradoException("usuario", id));
    }

    private Reclamo reclamo(String numero) {
        return reclamos.buscarPorNumero(numero)
                .orElseThrow(() -> new RecursoNoEncontradoException("reclamo", numero));
    }

    private LocalDateTime ahora() {
        return LocalDateTime.now(reloj);
    }

    private void publicarAsignacion(Reclamo reclamo, Usuario actor) {
        LocalDateTime fecha = reclamo.getHistorial().get(reclamo.getHistorial().size() - 1).getFecha();
        eventos.publicar(new ReclamoAsignado(reclamo.getNumero(), reclamo.getArea().getId(),
                reclamo.getArea().getNombre(), actor == null ? null : actor.getId(), fecha));
    }
}
