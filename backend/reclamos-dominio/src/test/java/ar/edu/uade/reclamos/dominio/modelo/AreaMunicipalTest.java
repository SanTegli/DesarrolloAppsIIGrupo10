package ar.edu.uade.reclamos.dominio.modelo;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ar.edu.uade.reclamos.dominio.DatosDePrueba;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AreaMunicipalTest {

    private DatosDePrueba datos;

    @BeforeEach
    void preparar() {
        datos = new DatosDePrueba();
    }

    @Test
    @DisplayName("Un área puede atender si cubre el barrio Y atiende la categoría")
    void puedeAtender() {
        assertTrue(datos.alumbrado.puedeAtender(datos.luminaria, datos.bernal));
        assertTrue(datos.alumbrado.puedeAtender(datos.luminaria, datos.quilmesCentro));
        assertFalse(datos.alumbrado.puedeAtender(datos.bache, datos.bernal), "no atiende la categoría");
        assertFalse(datos.alumbrado.puedeAtender(datos.luminaria, datos.ezpeleta), "no cubre el barrio");
        assertFalse(datos.obrasPublicas.puedeAtender(datos.bache, datos.quilmesCentro));
    }

    @Test
    @DisplayName("Un área inactiva no puede atender nada")
    void areaInactiva() {
        datos.alumbrado.desactivar();
        assertFalse(datos.alumbrado.puedeAtender(datos.luminaria, datos.bernal));

        datos.alumbrado.activar();
        assertTrue(datos.alumbrado.puedeAtender(datos.luminaria, datos.bernal));
    }

    @Test
    @DisplayName("Barrios y categorías solo se modifican con los métodos del área")
    void coleccionesProtegidas() {
        assertThrows(UnsupportedOperationException.class, () -> datos.alumbrado.getBarrios().add(datos.ezpeleta));
        assertThrows(UnsupportedOperationException.class, () -> datos.alumbrado.getCategorias().clear());
    }

    @Test
    @DisplayName("El agente pertenece a su área y a ninguna otra")
    void pertenenciaDelAgente() {
        assertTrue(datos.agenteAlumbrado.perteneceA(datos.alumbrado));
        assertFalse(datos.agenteAlumbrado.perteneceA(datos.obrasPublicas));
        assertFalse(datos.agenteAlumbrado.perteneceA(null));
    }
}
