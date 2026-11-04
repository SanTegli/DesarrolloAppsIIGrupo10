package ar.edu.uade.reclamos.aplicacion.control;

import ar.edu.uade.reclamos.comun.excepcion.LimiteExcedidoException;
import ar.edu.uade.reclamos.dominio.modelo.Ciudadano;
import ar.edu.uade.reclamos.dominio.repositorio.ReclamoRepository;
import java.time.LocalDateTime;

/** Hasta {@code maximo} reclamos nuevos por ciudadano por día calendario. */
public class LimiteDiarioDeReclamos extends ControlDeUso {

    private final ReclamoRepository reclamos;
    private final int maximo;

    public LimiteDiarioDeReclamos(ReclamoRepository reclamos, int maximo) {
        this.reclamos = reclamos;
        this.maximo = maximo;
    }

    @Override
    protected void revisar(Ciudadano ciudadano, LocalDateTime ahora) {
        long hoy = reclamos.contarCreadosPorCiudadanoDesde(ciudadano.getId(), ahora.toLocalDate().atStartOfDay());
        if (hoy >= maximo) {
            throw new LimiteExcedidoException("Ya presentaste " + maximo + " reclamos hoy, que es el máximo por día. "
                    + "Podés presentar más mañana.");
        }
    }
}
