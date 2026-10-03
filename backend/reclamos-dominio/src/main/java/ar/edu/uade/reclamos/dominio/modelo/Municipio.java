package ar.edu.uade.reclamos.dominio.modelo;

import ar.edu.uade.reclamos.comun.validacion.Validador;

public class Municipio extends Entidad {

    private String nombre;
    private String provincia;

    /** Requerido por JPA. */
    protected Municipio() {
    }

    public Municipio(String nombre, String provincia) {
        this.nombre = Validador.requerirTexto(nombre, "nombre", 80);
        this.provincia = Validador.requerirTexto(provincia, "provincia", 80);
    }

    public String getNombre() {
        return nombre;
    }

    public String getProvincia() {
        return provincia;
    }
}
