package ar.edu.uade.reclamos.aplicacion;

import ar.edu.uade.reclamos.aplicacion.evento.NotificacionesReclamoListener;
import ar.edu.uade.reclamos.dominio.evento.EstadoReclamoCambiado;
import ar.edu.uade.reclamos.dominio.evento.EventoDominio;
import ar.edu.uade.reclamos.dominio.evento.PublicadorEventos;
import ar.edu.uade.reclamos.dominio.evento.ReclamoAsignado;
import ar.edu.uade.reclamos.dominio.evento.ReclamoCreado;
import ar.edu.uade.reclamos.dominio.evento.ReclamoResuelto;
import ar.edu.uade.reclamos.dominio.evento.ReclamoVencido;
import ar.edu.uade.reclamos.dominio.modelo.AgenteMunicipal;
import ar.edu.uade.reclamos.dominio.modelo.AreaMunicipal;
import ar.edu.uade.reclamos.dominio.modelo.Barrio;
import ar.edu.uade.reclamos.dominio.modelo.Categoria;
import ar.edu.uade.reclamos.dominio.modelo.Entidad;
import ar.edu.uade.reclamos.dominio.modelo.Notificacion;
import ar.edu.uade.reclamos.dominio.modelo.Reclamo;
import ar.edu.uade.reclamos.dominio.modelo.Usuario;
import ar.edu.uade.reclamos.dominio.repositorio.AreaMunicipalRepository;
import ar.edu.uade.reclamos.dominio.repositorio.BarrioRepository;
import ar.edu.uade.reclamos.dominio.repositorio.CategoriaRepository;
import ar.edu.uade.reclamos.dominio.repositorio.NotificacionRepository;
import ar.edu.uade.reclamos.dominio.repositorio.ReclamoRepository;
import ar.edu.uade.reclamos.dominio.repositorio.UsuarioRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Implementaciones en memoria de los contratos del dominio, para probar los servicios sin Spring
 * ni base de datos. Es la ventaja del patrón Repository: el servicio no nota la diferencia.
 */
public final class DoblesEnMemoria {
    private DoblesEnMemoria() {
    }

    private static <T extends Entidad> T conId(T entidad, List<T> guardados) {
        if (entidad.getId() == null) {
            entidad.setId(guardados.size() + 1L);
        }
        if (!guardados.contains(entidad)) {
            guardados.add(entidad);
        }
        return entidad;
    }

    private static <T extends Entidad> Optional<T> porId(List<T> guardados, Long id) {
        return guardados.stream().filter(entidad -> entidad.getId().equals(id)).findFirst();
    }

    public static class Reclamos implements ReclamoRepository {
        public final List<Reclamo> guardados = new ArrayList<>();

        @Override
        public Reclamo guardar(Reclamo reclamo) {
            return conId(reclamo, guardados);
        }

        @Override
        public Optional<Reclamo> buscarPorNumero(String numero) {
            return guardados.stream().filter(reclamo -> reclamo.getNumero().equals(numero)).findFirst();
        }

        @Override
        public List<Reclamo> buscarTodos() {
            return List.copyOf(guardados);
        }

        @Override
        public List<Reclamo> buscarPorCiudadano(Long ciudadanoId) {
            return guardados.stream().filter(r -> r.getCiudadano().getId().equals(ciudadanoId)).toList();
        }

        @Override
        public List<Reclamo> buscarPorArea(Long areaId) {
            return guardados.stream().filter(r -> r.getArea() != null && r.getArea().getId().equals(areaId)).toList();
        }

        @Override
        public long contarPendientesPorArea(Long areaId) {
            return buscarPorArea(areaId).stream().filter(r -> r.getEstado().estaPendienteDeResolucion()).count();
        }

        @Override
        public List<Reclamo> buscarVencidosSinMarcar(LocalDateTime ahora) {
            return guardados.stream()
                    .filter(r -> !r.isVencido() && r.getEstado().estaPendienteDeResolucion()
                            && r.getFechaLimite().isBefore(ahora))
                    .toList();
        }
    }

    public static class Notificaciones implements NotificacionRepository {
        public final List<Notificacion> guardadas = new ArrayList<>();

        @Override
        public Notificacion guardar(Notificacion notificacion) {
            return conId(notificacion, guardadas);
        }

        @Override
        public List<Notificacion> buscarPorReclamo(String numeroReclamo) {
            return guardadas.stream().filter(n -> n.getReclamo().getNumero().equals(numeroReclamo)).toList();
        }

        @Override
        public List<Notificacion> buscarPorDestinatario(Long usuarioId) {
            return guardadas.stream().filter(n -> n.getDestinatario().getId().equals(usuarioId)).toList();
        }

        /** Ids de los destinatarios, en el orden en que se guardaron los avisos. */
        public List<Long> destinatarios() {
            return guardadas.stream().map(n -> n.getDestinatario().getId()).toList();
        }

        public List<String> mensajes() {
            return guardadas.stream().map(Notificacion::getMensaje).toList();
        }
    }

    public static class Usuarios implements UsuarioRepository {
        public final List<Usuario> guardados = new ArrayList<>();

        public Usuarios(Usuario... iniciales) {
            guardados.addAll(List.of(iniciales));
        }

        @Override
        public Usuario guardar(Usuario usuario) {
            return conId(usuario, guardados);
        }

        @Override
        public Optional<Usuario> buscarPorId(Long id) {
            return porId(guardados, id);
        }

        @Override
        public Optional<Usuario> buscarPorDni(String dni) {
            return guardados.stream().filter(usuario -> usuario.getDni().equals(dni)).findFirst();
        }

        @Override
        public List<Usuario> buscarTodos() {
            return List.copyOf(guardados);
        }

        @Override
        public List<AgenteMunicipal> buscarAgentesPorArea(Long areaId) {
            return guardados.stream()
                    .filter(usuario -> usuario instanceof AgenteMunicipal && usuario.isActivo())
                    .map(usuario -> (AgenteMunicipal) usuario)
                    .filter(agente -> agente.getArea().getId().equals(areaId))
                    .sorted(Comparator.comparing(AgenteMunicipal::getId))
                    .toList();
        }
    }

    public static class Areas implements AreaMunicipalRepository {
        public final List<AreaMunicipal> guardadas = new ArrayList<>();

        public Areas(AreaMunicipal... iniciales) {
            guardadas.addAll(List.of(iniciales));
        }

        @Override
        public AreaMunicipal guardar(AreaMunicipal area) {
            return conId(area, guardadas);
        }

        @Override
        public Optional<AreaMunicipal> buscarPorId(Long id) {
            return porId(guardadas, id);
        }

        @Override
        public List<AreaMunicipal> buscarTodas() {
            return List.copyOf(guardadas);
        }

        @Override
        public List<AreaMunicipal> buscarCandidatas(Long categoriaId, Long barrioId) {
            return guardadas.stream()
                    .filter(AreaMunicipal::isActiva)
                    .filter(area -> area.getCategorias().stream().anyMatch(c -> c.getId().equals(categoriaId)))
                    .filter(area -> area.getBarrios().stream().anyMatch(b -> b.getId().equals(barrioId)))
                    .sorted(Comparator.comparing(AreaMunicipal::getId))
                    .toList();
        }
    }

    public static class Categorias implements CategoriaRepository {
        public final List<Categoria> guardadas = new ArrayList<>();

        public Categorias(Categoria... iniciales) {
            guardadas.addAll(List.of(iniciales));
        }

        @Override
        public Categoria guardar(Categoria categoria) {
            return conId(categoria, guardadas);
        }

        @Override
        public Optional<Categoria> buscarPorId(Long id) {
            return porId(guardadas, id);
        }

        @Override
        public List<Categoria> buscarTodas() {
            return List.copyOf(guardadas);
        }
    }

    public static class Barrios implements BarrioRepository {
        public final List<Barrio> guardados = new ArrayList<>();

        public Barrios(Barrio... iniciales) {
            guardados.addAll(List.of(iniciales));
        }

        @Override
        public Barrio guardar(Barrio barrio) {
            return conId(barrio, guardados);
        }

        @Override
        public Optional<Barrio> buscarPorId(Long id) {
            return porId(guardados, id);
        }

        @Override
        public List<Barrio> buscarTodos() {
            return List.copyOf(guardados);
        }

        @Override
        public List<Barrio> buscarPorMunicipio(Long municipioId) {
            return guardados.stream().filter(b -> b.getMunicipio().getId().equals(municipioId)).toList();
        }
    }

    /**
     * Publicador que registra los eventos y, si tiene un listener conectado, se los entrega en el
     * momento, igual que los eventos síncronos de Spring.
     */
    public static class Eventos implements PublicadorEventos {
        public final List<EventoDominio> publicados = new ArrayList<>();
        private NotificacionesReclamoListener listener;

        public void conectar(NotificacionesReclamoListener listener) {
            this.listener = listener;
        }

        @Override
        public void publicar(EventoDominio evento) {
            publicados.add(evento);
            if (listener == null) {
                return;
            }
            switch (evento) {
                case ReclamoCreado creado -> listener.creado(creado);
                case ReclamoAsignado asignado -> listener.asignado(asignado);
                case EstadoReclamoCambiado cambio -> listener.estadoCambiado(cambio);
                case ReclamoResuelto resuelto -> listener.resuelto(resuelto);
                case ReclamoVencido vencido -> listener.vencido(vencido);
                default -> throw new IllegalArgumentException("Evento sin listener: " + evento.nombre());
            }
        }

        public List<String> nombres() {
            return publicados.stream().map(EventoDominio::nombre).toList();
        }
    }
}
