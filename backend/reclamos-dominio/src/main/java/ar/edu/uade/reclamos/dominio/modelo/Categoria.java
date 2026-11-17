package ar.edu.uade.reclamos.dominio.modelo;

import ar.edu.uade.reclamos.comun.validacion.Validador;

/**
 * Tipo de problema (bache, luminaria rota, residuos). Define el plazo de atención y la prioridad base.
 *
 * <p>Una categoría puede ser "otra" (PR 26): la elige el vecino cuando ninguna le sirve y escribe qué tipo de
 * problema es. No la atiende ningún área; un administrador recategoriza el reclamo con ayuda de la IA.
 */
public class Categoria extends Entidad {

    private String nombre;
    private String descripcion;
    private int slaHoras;
    private Prioridad prioridadBase;
    private boolean otra;

    /** Requerido por JPA. */
    protected Categoria() {
    }

    public Categoria(String nombre, String descripcion, int slaHoras, Prioridad prioridadBase) {
        this(nombre, descripcion, slaHoras, prioridadBase, false);
    }

    public Categoria(String nombre, String descripcion, int slaHoras, Prioridad prioridadBase, boolean otra) {
        this.otra = otra;
        this.nombre = Validador.requerirTexto(nombre, "nombre", 80);
        this.descripcion = descripcion;
        this.slaHoras = Validador.requerirPositivo(slaHoras, "slaHoras");
        this.prioridadBase = Validador.requerirNoNulo(prioridadBase, "prioridadBase");
    }

    public String getNombre() {
        return nombre;
    }

    public String getDescripcion() {
        return descripcion;
    }

    /** Horas que tiene el municipio para resolver un reclamo de esta categoría. */
    public int getSlaHoras() {
        return slaHoras;
    }

    public Prioridad getPrioridadBase() {
        return prioridadBase;
    }

    /** Es la categoría "Otro problema": el reclamo espera que un administrador le dé una categoría real. */
    public boolean esOtra() {
        return otra;
    }
}
