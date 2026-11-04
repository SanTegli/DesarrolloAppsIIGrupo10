package ar.edu.uade.reclamos.persistencia.adaptador;

import ar.edu.uade.reclamos.dominio.modelo.CodigoVerificacion;
import ar.edu.uade.reclamos.dominio.repositorio.CodigoVerificacionRepository;
import ar.edu.uade.reclamos.persistencia.jpa.CodigoVerificacionJpaRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional(readOnly = true)
public class CodigoVerificacionRepositoryJpa implements CodigoVerificacionRepository {
    private final CodigoVerificacionJpaRepository jpa;

    public CodigoVerificacionRepositoryJpa(CodigoVerificacionJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    @Transactional
    public CodigoVerificacion guardar(CodigoVerificacion codigo) {
        return jpa.save(codigo);
    }

    @Override
    public Optional<CodigoVerificacion> buscarPorCodigo(String codigo) {
        return codigo == null ? Optional.empty() : jpa.findByCodigo(codigo);
    }

    @Override
    public long contarCreadosPorCiudadanoDesde(Long ciudadanoId, LocalDateTime desde) {
        return jpa.countByCiudadanoIdAndFechaCreacionGreaterThanEqual(ciudadanoId, desde);
    }
}
