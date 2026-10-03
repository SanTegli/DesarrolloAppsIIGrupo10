package ar.edu.uade.reclamos.dominio.modelo;

import ar.edu.uade.reclamos.comun.excepcion.AccesoDenegadoException;
import ar.edu.uade.reclamos.comun.excepcion.ReglaNegocioException;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Única fuente de verdad del ciclo de vida del reclamo: qué transiciones existen y qué rol puede
 * ejecutar cada una. No se usa el patrón State: una tabla alcanza y es más fácil de leer.
 *
 * <pre>
 * INGRESADO  -> ASIGNADO    Sistema, Administrador
 * INGRESADO  -> RECHAZADO   Administrador
 * INGRESADO  -> CANCELADO   Ciudadano
 * ASIGNADO   -> ASIGNADO    Administrador (reasignación)
 * ASIGNADO   -> EN_PROCESO  Agente municipal
 * ASIGNADO   -> CANCELADO   Ciudadano
 * EN_PROCESO -> RESUELTO    Agente municipal
 * RESUELTO   -> CERRADO     Ciudadano
 * RESUELTO   -> EN_PROCESO  Ciudadano (reapertura)
 * </pre>
 */
public final class PoliticaTransiciones {

    private record Transicion(EstadoReclamo origen, EstadoReclamo destino) {
    }

    private static final Map<Transicion, Set<Rol>> REGLAS = new LinkedHashMap<>();

    static {
        permitir(EstadoReclamo.INGRESADO, EstadoReclamo.ASIGNADO, Rol.SISTEMA, Rol.ADMINISTRADOR);
        permitir(EstadoReclamo.INGRESADO, EstadoReclamo.RECHAZADO, Rol.ADMINISTRADOR);
        permitir(EstadoReclamo.INGRESADO, EstadoReclamo.CANCELADO, Rol.CIUDADANO);
        permitir(EstadoReclamo.ASIGNADO, EstadoReclamo.ASIGNADO, Rol.ADMINISTRADOR);
        permitir(EstadoReclamo.ASIGNADO, EstadoReclamo.EN_PROCESO, Rol.AGENTE_MUNICIPAL);
        permitir(EstadoReclamo.ASIGNADO, EstadoReclamo.CANCELADO, Rol.CIUDADANO);
        permitir(EstadoReclamo.EN_PROCESO, EstadoReclamo.RESUELTO, Rol.AGENTE_MUNICIPAL);
        permitir(EstadoReclamo.RESUELTO, EstadoReclamo.CERRADO, Rol.CIUDADANO);
        permitir(EstadoReclamo.RESUELTO, EstadoReclamo.EN_PROCESO, Rol.CIUDADANO);
    }

    private PoliticaTransiciones() {
    }

    private static void permitir(EstadoReclamo origen, EstadoReclamo destino, Rol primero, Rol... resto) {
        REGLAS.put(new Transicion(origen, destino), EnumSet.of(primero, resto));
    }

    /** ¿Existe la transición, sin importar quién la ejecute? */
    public static boolean existe(EstadoReclamo origen, EstadoReclamo destino) {
        return REGLAS.containsKey(new Transicion(origen, destino));
    }

    /** Roles que pueden ejecutar la transición; vacío si la transición no existe. */
    public static Set<Rol> rolesPermitidos(EstadoReclamo origen, EstadoReclamo destino) {
        Set<Rol> roles = REGLAS.get(new Transicion(origen, destino));
        return roles == null ? EnumSet.noneOf(Rol.class) : EnumSet.copyOf(roles);
    }

    public static boolean permite(EstadoReclamo origen, EstadoReclamo destino, Rol rol) {
        return rolesPermitidos(origen, destino).contains(rol);
    }

    /**
     * Valida la transición o lanza el error que corresponde.
     *
     * @throws ReglaNegocioException   si la transición no existe (HTTP 409)
     * @throws AccesoDenegadoException si existe pero el rol no puede ejecutarla (HTTP 403)
     */
    public static void validar(EstadoReclamo origen, EstadoReclamo destino, Rol rol) {
        if (!existe(origen, destino)) {
            throw new ReglaNegocioException(
                    "Un reclamo en estado " + origen + " no puede pasar a " + destino + ".");
        }
        if (!permite(origen, destino, rol)) {
            throw new AccesoDenegadoException(
                    "El rol " + rol + " no puede pasar un reclamo de " + origen + " a " + destino + ".");
        }
    }

    /** Estados a los que el rol puede llevar un reclamo desde {@code origen}. Sirve para armar las acciones de la UI. */
    public static Set<EstadoReclamo> destinosPosibles(EstadoReclamo origen, Rol rol) {
        Set<EstadoReclamo> destinos = EnumSet.noneOf(EstadoReclamo.class);
        REGLAS.forEach((transicion, roles) -> {
            if (transicion.origen() == origen && roles.contains(rol)) {
                destinos.add(transicion.destino());
            }
        });
        return destinos;
    }
}
