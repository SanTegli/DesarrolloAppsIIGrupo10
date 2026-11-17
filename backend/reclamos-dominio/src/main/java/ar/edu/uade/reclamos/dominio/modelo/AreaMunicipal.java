package ar.edu.uade.reclamos.dominio.modelo;

import ar.edu.uade.reclamos.comun.validacion.Validador;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Dependencia municipal que resuelve reclamos (Alumbrado, Obras Públicas, Higiene Urbana).
 * Pertenece a un municipio, que es su jurisdicción, y atiende ciertas categorías. Cada municipio tiene sus
 * propias áreas: el Alumbrado de Quilmes no atiende reclamos de Avellaneda.
 */
public class AreaMunicipal extends Entidad {

    private String nombre;
    private String emailContacto;
    private boolean activa = true;
    private Municipio municipio;
    private Set<Categoria> categorias = new LinkedHashSet<>();

    /** Requerido por JPA. */
    protected AreaMunicipal() {
    }

    public AreaMunicipal(String nombre, String emailContacto, Municipio municipio) {
        this.nombre = Validador.requerirTexto(nombre, "nombre", 80);
        this.emailContacto = Validador.requerirEmail(emailContacto);
        this.municipio = Validador.requerirNoNulo(municipio, "municipio");
    }

    public void agregarCategoria(Categoria categoria) {
        categorias.add(Validador.requerirNoNulo(categoria, "categoría"));
    }

    public boolean tieneJurisdiccionSobre(Municipio otro) {
        return municipio.equals(otro);
    }

    public boolean atiende(Categoria categoria) {
        return categorias.contains(categoria);
    }

    /** Regla de asignación automática: área activa, del municipio del reclamo y que atiende la categoría. */
    public boolean puedeAtender(Categoria categoria, Municipio municipioDelReclamo) {
        return activa && atiende(categoria) && tieneJurisdiccionSobre(municipioDelReclamo);
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

    public Set<Categoria> getCategorias() {
        return Collections.unmodifiableSet(categorias);
    }
}
