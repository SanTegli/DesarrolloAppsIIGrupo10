package ar.edu.uade.reclamos.aplicacion.facade;

import ar.edu.uade.reclamos.aplicacion.servicio.*;
import ar.edu.uade.reclamos.dominio.modelo.*;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

/** Punto de entrada de la aplicación, independiente de cualquier transporte. */
@Component
public class GestionReclamosFacade {
    private final ReclamoService escritura;
    private final ConsultaReclamosService consultas;
    private final ConsultaCatalogosService catalogos;

    public GestionReclamosFacade(ReclamoService escritura, ConsultaReclamosService consultas,
                                  ConsultaCatalogosService catalogos) {
        this.escritura = escritura;
        this.consultas = consultas;
        this.catalogos = catalogos;
    }

    public Reclamo crear(Long usuarioId, Long categoriaId, Long barrioId, String descripcion, String direccion) {
        return escritura.crear(usuarioId, categoriaId, barrioId, descripcion, direccion);
    }

    public Reclamo asignar(Long usuarioId, String numero, Long areaId, String observacion) {
        return escritura.asignar(usuarioId, numero, areaId, observacion);
    }

    public Reclamo reasignar(Long usuarioId, String numero, Long areaId, String observacion) {
        return escritura.reasignar(usuarioId, numero, areaId, observacion);
    }

    public Reclamo cambiarEstado(Long usuarioId, String numero, EstadoReclamo estado, String observacion) {
        return escritura.cambiarEstado(usuarioId, numero, estado, observacion);
    }

    public Reclamo tomar(Long usuarioId, String numero, String observacion) {
        return escritura.tomar(usuarioId, numero, observacion);
    }

    public Reclamo resolver(Long usuarioId, String numero, String observacion) {
        return escritura.resolver(usuarioId, numero, observacion);
    }

    public Reclamo cerrar(Long usuarioId, String numero, String observacion) {
        return escritura.cerrar(usuarioId, numero, observacion);
    }

    public Reclamo reabrir(Long usuarioId, String numero, String observacion) {
        return escritura.reabrir(usuarioId, numero, observacion);
    }

    public Reclamo rechazar(Long usuarioId, String numero, String observacion) {
        return escritura.rechazar(usuarioId, numero, observacion);
    }

    public Reclamo cancelar(Long usuarioId, String numero, String observacion) {
        return escritura.cancelar(usuarioId, numero, observacion);
    }

    public List<Reclamo> listar(Long usuarioId, EstadoReclamo estado, Long areaId, Long ciudadanoId) {
        return consultas.listar(usuarioId, estado, areaId, ciudadanoId);
    }

    public Reclamo buscarPorNumero(Long usuarioId, String numero) {
        return consultas.buscarPorNumero(usuarioId, numero);
    }

    public List<Notificacion> consultarNotificaciones(Long usuarioId, String numero) {
        return consultas.consultarNotificaciones(usuarioId, numero);
    }

    public Set<EstadoReclamo> accionesDisponibles(Long usuarioId, String numero) {
        return consultas.accionesDisponibles(usuarioId, numero);
    }

    public List<Usuario> usuarios() {
        return catalogos.usuarios();
    }

    public List<Categoria> categorias() {
        return catalogos.categorias();
    }

    public List<Barrio> barrios() {
        return catalogos.barrios();
    }

    public List<AreaMunicipal> areas() {
        return catalogos.areas();
    }

    public Usuario usuario(Long id) {
        return catalogos.usuario(id);
    }
}
