package ar.edu.uade.reclamos.dominio.modelo;

import ar.edu.uade.reclamos.comun.excepcion.AccesoDenegadoException;
import ar.edu.uade.reclamos.comun.excepcion.DatosInvalidosException;
import ar.edu.uade.reclamos.comun.excepcion.ReglaNegocioException;
import ar.edu.uade.reclamos.comun.validacion.Validador;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Reclamo de un ciudadano sobre un problema urbano. Es el agregado principal: protege su estado,
 * valida cada cambio contra {@link PoliticaTransiciones} y registra su propio historial.
 *
 * <p>Se crea con {@code ReclamoFactory}, no con {@code new}.
 */
public class Reclamo extends Entidad {

    public static final int LONGITUD_MAXIMA_DESCRIPCION = 1000;
    public static final int LONGITUD_MAXIMA_DIRECCION = 200;
    public static final int LONGITUD_MAXIMA_MOTIVO_PRIORIDAD = 300;

    private String numero;
    private String descripcion;
    private String direccion;
    private EstadoReclamo estado;
    private Prioridad prioridad;
    private OrigenPrioridad origenPrioridad;
    private String motivoPrioridad;
    private boolean vencido;
    private boolean asignacionPendiente;
    private Ubicacion ubicacion;
    /** Bloqueo optimista: lo incrementa la persistencia en cada actualización. */
    private long version;
    private LocalDateTime fechaCreacion;
    private LocalDateTime fechaLimite;
    private Ciudadano ciudadano;
    private AgenteMunicipal agente;
    private Categoria categoria;
    private Barrio barrio;
    private AreaMunicipal area;
    private List<HistorialEstado> historial = new ArrayList<>();

    /** Requerido por JPA. */
    protected Reclamo() {
    }

    public Reclamo(String numero, Ciudadano ciudadano, Categoria categoria, Barrio barrio, String descripcion,
                   String direccion, Prioridad prioridad, LocalDateTime fechaCreacion, LocalDateTime fechaLimite) {
        this.numero = Validador.requerirTexto(numero, "número");
        this.ciudadano = Validador.requerirNoNulo(ciudadano, "ciudadano");
        this.categoria = Validador.requerirNoNulo(categoria, "categoría");
        this.barrio = Validador.requerirNoNulo(barrio, "barrio");
        this.descripcion = Validador.requerirTexto(descripcion, "descripción", LONGITUD_MAXIMA_DESCRIPCION);
        this.direccion = Validador.requerirTexto(direccion, "dirección", LONGITUD_MAXIMA_DIRECCION);
        this.prioridad = Validador.requerirNoNulo(prioridad, "prioridad");
        this.fechaCreacion = Validador.requerirNoNulo(fechaCreacion, "fechaCreacion");
        this.fechaLimite = Validador.requerirNoNulo(fechaLimite, "fechaLimite");
        if (fechaLimite.isBefore(fechaCreacion)) {
            throw new DatosInvalidosException("La fecha límite no puede ser anterior a la fecha de creación.");
        }
        this.estado = EstadoReclamo.INGRESADO;
        this.origenPrioridad = OrigenPrioridad.CATEGORIA;
        this.ubicacion = Ubicacion.pendiente();
        registrarCambio(null, ciudadano, "Reclamo ingresado", fechaCreacion);
    }

    /**
     * Asigna o reasigna el área responsable y deja el reclamo en {@link EstadoReclamo#ASIGNADO}.
     *
     * @param actor quien asigna; {@code null} cuando la asignación es automática (rol {@link Rol#SISTEMA})
     */
    public void asignarArea(AreaMunicipal nuevaArea, Usuario actor, String observacion, LocalDateTime ahora) {
        Validador.requerirNoNulo(nuevaArea, "área");
        Rol rol = actor == null ? Rol.SISTEMA : actor.getRol();
        PoliticaTransiciones.validar(estado, EstadoReclamo.ASIGNADO, rol);
        if (!nuevaArea.isActiva()) {
            throw new ReglaNegocioException("El área " + nuevaArea.getNombre() + " no está activa.");
        }
        if (nuevaArea.equals(area)) {
            throw new ReglaNegocioException("El reclamo ya está asignado al área " + nuevaArea.getNombre() + ".");
        }
        EstadoReclamo anterior = estado;
        this.area = nuevaArea;
        this.agente = null;
        this.asignacionPendiente = false;
        this.estado = EstadoReclamo.ASIGNADO;
        registrarCambio(anterior, actor, observacion, ahora);
    }

    /**
     * Cambia el estado del reclamo. Además del rol, controla la pertenencia: un ciudadano solo opera
     * sobre sus reclamos y un agente solo sobre los del área a la que pertenece.
     *
     * <p>Para pasar a {@link EstadoReclamo#ASIGNADO} se usa {@link #asignarArea}, que necesita el área.
     */
    public void cambiarEstado(EstadoReclamo nuevoEstado, Usuario actor, String observacion, LocalDateTime ahora) {
        Validador.requerirNoNulo(nuevoEstado, "estado");
        Validador.requerirNoNulo(actor, "usuario");
        if (nuevoEstado == EstadoReclamo.ASIGNADO) {
            throw new DatosInvalidosException("Para asignar un área se usa la operación de asignación.");
        }
        PoliticaTransiciones.validar(estado, nuevoEstado, actor.getRol());
        validarPertenencia(actor);

        EstadoReclamo anterior = estado;
        if (anterior == EstadoReclamo.ASIGNADO && nuevoEstado == EstadoReclamo.EN_PROCESO) {
            this.agente = (AgenteMunicipal) actor;
        }
        this.estado = nuevoEstado;
        registrarCambio(anterior, actor, observacion, ahora);
    }

    /**
     * Marca el reclamo como vencido y le sube la prioridad si pasó su fecha límite sin resolverse.
     *
     * @return {@code true} solo la primera vez que se marca, para publicar el evento una única vez
     */
    public boolean marcarVencido(LocalDateTime ahora) {
        if (vencido || !estado.estaPendienteDeResolucion() || !ahora.isAfter(fechaLimite)) {
            return false;
        }
        this.vencido = true;
        this.prioridad = prioridad.siguiente();
        return true;
    }

    /**
     * La define la estrategia de prioridad después de crear el reclamo.
     *
     * @param origen qué decidió la prioridad, para que gestión sepa cuánto confiar en ella
     * @param motivo explicación breve; {@code null} cuando es la prioridad de la categoría
     */
    public void definirPrioridad(Prioridad nuevaPrioridad, OrigenPrioridad origen, String motivo) {
        this.prioridad = Validador.requerirNoNulo(nuevaPrioridad, "prioridad");
        this.origenPrioridad = Validador.requerirNoNulo(origen, "origen");
        this.motivoPrioridad = motivo == null || motivo.isBlank() ? null
                : Validador.requerirTexto(motivo, "motivo", LONGITUD_MAXIMA_MOTIVO_PRIORIDAD);
    }

    /** Guarda dónde ocurre el reclamo; reemplaza la ubicación anterior. */
    public void registrarUbicacion(Ubicacion nuevaUbicacion) {
        this.ubicacion = Validador.requerirNoNulo(nuevaUbicacion, "ubicación");
    }

    /**
     * El servicio de cobertura no respondió y el reclamo quedó sin asignar por esa razón, no por falta de
     * áreas. Un administrador lo asigna a mano; asignarlo borra la marca.
     */
    public void marcarAsignacionPendiente() {
        if (estado != EstadoReclamo.INGRESADO) {
            throw new ReglaNegocioException("Solo un reclamo INGRESADO puede quedar con la asignación pendiente.");
        }
        this.asignacionPendiente = true;
    }

    public boolean perteneceA(Usuario usuario) {
        return ciudadano.equals(usuario);
    }

    private void validarPertenencia(Usuario actor) {
        if (actor.getRol() == Rol.CIUDADANO && !perteneceA(actor)) {
            throw new AccesoDenegadoException("El reclamo " + numero + " pertenece a otro ciudadano.");
        }
        if (actor.getRol() == Rol.AGENTE_MUNICIPAL && !((AgenteMunicipal) actor).perteneceA(area)) {
            throw new AccesoDenegadoException("El reclamo " + numero + " está asignado a otra área.");
        }
    }

    private void registrarCambio(EstadoReclamo anterior, Usuario actor, String observacion, LocalDateTime fecha) {
        historial.add(new HistorialEstado(this, anterior, estado, actor, observacion,
                Validador.requerirNoNulo(fecha, "fecha")));
    }

    public String getNumero() {
        return numero;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public String getDireccion() {
        return direccion;
    }

    public EstadoReclamo getEstado() {
        return estado;
    }

    public Prioridad getPrioridad() {
        return prioridad;
    }

    /** Los reclamos guardados antes de existir este dato se informan como {@link OrigenPrioridad#CATEGORIA}. */
    public OrigenPrioridad getOrigenPrioridad() {
        return origenPrioridad == null ? OrigenPrioridad.CATEGORIA : origenPrioridad;
    }

    /** {@code null} cuando la prioridad es la de la categoría. */
    public String getMotivoPrioridad() {
        return motivoPrioridad;
    }

    public boolean isVencido() {
        return vencido;
    }

    public boolean isAsignacionPendiente() {
        return asignacionPendiente;
    }

    /** Nunca {@code null}: los reclamos guardados antes de existir este dato se informan como pendientes. */
    public Ubicacion getUbicacion() {
        return ubicacion == null ? Ubicacion.pendiente() : ubicacion;
    }

    public long getVersion() {
        return version;
    }

    public LocalDateTime getFechaCreacion() {
        return fechaCreacion;
    }

    public LocalDateTime getFechaLimite() {
        return fechaLimite;
    }

    public Ciudadano getCiudadano() {
        return ciudadano;
    }

    /** {@code null} hasta que un agente toma el reclamo. */
    public AgenteMunicipal getAgente() {
        return agente;
    }

    public Categoria getCategoria() {
        return categoria;
    }

    public Barrio getBarrio() {
        return barrio;
    }

    /** {@code null} mientras el reclamo está sin asignar. */
    public AreaMunicipal getArea() {
        return area;
    }

    /** Cambios de estado en orden cronológico. El primero es el ingreso. */
    public List<HistorialEstado> getHistorial() {
        return Collections.unmodifiableList(historial);
    }
}
