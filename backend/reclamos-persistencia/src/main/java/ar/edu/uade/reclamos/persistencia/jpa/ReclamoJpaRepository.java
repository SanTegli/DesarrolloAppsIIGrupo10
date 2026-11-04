package ar.edu.uade.reclamos.persistencia.jpa;

import ar.edu.uade.reclamos.dominio.modelo.EstadoReclamo;
import ar.edu.uade.reclamos.dominio.modelo.EstadoUbicacion;
import ar.edu.uade.reclamos.dominio.modelo.Reclamo;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Contrato técnico interno; los servicios usan el Repository del dominio. */
public interface ReclamoJpaRepository extends JpaRepository<Reclamo, Long> {
    @EntityGraph(attributePaths = {"ciudadano", "categoria", "barrio.municipio", "area", "agente"})
    Optional<Reclamo> findByNumero(String numero);

    @EntityGraph(attributePaths = {"ciudadano", "categoria", "barrio.municipio", "area", "agente"})
    List<Reclamo> findAllByOrderByFechaCreacionDescIdDesc();

    @EntityGraph(attributePaths = {"ciudadano", "categoria", "barrio.municipio", "area", "agente"})
    List<Reclamo> findByCiudadanoIdOrderByFechaCreacionDescIdDesc(Long ciudadanoId);

    @EntityGraph(attributePaths = {"ciudadano", "categoria", "barrio.municipio", "area", "agente"})
    List<Reclamo> findByAreaIdOrderByFechaCreacionDescIdDesc(Long areaId);

    long countByAreaIdAndEstadoIn(Long areaId, Collection<EstadoReclamo> estados);

    @EntityGraph(attributePaths = {"ciudadano", "categoria", "barrio.municipio", "area", "agente"})
    @Query("select r from Reclamo r where r.categoria.id = :categoriaId and r.estado in :estados"
            + " and r.ubicacion.estado = :confirmada"
            + " and r.ubicacion.latitud between :latitudMinima and :latitudMaxima"
            + " and r.ubicacion.longitud between :longitudMinima and :longitudMaxima"
            + " order by r.fechaCreacion desc, r.id desc")
    List<Reclamo> buscarEnRecuadro(@Param("categoriaId") Long categoriaId,
                                   @Param("estados") Collection<EstadoReclamo> estados,
                                   @Param("confirmada") EstadoUbicacion confirmada,
                                   @Param("latitudMinima") double latitudMinima,
                                   @Param("latitudMaxima") double latitudMaxima,
                                   @Param("longitudMinima") double longitudMinima,
                                   @Param("longitudMaxima") double longitudMaxima);

    long countByCiudadanoIdAndFechaCreacionGreaterThanEqual(Long ciudadanoId, LocalDateTime desde);

    List<Reclamo> findByVencidoFalseAndEstadoInAndFechaLimiteBeforeOrderByFechaCreacionDescIdDesc(
            Collection<EstadoReclamo> estados, LocalDateTime ahora);
}

