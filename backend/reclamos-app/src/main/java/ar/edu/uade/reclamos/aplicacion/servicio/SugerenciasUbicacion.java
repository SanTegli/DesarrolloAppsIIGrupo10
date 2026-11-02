package ar.edu.uade.reclamos.aplicacion.servicio;

import ar.edu.uade.reclamos.dominio.integracion.SugerenciaUbicacion;
import java.util.List;

/**
 * Resultado de buscar una dirección.
 *
 * @param disponible  {@code false} si el geocodificador no respondió; no es lo mismo que no encontrar nada
 * @param sugerencias solo las que tienen coordenadas dentro del municipio, sin repetir
 */
public record SugerenciasUbicacion(boolean disponible, List<SugerenciaUbicacion> sugerencias) {

    public SugerenciasUbicacion {
        sugerencias = List.copyOf(sugerencias);
    }

    public static SugerenciasUbicacion noDisponible() {
        return new SugerenciasUbicacion(false, List.of());
    }
}
