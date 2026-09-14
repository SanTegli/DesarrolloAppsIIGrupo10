package com.uade.logistica.api_envios;

import com.uade.logistica.api_envios.dao.CiudadanoRepositoryEnMemoria;
import com.uade.logistica.api_envios.dao.ReclamoRepositoryEnMemoria;
import com.uade.logistica.api_envios.dominio.Ciudadano;
import com.uade.logistica.api_envios.dominio.Reclamo;
import com.uade.logistica.api_envios.dominio.ReclamoEvento;
import com.uade.logistica.api_envios.servicios.*;
import com.uade.logistica.api_envios.utilidad.ValidadorReclamo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class ReclamoPatronesTest {

    private ReclamoRepositoryEnMemoria reclamoRepository;
    private CiudadanoRepositoryEnMemoria ciudadanoRepository;
    private ReclamoUrgenteFactory urgenteFactory;
    private ReclamoNormalFactory normalFactory;
    private Map<String, AsignacionAreaStrategy> estrategias;
    private List<ReclamoObserver> observadores;
    private List<ReclamoEvento> eventosRecibidos;
    private NotificacionService notificacionService;
    private CiudadanoService ciudadanoService;
    private ReclamoService reclamoService;
    private ReclamoFacade reclamoFacade;

    @BeforeEach
    void setUp() {
        reclamoRepository = new ReclamoRepositoryEnMemoria();
        ciudadanoRepository = new CiudadanoRepositoryEnMemoria();
        urgenteFactory = new ReclamoUrgenteFactory();
        normalFactory = new ReclamoNormalFactory();

        estrategias = Map.of(
                "ALUMBRADO", new AsignacionAlumbradoStrategy(),
                "TRANSITO", new AsignacionTransitoStrategy()
        );

        eventosRecibidos = new ArrayList<>();
        ReclamoObserver testObserver = eventosRecibidos::add;
        observadores = List.of(testObserver, new NotificacionMesaEntradasObserver());

        notificacionService = new NotificacionService(observadores);

        // Simulamos el dispatcher de eventos de Spring directamente para pruebas unitarias limpias
        ApplicationEventPublisher testPublisher = event -> {
            if (event instanceof com.uade.logistica.api_envios.dominio.eventos.ReclamoCreadoEvent creado) {
                notificacionService.manejarReclamoCreado(creado);
            } else if (event instanceof com.uade.logistica.api_envios.dominio.eventos.ReclamoResueltoEvent resuelto) {
                notificacionService.manejarReclamoResuelto(resuelto);
            }
        };

        ciudadanoService = new CiudadanoService(ciudadanoRepository);
        reclamoService = new ReclamoService(reclamoRepository, estrategias, urgenteFactory, normalFactory, testPublisher);
        reclamoFacade = new ReclamoFacade(reclamoService, ciudadanoService, notificacionService);
    }

    @Test
    @DisplayName("Patrón Factory: ReclamoNormalFactory asigna ID REC- y prioridad MEDIA")
    void testPatronFactoryNormal() {
        Reclamo reclamo = normalFactory.crearReclamo("12345678", "Av. Santa Fe 1234", "Bache leve", "VIAL");
        assertNotNull(reclamo.getId());
        assertTrue(reclamo.getId().startsWith("REC-"));
        assertEquals("MEDIA", reclamo.getPrioridad());
        assertEquals("12345678", reclamo.getDniCiudadano());
    }

    @Test
    @DisplayName("Patrón Factory: ReclamoUrgenteFactory asigna ID REC-URG- y prioridad ALTA")
    void testPatronFactoryUrgente() {
        Reclamo reclamo = urgenteFactory.crearReclamo("87654321", "Av. Corrientes 500", "Cable con peligro de caída", "ELECTRICIDAD");
        assertNotNull(reclamo.getId());
        assertTrue(reclamo.getId().startsWith("REC-URG-"));
        assertEquals("ALTA", reclamo.getPrioridad());
    }

    @Test
    @DisplayName("Patrón Strategy: Categoría ALUMBRADO y TRANSITO seleccionan el área correcta")
    void testPatronStrategy() {
        AsignacionAreaStrategy alumbrado = estrategias.get("ALUMBRADO");
        assertEquals("Dirección de Mantenimiento del Espacio Público y Luminarias", alumbrado.determinarArea("Farol roto"));

        AsignacionAreaStrategy transito = estrategias.get("TRANSITO");
        assertEquals("Dirección General de Seguridad Vial y Semaforización", transito.determinarArea("Semáforo intermitente"));
    }

    @Test
    @DisplayName("Patrón Repository: Persistencia, búsqueda y listado en memoria")
    void testPatronRepository() {
        Reclamo reclamo = normalFactory.crearReclamo("30123456", "Calle Falsa 123", "Falta señalética", "TRANSITO");
        reclamoRepository.guardar(reclamo);

        Optional<Reclamo> encontrado = reclamoRepository.buscarPorId(reclamo.getId());
        assertTrue(encontrado.isPresent());
        assertEquals("Calle Falsa 123", encontrado.get().getDireccion());
        assertEquals(1, reclamoRepository.obtenerTodos().size());
    }

    @Test
    @DisplayName("Patrón Observer y Eventos de Dominio: Notificación automática al crear y resolver")
    void testPatronObserverYEventos() {
        Reclamo reclamo = reclamoService.crearReclamo("35111222", "Mitre 450", "Luz quemada en la esquina", "ALUMBRADO");
        assertNotNull(reclamo);

        // Verificamos que el Observer recibió la notificación generada por el Evento de Dominio
        assertEquals(1, eventosRecibidos.size());
        assertEquals("ReclamoCreado", eventosRecibidos.get(0).getTipoEvento());
        assertEquals(reclamo.getId(), eventosRecibidos.get(0).getReclamoId());

        // Resolver reclamo
        reclamoService.resolverReclamo(reclamo.getId());
        assertEquals(2, eventosRecibidos.size());
        assertEquals("ReclamoResuelto", eventosRecibidos.get(1).getTipoEvento());
    }

    @Test
    @DisplayName("Patrón Facade: Orquestación completa de Ciudadano, Reclamo y Notificaciones")
    void testPatronFacadeFlujoCompleto() {
        Reclamo reclamo = reclamoFacade.registrarReclamo(
                "40555666",
                "Av. Belgrano 1000",
                "Semáforo apagado peligro frente a escuela",
                "TRANSITO",
                "Juan Pérez",
                "juan.perez@uade.edu.ar"
        );

        assertNotNull(reclamo);
        // Debe ser urgente por contener "peligro" y "escuela"
        assertEquals("ALTA", reclamo.getPrioridad());
        assertEquals("Dirección General de Seguridad Vial y Semaforización", reclamo.getAreaMunicipal());

        // Verificar que el ciudadano fue automáticamente creado por la fachada
        Optional<Ciudadano> ciudadano = reclamoFacade.consultarCiudadano("40555666");
        assertTrue(ciudadano.isPresent());
        assertEquals("Juan Pérez", ciudadano.get().getNombre());
        assertEquals("juan.perez@uade.edu.ar", ciudadano.get().getEmail());

        // Resolver reclamo a través de la fachada
        Reclamo resuelto = reclamoFacade.resolverReclamo(reclamo.getId());
        assertEquals("RESUELTO", resuelto.getEstado());

        // Verificar historial en la fachada
        assertEquals(2, reclamoFacade.obtenerHistorialNotificaciones().size());
    }

    @Test
    @DisplayName("Utilidad ValidadorReclamo: Rechaza DNIs o emails incorrectos con excepciones claras")
    void testValidaciones() {
        assertThrows(IllegalArgumentException.class, () -> ValidadorReclamo.requerirDniValido("123")); // DNI muy corto
        assertThrows(IllegalArgumentException.class, () -> ValidadorReclamo.requerirEmailValido("email-invalido"));
        assertThrows(IllegalArgumentException.class, () -> ValidadorReclamo.requerirTextoNoVacio("", "descripción"));
    }
}
