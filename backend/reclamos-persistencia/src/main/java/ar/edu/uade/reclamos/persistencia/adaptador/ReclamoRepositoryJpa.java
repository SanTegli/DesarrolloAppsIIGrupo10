package ar.edu.uade.reclamos.persistencia.adaptador;

import ar.edu.uade.reclamos.dominio.modelo.EstadoReclamo;
import ar.edu.uade.reclamos.dominio.modelo.Reclamo;
import ar.edu.uade.reclamos.dominio.repositorio.ReclamoRepository;
import ar.edu.uade.reclamos.persistencia.jpa.ReclamoJpaRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional(readOnly = true)
public class ReclamoRepositoryJpa implements ReclamoRepository {
    private final ReclamoJpaRepository jpa;

    public ReclamoRepositoryJpa(ReclamoJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    @Transactional
    public Reclamo guardar(Reclamo reclamo) {
        return CargadorRelaciones.cargar(jpa.save(reclamo));
    }

    @Override
    public Optional<Reclamo> buscarPorNumero(String numero) {
        return jpa.findByNumero(numero).map(CargadorRelaciones::cargar);
    }

    @Override
    public List<Reclamo> buscarTodos() {
        return cargar(jpa.findAllByOrderByFechaCreacionDescIdDesc());
    }

    @Override
    public List<Reclamo> buscarPorCiudadano(Long ciudadanoId) {
        return cargar(jpa.findByCiudadanoIdOrderByFechaCreacionDescIdDesc(ciudadanoId));
    }

    @Override
    public List<Reclamo> buscarPorArea(Long areaId) {
        return cargar(jpa.findByAreaIdOrderByFechaCreacionDescIdDesc(areaId));
    }

    @Override
    public long contarPendientesPorArea(Long areaId) {
        return jpa.countByAreaIdAndEstadoIn(areaId, List.of(EstadoReclamo.ASIGNADO, EstadoReclamo.EN_PROCESO));
    }

    @Override
    public List<Reclamo> buscarVencidosSinMarcar(LocalDateTime ahora) {
        return cargar(jpa.findByVencidoFalseAndEstadoInAndFechaLimiteBeforeOrderByFechaCreacionDescIdDesc(
                List.of(EstadoReclamo.INGRESADO, EstadoReclamo.ASIGNADO, EstadoReclamo.EN_PROCESO), ahora));
    }

    private List<Reclamo> cargar(List<Reclamo> reclamos) {
        return reclamos.stream().map(CargadorRelaciones::cargar).toList();
    }
}
