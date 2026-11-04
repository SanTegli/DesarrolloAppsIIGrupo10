package ar.edu.uade.reclamos.aplicacion.tablero;

import java.time.LocalDate;

/**
 * El tablero público: cifras sin datos personales.
 *
 * @param creadosEnPeriodo    reclamos creados entre {@code desde} y {@code hasta}, los dos días incluidos
 * @param pendientes          hoy en INGRESADO, ASIGNADO o EN_PROCESO
 * @param porcentajeAtendidos resueltos y cerrados sobre pendientes, resueltos y cerrados; {@code null} si no hay
 *                            ninguno ("sin casos"), para no mostrar un porcentaje que no significa nada
 */
public record TableroPublico(LocalDate desde, LocalDate hasta, Long barrioId, Long categoriaId,
                             long creadosEnPeriodo, long pendientes, long resueltos, long cerrados,
                             long rechazados, long cancelados, Double porcentajeAtendidos) {
}
