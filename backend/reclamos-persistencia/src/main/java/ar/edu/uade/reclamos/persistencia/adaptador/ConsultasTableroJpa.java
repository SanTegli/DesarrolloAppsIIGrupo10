package ar.edu.uade.reclamos.persistencia.adaptador;

import ar.edu.uade.reclamos.dominio.modelo.EstadoReclamo;
import ar.edu.uade.reclamos.dominio.reporte.ConsultasTablero;
import ar.edu.uade.reclamos.dominio.reporte.ConteoPorEstado;
import ar.edu.uade.reclamos.dominio.reporte.FilaPendientes;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Consultas agregadas en JPQL con proyecciones: la base cuenta y agrupa, y vuelven DTOs planos. Ningún reclamo
 * se carga como entidad, así que el costo no crece con el historial ni con las relaciones.
 */
@Repository
@Transactional(readOnly = true)
public class ConsultasTableroJpa implements ConsultasTablero {

    private static final List<EstadoReclamo> PENDIENTES =
            List.of(EstadoReclamo.INGRESADO, EstadoReclamo.ASIGNADO, EstadoReclamo.EN_PROCESO);
    private static final String PROYECCION = "ar.edu.uade.reclamos.dominio.reporte.";

    private final EntityManager em;

    public ConsultasTableroJpa(EntityManager em) {
        this.em = em;
    }

    @Override
    public long contarCreados(LocalDateTime desde, LocalDateTime hasta, Long barrioId, Long categoriaId) {
        Filtros filtros = new Filtros(barrioId, categoriaId);
        TypedQuery<Long> consulta = em.createQuery("select count(r) from Reclamo r"
                + " where r.fechaCreacion >= :desde and r.fechaCreacion < :hasta" + filtros.condiciones(), Long.class)
                .setParameter("desde", desde)
                .setParameter("hasta", hasta);
        filtros.aplicar(consulta);
        return consulta.getSingleResult();
    }

    @Override
    public List<ConteoPorEstado> contarPorEstado(Long barrioId, Long categoriaId) {
        Filtros filtros = new Filtros(barrioId, categoriaId);
        TypedQuery<ConteoPorEstado> consulta = em.createQuery("select new " + PROYECCION
                + "ConteoPorEstado(r.estado, count(r)) from Reclamo r where 1 = 1" + filtros.condiciones()
                + " group by r.estado", ConteoPorEstado.class);
        filtros.aplicar(consulta);
        return consulta.getResultList().stream()
                .sorted(Comparator.comparing(ConteoPorEstado::estado))
                .toList();
    }

    @Override
    public List<FilaPendientes> pendientesPorAreaYBarrio(LocalDateTime ahora, Long areaId) {
        TypedQuery<FilaPendientes> consulta = em.createQuery("select new " + PROYECCION
                        + "FilaPendientes(a.id, a.nombre, b.id, b.nombre, count(r),"
                        + " sum(case when r.fechaLimite < :ahora then 1 else 0 end))"
                        + " from Reclamo r join r.barrio b left join r.area a"
                        + " where r.estado in :pendientes" + (areaId == null ? "" : " and a.id = :areaId")
                        + " group by a.id, a.nombre, b.id, b.nombre", FilaPendientes.class)
                .setParameter("ahora", ahora)
                .setParameter("pendientes", PENDIENTES);
        if (areaId != null) {
            consulta.setParameter("areaId", areaId);
        }
        // Primero las filas con más vencidos: es lo que hay que atender antes.
        return consulta.getResultList().stream()
                .sorted(Comparator.comparing(FilaPendientes::vencidos, Comparator.reverseOrder())
                        .thenComparing(FilaPendientes::pendientes, Comparator.reverseOrder())
                        .thenComparing(fila -> fila.area() == null ? "" : fila.area())
                        .thenComparing(FilaPendientes::barrio))
                .toList();
    }

    /** Filtros opcionales: solo se agregan a la consulta los que vienen. */
    private static final class Filtros {
        private final Map<String, Long> valores = new TreeMap<>();

        Filtros(Long barrioId, Long categoriaId) {
            if (barrioId != null) {
                valores.put("barrioId", barrioId);
            }
            if (categoriaId != null) {
                valores.put("categoriaId", categoriaId);
            }
        }

        String condiciones() {
            StringBuilder condiciones = new StringBuilder();
            if (valores.containsKey("barrioId")) {
                condiciones.append(" and r.barrio.id = :barrioId");
            }
            if (valores.containsKey("categoriaId")) {
                condiciones.append(" and r.categoria.id = :categoriaId");
            }
            return condiciones.toString();
        }

        void aplicar(TypedQuery<?> consulta) {
            valores.forEach(consulta::setParameter);
        }
    }
}
