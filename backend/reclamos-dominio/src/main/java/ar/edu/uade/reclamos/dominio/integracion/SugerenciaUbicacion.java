package ar.edu.uade.reclamos.dominio.integracion;

/**
 * Una interpretación posible de la dirección escrita por el ciudadano.
 *
 * @param nomenclatura la dirección normalizada, por ejemplo {@code GRL BELGRANO 450, Bernal Oeste, Quilmes}
 * @param latitud      {@code null} si el geocodificador reconoce la calle pero no tiene coordenadas
 * @param longitud     {@code null} en el mismo caso
 */
public record SugerenciaUbicacion(String nomenclatura, Double latitud, Double longitud) {

    public boolean tieneCoordenadas() {
        return latitud != null && longitud != null;
    }
}
