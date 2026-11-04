package ar.edu.uade.reclamos.aplicacion.verificacion;

import ar.edu.uade.reclamos.aplicacion.configuracion.PropiedadesReclamos;
import ar.edu.uade.reclamos.comun.excepcion.AccesoDenegadoException;
import ar.edu.uade.reclamos.comun.excepcion.DatosInvalidosException;
import ar.edu.uade.reclamos.comun.excepcion.LimiteExcedidoException;
import ar.edu.uade.reclamos.comun.excepcion.RecursoNoEncontradoException;
import ar.edu.uade.reclamos.comun.excepcion.ReglaNegocioException;
import ar.edu.uade.reclamos.comun.excepcion.UsoRestringidoException;
import ar.edu.uade.reclamos.dominio.modelo.Ciudadano;
import ar.edu.uade.reclamos.dominio.modelo.CodigoVerificacion;
import ar.edu.uade.reclamos.dominio.modelo.Telefono;
import ar.edu.uade.reclamos.dominio.modelo.Usuario;
import ar.edu.uade.reclamos.dominio.repositorio.CodigoVerificacionRepository;
import ar.edu.uade.reclamos.dominio.repositorio.UsuarioRepository;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Verificación del teléfono: un número, una cuenta.
 *
 * <ol>
 *   <li>El ciudadano pide un código de un solo uso ({@link #iniciar}).</li>
 *   <li>Lo manda por el canal: con Telegram, abre el enlace y comparte su contacto con el bot.</li>
 *   <li>El canal confirma el código con el teléfono ({@link #confirmar}). Si el número está en otra cuenta, no
 *   se acepta.</li>
 * </ol>
 *
 * <p>El teléfono nunca se loguea ni sale del backend: la API lo devuelve enmascarado, y no viaja a la IA ni a
 * RabbitMQ.
 */
@Service
public class ServicioVerificacionTelefono {

    /** Sin 0, O, 1, I ni L, que se confunden al leerlos. */
    private static final String ALFABETO = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
    private static final int INTENTOS_CODIGO_UNICO = 5;

    private final UsuarioRepository usuarios;
    private final CodigoVerificacionRepository codigos;
    private final CanalVerificacion canal;
    private final PropiedadesReclamos.Uso uso;
    private final Clock reloj;
    private final SecureRandom azar = new SecureRandom();

    @Autowired
    public ServicioVerificacionTelefono(UsuarioRepository usuarios, CodigoVerificacionRepository codigos,
                                        CanalVerificacion canal, PropiedadesReclamos propiedades, Clock reloj) {
        this(usuarios, codigos, canal, propiedades.uso(), reloj);
    }

    public ServicioVerificacionTelefono(UsuarioRepository usuarios, CodigoVerificacionRepository codigos,
                                        CanalVerificacion canal, PropiedadesReclamos.Uso uso, Clock reloj) {
        this.usuarios = usuarios;
        this.codigos = codigos;
        this.canal = canal;
        this.uso = uso;
        this.reloj = reloj;
    }

    /** Crea un código para el ciudadano. Hasta {@code reclamos.uso.codigos-por-hora} por hora. */
    @Transactional
    public CodigoEmitido iniciar(Long usuarioId) {
        Ciudadano ciudadano = ciudadano(usuarioId);
        if (ciudadano.isBloqueado()) {
            throw new UsoRestringidoException(UsoRestringidoException.CUENTA_BLOQUEADA,
                    "Tu cuenta está bloqueada: no podés verificar el teléfono. Comunicate con el municipio.");
        }
        LocalDateTime ahora = ahora();
        if (codigos.contarCreadosPorCiudadanoDesde(ciudadano.getId(), ahora.minusHours(1)) >= uso.codigosPorHora()) {
            throw new LimiteExcedidoException("Ya pediste " + uso.codigosPorHora()
                    + " códigos en la última hora. Esperá un rato y probá de nuevo.");
        }
        CodigoVerificacion codigo = codigos.guardar(
                new CodigoVerificacion(codigoNuevo(), ciudadano, ahora, ahora.plus(uso.vigenciaCodigo())));
        return new CodigoEmitido(codigo.getCodigo(), canal.enlace(codigo.getCodigo()), codigo.getVence(),
                canal.nombre());
    }

    /**
     * Confirma un código con el teléfono que mandó el canal. No lanza excepciones por los casos esperables:
     * devuelve el resultado, para que el bot le conteste al ciudadano.
     */
    @Transactional
    public ResultadoVerificacion confirmar(String codigoRecibido, String telefono) {
        Optional<CodigoVerificacion> encontrado = codigos.buscarPorCodigo(normalizarCodigo(codigoRecibido));
        if (encontrado.isEmpty()) {
            return ResultadoVerificacion.CODIGO_INEXISTENTE;
        }
        CodigoVerificacion codigo = encontrado.get();
        LocalDateTime ahora = ahora();
        if (!codigo.estaVigente(ahora)) {
            return ResultadoVerificacion.CODIGO_VENCIDO;
        }
        String numero;
        try {
            numero = Telefono.normalizar(telefono);
        } catch (DatosInvalidosException invalido) {
            return ResultadoVerificacion.TELEFONO_INVALIDO;
        }
        Ciudadano duenio = ciudadano(codigo.getCiudadano().getId());
        if (duenio.isBloqueado()) {
            return ResultadoVerificacion.CUENTA_BLOQUEADA;
        }
        Optional<Ciudadano> conEseNumero = usuarios.buscarPorTelefonoVerificado(numero);
        if (conEseNumero.isPresent() && !conEseNumero.get().equals(duenio)) {
            return ResultadoVerificacion.TELEFONO_EN_OTRA_CUENTA;
        }
        codigo.usar(ahora);
        codigos.guardar(codigo);
        // Si ya tenía otro número, lo reemplaza: el anterior queda libre para otra cuenta.
        duenio.verificarTelefono(numero, ahora);
        usuarios.guardar(duenio);
        return ResultadoVerificacion.VERIFICADO;
    }

    /**
     * Modo simulado: el usuario de la sesión confirma su propio código con un número que escribe. Solo existe
     * para trabajar sin Telegram; con el bot, el número lo manda Telegram y no se puede inventar.
     */
    @Transactional
    public Usuario confirmarSimulado(Long usuarioId, String codigoRecibido, String telefono) {
        if (!"simulado".equals(canal.nombre())) {
            throw new RecursoNoEncontradoException(
                    "La verificación simulada no está disponible: el sistema usa el bot de Telegram.");
        }
        String codigo = normalizarCodigo(codigoRecibido);
        codigos.buscarPorCodigo(codigo)
                .filter(encontrado -> encontrado.getCiudadano().getId().equals(usuarioId))
                .orElseThrow(() -> new RecursoNoEncontradoException("código", codigo));
        ResultadoVerificacion resultado = confirmar(codigo, telefono);
        return switch (resultado) {
            case VERIFICADO -> ciudadano(usuarioId);
            case TELEFONO_INVALIDO -> throw new DatosInvalidosException("El teléfono tiene que tener entre 8 y 15 "
                    + "dígitos, con el código de país.");
            case CUENTA_BLOQUEADA -> throw new UsoRestringidoException(UsoRestringidoException.CUENTA_BLOQUEADA,
                    resultado.mensaje());
            default -> throw new ReglaNegocioException(resultado.mensaje());
        };
    }

    private Ciudadano ciudadano(Long usuarioId) {
        Usuario usuario = usuarios.buscarPorId(usuarioId)
                .orElseThrow(() -> new RecursoNoEncontradoException("usuario", usuarioId));
        if (usuario instanceof Ciudadano ciudadano) {
            return ciudadano;
        }
        throw new AccesoDenegadoException("Solo los ciudadanos verifican su teléfono.");
    }

    private String codigoNuevo() {
        for (int intento = 0; intento < INTENTOS_CODIGO_UNICO; intento++) {
            StringBuilder codigo = new StringBuilder(CodigoVerificacion.LONGITUD);
            for (int i = 0; i < CodigoVerificacion.LONGITUD; i++) {
                codigo.append(ALFABETO.charAt(azar.nextInt(ALFABETO.length())));
            }
            if (codigos.buscarPorCodigo(codigo.toString()).isEmpty()) {
                return codigo.toString();
            }
        }
        throw new IllegalStateException("No se pudo generar un código de verificación único");
    }

    private static String normalizarCodigo(String codigo) {
        return codigo == null ? "" : codigo.strip().toUpperCase(Locale.ROOT);
    }

    private LocalDateTime ahora() {
        return LocalDateTime.now(reloj);
    }
}
