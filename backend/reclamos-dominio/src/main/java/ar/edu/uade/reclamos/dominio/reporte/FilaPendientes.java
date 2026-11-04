package ar.edu.uade.reclamos.dominio.reporte;

/**
 * Proyección: pendientes y vencidos de un área en un barrio.
 *
 * @param areaId {@code null} para los reclamos todavía sin área (INGRESADO)
 * @param area   {@code null} igual que {@code areaId}
 */
public record FilaPendientes(Long areaId, String area, Long barrioId, String barrio, Long pendientes,
                             Long vencidos) {
}
