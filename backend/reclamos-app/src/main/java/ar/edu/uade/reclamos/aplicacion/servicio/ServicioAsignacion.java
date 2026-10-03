package ar.edu.uade.reclamos.aplicacion.servicio;

import ar.edu.uade.reclamos.comun.excepcion.RecursoNoEncontradoException;
import ar.edu.uade.reclamos.dominio.estrategia.EstrategiaAsignacion;
import ar.edu.uade.reclamos.dominio.modelo.AreaMunicipal;
import ar.edu.uade.reclamos.dominio.modelo.Reclamo;
import ar.edu.uade.reclamos.dominio.modelo.Usuario;
import ar.edu.uade.reclamos.dominio.repositorio.AreaMunicipalRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * Decide qué área atiende un reclamo. La asignación automática delega la elección en la
 * {@link EstrategiaAsignacion} activa; la manual aplica el área que eligió el administrador.
 * No abre transacciones: participa de la del caso de uso que lo llama.
 */
@Service
public class ServicioAsignacion {
    static final String OBSERVACION_AUTOMATICA = "Asignación automática";

    private final AreaMunicipalRepository areas;
    private final EstrategiaAsignacion estrategia;

    public ServicioAsignacion(AreaMunicipalRepository areas, EstrategiaAsignacion estrategia) {
        this.areas = areas;
        this.estrategia = estrategia;
    }

    /**
     * Busca las áreas candidatas (activas, que atienden la categoría y cubren el barrio) y asigna la
     * que elige la estrategia. Si ninguna puede atenderlo, el reclamo queda INGRESADO.
     *
     * @return {@code true} si el reclamo quedó asignado
     */
    public boolean asignarAutomaticamente(Reclamo reclamo, Long categoriaId, Long barrioId, LocalDateTime ahora) {
        Optional<AreaMunicipal> elegida =
                estrategia.seleccionarArea(reclamo, areas.buscarCandidatas(categoriaId, barrioId));
        elegida.ifPresent(area -> reclamo.asignarArea(area, null, OBSERVACION_AUTOMATICA, ahora));
        return elegida.isPresent();
    }

    /** Asignación o reasignación decidida por un usuario. Las reglas de rol y estado las valida el dominio. */
    public void asignarManualmente(Reclamo reclamo, Long areaId, Usuario actor, String observacion,
                                   LocalDateTime ahora) {
        AreaMunicipal area = areas.buscarPorId(areaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("área", areaId));
        reclamo.asignarArea(area, actor, observacion, ahora);
    }
}
