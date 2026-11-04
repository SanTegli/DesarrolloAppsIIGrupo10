package ar.edu.uade.reclamos.dominio.repositorio;

import ar.edu.uade.reclamos.dominio.modelo.CodigoVerificacion;
import java.time.LocalDateTime;
import java.util.Optional;

/** Códigos de verificación del teléfono. */
public interface CodigoVerificacionRepository {

    CodigoVerificacion guardar(CodigoVerificacion codigo);

    Optional<CodigoVerificacion> buscarPorCodigo(String codigo);

    /** Cuántos códigos pidió el ciudadano desde {@code desde}. Lo usa el límite de códigos por hora. */
    long contarCreadosPorCiudadanoDesde(Long ciudadanoId, LocalDateTime desde);
}
