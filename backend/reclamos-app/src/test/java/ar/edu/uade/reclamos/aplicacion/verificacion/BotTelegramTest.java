package ar.edu.uade.reclamos.aplicacion.verificacion;

import static org.hamcrest.Matchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.anyString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * El bot contra respuestas de la Bot API de Telegram con el formato real de {@code getUpdates}, sin llamar a
 * Telegram ni necesitar un token.
 */
class BotTelegramTest {

    private static final String TOKEN = "123456:TOKEN-DE-PRUEBA";
    private static final String BASE = "https://api.telegram.test/bot" + TOKEN;
    private static final long CHAT = 7001;
    private static final long ANA = 7001;

    private MockRestServiceServer telegram;
    private ServicioVerificacionTelefono verificacion;
    private BotTelegram bot;

    @BeforeEach
    void preparar() {
        RestClient.Builder http = RestClient.builder().baseUrl("https://api.telegram.test");
        telegram = MockRestServiceServer.bindTo(http).build();
        verificacion = mock(ServicioVerificacionTelefono.class);
        bot = new BotTelegram(new ClienteTelegram(http.build(), TOKEN), verificacion);
    }

    @AfterEach
    void verificar() {
        telegram.verify();
    }

    @Test
    void conStartPideElContactoYConElContactoPropioConfirmaElCodigo() {
        when(verificacion.confirmar("RU4K7P2Q", "5491155550000")).thenReturn(ResultadoVerificacion.VERIFICADO);
        recibir(0, actualizacion(10, "\"text\": \"/start ru4k7p2q\""));
        esperarRespuesta(BotTelegram.PEDIR_CONTACTO, true);
        recibir(11, actualizacion(11, "\"contact\": {\"phone_number\": \"5491155550000\", \"first_name\": \"Ana\", "
                + "\"user_id\": " + ANA + "}"));
        esperarRespuesta(ResultadoVerificacion.VERIFICADO.mensaje(), false);

        bot.consultar();
        bot.consultar();

        verify(verificacion).confirmar("RU4K7P2Q", "5491155550000");
    }

    @Test
    void elContactoDeOtraPersonaNoSeAcepta() {
        recibir(0, actualizacion(20, "\"text\": \"/start RU4K7P2Q\""));
        esperarRespuesta(BotTelegram.PEDIR_CONTACTO, true);
        recibir(21, actualizacion(21, "\"contact\": {\"phone_number\": \"5491166660000\", \"first_name\": \"Otro\", "
                + "\"user_id\": 9999}"));
        esperarRespuesta(BotTelegram.CONTACTO_AJENO, true);

        bot.consultar();
        bot.consultar();

        verify(verificacion, never()).confirmar(anyString(), anyString());
    }

    @Test
    void unContactoSinHaberAbiertoElEnlaceNoVerificaNada() {
        recibir(0, actualizacion(30, "\"contact\": {\"phone_number\": \"5491155550000\", \"user_id\": " + ANA + "}"));
        esperarRespuesta(BotTelegram.SIN_CODIGO, false);
        bot.consultar();

        verify(verificacion, never()).confirmar(anyString(), anyString());
    }

    @Test
    void cualquierOtroMensajeRecibeLaAyuda() {
        recibir(0, actualizacion(40, "\"text\": \"hola\""));
        esperarRespuesta(BotTelegram.AYUDA, false);
        bot.consultar();
    }

    @Test
    void siTelegramFallaNoSeCaeYVuelveAPedirDesdeElMismoPunto() {
        telegram.expect(requestTo(startsWith(BASE + "/getUpdates?offset=0&"))).andRespond(withServerError());
        recibir(0, "");

        bot.consultar();
        bot.consultar();
    }

    private void recibir(long desde, String actualizaciones) {
        // El resto de la consulta (timeout y allowed_updates) va codificado; lo que importa es el offset.
        telegram.expect(requestTo(startsWith(BASE + "/getUpdates?offset=" + desde + "&")))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"ok\": true, \"result\": [" + actualizaciones + "]}",
                        MediaType.APPLICATION_JSON));
    }

    private void esperarRespuesta(String texto, boolean pideContacto) {
        var respuesta = telegram.expect(requestTo(BASE + "/sendMessage"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.chat_id").value(CHAT))
                .andExpect(jsonPath("$.text").value(texto));
        if (pideContacto) {
            respuesta.andExpect(jsonPath("$.reply_markup.keyboard[0][0].request_contact").value(true));
        }
        respuesta.andRespond(withSuccess("{\"ok\": true, \"result\": {}}", MediaType.APPLICATION_JSON));
    }

    /** Una actualización de getUpdates con el formato de Telegram y el contenido dado. */
    private static String actualizacion(long id, String contenido) {
        return "{\"update_id\": " + id + ", \"message\": {\"message_id\": " + id + ", "
                + "\"from\": {\"id\": " + ANA + ", \"is_bot\": false, \"first_name\": \"Ana\"}, "
                + "\"chat\": {\"id\": " + CHAT + ", \"type\": \"private\"}, \"date\": 1793700000, " + contenido + "}}";
    }
}
