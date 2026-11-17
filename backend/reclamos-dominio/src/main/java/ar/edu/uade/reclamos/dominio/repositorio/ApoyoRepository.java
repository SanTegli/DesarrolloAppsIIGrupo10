package ar.edu.uade.reclamos.dominio.repositorio;

import ar.edu.uade.reclamos.dominio.modelo.Apoyo;
import ar.edu.uade.reclamos.dominio.modelo.Ciudadano;
import java.util.List;
import java.util.Optional;

/** Apoyos de ciudadanos a reclamos de otros (PR 17). Un ciudadano apoya cada reclamo una sola vez. */
public interface ApoyoRepository {

    /** Lo inserta. Si el ciudadano ya apoyaba el reclamo, la base lo rechaza por la restricción única. */
    Apoyo guardar(Apoyo apoyo);

    Optional<Apoyo> buscar(Long ciudadanoId, Long reclamoId);

    void eliminar(Apoyo apoyo);

    long contarPorReclamo(Long reclamoId);

    /** Los ciudadanos que apoyan el reclamo, del primero al último (PR 27: reciben sus avisos). */
    List<Ciudadano> buscarApoyantes(Long reclamoId);
}
