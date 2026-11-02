package ar.edu.uade.reclamos.aplicacion.integracion.soap;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.XmlType;
import java.util.ArrayList;
import java.util.List;

/** Respuesta de {@code ConsultarCobertura}: las áreas que cubren la combinación, o ninguna. */
@XmlRootElement(name = "ConsultarCoberturaResponse")
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "")
public class ConsultarCoberturaResponse {

    @XmlElement(name = "area")
    private List<AreaCubierta> areas = new ArrayList<>();

    /** Nunca {@code null}: sin elementos {@code area} es una lista vacía. */
    public List<AreaCubierta> getAreas() {
        if (areas == null) {
            areas = new ArrayList<>();
        }
        return areas;
    }
}
