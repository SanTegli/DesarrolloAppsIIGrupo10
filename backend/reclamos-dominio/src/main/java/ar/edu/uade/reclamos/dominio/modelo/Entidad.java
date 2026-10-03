package ar.edu.uade.reclamos.dominio.modelo;

/**
 * Base de las entidades con identidad. El id lo asigna la persistencia; hasta entonces es {@code null}.
 * Dos entidades son iguales si son del mismo tipo y tienen el mismo id.
 */
public abstract class Entidad {

    private Long id;

    public Long getId() {
        return id;
    }

    /** Lo usa la capa de persistencia (y los repositorios en memoria de los tests). */
    public void setId(Long id) {
        this.id = id;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Entidad otra)) {
            return false;
        }
        // isAssignableFrom en ambos sentidos tolera los proxies que JPA genera como subclases.
        boolean mismoTipo = getClass().isAssignableFrom(otra.getClass())
                || otra.getClass().isAssignableFrom(getClass());
        return mismoTipo && id != null && id.equals(otra.getId());
    }

    /** Constante a propósito: el id cambia al persistir y el hash no debe cambiar con él. */
    @Override
    public int hashCode() {
        return Entidad.class.hashCode();
    }
}
