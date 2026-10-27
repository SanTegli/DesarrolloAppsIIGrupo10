package ar.edu.uade.reclamos.aplicacion.servicio;

import ar.edu.uade.reclamos.comun.excepcion.RecursoNoEncontradoException;
import ar.edu.uade.reclamos.dominio.estrategia.EstrategiaAsignacion;
import ar.edu.uade.reclamos.dominio.integracion.CatalogoCobertura;
import ar.edu.uade.reclamos.dominio.integracion.ServicioExternoNoDisponibleException;
import ar.edu.uade.reclamos.dominio.modelo.AreaMunicipal;
import ar.edu.uade.reclamos.dominio.modelo.Reclamo;
import ar.edu.uade.reclamos.dominio.modelo.Usuario;
import ar.edu.uade.reclamos.dominio.repositorio.AreaMunicipalRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Decide qué área atiende un reclamo. La asignación automática pide las áreas candidatas al
 * {@link CatalogoCobertura} y delega la elección en la {@link EstrategiaAsignacion} activa; la manual
 * aplica el área que eligió el administrador.
 * No abre transacciones: participa de la del caso de uso que lo llama.
 */
@Service
public class ServicioAsignacion {

    static final String OBSERVACION_AUTOMATICA = "Asignación automática";
    private static final Logger LOGGER = LoggerFactory.getLogger(ServicioAsignacion.class);

    private final AreaMunicipalRepository areas;
    private final CatalogoCobertura cobertura;
    private final EstrategiaAsignacion estrategia;

    public ServicioAsignacion(AreaMunicipalRepository areas, CatalogoCobertura cobertura,
                              EstrategiaAsignacion estrategia) {
        this.areas = areas;
        this.cobertura = cobertura;
        this.estrategia = estrategia;
    }

    /**
     * Asigna el área que elige la estrategia entre las candidatas (activas, que atienden la categoría y
     * cubren el barrio). Si ninguna puede atenderlo, el reclamo queda INGRESADO. Si el catálogo de
     * cobertura no responde, también queda INGRESADO, pero marcado con la asignación pendiente.
     *
     * @return {@code true} si el reclamo quedó asignado
     */
    public boolean asignarAutomaticamente(Reclamo reclamo, Long categoriaId, Long barrioId, LocalDateTime ahora) {
        List<AreaMunicipal> candidatas;
        try {
            candidatas = cobertura.buscarCandidatas(reclamo.getBarrio().getMunicipio().getId(), barrioId,
                    categoriaId);
        } catch (ServicioExternoNoDisponibleException error) {
            LOGGER.warn("Cobertura no disponible para el reclamo {}: queda con la asignación pendiente",
                    reclamo.getNumero(), error);
            reclamo.marcarAsignacionPendiente();
            return false;
        }
        Optional<AreaMunicipal> elegida = estrategia.seleccionarArea(reclamo, candidatas);
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
