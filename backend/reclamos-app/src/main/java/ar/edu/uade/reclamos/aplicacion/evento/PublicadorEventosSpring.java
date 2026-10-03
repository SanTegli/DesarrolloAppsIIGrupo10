package ar.edu.uade.reclamos.aplicacion.evento;

import ar.edu.uade.reclamos.dominio.evento.EventoDominio;
import ar.edu.uade.reclamos.dominio.evento.PublicadorEventos;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component
public class PublicadorEventosSpring implements PublicadorEventos {
    private final ApplicationEventPublisher publicador;

    public PublicadorEventosSpring(ApplicationEventPublisher publicador) {
        this.publicador = publicador;
    }

    @Override
    public void publicar(EventoDominio evento) {
        publicador.publishEvent(evento);
    }
}
