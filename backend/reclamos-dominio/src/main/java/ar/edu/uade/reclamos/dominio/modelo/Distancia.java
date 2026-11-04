package ar.edu.uade.reclamos.dominio.modelo;

/**
 * Distancias sobre la superficie de la Tierra, con la fórmula de Haversine. Para distancias de pocos cientos
 * de metros el error es de centímetros: alcanza para decidir si dos reclamos están en la misma esquina.
 */
public final class Distancia {

    /** Radio medio de la Tierra, en metros. */
    public static final double RADIO_TIERRA_METROS = 6_371_000;
    private static final double METROS_POR_GRADO = Math.PI * RADIO_TIERRA_METROS / 180;

    private Distancia() {
    }

    /** Metros entre dos puntos dados en grados decimales. */
    public static double metros(double latitud1, double longitud1, double latitud2, double longitud2) {
        double fi1 = Math.toRadians(latitud1);
        double fi2 = Math.toRadians(latitud2);
        double deltaFi = Math.toRadians(latitud2 - latitud1);
        double deltaLambda = Math.toRadians(longitud2 - longitud1);
        double a = Math.sin(deltaFi / 2) * Math.sin(deltaFi / 2)
                + Math.cos(fi1) * Math.cos(fi2) * Math.sin(deltaLambda / 2) * Math.sin(deltaLambda / 2);
        return 2 * RADIO_TIERRA_METROS * Math.asin(Math.min(1, Math.sqrt(a)));
    }

    /** Cuántos grados de latitud son {@code metros} (igual en todo el planeta). */
    public static double gradosDeLatitud(double metros) {
        return metros / METROS_POR_GRADO;
    }

    /**
     * Cuántos grados de longitud son {@code metros} a esa latitud: cuanto más lejos del ecuador, más grados
     * hacen falta para la misma distancia.
     */
    public static double gradosDeLongitud(double metros, double latitud) {
        double coseno = Math.max(Math.cos(Math.toRadians(latitud)), 0.01);
        return metros / (METROS_POR_GRADO * coseno);
    }
}
