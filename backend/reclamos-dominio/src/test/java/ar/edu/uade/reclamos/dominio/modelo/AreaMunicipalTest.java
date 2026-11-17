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
    @DisplayName("Un área puede atender si es del municipio del reclamo Y atiende la categoría")
    void puedeAtender() {
        assertTrue(datos.alumbrado.puedeAtender(datos.luminaria, datos.quilmes));
        assertFalse(datos.alumbrado.puedeAtender(datos.bache, datos.quilmes), "no atiende la categoría");
        assertFalse(datos.alumbrado.puedeAtender(datos.luminaria, datos.avellaneda), "es de otro municipio");
        assertTrue(datos.obrasAvellaneda.puedeAtender(datos.bache, datos.avellaneda));
        assertFalse(datos.obrasAvellaneda.puedeAtender(datos.bache, datos.quilmes), "cada municipio, sus áreas");
    }

    @Test
    @DisplayName("Un área inactiva no puede atender nada")
    void areaInactiva() {
        datos.alumbrado.desactivar();
        assertFalse(datos.alumbrado.puedeAtender(datos.luminaria, datos.quilmes));

        datos.alumbrado.activar();
        assertTrue(datos.alumbrado.puedeAtender(datos.luminaria, datos.quilmes));
    }

    @Test
    @DisplayName("Las categorías solo se modifican con los métodos del área")
    void coleccionesProtegidas() {
        assertThrows(UnsupportedOperationException.class, () -> datos.alumbrado.getCategorias().add(datos.bache));
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
