package ar.edu.uade.reclamos.aplicacion.estrategia;

import ar.edu.uade.reclamos.dominio.estrategia.EstrategiaPrioridad;
import ar.edu.uade.reclamos.dominio.modelo.Prioridad;
import ar.edu.uade.reclamos.dominio.modelo.Reclamo;
import java.util.regex.Pattern;

/**
 * Urgente, peligro, riesgo y accidente elevan la prioridad como mínimo a ALTA.
 * Busca palabras completas, sin distinguir mayúsculas, y nunca reduce la base.
 * No interpreta negaciones, plurales ni el contexto de la descripción.
 */
public class PrioridadPorPalabrasClave implements EstrategiaPrioridad {
    private static final Pattern PALABRAS = Pattern.compile(
            "(?<![\\p{L}\\p{N}_])(urgente|peligro|riesgo|accidente)(?![\\p{L}\\p{N}_])",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    @Override
    public Prioridad calcular(Reclamo reclamo) {
        Prioridad base = reclamo.getCategoria().getPrioridadBase();
        return PALABRAS.matcher(reclamo.getDescripcion()).find()
                ? Prioridad.mayor(base, Prioridad.ALTA) : base;
    }
}
