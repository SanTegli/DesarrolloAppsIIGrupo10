package ar.edu.uade.reclamos.dominio.fabrica;

import ar.edu.uade.reclamos.comun.excepcion.DatosInvalidosException;
import ar.edu.uade.reclamos.comun.excepcion.ReglaNegocioException;
import ar.edu.uade.reclamos.comun.validacion.Validador;
import ar.edu.uade.reclamos.dominio.modelo.Categoria;
import ar.edu.uade.reclamos.dominio.modelo.Ciudadano;
import ar.edu.uade.reclamos.dominio.modelo.Municipio;
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

    public Reclamo crear(Ciudadano ciudadano, Categoria categoria, Municipio municipio, String descripcion,
                         String direccion) {
        return crear(ciudadano, categoria, municipio, descripcion, direccion, null);
    }

    /**
     * Igual que {@link #crear(Ciudadano, Categoria, Municipio, String, String)}, con lo que escribió el vecino
     * sobre el tipo de problema. Es obligatorio si la categoría es "otra" y se ignora en las demás: el
     * formulario lo pide solo para "Otro problema".
     */
    public Reclamo crear(Ciudadano ciudadano, Categoria categoria, Municipio municipio, String descripcion,
                         String direccion, String categoriaPropuesta) {
        Validador.requerirNoNulo(ciudadano, "ciudadano");
        Validador.requerirNoNulo(categoria, "categoría");
        Validador.requerirNoNulo(municipio, "municipio");
        if (!ciudadano.isActivo()) {
            throw new ReglaNegocioException("El ciudadano está inactivo y no puede crear reclamos.");
        }
        LocalDateTime ahora = LocalDateTime.now(reloj).truncatedTo(ChronoUnit.SECONDS);
        LocalDateTime fechaLimite = ahora.plusHours(categoria.getSlaHoras());
        if (categoria.esOtra() && (categoriaPropuesta == null || categoriaPropuesta.isBlank())) {
            throw new DatosInvalidosException("Elegiste \"" + categoria.getNombre()
                    + "\": contanos qué tipo de problema es.");
        }
        Reclamo reclamo = new Reclamo(generarNumero(), ciudadano, categoria, municipio, descripcion, direccion,
                categoria.getPrioridadBase(), ahora, fechaLimite);
        if (categoria.esOtra()) {
            reclamo.proponerCategoria(categoriaPropuesta.strip());
        }
        return reclamo;
    }

    /** Formato REC-XXXXXXXX: prefijo más ocho caracteres hexadecimales en mayúscula. */
    protected String generarNumero() {
        return PREFIJO_NUMERO + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
}
