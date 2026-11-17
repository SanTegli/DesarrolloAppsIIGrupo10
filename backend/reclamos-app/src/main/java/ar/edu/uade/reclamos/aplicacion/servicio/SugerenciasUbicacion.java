package ar.edu.uade.reclamos.aplicacion.servicio;

import ar.edu.uade.reclamos.dominio.modelo.Municipio;
import java.util.List;

/**
 * Resultado de buscar una dirección en todos los municipios.
 *
 * @param disponible  {@code false} si el geocodificador no respondió para ningún municipio; no es lo mismo que
 *                    no encontrar nada
 * @param sugerencias solo las que tienen coordenadas dentro de su municipio, sin repetir
 */
public record SugerenciasUbicacion(boolean disponible, List<Opcion> sugerencias) {

    public SugerenciasUbicacion {
        sugerencias = List.copyOf(sugerencias);
    }

    public static SugerenciasUbicacion noDisponible() {
        return new SugerenciasUbicacion(false, List.of());
    }

    /**
     * Una interpretación de la dirección, con el municipio donde la encontró el geocodificador. Elegirla define
     * el municipio del reclamo.
     */
    public record Opcion(String nomenclatura, double latitud, double longitud, Municipio municipio) { }
}
