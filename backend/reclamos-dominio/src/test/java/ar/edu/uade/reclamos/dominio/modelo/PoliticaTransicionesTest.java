package ar.edu.uade.reclamos.dominio.modelo;

import static ar.edu.uade.reclamos.dominio.modelo.EstadoReclamo.ASIGNADO;
import static ar.edu.uade.reclamos.dominio.modelo.EstadoReclamo.CANCELADO;
import static ar.edu.uade.reclamos.dominio.modelo.EstadoReclamo.CERRADO;
import static ar.edu.uade.reclamos.dominio.modelo.EstadoReclamo.EN_PROCESO;
import static ar.edu.uade.reclamos.dominio.modelo.EstadoReclamo.INGRESADO;
import static ar.edu.uade.reclamos.dominio.modelo.EstadoReclamo.RECHAZADO;
import static ar.edu.uade.reclamos.dominio.modelo.EstadoReclamo.RESUELTO;
import static ar.edu.uade.reclamos.dominio.modelo.Rol.ADMINISTRADOR;
import static ar.edu.uade.reclamos.dominio.modelo.Rol.AGENTE_MUNICIPAL;
import static ar.edu.uade.reclamos.dominio.modelo.Rol.CIUDADANO;
import static ar.edu.uade.reclamos.dominio.modelo.Rol.SISTEMA;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ar.edu.uade.reclamos.comun.excepcion.AccesoDenegadoException;
import ar.edu.uade.reclamos.comun.excepcion.ReglaNegocioException;
import java.util.EnumSet;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PoliticaTransicionesTest {

    @Test
    @DisplayName("Cada transición del enunciado está permitida para sus roles y solo para ellos")
    void tablaCompletaDeTransiciones() {
        assertEquals(EnumSet.of(SISTEMA, ADMINISTRADOR), PoliticaTransiciones.rolesPermitidos(INGRESADO, ASIGNADO));
        assertEquals(EnumSet.of(ADMINISTRADOR), PoliticaTransiciones.rolesPermitidos(INGRESADO, RECHAZADO));
        assertEquals(EnumSet.of(CIUDADANO), PoliticaTransiciones.rolesPermitidos(INGRESADO, CANCELADO));
        assertEquals(EnumSet.of(ADMINISTRADOR), PoliticaTransiciones.rolesPermitidos(ASIGNADO, ASIGNADO));
        assertEquals(EnumSet.of(AGENTE_MUNICIPAL), PoliticaTransiciones.rolesPermitidos(ASIGNADO, EN_PROCESO));
        assertEquals(EnumSet.of(CIUDADANO), PoliticaTransiciones.rolesPermitidos(ASIGNADO, CANCELADO));
        assertEquals(EnumSet.of(AGENTE_MUNICIPAL), PoliticaTransiciones.rolesPermitidos(EN_PROCESO, RESUELTO));
        assertEquals(EnumSet.of(CIUDADANO), PoliticaTransiciones.rolesPermitidos(RESUELTO, CERRADO));
        assertEquals(EnumSet.of(CIUDADANO), PoliticaTransiciones.rolesPermitidos(RESUELTO, EN_PROCESO));
    }

    @Test
    @DisplayName("Existen exactamente nueve transiciones")
    void noHayTransicionesDeMas() {
        int cantidad = 0;
        for (EstadoReclamo origen : EstadoReclamo.values()) {
            for (EstadoReclamo destino : EstadoReclamo.values()) {
                if (PoliticaTransiciones.existe(origen, destino)) {
                    cantidad++;
                }
            }
        }
        assertEquals(9, cantidad);
    }

    @Test
    @DisplayName("Los estados finales no tienen salida")
    void losEstadosFinalesNoTienenSalida() {
        for (EstadoReclamo estadoFinal : Set.of(CERRADO, RECHAZADO, CANCELADO)) {
            assertTrue(estadoFinal.esFinal());
            for (EstadoReclamo destino : EstadoReclamo.values()) {
                assertFalse(PoliticaTransiciones.existe(estadoFinal, destino),
                        estadoFinal + " no debería poder pasar a " + destino);
            }
        }
    }

    @Test
    @DisplayName("Una transición inexistente es una regla de negocio violada (409)")
    void transicionInexistente() {
        assertThrows(ReglaNegocioException.class,
                () -> PoliticaTransiciones.validar(INGRESADO, RESUELTO, AGENTE_MUNICIPAL));
        assertThrows(ReglaNegocioException.class,
                () -> PoliticaTransiciones.validar(CERRADO, EN_PROCESO, CIUDADANO));
        // Aunque el rol sea administrador, la transición no existe: gana el 409 sobre el 403.
        assertThrows(ReglaNegocioException.class,
                () -> PoliticaTransiciones.validar(EN_PROCESO, CANCELADO, ADMINISTRADOR));
    }

    @Test
    @DisplayName("Una transición existente con el rol equivocado es acceso denegado (403)")
    void rolNoPermitido() {
        assertThrows(AccesoDenegadoException.class,
                () -> PoliticaTransiciones.validar(EN_PROCESO, RESUELTO, CIUDADANO));
        assertThrows(AccesoDenegadoException.class,
                () -> PoliticaTransiciones.validar(INGRESADO, RECHAZADO, AGENTE_MUNICIPAL));
        assertThrows(AccesoDenegadoException.class,
                () -> PoliticaTransiciones.validar(ASIGNADO, ASIGNADO, SISTEMA));
    }

    @Test
    @DisplayName("Una transición válida con el rol correcto no lanza error")
    void transicionValida() {
        assertDoesNotThrow(() -> PoliticaTransiciones.validar(ASIGNADO, EN_PROCESO, AGENTE_MUNICIPAL));
        assertTrue(PoliticaTransiciones.permite(RESUELTO, CERRADO, CIUDADANO));
        assertFalse(PoliticaTransiciones.permite(RESUELTO, CERRADO, ADMINISTRADOR));
    }

    @Test
    @DisplayName("Los destinos posibles dependen del estado y del rol")
    void destinosPosibles() {
        assertEquals(EnumSet.of(CERRADO, EN_PROCESO), PoliticaTransiciones.destinosPosibles(RESUELTO, CIUDADANO));
        assertEquals(EnumSet.of(ASIGNADO, RECHAZADO), PoliticaTransiciones.destinosPosibles(INGRESADO, ADMINISTRADOR));
        assertEquals(EnumSet.of(EN_PROCESO), PoliticaTransiciones.destinosPosibles(ASIGNADO, AGENTE_MUNICIPAL));
        assertTrue(PoliticaTransiciones.destinosPosibles(EN_PROCESO, CIUDADANO).isEmpty());
        assertTrue(PoliticaTransiciones.destinosPosibles(CERRADO, ADMINISTRADOR).isEmpty());
    }

    @Test
    @DisplayName("Modificar el conjunto devuelto no altera las reglas")
    void lasReglasNoSePuedenModificarDesdeAfuera() {
        PoliticaTransiciones.rolesPermitidos(RESUELTO, CERRADO).add(ADMINISTRADOR);
        assertFalse(PoliticaTransiciones.permite(RESUELTO, CERRADO, ADMINISTRADOR));
    }
}
