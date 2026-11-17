package ar.edu.uade.reclamos.aplicacion.servicio;

import ar.edu.uade.reclamos.aplicacion.configuracion.PropiedadesReclamos;
import ar.edu.uade.reclamos.comun.excepcion.DatosInvalidosException;
import ar.edu.uade.reclamos.comun.excepcion.RecursoNoEncontradoException;
import ar.edu.uade.reclamos.aplicacion.integracion.ComparadorDuplicados;
import ar.edu.uade.reclamos.dominio.modelo.Categoria;
import ar.edu.uade.reclamos.dominio.modelo.Distancia;
import ar.edu.uade.reclamos.dominio.modelo.Reclamo;
import ar.edu.uade.reclamos.dominio.modelo.Ubicacion;
import ar.edu.uade.reclamos.dominio.modelo.Usuario;
import ar.edu.uade.reclamos.dominio.repositorio.ApoyoRepository;
import ar.edu.uade.reclamos.dominio.repositorio.CategoriaRepository;
import ar.edu.uade.reclamos.dominio.repositorio.ReclamoRepository;
import ar.edu.uade.reclamos.dominio.repositorio.UsuarioRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Posibles duplicados para el alta: reclamos abiertos a pocos metros del punto que eligió el ciudadano en el
 * mapa. La base filtra por un recuadro alrededor del punto; la distancia exacta se calcula con Haversine.
 *
 * <p>Desde el PR 27, {@link #paraElAlta} compara además los textos con la IA: busca reclamos de cualquier
 * categoría y le pregunta a REST 2 cuál describe el mismo problema. Sin IA quedan los de la misma categoría.
 *
 * <p>Sin coordenadas no hay cercanía: los reclamos sin la ubicación confirmada nunca aparecen.
 */
@Service
@Transactional(readOnly = true)
public class ServicioDuplicados {

    private final ReclamoRepository reclamos;
    private final CategoriaRepository categorias;
    private final UsuarioRepository usuarios;
    private final ApoyoRepository apoyos;
    private final PropiedadesReclamos.Duplicados configuracion;
    private final ComparadorDuplicados comparador;

    public ServicioDuplicados(ReclamoRepository reclamos, CategoriaRepository categorias, UsuarioRepository usuarios,
                              ApoyoRepository apoyos, PropiedadesReclamos propiedades,
                              ComparadorDuplicados comparador) {
        this.reclamos = reclamos;
        this.categorias = categorias;
        this.usuarios = usuarios;
        this.apoyos = apoyos;
        this.configuracion = propiedades.duplicados();
        this.comparador = comparador;
    }

    /**
     * Para el alta (PR 27): los reclamos abiertos a {@code radio-metros} del punto, de cualquier categoría, hasta
     * {@code maximo-comparados}, comparados por la IA con el texto del reclamo nuevo. Si la IA elige uno, va
     * primero y marcado; después van los de la misma categoría. Si la IA dice que ninguno es el mismo problema, o
     * no responde, quedan solo los de la misma categoría.
     */
    public DuplicadosDelAlta paraElAlta(Long usuarioId, Long categoriaId, String descripcion, double latitud,
                                       double longitud) {
        Usuario usuario = usuarios.buscarPorId(usuarioId)
                .orElseThrow(() -> new RecursoNoEncontradoException("usuario", usuarioId));
        Categoria categoria = categorias.buscarPorId(categoriaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("categoría", categoriaId));
        validarPunto(latitud, longitud);
        List<Cercano> cercanos = cercanos(null, latitud, longitud, configuracion.radioMetros(),
                configuracion.maximoComparados());
        Optional<ComparadorDuplicados.Resultado> resultado = comparador.comparar(categoria.getNombre(), descripcion,
                cercanos.stream().map(Cercano::reclamo).toList());
        Reclamo similar = resultado.map(ComparadorDuplicados.Resultado::similar).orElse(null);
        List<CandidatoDuplicado> candidatos = new ArrayList<>();
        cercanos.stream().filter(cercano -> cercano.reclamo().equals(similar)).findFirst()
                .ifPresent(cercano -> candidatos.add(candidato(cercano, usuario, true)));
        cercanos.stream()
                .filter(cercano -> !cercano.reclamo().equals(similar))
                .filter(cercano -> cercano.reclamo().getCategoria().equals(categoria))
                .limit(configuracion.maximoCandidatos() - candidatos.size())
                .forEach(cercano -> candidatos.add(candidato(cercano, usuario, false)));
        return new DuplicadosDelAlta(resultado.isPresent(),
                resultado.map(ComparadorDuplicados.Resultado::motivo).orElse(null), candidatos);
    }

    /**
     * @param radioMetros {@code null} para usar el radio por defecto
     * @param limite      {@code null} para usar el máximo configurado
     * @return los candidatos del más cercano al más lejano
     */
    public List<CandidatoDuplicado> candidatos(Long usuarioId, Long categoriaId, double latitud, double longitud,
                                               Integer radioMetros, Integer limite) {
        Usuario usuario = usuarios.buscarPorId(usuarioId)
                .orElseThrow(() -> new RecursoNoEncontradoException("usuario", usuarioId));
        categorias.buscarPorId(categoriaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("categoría", categoriaId));
        validarPunto(latitud, longitud);
        int radio = radioMetros == null ? configuracion.radioMetros() : radioMetros;
        if (radio < 1 || radio > configuracion.radioMaximoMetros()) {
            throw new DatosInvalidosException("El radio tiene que estar entre 1 y "
                    + configuracion.radioMaximoMetros() + " metros.");
        }
        int cantidad = limite == null ? configuracion.maximoCandidatos() : limite;
        if (cantidad < 1 || cantidad > configuracion.maximoCandidatos()) {
            throw new DatosInvalidosException("Se pueden pedir entre 1 y " + configuracion.maximoCandidatos()
                    + " candidatos.");
        }
        return cercanos(categoriaId, latitud, longitud, radio, cantidad).stream()
                .map(cercano -> candidato(cercano, usuario, false))
                .toList();
    }

    /** Del más cercano al más lejano. {@code categoriaId} {@code null} para todas. */
    private List<Cercano> cercanos(Long categoriaId, double latitud, double longitud, int radio, int cantidad) {
        double deltaLatitud = Distancia.gradosDeLatitud(radio);
        double deltaLongitud = Distancia.gradosDeLongitud(radio, latitud);
        return reclamos.buscarAbiertosConUbicacionEn(categoriaId, latitud - deltaLatitud, latitud + deltaLatitud,
                        longitud - deltaLongitud, longitud + deltaLongitud).stream()
                .filter(Reclamo::puedeSerDuplicado)
                .map(reclamo -> new Cercano(reclamo, distancia(reclamo.getUbicacion(), latitud, longitud)))
                .filter(cercano -> cercano.metros() <= radio)
                .sorted(Comparator.comparingDouble(Cercano::metros))
                .limit(cantidad)
                .toList();
    }

    private CandidatoDuplicado candidato(Cercano cercano, Usuario usuario, boolean masParecido) {
        Reclamo reclamo = cercano.reclamo();
        boolean propio = reclamo.perteneceA(usuario);
        boolean apoyado = !propio && apoyos.buscar(usuario.getId(), reclamo.getId()).isPresent();
        return new CandidatoDuplicado(reclamo, cercano.metros(), apoyos.contarPorReclamo(reclamo.getId()),
                apoyado, propio, masParecido);
    }

    private static double distancia(Ubicacion ubicacion, double latitud, double longitud) {
        return Distancia.metros(latitud, longitud, ubicacion.getLatitud(), ubicacion.getLongitud());
    }

    private static void validarPunto(double latitud, double longitud) {
        if (!Double.isFinite(latitud) || latitud < -90 || latitud > 90) {
            throw new DatosInvalidosException("La latitud debe estar entre -90 y 90.");
        }
        if (!Double.isFinite(longitud) || longitud < -180 || longitud > 180) {
            throw new DatosInvalidosException("La longitud debe estar entre -180 y 180.");
        }
    }

    private record Cercano(Reclamo reclamo, double metros) { }
}
