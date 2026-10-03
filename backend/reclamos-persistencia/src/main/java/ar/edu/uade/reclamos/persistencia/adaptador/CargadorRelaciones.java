package ar.edu.uade.reclamos.persistencia.adaptador;

import ar.edu.uade.reclamos.dominio.modelo.AgenteMunicipal;
import ar.edu.uade.reclamos.dominio.modelo.AreaMunicipal;
import ar.edu.uade.reclamos.dominio.modelo.Barrio;
import ar.edu.uade.reclamos.dominio.modelo.Notificacion;
import ar.edu.uade.reclamos.dominio.modelo.Reclamo;
import ar.edu.uade.reclamos.dominio.modelo.Usuario;
import org.hibernate.Hibernate;

/**
 * Inicializa dentro de la transacción las relaciones que usa el dominio.
 * Evita entregar colecciones sin cargar con open-in-view desactivado.
 * No recorre las referencias inversas del historial para evitar ciclos.
 */
final class CargadorRelaciones {
    private CargadorRelaciones() { }

    static Barrio cargar(Barrio barrio) {
        Hibernate.initialize(barrio.getMunicipio());
        return barrio;
    }

    static AreaMunicipal cargar(AreaMunicipal area) {
        if (area != null) {
            Hibernate.initialize(area.getMunicipio());
            // Los getters devuelven wrappers inmutables: iterar inicializa la colección subyacente.
            area.getBarrios().forEach(CargadorRelaciones::cargar);
            area.getCategorias().forEach(Hibernate::initialize);
        }
        return area;
    }

    static Usuario cargar(Usuario usuario) {
        Hibernate.initialize(usuario);
        Usuario real = (Usuario) Hibernate.unproxy(usuario);
        if (real instanceof AgenteMunicipal agente) {
            cargar(agente.getArea());
        }
        return real;
    }

    static Reclamo cargar(Reclamo reclamo) {
        cargar(reclamo.getCiudadano());
        Hibernate.initialize(reclamo.getCategoria());
        cargar(reclamo.getBarrio());
        cargar(reclamo.getArea());
        if (reclamo.getAgente() != null) {
            cargar(reclamo.getAgente());
        }
        reclamo.getHistorial().forEach(cambio -> {
            if (cambio.getUsuario() != null) {
                cargar(cambio.getUsuario());
            }
        });
        return reclamo;
    }

    static Notificacion cargar(Notificacion notificacion) {
        cargar(notificacion.getDestinatario());
        cargar(notificacion.getReclamo());
        return notificacion;
    }
}
