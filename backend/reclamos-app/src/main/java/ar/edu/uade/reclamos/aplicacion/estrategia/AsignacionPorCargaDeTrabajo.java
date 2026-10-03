package ar.edu.uade.reclamos.aplicacion.estrategia;

import ar.edu.uade.reclamos.dominio.estrategia.EstrategiaAsignacion;
import ar.edu.uade.reclamos.dominio.modelo.AreaMunicipal;
import ar.edu.uade.reclamos.dominio.modelo.Reclamo;
import ar.edu.uade.reclamos.dominio.repositorio.ReclamoRepository;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** Menor cantidad de ASIGNADO/EN_PROCESO; empate resuelto por menor id. */
public class AsignacionPorCargaDeTrabajo implements EstrategiaAsignacion {
    private final ReclamoRepository reclamos;

    public AsignacionPorCargaDeTrabajo(ReclamoRepository reclamos) {
        this.reclamos = reclamos;
    }

    @Override
    public Optional<AreaMunicipal> seleccionarArea(Reclamo reclamo, List<AreaMunicipal> candidatas) {
        if (candidatas.isEmpty()) {
            return Optional.empty();
        }
        if (candidatas.size() == 1) {
            return Optional.of(candidatas.getFirst());
        }
        return candidatas.stream()
                .map(area -> new Carga(area, reclamos.contarPendientesPorArea(area.getId())))
                .min(Comparator.comparingLong(Carga::pendientes)
                        .thenComparing(carga -> carga.area().getId()))
                .map(Carga::area);
    }

    private record Carga(AreaMunicipal area, long pendientes) { }
}
