package ar.edu.uade.reclamos.dominio.repositorio;

import ar.edu.uade.reclamos.dominio.modelo.Apoyo;
import java.util.Optional;

/** Apoyos de ciudadanos a reclamos de otros (PR 17). Un ciudadano apoya cada reclamo una sola vez. */
public interface ApoyoRepository {

    /** Lo inserta. Si el ciudadano ya apoyaba el reclamo, la base lo rechaza por la restricción única. */
    Apoyo guardar(Apoyo apoyo);

    Optional<Apoyo> buscar(Long ciudadanoId, Long reclamoId);

    void eliminar(Apoyo apoyo);

    long contarPorReclamo(Long reclamoId);
}
