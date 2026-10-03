package ar.edu.uade.reclamos.dominio.modelo;

import ar.edu.uade.reclamos.comun.validacion.Validador;

/** Zona de un municipio. Cada reclamo ocurre en un barrio. */
public class Barrio extends Entidad {

    private String nombre;
    private Municipio municipio;

    /** Requerido por JPA. */
    protected Barrio() {
    }

    public Barrio(String nombre, Municipio municipio) {
        this.nombre = Validador.requerirTexto(nombre, "nombre", 80);
        this.municipio = Validador.requerirNoNulo(municipio, "municipio");
    }

    public String getNombre() {
        return nombre;
    }

    public Municipio getMunicipio() {
        return municipio;
    }
}
