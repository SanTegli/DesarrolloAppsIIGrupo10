package ar.edu.uade.reclamos.aplicacion.configuracion;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "reclamos")
public record PropiedadesReclamos(@DefaultValue Prioridad prioridad, @DefaultValue Asignacion asignacion) {
    public record Prioridad(@DefaultValue("categoria") String estrategia) { }
    public record Asignacion(@DefaultValue("jurisdiccion") String estrategia) { }
}
