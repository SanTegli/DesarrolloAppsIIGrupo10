package ar.edu.uade.reclamos.dominio.reporte;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Lado de lectura de los tableros (Domain Reporting, CQRS: Clase 5). Las métricas no recorren reclamos ni
 * pasan por el agregado: son consultas agregadas que devuelven proyecciones planas. Por eso es un puerto
 * aparte de {@code ReclamoRepository}, que sigue siendo del lado de escritura.
 *
 * <p>Los filtros {@code null} no filtran.
 */
public interface ConsultasTablero {

    /** Reclamos creados en {@code [desde, hasta)}. */
    long contarCreados(LocalDateTime desde, LocalDateTime hasta, Long barrioId, Long categoriaId);

    /** Cuántos reclamos hay hoy en cada estado. Los estados sin reclamos no aparecen. */
    List<ConteoPorEstado> contarPorEstado(Long barrioId, Long categoriaId);

    /**
     * Reclamos pendientes (INGRESADO, ASIGNADO o EN_PROCESO) por área y barrio, con cuántos pasaron su fecha
     * límite a {@code ahora}. Los INGRESADO van en la fila sin área. Con {@code areaId}, solo esa área.
     */
    List<FilaPendientes> pendientesPorAreaYBarrio(LocalDateTime ahora, Long areaId);
}
