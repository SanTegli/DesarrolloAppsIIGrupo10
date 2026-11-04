package ar.edu.uade.reclamos.aplicacion.verificacion;

import ar.edu.uade.reclamos.aplicacion.verificacion.ClienteTelegram.Mensaje;
import ar.edu.uade.reclamos.aplicacion.verificacion.ClienteTelegram.Teclado;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * El bot que verifica teléfonos. Consulta los mensajes nuevos cada pocos segundos ({@code getUpdates}) y
 * conversa así:
 *
 * <ol>
 *   <li>{@code /start RU4K7P2Q} (lo manda Telegram al abrir el enlace): guarda el código para ese chat y
 *   contesta con el botón "Compartir mi número".</li>
 *   <li>Contacto: si es el del mismo usuario que escribe, confirma el código con ese teléfono y contesta el
 *   resultado. Un contacto de otra persona no se acepta: así nadie verifica un número ajeno.</li>
 * </ol>
 *
 * <p>Telegram admite un solo proceso consultando {@code getUpdates} por bot: en la demo lo hace el backend del
 * contenedor; para desarrollar, cada integrante usa su propio bot.
 */
public class BotTelegram {

    static final String AYUDA = "Hola. Para verificar tu teléfono, entrá a la página de reclamos, tocá "
            + "\"Verificar con Telegram\" y abrí el enlace que aparece.";
    static final String PEDIR_CONTACTO = "Tocá el botón para compartir tu número. Solo se usa para que cada "
            + "número tenga una sola cuenta.";
    static final String CONTACTO_AJENO = "Compartí tu propio número con el botón: no se aceptan contactos de otras "
            + "personas.";
    static final String SIN_CODIGO = "Primero abrí el enlace que aparece en la página, así sé qué cuenta verificar.";

    private static final Logger LOGGER = LoggerFactory.getLogger(BotTelegram.class);

    private final ClienteTelegram telegram;
    private final ServicioVerificacionTelefono verificacion;
    /** Chat de Telegram → código que mandó con /start. Si el backend se reinicia, el usuario vuelve a abrir el enlace. */
    private final Map<Long, String> pendientes = new ConcurrentHashMap<>();
    private long proximo;

    public BotTelegram(ClienteTelegram telegram, ServicioVerificacionTelefono verificacion) {
        this.telegram = telegram;
        this.verificacion = verificacion;
    }

    @Scheduled(fixedDelayString = "${reclamos.telegram.intervalo-ms:2000}")
    public void consultar() {
        List<Mensaje> mensajes;
        try {
            mensajes = telegram.recibir(proximo);
        } catch (TelegramNoDisponibleException error) {
            LOGGER.warn("{}; se vuelve a intentar en la próxima consulta", error.getMessage());
            return;
        }
        for (Mensaje mensaje : mensajes) {
            proximo = mensaje.idActualizacion() + 1;
            try {
                procesar(mensaje);
            } catch (RuntimeException error) {
                // Sin el texto del mensaje ni el teléfono en el log.
                LOGGER.warn("No se pudo procesar el mensaje {} de Telegram: {}", mensaje.idActualizacion(),
                        error.getClass().getSimpleName());
            }
        }
    }

    void procesar(Mensaje mensaje) {
        if (mensaje.chatId() == 0) {
            return;
        }
        if (mensaje.contacto() != null) {
            recibirContacto(mensaje);
            return;
        }
        String texto = mensaje.texto() == null ? "" : mensaje.texto().strip();
        if (texto.startsWith("/start")) {
            String codigo = texto.substring("/start".length()).strip().toUpperCase(Locale.ROOT);
            if (codigo.isEmpty()) {
                telegram.enviar(mensaje.chatId(), AYUDA, Teclado.NINGUNO);
                return;
            }
            pendientes.put(mensaje.chatId(), codigo);
            telegram.enviar(mensaje.chatId(), PEDIR_CONTACTO, Teclado.PEDIR_CONTACTO);
            return;
        }
        telegram.enviar(mensaje.chatId(), AYUDA, Teclado.NINGUNO);
    }

    private void recibirContacto(Mensaje mensaje) {
        if (mensaje.remitenteId() == null || !mensaje.remitenteId().equals(mensaje.contacto().usuarioId())) {
            telegram.enviar(mensaje.chatId(), CONTACTO_AJENO, Teclado.PEDIR_CONTACTO);
            return;
        }
        String codigo = pendientes.remove(mensaje.chatId());
        if (codigo == null) {
            telegram.enviar(mensaje.chatId(), SIN_CODIGO, Teclado.QUITAR);
            return;
        }
        ResultadoVerificacion resultado = verificacion.confirmar(codigo, mensaje.contacto().telefono());
        LOGGER.info("Verificación de teléfono por Telegram: {}", resultado);
        telegram.enviar(mensaje.chatId(), resultado.mensaje(), Teclado.QUITAR);
    }
}
