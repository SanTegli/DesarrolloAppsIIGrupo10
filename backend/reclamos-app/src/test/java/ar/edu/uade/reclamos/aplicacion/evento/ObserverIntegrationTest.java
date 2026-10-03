package ar.edu.uade.reclamos.aplicacion.evento;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import ar.edu.uade.reclamos.aplicacion.servicio.ReclamoService;
import ar.edu.uade.reclamos.comun.excepcion.AccesoDenegadoException;
import ar.edu.uade.reclamos.dominio.evento.*;
import ar.edu.uade.reclamos.dominio.modelo.*;
import ar.edu.uade.reclamos.dominio.repositorio.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Casos de uso y listeners reales; el spy solo inyecta fallas despues de guardar un aviso real. */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:observer;MODE=MySQL;DB_CLOSE_DELAY=-1")
@ActiveProfiles("test")
@RecordApplicationEvents
class ObserverIntegrationTest {
    private static class FallaListener extends RuntimeException {
        FallaListener() {
            super("Falla del listener despues de persistir");
        }
    }

    private static final AtomicInteger DNI = new AtomicInteger(90000000);
    @Autowired ReclamoService servicio;
    @Autowired ReclamoRepository reclamos;
    @Autowired UsuarioRepository usuarios;
    @Autowired MunicipioRepository municipios;
    @Autowired CategoriaRepository categorias;
    @Autowired BarrioRepository barrios;
    @Autowired AreaMunicipalRepository areas;
    @Autowired PlatformTransactionManager transacciones;
    @Autowired ApplicationEvents eventos;
    @MockitoSpyBean NotificacionRepository notificaciones;

    private record Datos(Long ciudadano, Long otroCiudadano, Long agente, Long otroAgente,
                         Long administrador, Long categoria, Long categoriaSinArea,
                         Long barrio, Long area, Long otraArea) { }
    private Datos datos;

    @BeforeEach
    void prepararEscenario() {
        datos = new TransactionTemplate(transacciones).execute(tx -> {
            Municipio municipio = municipios.guardar(new Municipio("Municipio Observer", "Buenos Aires"));
            Barrio barrio = barrios.guardar(new Barrio("Barrio Observer", municipio));
            Categoria categoria = categorias.guardar(new Categoria("Bache", "Prueba", 24, Prioridad.ALTA));
            Categoria sinArea = categorias.guardar(new Categoria("Sin cobertura", "Prueba", 24, Prioridad.BAJA));
            AreaMunicipal area = new AreaMunicipal("Obras", "obras@test.org", municipio);
            AreaMunicipal otraArea = new AreaMunicipal("Vial", "vial@test.org", municipio);
            for (AreaMunicipal candidata : List.of(area, otraArea)) {
                candidata.agregarCategoria(categoria);
                candidata.agregarBarrio(barrio);
                areas.guardar(candidata);
            }
            Usuario ciudadano = usuarios.guardar(new Ciudadano(dni(), "Ana", "Observer", "ana@test.org", null));
            Usuario otroCiudadano = usuarios.guardar(new Ciudadano(dni(), "Luis", "Observer", "luis@test.org", null));
            Usuario agente = usuarios.guardar(new AgenteMunicipal(dni(), "Carla", "Observer", "carla@test.org", null, area));
            Usuario otroAgente = usuarios.guardar(new AgenteMunicipal(dni(), "Diego", "Observer", "diego@test.org", null, otraArea));
            Usuario administrador = usuarios.guardar(new Administrador(dni(), "Elena", "Observer", "elena@test.org", null));
            return new Datos(ciudadano.getId(), otroCiudadano.getId(), agente.getId(), otroAgente.getId(),
                    administrador.getId(), categoria.getId(), sinArea.getId(), barrio.getId(),
                    area.getId(), otraArea.getId());
        });
        eventos.clear();
    }

    @AfterEach
    void quitarFallaSimulada() {
        reset(notificaciones);
    }

    @Test
    void creacionAutomaticaTomaResolucionReaperturaYCierreNotificanSinDuplicados() {
        Reclamo creado = crear(true);
        String numero = creado.getNumero();
        assertThat(eventosDel(numero)).extracting(EventoDominio::nombre)
                .containsExactly("ReclamoCreado", "ReclamoAsignado");
        assertThat(avisos(numero)).extracting(n -> n.getDestinatario().getId())
                .containsExactly(datos.ciudadano(), datos.ciudadano(), datos.agente());
        assertThat(avisos(numero)).extracting(Notificacion::getMensaje)
                .containsExactly("Tu reclamo " + numero + " fue ingresado.",
                        "Tu reclamo " + numero + " fue asignado al area Obras.",
                        "El reclamo " + numero + " fue asignado a tu area Obras.");
        servicio.tomar(datos.agente(), numero, "Tomar");
        assertThat(avisos(numero)).hasSize(4);
        servicio.resolver(datos.agente(), numero, "Reparado");
        assertThat(eventosDel(numero)).extracting(EventoDominio::nombre)
                .containsExactly("ReclamoCreado", "ReclamoAsignado", "EstadoReclamoCambiado",
                        "EstadoReclamoCambiado", "ReclamoResuelto");
        assertThat(avisos(numero)).hasSize(5);
        assertThat(avisos(numero).get(4).getMensaje()).contains("fue resuelto");
        assertThat(avisos(numero).get(4).getDestinatario().getId()).isEqualTo(datos.ciudadano());
        servicio.reabrir(datos.ciudadano(), numero, "Persiste");
        assertThat(avisos(numero)).extracting(n -> n.getDestinatario().getId())
                .containsExactly(datos.ciudadano(), datos.ciudadano(), datos.agente(), datos.ciudadano(),
                        datos.ciudadano(), datos.ciudadano(), datos.agente());
        servicio.resolver(datos.agente(), numero, "Reparado nuevamente");
        servicio.cerrar(datos.ciudadano(), numero, "Confirmado");
        List<Notificacion> avisos = avisos(numero);
        assertThat(avisos).extracting(n -> n.getDestinatario().getId())
                .containsExactly(datos.ciudadano(), datos.ciudadano(), datos.agente(), datos.ciudadano(),
                        datos.ciudadano(), datos.ciudadano(), datos.agente(), datos.ciudadano(),
                        datos.ciudadano(), datos.agente());
        assertThat(avisos).extracting(Notificacion::getId).doesNotHaveDuplicates();
        assertThat(avisos).allSatisfy(aviso -> {
            assertThat(aviso.getCanal()).isEqualTo(CanalNotificacion.INTERNO);
            assertThat(aviso.getReclamo().getNumero()).isEqualTo(numero);
            assertThat(aviso.getFechaEnvio()).isNotNull();
        });
        assertThat(notificaciones.buscarPorDestinatario(datos.otroCiudadano())).isEmpty();
        assertThat(notificaciones.buscarPorDestinatario(datos.otroAgente())).isEmpty();
    }

    @Test
    void sinAsignacionAutomaticaAsignacionManualReasignacionCancelacionYRechazoAvisanAlCiudadanoYAlArea() {
        String numero = crear(false).getNumero();
        assertThat(eventosDel(numero)).extracting(EventoDominio::nombre).containsExactly("ReclamoCreado");
        assertThat(avisos(numero)).hasSize(1);
        servicio.asignar(datos.administrador(), numero, datos.area(), "Manual");
        servicio.reasignar(datos.administrador(), numero, datos.otraArea(), "Reasignar");
        servicio.cancelar(datos.ciudadano(), numero, "Cancelar");
        assertThat(eventosDel(numero)).extracting(EventoDominio::nombre)
                .containsExactly("ReclamoCreado", "ReclamoAsignado", "ReclamoAsignado", "EstadoReclamoCambiado");
        assertThat(avisos(numero)).extracting(n -> n.getDestinatario().getId())
                .containsExactly(datos.ciudadano(), datos.ciudadano(), datos.agente(),
                        datos.ciudadano(), datos.otroAgente(), datos.ciudadano());
        assertThat(avisos(numero).get(3).getMensaje()).contains("Vial");
        assertThat(avisos(numero).get(5).getMensaje()).contains("CANCELADO");
        String rechazado = crear(false).getNumero();
        servicio.rechazar(datos.administrador(), rechazado, "No corresponde");
        assertThat(avisos(rechazado)).hasSize(2);
        assertThat(avisos(rechazado).get(1).getMensaje()).contains("RECHAZADO");
    }

    @Test
    void fallaDelListenerRevierteCreacionHistorialYTodosLosAvisosInclusoLosYaGuardados() {
        AtomicInteger guardados = new AtomicInteger();
        AtomicReference<String> numero = new AtomicReference<>();
        doAnswer(invocacion -> {
            Notificacion aviso = invocacion.getArgument(0);
            numero.set(aviso.getReclamo().getNumero());
            invocacion.callRealMethod();
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            if (guardados.incrementAndGet() == 2) {
                throw new FallaListener();
            }
            return aviso;
        }).when(notificaciones).guardar(any());
        assertThatThrownBy(() -> crear(true)).isInstanceOf(FallaListener.class);
        assertThat(guardados).hasValue(2);
        assertThat(reclamos.buscarPorNumero(numero.get())).isEmpty();
        assertThat(avisos(numero.get())).isEmpty();
        assertThat(notificaciones.buscarPorDestinatario(datos.ciudadano())).isEmpty();
    }

    @Test
    void fallaDelListenerRevierteTomaAgenteHistorialYAviso() {
        String numero = crear(true).getNumero();
        List<Long> avisosAntes = avisos(numero).stream().map(Notificacion::getId).toList();
        doAnswer(invocacion -> {
            invocacion.callRealMethod();
            throw new FallaListener();
        }).when(notificaciones).guardar(any());
        assertThatThrownBy(() -> servicio.tomar(datos.agente(), numero, "Tomar"))
                .isInstanceOf(FallaListener.class);
        Reclamo revertido = reclamos.buscarPorNumero(numero).orElseThrow();
        assertThat(revertido.getEstado()).isEqualTo(EstadoReclamo.ASIGNADO);
        assertThat(revertido.getAgente()).isNull();
        assertThat(revertido.getHistorial()).hasSize(2);
        assertThat(avisos(numero)).extracting(Notificacion::getId).containsExactlyElementsOf(avisosAntes);
    }

    @Test
    void operacionDenegadaNoPublicaEventosNiAgregaAvisos() {
        String numero = crear(true).getNumero();
        List<Long> avisosAntes = avisos(numero).stream().map(Notificacion::getId).toList();
        eventos.clear();
        assertThatThrownBy(() -> servicio.tomar(datos.otroAgente(), numero, "Ajeno"))
                .isInstanceOf(AccesoDenegadoException.class);
        assertThat(eventosDel(numero)).isEmpty();
        assertThat(avisos(numero)).extracting(Notificacion::getId).containsExactlyElementsOf(avisosAntes);
        Reclamo intacto = reclamos.buscarPorNumero(numero).orElseThrow();
        assertThat(intacto.getEstado()).isEqualTo(EstadoReclamo.ASIGNADO);
        assertThat(intacto.getHistorial()).hasSize(2);
    }

    private Reclamo crear(boolean asignacionAutomatica) {
        return servicio.crear(datos.ciudadano(), asignacionAutomatica ? datos.categoria() : datos.categoriaSinArea(),
                datos.barrio(), "Pozo", "Calle 450");
    }

    private List<Notificacion> avisos(String numero) {
        return notificaciones.buscarPorReclamo(numero);
    }

    private List<EventoDominio> eventosDel(String numero) {
        return eventos.stream(EventoDominio.class).filter(evento -> evento.numeroReclamo().equals(numero)).toList();
    }

    private static String dni() {
        return Integer.toString(DNI.incrementAndGet());
    }
}
