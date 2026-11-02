package ar.edu.uade.reclamos.cobertura.catalogo;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * El catálogo de este servicio y la semilla del backend usan los mismos ids. Si alguien agrega un barrio,
 * una categoría o un área en uno solo de los dos, el backend recibiría ids que no conoce; este test lo
 * detecta en la integración continua.
 *
 * <p>Lee {@code datos-dev.sql} del backend desde el repositorio: Maven corre los tests con el módulo como
 * directorio de trabajo.
 */
class CatalogoSincronizadoTest {

    private static final Path SEMILLA_BACKEND =
            Path.of("../../backend/reclamos-persistencia/src/main/resources/db/datos-dev.sql");
    private static final Pattern INSERT = Pattern.compile(
            "INSERT INTO (\\w+) \\([^)]*\\)\\s*SELECT (.+?) WHERE", Pattern.DOTALL);

    private static Map<String, List<List<String>>> semilla;
    private static DatosCatalogo catalogo;

    @BeforeAll
    static void leerAmbos() throws IOException {
        semilla = leerSemilla(Files.readString(SEMILLA_BACKEND, StandardCharsets.UTF_8));
        catalogo = new ObjectMapper().readValue(
                CatalogoSincronizadoTest.class.getResourceAsStream("/cobertura.json"), DatosCatalogo.class);
    }

    @Test
    void mismosMunicipios() {
        Map<Long, String> enSemilla = new TreeMap<>();
        semilla.get("municipios").forEach(fila -> enSemilla.put(id(fila.get(0)), texto(fila.get(1))));
        Map<Long, String> enCatalogo = new TreeMap<>();
        catalogo.municipios().forEach(municipio -> enCatalogo.put(municipio.id(), municipio.nombre()));

        assertThat(enCatalogo).isEqualTo(enSemilla);
    }

    @Test
    void mismosBarriosEnElMismoMunicipio() {
        Map<Long, String> enSemilla = new TreeMap<>();
        semilla.get("barrios").forEach(fila -> enSemilla.put(id(fila.get(0)), texto(fila.get(1)) + " @" + fila.get(2)));
        Map<Long, String> enCatalogo = new TreeMap<>();
        catalogo.municipios().forEach(municipio -> municipio.barrios().forEach(
                barrio -> enCatalogo.put(barrio.id(), barrio.nombre() + " @" + municipio.id())));

        assertThat(enCatalogo).isEqualTo(enSemilla);
    }

    @Test
    void mismasCategorias() {
        Map<Long, String> enSemilla = new TreeMap<>();
        semilla.get("categorias").forEach(fila -> enSemilla.put(id(fila.get(0)), texto(fila.get(1))));
        Map<Long, String> enCatalogo = new TreeMap<>();
        catalogo.categorias().forEach(categoria -> enCatalogo.put(categoria.id(), categoria.nombre()));

        assertThat(enCatalogo).isEqualTo(enSemilla);
    }

    @Test
    void mismasAreasEnElMismoMunicipio() {
        Map<Long, String> enSemilla = new TreeMap<>();
        semilla.get("areas_municipales").forEach(
                fila -> enSemilla.put(id(fila.get(0)), texto(fila.get(1)) + " @" + fila.get(4)));
        Map<Long, String> enCatalogo = new TreeMap<>();
        catalogo.areas().forEach(area -> enCatalogo.put(area.id(), area.nombre() + " @" + area.municipioId()));

        assertThat(enCatalogo).isEqualTo(enSemilla);
    }

    @Test
    void mismaCoberturaDeBarriosYCategorias() {
        Set<String> barriosEnCatalogo = new TreeSet<>();
        Set<String> categoriasEnCatalogo = new TreeSet<>();
        catalogo.areas().forEach(area -> {
            area.barrios().forEach(barrio -> barriosEnCatalogo.add(area.id() + "-" + barrio));
            area.categorias().forEach(categoria -> categoriasEnCatalogo.add(area.id() + "-" + categoria));
        });

        assertThat(barriosEnCatalogo).isEqualTo(pares("area_barrio"));
        assertThat(categoriasEnCatalogo).isEqualTo(pares("area_categoria"));
    }

    private static Set<String> pares(String tabla) {
        Set<String> pares = new TreeSet<>();
        semilla.get(tabla).forEach(fila -> pares.add(id(fila.get(0)) + "-" + id(fila.get(1))));
        return pares;
    }

    /** Filas de cada tabla, en el formato de la semilla: {@code INSERT INTO t (...) SELECT v1, v2 WHERE ...}. */
    private static Map<String, List<List<String>>> leerSemilla(String sql) {
        Map<String, List<List<String>>> filas = new TreeMap<>();
        Matcher insert = INSERT.matcher(sql);
        while (insert.find()) {
            filas.computeIfAbsent(insert.group(1), tabla -> new ArrayList<>()).add(separar(insert.group(2)));
        }
        return filas;
    }

    /** Separa por comas, sin cortar dentro de un texto entre comillas simples. */
    private static List<String> separar(String valores) {
        List<String> partes = new ArrayList<>();
        StringBuilder actual = new StringBuilder();
        boolean enTexto = false;
        for (char c : valores.toCharArray()) {
            if (c == '\'') {
                enTexto = !enTexto;
            }
            if (c == ',' && !enTexto) {
                partes.add(actual.toString().trim());
                actual.setLength(0);
            } else {
                actual.append(c);
            }
        }
        partes.add(actual.toString().trim());
        return partes;
    }

    private static long id(String valor) {
        return Long.parseLong(valor.trim());
    }

    private static String texto(String valor) {
        return valor.substring(1, valor.length() - 1).replace("''", "'");
    }
}
