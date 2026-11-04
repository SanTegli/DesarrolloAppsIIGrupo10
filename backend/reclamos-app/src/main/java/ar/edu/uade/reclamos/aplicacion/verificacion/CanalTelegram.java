package ar.edu.uade.reclamos.aplicacion.verificacion;

/** El enlace {@code t.me/<bot>?start=<código>}: Telegram abre el chat con el bot y le manda {@code /start <código>}. */
public class CanalTelegram implements CanalVerificacion {

    private final String usuarioBot;

    public CanalTelegram(String usuarioBot) {
        this.usuarioBot = usuarioBot.startsWith("@") ? usuarioBot.substring(1) : usuarioBot;
    }

    @Override
    public String nombre() {
        return "telegram";
    }

    @Override
    public String enlace(String codigo) {
        return "https://t.me/" + usuarioBot + "?start=" + codigo;
    }
}
