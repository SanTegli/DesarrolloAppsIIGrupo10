package ar.edu.uade.reclamos.aplicacion.tablero;

import ar.edu.uade.reclamos.dominio.reporte.FilaPendientes;
import java.time.LocalDateTime;
import java.util.List;

/**
 * El tablero de gestión, para el municipio.
 *
 * @param areaId             el área del agente; {@code null} para el administrador, que ve todas
 * @param vencidos           pendientes que pasaron su fecha límite, calculados por fecha
 * @param sinAsignar         pendientes sin área (INGRESADO); {@code null} para un agente, que no asigna
 * @param porcentajeVencidos vencidos sobre pendientes; {@code null} sin pendientes ("sin casos")
 */
public record TableroGestion(LocalDateTime fecha, Long areaId, String area, long pendientes, long vencidos,
                             Long sinAsignar, Double porcentajeVencidos, List<FilaPendientes> filas) {
}
