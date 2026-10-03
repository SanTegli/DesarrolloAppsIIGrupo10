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

    private String numero;
    private String descripcion;
    private String direccion;
    private EstadoReclamo estado;
    private Prioridad prioridad;
    private boolean vencido;
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

    /** La define la estrategia de prioridad después de crear el reclamo. */
    public void definirPrioridad(Prioridad nuevaPrioridad) {
        this.prioridad = Validador.requerirNoNulo(nuevaPrioridad, "prioridad");
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

    public boolean isVencido() {
        return vencido;
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
