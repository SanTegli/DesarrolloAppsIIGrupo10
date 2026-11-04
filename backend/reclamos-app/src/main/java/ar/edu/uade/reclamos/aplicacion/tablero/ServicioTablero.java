package ar.edu.uade.reclamos.aplicacion.tablero;

import ar.edu.uade.reclamos.comun.excepcion.AccesoDenegadoException;
import ar.edu.uade.reclamos.comun.excepcion.DatosInvalidosException;
import ar.edu.uade.reclamos.comun.excepcion.RecursoNoEncontradoException;
import ar.edu.uade.reclamos.dominio.modelo.Administrador;
import ar.edu.uade.reclamos.dominio.modelo.AgenteMunicipal;
import ar.edu.uade.reclamos.dominio.modelo.EstadoReclamo;
import ar.edu.uade.reclamos.dominio.modelo.Usuario;
import ar.edu.uade.reclamos.dominio.reporte.ConsultasTablero;
import ar.edu.uade.reclamos.dominio.reporte.ConteoPorEstado;
import ar.edu.uade.reclamos.dominio.reporte.FilaPendientes;
import ar.edu.uade.reclamos.dominio.repositorio.BarrioRepository;
import ar.edu.uade.reclamos.dominio.repositorio.CategoriaRepository;
import ar.edu.uade.reclamos.dominio.repositorio.UsuarioRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Los dos tableros (PR 18). Lee de {@link ConsultasTablero}, el lado de lectura: no carga reclamos. Acá solo se
 * validan los filtros, se decide qué ve cada rol y se calculan los porcentajes.
 */
@Service
@Transactional(readOnly = true)
public class ServicioTablero {

    /** Sin fechas, el período son los últimos 30 días, hoy incluido. */
    static final int DIAS_POR_DEFECTO = 30;
    static final int DIAS_MAXIMOS = 366;

    private final ConsultasTablero consultas;
    private final UsuarioRepository usuarios;
    private final BarrioRepository barrios;
    private final CategoriaRepository categorias;
    private final Clock reloj;

    public ServicioTablero(ConsultasTablero consultas, UsuarioRepository usuarios, BarrioRepository barrios,
                           CategoriaRepository categorias, Clock reloj) {
        this.consultas = consultas;
        this.usuarios = usuarios;
        this.barrios = barrios;
        this.categorias = categorias;
        this.reloj = reloj;
    }

    /** Público: no necesita sesión. Los dos extremos del período se incluyen. */
    public TableroPublico publico(LocalDate desde, LocalDate hasta, Long barrioId, Long categoriaId) {
        LocalDate fin = hasta == null ? LocalDate.now(reloj) : hasta;
        LocalDate inicio = desde == null ? fin.minusDays(DIAS_POR_DEFECTO - 1) : desde;
        if (inicio.isAfter(fin)) {
            throw new DatosInvalidosException("La fecha desde no puede ser posterior a la fecha hasta.");
        }
        if (ChronoUnit.DAYS.between(inicio, fin) + 1 > DIAS_MAXIMOS) {
            throw new DatosInvalidosException("El período puede tener como máximo " + DIAS_MAXIMOS + " días.");
        }
        if (barrioId != null) {
            barrios.buscarPorId(barrioId).orElseThrow(() -> new RecursoNoEncontradoException("barrio", barrioId));
        }
        if (categoriaId != null) {
            categorias.buscarPorId(categoriaId)
                    .orElseThrow(() -> new RecursoNoEncontradoException("categoría", categoriaId));
        }
        long creados = consultas.contarCreados(inicio.atStartOfDay(), fin.plusDays(1).atStartOfDay(), barrioId,
                categoriaId);
        Map<EstadoReclamo, Long> hoy = new EnumMap<>(EstadoReclamo.class);
        for (ConteoPorEstado conteo : consultas.contarPorEstado(barrioId, categoriaId)) {
            hoy.put(conteo.estado(), conteo.cantidad());
        }
        long pendientes = cantidad(hoy, EstadoReclamo.INGRESADO) + cantidad(hoy, EstadoReclamo.ASIGNADO)
                + cantidad(hoy, EstadoReclamo.EN_PROCESO);
        long resueltos = cantidad(hoy, EstadoReclamo.RESUELTO);
        long cerrados = cantidad(hoy, EstadoReclamo.CERRADO);
        return new TableroPublico(inicio, fin, barrioId, categoriaId, creados, pendientes, resueltos, cerrados,
                cantidad(hoy, EstadoReclamo.RECHAZADO), cantidad(hoy, EstadoReclamo.CANCELADO),
                porcentaje(resueltos + cerrados, pendientes + resueltos + cerrados));
    }

    /** Administradores (todas las áreas) y agentes (la suya). Los ciudadanos no. */
    public TableroGestion gestion(Long usuarioId) {
        Usuario usuario = usuarios.buscarPorId(usuarioId)
                .orElseThrow(() -> new RecursoNoEncontradoException("usuario", usuarioId));
        LocalDateTime ahora = LocalDateTime.now(reloj);
        return switch (usuario) {
            case Administrador administrador -> armar(ahora, null, null,
                    consultas.pendientesPorAreaYBarrio(ahora, null), true);
            case AgenteMunicipal agente -> armar(ahora, agente.getArea().getId(), agente.getArea().getNombre(),
                    consultas.pendientesPorAreaYBarrio(ahora, agente.getArea().getId()), false);
            default -> throw new AccesoDenegadoException(
                    "El tablero de gestión es solo para agentes y administradores.");
        };
    }

    private static TableroGestion armar(LocalDateTime ahora, Long areaId, String area, List<FilaPendientes> filas,
                                        boolean conSinAsignar) {
        long pendientes = filas.stream().mapToLong(FilaPendientes::pendientes).sum();
        long vencidos = filas.stream().mapToLong(FilaPendientes::vencidos).sum();
        Long sinAsignar = conSinAsignar
                ? filas.stream().filter(fila -> fila.areaId() == null).mapToLong(FilaPendientes::pendientes).sum()
                : null;
        return new TableroGestion(ahora, areaId, area, pendientes, vencidos, sinAsignar,
                porcentaje(vencidos, pendientes), filas);
    }

    private static long cantidad(Map<EstadoReclamo, Long> conteos, EstadoReclamo estado) {
        return conteos.getOrDefault(estado, 0L);
    }

    /** Con un decimal; {@code null} si el denominador es cero ("sin casos"). */
    static Double porcentaje(long parte, long total) {
        if (total == 0) {
            return null;
        }
        return Math.round(parte * 1000.0 / total) / 10.0;
    }
}
