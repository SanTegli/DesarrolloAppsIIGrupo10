package ar.edu.uade.reclamos.dominio.modelo;

import ar.edu.uade.reclamos.comun.validacion.Validador;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Dependencia municipal que resuelve reclamos (Alumbrado, Obras Públicas, Higiene Urbana).
 * Atiende ciertas categorías y tiene jurisdicción sobre ciertos barrios.
 */
public class AreaMunicipal extends Entidad {

    private String nombre;
    private String emailContacto;
    private boolean activa = true;
    private Municipio municipio;
    private Set<Barrio> barrios = new LinkedHashSet<>();
    private Set<Categoria> categorias = new LinkedHashSet<>();

    /** Requerido por JPA. */
    protected AreaMunicipal() {
    }

    public AreaMunicipal(String nombre, String emailContacto, Municipio municipio) {
        this.nombre = Validador.requerirTexto(nombre, "nombre", 80);
        this.emailContacto = Validador.requerirEmail(emailContacto);
        this.municipio = Validador.requerirNoNulo(municipio, "municipio");
    }

    public void agregarBarrio(Barrio barrio) {
        barrios.add(Validador.requerirNoNulo(barrio, "barrio"));
    }

    public void agregarCategoria(Categoria categoria) {
        categorias.add(Validador.requerirNoNulo(categoria, "categoría"));
    }

    public boolean tieneJurisdiccionSobre(Barrio barrio) {
        return barrios.contains(barrio);
    }

    public boolean atiende(Categoria categoria) {
        return categorias.contains(categoria);
    }

    /** Regla de asignación automática: área activa, que atiende la categoría y cubre el barrio. */
    public boolean puedeAtender(Categoria categoria, Barrio barrio) {
        return activa && atiende(categoria) && tieneJurisdiccionSobre(barrio);
    }

    public void activar() {
        this.activa = true;
    }

    public void desactivar() {
        this.activa = false;
    }

    public String getNombre() {
        return nombre;
    }

    public String getEmailContacto() {
        return emailContacto;
    }

    public boolean isActiva() {
        return activa;
    }

    public Municipio getMunicipio() {
        return municipio;
    }

    public Set<Barrio> getBarrios() {
        return Collections.unmodifiableSet(barrios);
    }

    public Set<Categoria> getCategorias() {
        return Collections.unmodifiableSet(categorias);
    }
}
