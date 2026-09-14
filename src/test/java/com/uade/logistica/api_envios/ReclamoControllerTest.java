package com.uade.logistica.api_envios;

import com.uade.logistica.api_envios.controladores.ReclamoController;
import com.uade.logistica.api_envios.controladores.ReclamoController.SolicitudReclamoDto;
import com.uade.logistica.api_envios.dominio.Ciudadano;
import com.uade.logistica.api_envios.dominio.Reclamo;
import com.uade.logistica.api_envios.servicios.ReclamoFacade;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

class ReclamoControllerTest {

    private ReclamoFacade reclamoFacade;
    private ReclamoController controller;

    @BeforeEach
    void setUp() {
        reclamoFacade = Mockito.mock(ReclamoFacade.class);
        controller = new ReclamoController(reclamoFacade);
    }

    @Test
    @DisplayName("POST /api/v1/reclamos: Crea reclamo y responde 201 Created")
    void testCrearReclamo() {
        Reclamo reclamoSimulado = new Reclamo("REC-12345678", "30111222", "Calle 10", "Bache grande", "TRANSITO");
        reclamoSimulado.setAreaMunicipal("Dirección General de Seguridad Vial y Semaforización");
        reclamoSimulado.setPrioridad("MEDIA");

        when(reclamoFacade.registrarReclamo(anyString(), anyString(), anyString(), anyString(), any(), any()))
                .thenReturn(reclamoSimulado);

        SolicitudReclamoDto dto = new SolicitudReclamoDto(
                "30111222",
                "Calle 10",
                "Bache grande",
                "TRANSITO",
                "Carlos Lopez",
                "carlos@uade.edu.ar"
        );

        ResponseEntity<Reclamo> respuesta = controller.crearReclamo(dto);

        assertNotNull(respuesta);
        assertEquals(HttpStatus.CREATED, respuesta.getStatusCode());
        assertNotNull(respuesta.getBody());
        assertEquals("REC-12345678", respuesta.getBody().getId());
    }

    @Test
    @DisplayName("GET /api/v1/reclamos/{id}: Retorna 200 OK si existe o 404 si no existe")
    void testObtenerPorId() {
        Reclamo reclamo = new Reclamo("REC-123", "30111222", "Calle 10", "Bache", "TRANSITO");
        when(reclamoFacade.consultarReclamo("REC-123")).thenReturn(Optional.of(reclamo));
        when(reclamoFacade.consultarReclamo("REC-INEXISTENTE")).thenReturn(Optional.empty());

        ResponseEntity<Reclamo> okRespuesta = controller.obtenerPorId("REC-123");
        assertEquals(HttpStatus.OK, okRespuesta.getStatusCode());
        assertEquals("REC-123", okRespuesta.getBody().getId());

        ResponseEntity<Reclamo> noExisteRespuesta = controller.obtenerPorId("REC-INEXISTENTE");
        assertEquals(HttpStatus.NOT_FOUND, noExisteRespuesta.getStatusCode());
    }

    @Test
    @DisplayName("GET /api/v1/reclamos/ciudadanos: Retorna lista de ciudadanos")
    void testListarCiudadanos() {
        Ciudadano c1 = new Ciudadano("30111222", "Carlos", "carlos@uade.edu.ar");
        when(reclamoFacade.listarCiudadanos()).thenReturn(List.of(c1));

        ResponseEntity<List<Ciudadano>> respuesta = controller.listarCiudadanos();
        assertEquals(HttpStatus.OK, respuesta.getStatusCode());
        assertEquals(1, respuesta.getBody().size());
        assertEquals("30111222", respuesta.getBody().get(0).getDni());
    }

    @Test
    @DisplayName("ExceptionHandler: Manejo de errores de validación")
    void testExceptionHandler() {
        ResponseEntity<?> resBad = controller.manejarValidacion(new IllegalArgumentException("Dato erróneo"));
        assertEquals(HttpStatus.BAD_REQUEST, resBad.getStatusCode());

        ResponseEntity<?> resNotFound = controller.manejarNoEncontrado(new java.util.NoSuchElementException("No hallado"));
        assertEquals(HttpStatus.NOT_FOUND, resNotFound.getStatusCode());
    }
}
