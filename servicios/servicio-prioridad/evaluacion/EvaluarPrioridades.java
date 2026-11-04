import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Evaluación de la IA con casos reales: manda cada caso de casos.tsv al servicio de prioridad levantado y
 * escribe el resultado (aciertos y tiempos) en docs/evidencias/ia-evaluacion.md.
 *
 * <p>No necesita Maven ni dependencias: es un solo archivo que Java 21 ejecuta directo. Desde la raíz del
 * repositorio, con el servicio corriendo con la clave de Gemini:
 *
 * <pre>
 *   java servicios/servicio-prioridad/evaluacion/EvaluarPrioridades.java
 * </pre>
 *
 * Opciones: {@code --url http://localhost:8082}, {@code --pausa-ms 7000} (espera entre casos, para no pasar
 * el límite por minuto del nivel gratuito), {@code --casos <archivo>} y {@code --salida <archivo>}.
 */
public class EvaluarPrioridades {

    private static final List<String> NIVELES = List.of("BAJA", "MEDIA", "ALTA", "CRITICA");

    /**
     * Metas del componente de IA (docs/requerimientos.md, RNF-IA-1 a RNF-IA-4). La de latencia es el tiempo
     * límite con que el backend espera al servicio: por encima, usaría el respaldo por categoría.
     */
    static final double META_EXACTOS = 0.70;
    static final double META_A_UN_NIVEL = 0.90;
    static final double META_RESPONDIDOS = 0.95;
    static final long META_P95_MS = 6000;

    /** Una meta, lo que dio y si se cumple. */
    record Meta(String requisito, String nombre, String objetivo, String obtenido, boolean cumple) { }

    record Caso(int numero, String esperada, String categoria, String descripcion) { }

    record Resultado(Caso caso, String obtenida, String motivo, String proveedor, String modelo, String error,
                     long milisegundos) {
        boolean acierto() {
            return caso.esperada().equals(obtenida);
        }

        /** Diferencia de niveles: negativa si la IA puso menos prioridad que la esperada. */
        int diferencia() {
            return obtenida == null ? 0 : NIVELES.indexOf(obtenida) - NIVELES.indexOf(caso.esperada());
        }
    }

    public static void main(String[] args) throws Exception {
        String url = opcion(args, "--url", "http://localhost:8082");
        long pausa = Long.parseLong(opcion(args, "--pausa-ms", "7000"));
        Path casos = Path.of(opcion(args, "--casos", "servicios/servicio-prioridad/evaluacion/casos.tsv"));
        Path salida = Path.of(opcion(args, "--salida", "docs/evidencias/ia-evaluacion.md"));

        List<Caso> lista = leerCasos(casos);
        HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        List<Resultado> resultados = new ArrayList<>();
        for (Caso caso : lista) {
            Resultado resultado = evaluar(http, url, caso);
            resultados.add(resultado);
            System.out.printf("%2d/%d  esperada %-7s obtenida %-7s %5d ms  %s%n", caso.numero(), lista.size(),
                    caso.esperada(), resultado.obtenida() == null ? "-" : resultado.obtenida(),
                    resultado.milisegundos(), resultado.error() == null ? (resultado.acierto() ? "ok" : "distinta")
                            : resultado.error());
            if (caso.numero() < lista.size()) {
                Thread.sleep(pausa);
            }
        }
        if (resultados.stream().allMatch(r -> r.error() != null)) {
            System.out.println("Ningún caso respondió: no se modificó " + salida + ". ¿Está levantado el servicio?");
            System.exit(1);
        }
        Files.createDirectories(salida.toAbsolutePath().getParent());
        Files.writeString(salida, informe(resultados, url), StandardCharsets.UTF_8);
        System.out.println("Resultado guardado en " + salida);
        List<Meta> metas = metas(resultados);
        metas.forEach(meta -> System.out.printf("%-10s %-32s objetivo %-10s obtenido %-12s %s%n", meta.requisito(),
                meta.nombre(), meta.objetivo(), meta.obtenido(), meta.cumple() ? "cumple" : "NO CUMPLE"));
        System.out.println(metas.stream().allMatch(Meta::cumple)
                ? "El componente de IA cumple todas las metas."
                : "Hay metas sin cumplir: revisar las instrucciones del prompt o el modelo.");
    }

    private static List<Caso> leerCasos(Path archivo) throws IOException {
        List<Caso> casos = new ArrayList<>();
        for (String linea : Files.readAllLines(archivo, StandardCharsets.UTF_8)) {
            if (linea.isBlank() || linea.startsWith("#")) {
                continue;
            }
            String[] campos = linea.split("\t");
            if (campos.length != 3 || !NIVELES.contains(campos[0].strip())) {
                throw new IllegalArgumentException("Línea inválida en " + archivo + ": " + linea);
            }
            casos.add(new Caso(casos.size() + 1, campos[0].strip(), campos[1].strip(), campos[2].strip()));
        }
        return casos;
    }

    private static Resultado evaluar(HttpClient http, String url, Caso caso) {
        String cuerpo = "{\"categoria\": \"" + json(caso.categoria()) + "\", \"descripcion\": \""
                + json(caso.descripcion()) + "\"}";
        HttpRequest pedido = HttpRequest.newBuilder(URI.create(url + "/api/prioridades/evaluar"))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(cuerpo, StandardCharsets.UTF_8))
                .build();
        long inicio = System.nanoTime();
        try {
            HttpResponse<String> respuesta = http.send(pedido, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            long ms = (System.nanoTime() - inicio) / 1_000_000;
            String texto = respuesta.body();
            if (respuesta.statusCode() != 200) {
                return new Resultado(caso, null, null, null, null,
                        "HTTP " + respuesta.statusCode() + " " + campo(texto, "codigo") + ": " + campo(texto, "mensaje"), ms);
            }
            return new Resultado(caso, campo(texto, "prioridad"), campo(texto, "motivo"), campo(texto, "proveedor"),
                    campo(texto, "modelo"), null, ms);
        } catch (IOException | InterruptedException error) {
            String motivo = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
            return new Resultado(caso, null, null, null, null, "sin respuesta: " + motivo,
                    (System.nanoTime() - inicio) / 1_000_000);
        }
    }

    /** Compara los resultados con las metas de los requerimientos. */
    static List<Meta> metas(List<Resultado> resultados) {
        List<Resultado> respondidos = resultados.stream().filter(r -> r.error() == null).toList();
        int total = respondidos.size();
        double exactos = total == 0 ? 0 : respondidos.stream().filter(Resultado::acierto).count() / (double) total;
        double aUnNivel = total == 0 ? 0
                : respondidos.stream().filter(r -> Math.abs(r.diferencia()) <= 1).count() / (double) total;
        double respuesta = resultados.isEmpty() ? 0 : total / (double) resultados.size();
        long p95 = percentil(respondidos.stream().map(Resultado::milisegundos).sorted().toList(), 0.95);
        return List.of(
                new Meta("RNF-IA-1", "Aciertos exactos", "≥ " + entero(META_EXACTOS), entero(exactos),
                        total > 0 && exactos >= META_EXACTOS),
                new Meta("RNF-IA-1", "A un nivel o menos de la esperada", "≥ " + entero(META_A_UN_NIVEL),
                        entero(aUnNivel), total > 0 && aUnNivel >= META_A_UN_NIVEL),
                new Meta("RNF-IA-2", "Tiempo de respuesta, percentil 95", "≤ " + META_P95_MS + " ms",
                        total == 0 ? "sin datos" : p95 + " ms", total > 0 && p95 <= META_P95_MS),
                new Meta("RNF-IA-3", "Casos con respuesta válida", "≥ " + entero(META_RESPONDIDOS), entero(respuesta),
                        respuesta >= META_RESPONDIDOS));
    }

    /** Percentil por el método del rango más cercano, sobre una lista ya ordenada. */
    static long percentil(List<Long> ordenados, double fraccion) {
        if (ordenados.isEmpty()) {
            return 0;
        }
        int posicion = (int) Math.ceil(fraccion * ordenados.size()) - 1;
        return ordenados.get(Math.max(0, Math.min(posicion, ordenados.size() - 1)));
    }

    private static String entero(double fraccion) {
        return Math.round(fraccion * 100) + " %";
    }

    private static String informe(List<Resultado> resultados, String url) {
        List<Resultado> respondidos = resultados.stream().filter(r -> r.error() == null).toList();
        long aciertos = respondidos.stream().filter(Resultado::acierto).count();
        long aUnNivel = respondidos.stream().filter(r -> Math.abs(r.diferencia()) <= 1).count();
        List<Resultado> subestimados = respondidos.stream().filter(r -> r.diferencia() < 0).toList();
        List<Long> tiempos = respondidos.stream().map(Resultado::milisegundos).sorted().toList();
        String proveedor = respondidos.isEmpty() ? "sin respuestas"
                : respondidos.get(0).proveedor() + ", modelo " + respondidos.get(0).modelo();

        StringBuilder md = new StringBuilder();
        md.append("# Evaluación de la IA de prioridad\n\n");
        md.append("Generado por `EvaluarPrioridades.java` el ")
                .append(ZonedDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", Locale.ROOT)))
                .append(" contra `").append(url).append("`.\n\n");
        md.append("| Dato | Valor |\n| --- | --- |\n");
        md.append("| Proveedor | ").append(proveedor).append(" |\n");
        md.append("| Casos | ").append(resultados.size()).append(", ").append(respondidos.size())
                .append(" respondidos |\n");
        md.append("| Aciertos exactos | ").append(porcentaje(aciertos, respondidos.size())).append(" |\n");
        md.append("| A un nivel o menos de la esperada | ").append(porcentaje(aUnNivel, respondidos.size()))
                .append(" |\n");
        md.append("| Por debajo de la esperada | ").append(subestimados.size())
                .append(" (los más delicados: el reclamo quedaría con menos prioridad de la que merece) |\n");
        md.append("| Tiempo de respuesta | ").append(tiempos(tiempos)).append(" |\n\n");
        md.append("## Metas\n\n");
        md.append("Las metas están en [`requerimientos.md`](../requerimientos.md#requerimientos-del-componente-de-ia).\n\n");
        md.append("| Requisito | Meta | Objetivo | Obtenido | Resultado |\n| --- | --- | --- | --- | --- |\n");
        for (Meta meta : metas(resultados)) {
            md.append("| ").append(meta.requisito()).append(" | ").append(meta.nombre()).append(" | ")
                    .append(meta.objetivo()).append(" | ").append(meta.obtenido()).append(" | ")
                    .append(meta.cumple() ? "Cumple" : "**No cumple**").append(" |\n");
        }
        md.append("\nLa privacidad (RNF-IA-4) no se mide acá: la garantiza el contrato de REST 2, que solo acepta ")
                .append("categoría y descripción (`PrioridadPorIAIntegrationTest.alServicioDeIaSoloLeLlegaLaCategoriaYLaDescripcion`).\n\n");
        md.append("## Casos\n\n");
        if (respondidos.size() < resultados.size()) {
            int sinRespuesta = resultados.size() - respondidos.size();
            md.append(sinRespuesta == 1 ? "Hubo 1 caso" : "Hubo " + sinRespuesta + " casos")
                    .append(" sin respuesta válida; el detalle está en la tabla.\n\n");
        }
        md.append("En el sistema la prioridad final es la mayor entre la de la categoría y la sugerida: la IA puede ")
                .append("subir la prioridad, pero no bajarla. Las prioridades esperadas son la propuesta del grupo ")
                .append("en `casos.tsv`.\n\n");
        md.append("| # | Categoría | Descripción | Esperada | Obtenida | ms | Motivo o error |\n");
        md.append("| --- | --- | --- | --- | --- | --- | --- |\n");
        for (Resultado r : resultados) {
            String marca = r.error() != null ? "—" : r.acierto() ? r.obtenida() + " ✓" : r.obtenida() + " ✗";
            md.append("| ").append(r.caso().numero()).append(" | ").append(celda(r.caso().categoria()))
                    .append(" | ").append(celda(r.caso().descripcion())).append(" | ").append(r.caso().esperada())
                    .append(" | ").append(marca).append(" | ").append(r.milisegundos()).append(" | ")
                    .append(celda(r.error() != null ? r.error() : r.motivo())).append(" |\n");
        }
        return md.toString();
    }

    private static String porcentaje(long cantidad, int total) {
        return total == 0 ? "sin casos" : cantidad + " de " + total + " (" + Math.round(cantidad * 100.0 / total) + " %)";
    }

    private static String tiempos(List<Long> ordenados) {
        if (ordenados.isEmpty()) {
            return "sin respuestas";
        }
        long promedio = Math.round(ordenados.stream().mapToLong(Long::longValue).average().orElse(0));
        long mediana = ordenados.get(ordenados.size() / 2);
        return "promedio " + promedio + " ms, mediana " + mediana + " ms, máximo " + ordenados.get(ordenados.size() - 1)
                + " ms";
    }

    private static String celda(String texto) {
        return texto == null ? "" : texto.replace("|", "\\|").replace("\n", " ");
    }

    private static String json(String texto) {
        StringBuilder salida = new StringBuilder();
        for (char c : texto.toCharArray()) {
            switch (c) {
                case '"' -> salida.append("\\\"");
                case '\\' -> salida.append("\\\\");
                case '\n' -> salida.append("\\n");
                case '\r' -> salida.append("\\r");
                case '\t' -> salida.append("\\t");
                default -> {
                    if (c < 0x20) {
                        salida.append(String.format("\\u%04x", (int) c));
                    } else {
                        salida.append(c);
                    }
                }
            }
        }
        return salida.toString();
    }

    /** Valor de texto de un campo del JSON de respuesta; alcanza para las respuestas planas del servicio. */
    private static String campo(String json, String nombre) {
        Matcher valor = Pattern.compile("\"" + nombre + "\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"").matcher(json);
        if (!valor.find()) {
            return null;
        }
        String crudo = valor.group(1);
        StringBuilder texto = new StringBuilder();
        for (int i = 0; i < crudo.length(); i++) {
            char c = crudo.charAt(i);
            if (c != '\\' || i + 1 >= crudo.length()) {
                texto.append(c);
                continue;
            }
            char siguiente = crudo.charAt(++i);
            switch (siguiente) {
                case 'n' -> texto.append('\n');
                case 't' -> texto.append('\t');
                case 'r' -> texto.append('\r');
                case 'u' -> {
                    texto.append((char) Integer.parseInt(crudo.substring(i + 1, i + 5), 16));
                    i += 4;
                }
                default -> texto.append(siguiente);
            }
        }
        return texto.toString();
    }

    private static String opcion(String[] args, String nombre, String porDefecto) {
        for (int i = 0; i < args.length - 1; i++) {
            if (args[i].equals(nombre)) {
                return args[i + 1];
            }
        }
        return porDefecto;
    }
}
