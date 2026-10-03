package ar.edu.uade.reclamos.aplicacion.rest.mapper;

import ar.edu.uade.reclamos.aplicacion.rest.dto.*;
import ar.edu.uade.reclamos.dominio.modelo.*;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class RestMapper {
    public ReclamoResponse reclamo(Reclamo reclamo, Set<EstadoReclamo> acciones) {
        return new ReclamoResponse(reclamo.getNumero(), reclamo.getDescripcion(), reclamo.getDireccion(),
                reclamo.getEstado(), reclamo.getPrioridad(), reclamo.isVencido(), reclamo.getFechaCreacion(),
                reclamo.getFechaLimite(), categoriaReferencia(reclamo.getCategoria()),
                barrioReferencia(reclamo.getBarrio()), usuarioReferencia(reclamo.getCiudadano()),
                areaReferencia(reclamo.getArea()), usuarioReferencia(reclamo.getAgente()),
                reclamo.getHistorial().stream().map(this::historial).toList(), acciones);
    }

    public ResumenReclamoResponse resumen(Reclamo reclamo) {
        return new ResumenReclamoResponse(reclamo.getNumero(), reclamo.getDireccion(), reclamo.getEstado(),
                reclamo.getPrioridad(), reclamo.isVencido(), reclamo.getFechaCreacion(), reclamo.getFechaLimite(),
                categoriaReferencia(reclamo.getCategoria()), barrioReferencia(reclamo.getBarrio()),
                usuarioReferencia(reclamo.getCiudadano()), areaReferencia(reclamo.getArea()));
    }

    public HistorialEstadoResponse historial(HistorialEstado cambio) {
        return new HistorialEstadoResponse(cambio.getEstadoAnterior(), cambio.getEstadoNuevo(),
                cambio.getFecha(), cambio.getObservacion(), usuarioReferencia(cambio.getUsuario()));
    }

    public NotificacionResponse notificacion(Notificacion aviso) {
        return new NotificacionResponse(aviso.getCanal(), usuarioReferencia(aviso.getDestinatario()),
                aviso.getMensaje(), aviso.getFechaEnvio());
    }

    public UsuarioResponse usuario(Usuario usuario) {
        ReferenciaResponse area = usuario instanceof AgenteMunicipal agente ? areaReferencia(agente.getArea()) : null;
        return new UsuarioResponse(usuario.getId(), usuario.getNombreCompleto(), usuario.getRol(), area);
    }

    public CategoriaResponse categoria(Categoria categoria) {
        return new CategoriaResponse(categoria.getId(), categoria.getNombre(), categoria.getDescripcion(),
                categoria.getSlaHoras(), categoria.getPrioridadBase());
    }

    public BarrioResponse barrio(Barrio barrio) {
        return new BarrioResponse(barrio.getId(), barrio.getNombre(), barrio.getMunicipio().getNombre());
    }

    public AreaResponse area(AreaMunicipal area) {
        return new AreaResponse(area.getId(), area.getNombre(), area.isActiva(),
                area.getCategorias().stream().map(this::categoriaReferencia).toList(),
                area.getBarrios().stream().map(this::barrioReferencia).toList());
    }

    private ReferenciaResponse categoriaReferencia(Categoria categoria) {
        return new ReferenciaResponse(categoria.getId(), categoria.getNombre());
    }

    private ReferenciaResponse barrioReferencia(Barrio barrio) {
        return new ReferenciaResponse(barrio.getId(), barrio.getNombre());
    }

    private ReferenciaResponse areaReferencia(AreaMunicipal area) {
        return area == null ? null : new ReferenciaResponse(area.getId(), area.getNombre());
    }

    private UsuarioReferenciaResponse usuarioReferencia(Usuario usuario) {
        return usuario == null ? null : new UsuarioReferenciaResponse(usuario.getId(), usuario.getNombreCompleto());
    }
}
