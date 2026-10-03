package ar.edu.uade.reclamos.aplicacion.seguridad;

import ar.edu.uade.reclamos.comun.excepcion.AccesoDenegadoException;
import ar.edu.uade.reclamos.dominio.modelo.*;
import java.util.Set;
import org.springframework.stereotype.Component;

/** Permisos de aplicación; las transiciones y sus roles permanecen en el dominio. */
@Component
public class PermisosReclamo {
    public Ciudadano validarCreacion(Usuario usuario) {
        if (usuario instanceof Ciudadano ciudadano) {
            return ciudadano;
        }
        throw new AccesoDenegadoException("Solo un ciudadano puede crear reclamos.");
    }

    public boolean puedeConsultar(Usuario usuario, Reclamo reclamo) {
        return switch (usuario) {
            case Ciudadano ciudadano -> reclamo.perteneceA(ciudadano);
            case AgenteMunicipal agente -> agente.perteneceA(reclamo.getArea());
            case Administrador administrador -> true;
            default -> false;
        };
    }

    public void validarConsulta(Usuario usuario, Reclamo reclamo) {
        if (!puedeConsultar(usuario, reclamo)) {
            throw new AccesoDenegadoException("El usuario no puede consultar este reclamo.");
        }
    }

    public void validarFiltros(Usuario usuario, Long areaId, Long ciudadanoId) {
        if ((areaId != null || ciudadanoId != null) && !(usuario instanceof Administrador)) {
            throw new AccesoDenegadoException("Solo un administrador puede filtrar por área o ciudadano.");
        }
    }

    public Set<EstadoReclamo> accionesDisponibles(Usuario usuario, Reclamo reclamo) {
        return puedeConsultar(usuario, reclamo)
                ? PoliticaTransiciones.destinosPosibles(reclamo.getEstado(), usuario.getRol())
                : Set.of();
    }
}
