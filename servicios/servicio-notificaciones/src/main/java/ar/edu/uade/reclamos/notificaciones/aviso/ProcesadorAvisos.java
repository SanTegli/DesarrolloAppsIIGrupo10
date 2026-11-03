package ar.edu.uade.reclamos.notificaciones.aviso;

import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Lo que se hace con cada mensaje: leerlo, descartarlo si ya se procesó, mandar el correo y registrarlo.
 *
 * <p><b>Consumidor idempotente:</b> el {@code eventId} se registra después de enviar. Si el correo falla, no
 * se registra y el mensaje se reintenta. Si el servicio cae justo entre el envío y el registro, al reintentar
 * el correo se manda otra vez: no hay entrega exactamente una vez, y está documentado así en el contrato.
 */
public class ProcesadorAvisos {

    /** Qué pasó con un mensaje válido. */
    public enum Resultado { ENVIADO, REPETIDO }

    private static final Logger LOGGER = LoggerFactory.getLogger(ProcesadorAvisos.class);

    private final LectorMensajes lector;
    private final RegistroProcesados registro;
    private final EnvioCorreo correo;
    private final Clock reloj;

    public ProcesadorAvisos(LectorMensajes lector, RegistroProcesados registro, EnvioCorreo correo, Clock reloj) {
        this.lector = lector;
        this.registro = registro;
        this.correo = correo;
        this.reloj = reloj;
    }

    /**
     * @throws MensajeInvalidoException si el mensaje no cumple el contrato (va a la cola de errores sin reintentar)
     * @throws RuntimeException         si el correo no se pudo mandar (se reintenta)
     */
    public Resultado procesar(String cuerpo) {
        MensajeAviso aviso = lector.leer(cuerpo);
        if (registro.yaProcesado(aviso.eventId())) {
            LOGGER.info("Mensaje {} repetido: ya se había enviado, se descarta", aviso.eventId());
            return Resultado.REPETIDO;
        }
        correo.enviar(aviso);
        registro.registrar(aviso.eventId(), aviso.tipoEvento(), reloj.instant());
        // Sin el email en el log: alcanza con el evento y el reclamo para seguir el rastro.
        LOGGER.info("Correo enviado: {} del reclamo {} ({})", aviso.tipoEvento(), aviso.reclamoNumero(),
                aviso.eventId());
        return Resultado.ENVIADO;
    }
}
