package ar.edu.uade.reclamos.dominio.modelo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Haversine con puntos conocidos de Quilmes y de referencia. */
class DistanciaTest {

    private static final double LATITUD = -34.73614578;
    private static final double LONGITUD = -58.3228998575;

    @Test
    @DisplayName("El mismo punto está a cero metros")
    void mismoPunto() {
        assertEquals(0, Distancia.metros(LATITUD, LONGITUD, LATITUD, LONGITUD), 1e-9);
    }

    @Test
    @DisplayName("Un grado de latitud son unos 111 km, en cualquier longitud")
    void unGradoDeLatitud() {
        assertEquals(111_195, Distancia.metros(-34, -58, -35, -58), 1);
        assertEquals(111_195, Distancia.metros(10, 20, 11, 20), 1);
    }

    @Test
    @DisplayName("De Bernal al Obelisco hay unos 15,7 km")
    void bernalAlObelisco() {
        double metros = Distancia.metros(LATITUD, LONGITUD, -34.6037, -58.3816);
        assertTrue(metros > 15_500 && metros < 16_000, "dio " + metros);
    }

    @Test
    @DisplayName("Los grados del recuadro equivalen a los metros pedidos")
    void recuadro() {
        double norte = LATITUD + Distancia.gradosDeLatitud(50);
        double este = LONGITUD + Distancia.gradosDeLongitud(50, LATITUD);

        assertEquals(50, Distancia.metros(LATITUD, LONGITUD, norte, LONGITUD), 0.01);
        assertEquals(50, Distancia.metros(LATITUD, LONGITUD, LATITUD, este), 0.05);
    }

    @Test
    @DisplayName("La distancia no depende del orden de los puntos")
    void simetrica() {
        assertEquals(Distancia.metros(LATITUD, LONGITUD, -34.7, -58.3),
                Distancia.metros(-34.7, -58.3, LATITUD, LONGITUD), 1e-9);
    }
}
