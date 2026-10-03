package ar.edu.uade.reclamos.dominio.fabrica;

import ar.edu.uade.reclamos.comun.excepcion.ReglaNegocioException;
import ar.edu.uade.reclamos.comun.validacion.Validador;
import ar.edu.uade.reclamos.dominio.modelo.Barrio;
import ar.edu.uade.reclamos.dominio.modelo.Categoria;
import ar.edu.uade.reclamos.dominio.modelo.Ciudadano;
import ar.edu.uade.reclamos.dominio.modelo.Reclamo;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * Patrón Factory: único lugar donde nace un reclamo. Garantiza que todo reclamo nuevo tenga número,
 * estado INGRESADO, fecha de creación, fecha límite según el SLA de la categoría, la prioridad base
 * de la categoría y el primer registro de historial.
 *
 * <p>Recibe un {@link Clock} para que los tests controlen la fecha.
 */
public class ReclamoFactory {

    public static final String PREFIJO_NUMERO = "REC-";

    private final Clock reloj;

    public ReclamoFactory() {
        this(Clock.systemDefaultZone());
    }

    public ReclamoFactory(Clock reloj) {
        this.reloj = reloj;
    }

    public Reclamo crear(Ciudadano ciudadano, Categoria categoria, Barrio barrio, String descripcion,
                         String direccion) {
        Validador.requerirNoNulo(ciudadano, "ciudadano");
        Validador.requerirNoNulo(categoria, "categoría");
        Validador.requerirNoNulo(barrio, "barrio");
        if (!ciudadano.isActivo()) {
            throw new ReglaNegocioException("El ciudadano está inactivo y no puede crear reclamos.");
        }
        LocalDateTime ahora = LocalDateTime.now(reloj).truncatedTo(ChronoUnit.SECONDS);
        LocalDateTime fechaLimite = ahora.plusHours(categoria.getSlaHoras());
        return new Reclamo(generarNumero(), ciudadano, categoria, barrio, descripcion, direccion,
                categoria.getPrioridadBase(), ahora, fechaLimite);
    }

    /** Formato REC-XXXXXXXX: prefijo más ocho caracteres hexadecimales en mayúscula. */
    protected String generarNumero() {
        return PREFIJO_NUMERO + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
}
