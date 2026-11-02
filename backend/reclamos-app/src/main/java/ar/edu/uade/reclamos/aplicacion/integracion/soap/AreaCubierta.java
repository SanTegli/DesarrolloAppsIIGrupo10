package ar.edu.uade.reclamos.aplicacion.integracion.soap;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlType;

/** Tipo {@code Area} del contrato: un área que cubre la combinación consultada. */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "Area", propOrder = {"id", "nombre"})
public class AreaCubierta {

    private long id;
    private String nombre;

    /** Requerido por JAXB. */
    protected AreaCubierta() {
    }

    public AreaCubierta(long id, String nombre) {
        this.id = id;
        this.nombre = nombre;
    }

    public long getId() {
        return id;
    }

    public String getNombre() {
        return nombre;
    }
}
