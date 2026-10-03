package ar.edu.uade.reclamos.aplicacion.estrategia;

import ar.edu.uade.reclamos.dominio.estrategia.EstrategiaAsignacion;
import ar.edu.uade.reclamos.dominio.modelo.AreaMunicipal;
import ar.edu.uade.reclamos.dominio.modelo.Reclamo;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** Las candidatas ya están filtradas por el Repository; el menor id desempata. */
public class AsignacionPorJurisdiccion implements EstrategiaAsignacion {
    @Override
    public Optional<AreaMunicipal> seleccionarArea(Reclamo reclamo, List<AreaMunicipal> candidatas) {
        return candidatas.stream().min(Comparator.comparing(AreaMunicipal::getId));
    }
}
