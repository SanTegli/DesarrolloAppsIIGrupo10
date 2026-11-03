package ar.edu.uade.reclamos.aplicacion.mensajeria;

import ar.edu.uade.reclamos.dominio.modelo.EstadoMensaje;
import ar.edu.uade.reclamos.dominio.modelo.MensajeSaliente;
import ar.edu.uade.reclamos.dominio.repositorio.MensajeSalienteRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** La outbox en una lista, para probar sin base de datos. */
class OutboxEnMemoria implements MensajeSalienteRepository {
    final List<MensajeSaliente> guardados;
    int guardadas;

    OutboxEnMemoria() {
        this(new ArrayList<>());
    }

    OutboxEnMemoria(List<MensajeSaliente> guardados) {
        this.guardados = guardados;
    }

    @Override
    public MensajeSaliente guardar(MensajeSaliente mensaje) {
        guardadas++;
        if (!guardados.contains(mensaje)) {
            guardados.add(mensaje);
        }
        return mensaje;
    }

    @Override
    public List<MensajeSaliente> buscarPendientes(int limite) {
        return guardados.stream().filter(MensajeSaliente::isPendiente).limit(limite).toList();
    }

    @Override
    public Optional<MensajeSaliente> buscarPorEventId(String eventId) {
        return guardados.stream().filter(mensaje -> mensaje.getEventId().equals(eventId)).findFirst();
    }

    @Override
    public long contarPorEstado(EstadoMensaje estado) {
        return guardados.stream().filter(mensaje -> mensaje.getEstado() == estado).count();
    }
}
