package ar.edu.uade.reclamos.aplicacion.servicio;

import ar.edu.uade.reclamos.aplicacion.control.ControlesDeUso;
import ar.edu.uade.reclamos.aplicacion.seguridad.PermisosReclamo;
import ar.edu.uade.reclamos.comun.excepcion.RecursoNoEncontradoException;
import ar.edu.uade.reclamos.dominio.estrategia.EstrategiaPrioridad;
import ar.edu.uade.reclamos.dominio.estrategia.PrioridadCalculada;
import ar.edu.uade.reclamos.dominio.evento.*;
import ar.edu.uade.reclamos.dominio.fabrica.ReclamoFactory;
import ar.edu.uade.reclamos.dominio.modelo.*;
import ar.edu.uade.reclamos.dominio.repositorio.*;
import java.time.Clock;
import java.time.LocalDateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Casos de uso de escritura sobre un reclamo: crear, asignar, recategorizar y cambiar de estado.
 * Coordina fábrica, estrategia de prioridad, {@link ServicioAsignacion}, persistencia y eventos;
 * las reglas de transición y pertenencia las valida el dominio.
 */
@Service
public class ReclamoService {
    private final UsuarioRepository usuarios;
    private final CategoriaRepository categorias;
    private final MunicipioRepository municipios;
    private final ReclamoRepository reclamos;
    private final ReclamoFactory factory;
    private final EstrategiaPrioridad prioridad;
    private final ServicioAsignacion asignacion;
    private final PermisosReclamo permisos;
    private final Clock reloj;
    private final PublicadorEventos eventos;
    private final ControlesDeUso controles;
    private final PoliticaReapertura reaperturas;

    public ReclamoService(UsuarioRepository usuarios, CategoriaRepository categorias, MunicipioRepository municipios,
                          ReclamoRepository reclamos, ReclamoFactory factory,
                          EstrategiaPrioridad prioridad, ServicioAsignacion asignacion,
                          PermisosReclamo permisos, Clock reloj, PublicadorEventos eventos) {
        this(usuarios, categorias, municipios, reclamos, factory, prioridad, asignacion, permisos, reloj, eventos,
                ControlesDeUso.NINGUNO, PoliticaReapertura.PREDETERMINADA);
    }

    /**
     * @param controles   teléfono verificado, cuenta no bloqueada y límite diario, antes de crear o reabrir
     * @param reaperturas cuántas veces y hasta cuándo se puede reabrir un reclamo
     */
    @Autowired
    public ReclamoService(UsuarioRepository usuarios, CategoriaRepository categorias, MunicipioRepository municipios,
                          ReclamoRepository reclamos, ReclamoFactory factory,
                          EstrategiaPrioridad prioridad, ServicioAsignacion asignacion,
                          PermisosReclamo permisos, Clock reloj, PublicadorEventos eventos,
                          ControlesDeUso controles, PoliticaReapertura reaperturas) {
        this.controles = controles;
        this.reaperturas = reaperturas;
        this.usuarios = usuarios;
        this.categorias = categorias;
        this.municipios = municipios;
        this.reclamos = reclamos;
        this.factory = factory;
        this.prioridad = prioridad;
        this.asignacion = asignacion;
        this.permisos = permisos;
        this.reloj = reloj;
        this.eventos = eventos;
    }

    /** Alta sin ubicación en el mapa: queda pendiente. */
    @Transactional
    public Reclamo crear(Long usuarioId, Long categoriaId, Long municipioId, String descripcion, String direccion) {
        return crear(usuarioId, categoriaId, municipioId, descripcion, direccion, Ubicacion.pendiente());
    }

    /**
     * Alta con la ubicación ya validada por {@code ServicioUbicaciones}: confirmada si el ciudadano eligió una
     * sugerencia del geocodificador, pendiente si no.
     */
    @Transactional
    public Reclamo crear(Long usuarioId, Long categoriaId, Long municipioId, String descripcion, String direccion,
                         Ubicacion ubicacion) {
        return crear(usuarioId, categoriaId, municipioId, descripcion, direccion, ubicacion, null);
    }

    /**
     * Alta con la foto ya validada y guardada como pendiente por {@code ServicioFotos}.
     *
     * @param referenciaFoto la referencia en el almacén de fotos; {@code null} solo desde los tests de servicio
     */
    @Transactional
    public Reclamo crear(Long usuarioId, Long categoriaId, Long municipioId, String descripcion, String direccion,
                         Ubicacion ubicacion, String referenciaFoto) {
        return crear(usuarioId, categoriaId, municipioId, descripcion, direccion, ubicacion, referenciaFoto, null);
    }

    /**
     * Alta completa.
     *
     * @param categoriaPropuesta qué tipo de problema es, en palabras del vecino; obligatoria con "Otro problema"
     */
    @Transactional
    public Reclamo crear(Long usuarioId, Long categoriaId, Long municipioId, String descripcion, String direccion,
                         Ubicacion ubicacion, String referenciaFoto, String categoriaPropuesta) {
        Ciudadano ciudadano = permisos.validarCreacion(usuario(usuarioId));
        controles.antesDeCrear(ciudadano, ahora());
        Categoria categoria = categorias.buscarPorId(categoriaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("categoría", categoriaId));
        Municipio municipio = municipios.buscarPorId(municipioId)
                .orElseThrow(() -> new RecursoNoEncontradoException("municipio", municipioId));
        Reclamo reclamo = factory.crear(ciudadano, categoria, municipio, descripcion, direccion, categoriaPropuesta);
        reclamo.registrarUbicacion(ubicacion);
        if (referenciaFoto != null) {
            reclamo.registrarFoto(referenciaFoto);
        }
        PrioridadCalculada calculada = prioridad.calcular(reclamo);
        reclamo.definirPrioridad(calculada.prioridad(), calculada.origen(), calculada.motivo());
        asignacion.asignarAutomaticamente(reclamo, ahora());
        Reclamo guardado = reclamos.guardar(reclamo);
        eventos.publicar(new ReclamoCreado(guardado.getNumero(), ciudadano.getId(), categoriaId, municipioId,
                categoria.esOtra(), guardado.getPrioridad(), guardado.getFechaCreacion()));
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

    /**
     * Un administrador cambia la categoría de un reclamo sin área (PR 26), típicamente uno de "Otro problema" con
     * la sugerencia de la IA. Después intenta asignarlo automáticamente, como en el alta: si la categoría nueva
     * tiene un área en el municipio, el reclamo queda {@code ASIGNADO}.
     */
    @Transactional
    public Reclamo recategorizar(Long usuarioId, String numero, Long categoriaId, String observacion) {
        Usuario actor = usuario(usuarioId);
        Reclamo reclamo = reclamo(numero);
        Categoria nueva = categorias.buscarPorId(categoriaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("categoría", categoriaId));
        Categoria anterior = reclamo.getCategoria();
        boolean sugeridaPorIA = nueva.equals(reclamo.getCategoriaSugerida());
        LocalDateTime fecha = ahora();
        reclamo.recategorizar(nueva, actor, observacion, fecha);
        asignacion.asignarAutomaticamente(reclamo, fecha);
        Reclamo guardado = reclamos.guardar(reclamo);
        eventos.publicar(new ReclamoRecategorizado(guardado.getNumero(), anterior.getId(), anterior.getNombre(),
                nueva.getId(), nueva.getNombre(), sugeridaPorIA, actor.getId(), fecha));
        if (guardado.getArea() != null) {
            publicarAsignacion(guardado, null);
        }
        return guardado;
    }

    @Transactional
    public Reclamo cambiarEstado(Long usuarioId, String numero, EstadoReclamo estado, String observacion) {
        Usuario actor = usuario(usuarioId);
        Reclamo reclamo = reclamo(numero);
        EstadoReclamo anterior = reclamo.getEstado();
        LocalDateTime fecha = ahora();
        if (actor instanceof Ciudadano ciudadano && anterior == EstadoReclamo.RESUELTO
                && estado == EstadoReclamo.EN_PROCESO) {
            controles.antesDeReabrir(ciudadano, fecha);
        }
        reclamo.cambiarEstado(estado, actor, observacion, fecha, reaperturas);
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
