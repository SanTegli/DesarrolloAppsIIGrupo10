package ar.edu.uade.reclamos.dominio.modelo;

import ar.edu.uade.reclamos.comun.excepcion.AccesoDenegadoException;
import ar.edu.uade.reclamos.comun.excepcion.DatosInvalidosException;
import ar.edu.uade.reclamos.comun.excepcion.ReglaNegocioException;
import ar.edu.uade.reclamos.comun.validacion.Validador;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

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
    public static final int LONGITUD_MAXIMA_REFERENCIA_FOTO = 64;
    public static final int LONGITUD_MAXIMA_CATEGORIA_PROPUESTA = 80;
    public static final int LONGITUD_MAXIMA_MOTIVO_SUGERENCIA = 300;

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
    /** Referencia a la foto en el almacén de fotos; {@code null} en los reclamos anteriores a la foto. */
    private String foto;
    /** Bloqueo optimista: lo incrementa la persistencia en cada actualización. */
    private long version;
    private LocalDateTime fechaCreacion;
    private LocalDateTime fechaLimite;
    private Ciudadano ciudadano;
    private AgenteMunicipal agente;
    private Categoria categoria;
    private Municipio municipio;
    private AreaMunicipal area;
    /** PR 26: qué tipo de problema escribió el vecino al elegir la categoría "otra"; {@code null} en las demás. */
    private String categoriaPropuesta;
    /** PR 26: la categoría que sugirió la IA para un reclamo de "otra"; {@code null} si no sugirió ninguna. */
    private Categoria categoriaSugerida;
    /** PR 26: por qué la IA sugirió esa categoría, o por qué no sugirió; {@code null} mientras no respondió. */
    private String motivoSugerencia;
    private List<HistorialEstado> historial = new ArrayList<>();

    /** Requerido por JPA. */
    protected Reclamo() {
    }

    public Reclamo(String numero, Ciudadano ciudadano, Categoria categoria, Municipio municipio, String descripcion,
                   String direccion, Prioridad prioridad, LocalDateTime fechaCreacion, LocalDateTime fechaLimite) {
        this.numero = Validador.requerirTexto(numero, "número");
        this.ciudadano = Validador.requerirNoNulo(ciudadano, "ciudadano");
        this.categoria = Validador.requerirNoNulo(categoria, "categoría");
        this.municipio = Validador.requerirNoNulo(municipio, "municipio");
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
        if (!nuevaArea.getMunicipio().equals(municipio)) {
            throw new ReglaNegocioException("El área " + nuevaArea.getNombre() + " es de "
                    + nuevaArea.getMunicipio().getNombre() + " y el reclamo es de " + municipio.getNombre() + ".");
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
        cambiarEstado(nuevoEstado, actor, observacion, ahora, PoliticaReapertura.PREDETERMINADA);
    }

    /**
     * Igual que {@link #cambiarEstado(EstadoReclamo, Usuario, String, LocalDateTime)}, con la política de
     * reaperturas configurada. Reabrir (de {@code RESUELTO} a {@code EN_PROCESO}) exige un motivo, no superar la
     * cantidad máxima de reaperturas y estar dentro del plazo desde la última resolución.
     */
    public void cambiarEstado(EstadoReclamo nuevoEstado, Usuario actor, String observacion, LocalDateTime ahora,
                              PoliticaReapertura reaperturas) {
        Validador.requerirNoNulo(nuevoEstado, "estado");
        Validador.requerirNoNulo(actor, "usuario");
        Validador.requerirNoNulo(reaperturas, "política de reaperturas");
        if (nuevoEstado == EstadoReclamo.ASIGNADO) {
            throw new DatosInvalidosException("Para asignar un área se usa la operación de asignación.");
        }
        PoliticaTransiciones.validar(estado, nuevoEstado, actor.getRol());
        validarPertenencia(actor);
        if (esReapertura(estado, nuevoEstado)) {
            validarReapertura(observacion, ahora, reaperturas);
        }

        EstadoReclamo anterior = estado;
        if (anterior == EstadoReclamo.ASIGNADO && nuevoEstado == EstadoReclamo.EN_PROCESO) {
            this.agente = (AgenteMunicipal) actor;
        }
        this.estado = nuevoEstado;
        registrarCambio(anterior, actor, observacion, ahora);
    }

    /**
     * Cuántas veces se reabrió. Sale del historial: así cuenta también las reaperturas de antes de que existiera
     * el límite, y no se reinicia cuando el agente vuelve a resolver.
     */
    public int getCantidadReaperturas() {
        return (int) historial.stream()
                .filter(cambio -> esReapertura(cambio.getEstadoAnterior(), cambio.getEstadoNuevo()))
                .count();
    }

    /** Cuándo se resolvió por última vez, según el historial; vacío si nunca se resolvió. */
    public Optional<LocalDateTime> getFechaUltimaResolucion() {
        return historial.stream()
                .filter(cambio -> cambio.getEstadoNuevo() == EstadoReclamo.RESUELTO)
                .map(HistorialEstado::getFecha)
                .max(Comparator.naturalOrder());
    }

    /** Si hoy se podría reabrir, sin contar quién lo pide ni el motivo. Sirve para ofrecer o no la acción. */
    public boolean puedeReabrir(LocalDateTime ahora, PoliticaReapertura reaperturas) {
        return estado == EstadoReclamo.RESUELTO && motivoParaNoReabrir(ahora, reaperturas) == null;
    }

    private static boolean esReapertura(EstadoReclamo anterior, EstadoReclamo nuevo) {
        return anterior == EstadoReclamo.RESUELTO && nuevo == EstadoReclamo.EN_PROCESO;
    }

    private void validarReapertura(String motivo, LocalDateTime ahora, PoliticaReapertura reaperturas) {
        if (motivo == null || motivo.isBlank()) {
            throw new DatosInvalidosException("Para reabrir el reclamo hay que explicar el motivo.");
        }
        String problema = motivoParaNoReabrir(ahora, reaperturas);
        if (problema != null) {
            throw new ReglaNegocioException(problema);
        }
    }

    private String motivoParaNoReabrir(LocalDateTime ahora, PoliticaReapertura reaperturas) {
        int cantidad = getCantidadReaperturas();
        if (cantidad >= reaperturas.maximo()) {
            return reaperturas.maximo() == 0
                    ? "Los reclamos resueltos no se pueden reabrir: creá un reclamo nuevo."
                    : "El reclamo " + numero + " ya se reabrió " + (cantidad == 1 ? "una vez" : cantidad + " veces")
                            + ", que es el máximo. Si el problema sigue, creá un reclamo nuevo.";
        }
        Optional<LocalDateTime> resolucion = getFechaUltimaResolucion();
        if (resolucion.isPresent() && ahora.isAfter(resolucion.get().plus(reaperturas.plazo()))) {
            return "Pasaron más de " + reaperturas.plazoEnDias() + " desde que se resolvió el reclamo " + numero
                    + ": ya no se puede reabrir. Si el problema sigue, creá un reclamo nuevo.";
        }
        return null;
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

    /** La IA no respondió durante el alta y todavía tiene que revisar la prioridad en segundo plano. */
    public boolean estaPendienteDeIA() {
        return origenPrioridad == OrigenPrioridad.PENDIENTE_IA;
    }

    /**
     * Aplica la prioridad que la IA sugirió en segundo plano, después del alta. La regla es la misma que en el
     * alta: la IA sube, no baja. Queda la mayor entre la actual (que pudo subir por vencimiento mientras tanto)
     * y la sugerida.
     *
     * @return {@code true} si la prioridad cambió, para avisar solo en ese caso
     * @throws ReglaNegocioException si la prioridad no estaba pendiente de la IA
     */
    public boolean completarPrioridadPorIA(Prioridad sugerida, String motivo) {
        Validador.requerirNoNulo(sugerida, "prioridad sugerida");
        if (!estaPendienteDeIA()) {
            throw new ReglaNegocioException("La prioridad del reclamo " + numero + " ya no espera a la IA.");
        }
        Prioridad anterior = prioridad;
        if (prioridad.esMayorQue(sugerida)) {
            definirPrioridad(prioridad, OrigenPrioridad.CATEGORIA, "La IA sugirió " + sugerida
                    + "; se mantiene " + prioridad + ", que es mayor.");
        } else {
            definirPrioridad(sugerida, OrigenPrioridad.IA, motivo);
        }
        return prioridad != anterior;
    }

    /**
     * La IA no respondió en ninguno de los reintentos: la prioridad de la categoría queda como definitiva, con
     * origen {@link OrigenPrioridad#RESPALDO}. Si ya no estaba pendiente, no hace nada.
     */
    public void abandonarRevisionPorIA(String motivo) {
        if (estaPendienteDeIA()) {
            definirPrioridad(prioridad, OrigenPrioridad.RESPALDO, motivo);
        }
    }

    /**
     * Guarda qué tipo de problema escribió el vecino al elegir la categoría "otra". Lo llama la fábrica al crear
     * el reclamo; en las demás categorías no se usa.
     */
    public void proponerCategoria(String propuesta) {
        if (!categoria.esOtra()) {
            throw new DatosInvalidosException("La categoría " + categoria.getNombre()
                    + " no lleva un tipo de problema escrito: eso es solo para \"Otro problema\".");
        }
        if (categoriaPropuesta != null) {
            throw new ReglaNegocioException("El reclamo " + numero + " ya tiene una categoría propuesta.");
        }
        this.categoriaPropuesta = Validador.requerirTexto(propuesta, "tipo de problema",
                LONGITUD_MAXIMA_CATEGORIA_PROPUESTA);
    }

    /** La IA ya respondió sobre la categoría, con una sugerencia o sin ella. */
    public boolean tieneSugerenciaDeCategoria() {
        return motivoSugerencia != null;
    }

    /**
     * Guarda la categoría que sugirió la IA para un reclamo de "otra". Es solo una sugerencia: la categoría no
     * cambia hasta que un administrador recategoriza.
     *
     * @param sugerida la categoría sugerida, o {@code null} si la IA no encontró ninguna o no respondió
     * @param motivo   por qué; obligatorio, porque también explica la falta de sugerencia
     * @return {@code false} si no correspondía guardarla: el reclamo ya se recategorizó o ya tenía una sugerencia
     *         (el mensaje de la cola puede llegar dos veces o tarde)
     */
    public boolean registrarSugerenciaCategoria(Categoria sugerida, String motivo) {
        if (!categoria.esOtra() || tieneSugerenciaDeCategoria()) {
            return false;
        }
        if (sugerida != null && sugerida.esOtra()) {
            throw new DatosInvalidosException("La categoría sugerida no puede ser \"" + sugerida.getNombre() + "\".");
        }
        this.motivoSugerencia = Validador.requerirTexto(motivo, "motivo", LONGITUD_MAXIMA_MOTIVO_SUGERENCIA);
        this.categoriaSugerida = sugerida;
        return true;
    }

    /**
     * Cambia la categoría de un reclamo que todavía no tiene área, típicamente uno de "otra". Lo hace un
     * administrador. El plazo se vuelve a calcular desde la fecha de creación con el de la categoría nueva, y la
     * prioridad no baja: queda la mayor entre la actual y la base de la nueva. Deja un registro en el historial,
     * sin cambio de estado.
     *
     * @throws AccesoDenegadoException si no lo pide un administrador
     * @throws ReglaNegocioException   si el reclamo ya tiene área o la categoría es la misma
     */
    public void recategorizar(Categoria nueva, Usuario actor, String observacion, LocalDateTime ahora) {
        Validador.requerirNoNulo(nueva, "categoría");
        Validador.requerirNoNulo(actor, "usuario");
        if (actor.getRol() != Rol.ADMINISTRADOR) {
            throw new AccesoDenegadoException("Solo un administrador puede cambiar la categoría de un reclamo.");
        }
        if (estado != EstadoReclamo.INGRESADO) {
            throw new ReglaNegocioException("El reclamo " + numero + " ya está " + estado
                    + ": la categoría se cambia solo antes de asignarle un área.");
        }
        if (nueva.esOtra()) {
            throw new ReglaNegocioException("Elegí una categoría concreta, no \"" + nueva.getNombre() + "\".");
        }
        if (nueva.equals(categoria)) {
            throw new ReglaNegocioException("El reclamo ya es de la categoría " + nueva.getNombre() + ".");
        }
        String nota = "Categoría: " + categoria.getNombre() + " → " + nueva.getNombre() + "."
                + (observacion == null || observacion.isBlank() ? "" : " " + observacion.strip());
        this.categoria = nueva;
        this.fechaLimite = fechaCreacion.plusHours(nueva.getSlaHoras());
        if (vencido && !ahora.isAfter(fechaLimite)) {
            this.vencido = false;
        }
        if (nueva.getPrioridadBase().esMayorQue(prioridad)) {
            this.prioridad = nueva.getPrioridadBase();
            if (!estaPendienteDeIA()) {
                this.origenPrioridad = OrigenPrioridad.CATEGORIA;
                this.motivoPrioridad = null;
            }
        }
        registrarCambio(estado, actor, nota, ahora);
    }

    /** Guarda dónde ocurre el reclamo; reemplaza la ubicación anterior. */
    public void registrarUbicacion(Ubicacion nuevaUbicacion) {
        this.ubicacion = Validador.requerirNoNulo(nuevaUbicacion, "ubicación");
    }

    /**
     * Guarda la referencia a la foto del problema. Es la prueba que acompaña al alta: se registra una sola vez y
     * no se cambia después.
     */
    public void registrarFoto(String referencia) {
        if (foto != null) {
            throw new ReglaNegocioException("El reclamo " + numero + " ya tiene una foto.");
        }
        this.foto = Validador.requerirTexto(referencia, "foto", LONGITUD_MAXIMA_REFERENCIA_FOTO);
    }

    /**
     * Si puede aparecer como posible duplicado de un reclamo nuevo: sigue abierto y tiene la ubicación
     * confirmada en el mapa, porque sin coordenadas no hay cercanía.
     */
    public boolean puedeSerDuplicado() {
        return estado.estaPendienteDeResolucion() && ubicacion != null && ubicacion.isConfirmada();
    }

    public Optional<String> getFoto() {
        return Optional.ofNullable(foto);
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

    public Municipio getMunicipio() {
        return municipio;
    }

    /** {@code null} mientras el reclamo está sin asignar. */
    public AreaMunicipal getArea() {
        return area;
    }

    /** Qué tipo de problema escribió el vecino al elegir "otra"; {@code null} en las demás categorías. */
    public String getCategoriaPropuesta() {
        return categoriaPropuesta;
    }

    /** {@code null} si la IA no sugirió ninguna categoría, todavía no respondió o el reclamo no es de "otra". */
    public Categoria getCategoriaSugerida() {
        return categoriaSugerida;
    }

    /** {@code null} mientras la IA no respondió sobre la categoría. */
    public String getMotivoSugerencia() {
        return motivoSugerencia;
    }

    /** Cambios de estado en orden cronológico. El primero es el ingreso. */
    public List<HistorialEstado> getHistorial() {
        return Collections.unmodifiableList(historial);
    }
}
