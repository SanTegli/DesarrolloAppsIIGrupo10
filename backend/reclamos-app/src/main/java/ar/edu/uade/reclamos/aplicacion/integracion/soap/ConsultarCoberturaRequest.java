package ar.edu.uade.reclamos.aplicacion.integracion.soap;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.XmlType;

/** Pedido de la operación {@code ConsultarCobertura}: municipio, barrio y categoría del reclamo. */
@XmlRootElement(name = "ConsultarCoberturaRequest")
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "", propOrder = {"municipioId", "barrioId", "categoriaId"})
public class ConsultarCoberturaRequest {

    private long municipioId;
    private long barrioId;
    private long categoriaId;

    /** Requerido por JAXB. */
    protected ConsultarCoberturaRequest() {
    }

    public ConsultarCoberturaRequest(long municipioId, long barrioId, long categoriaId) {
        this.municipioId = municipioId;
        this.barrioId = barrioId;
        this.categoriaId = categoriaId;
    }

    public long getMunicipioId() {
        return municipioId;
    }

    public long getBarrioId() {
        return barrioId;
    }

    public long getCategoriaId() {
        return categoriaId;
    }
}
