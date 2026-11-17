package ar.edu.uade.reclamos.aplicacion.integracion.soap;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.XmlType;

/** Pedido de la operación {@code ConsultarCobertura}: municipio y categoría del reclamo. */
@XmlRootElement(name = "ConsultarCoberturaRequest")
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "", propOrder = {"municipioId", "categoriaId"})
public class ConsultarCoberturaRequest {

    private long municipioId;
    private long categoriaId;

    /** Requerido por JAXB. */
    protected ConsultarCoberturaRequest() {
    }

    public ConsultarCoberturaRequest(long municipioId, long categoriaId) {
        this.municipioId = municipioId;
        this.categoriaId = categoriaId;
    }

    public long getMunicipioId() {
        return municipioId;
    }

    public long getCategoriaId() {
        return categoriaId;
    }
}
