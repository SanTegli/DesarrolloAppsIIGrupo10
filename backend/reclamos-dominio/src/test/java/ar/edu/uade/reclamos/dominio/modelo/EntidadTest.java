package ar.edu.uade.reclamos.dominio.modelo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import ar.edu.uade.reclamos.comun.excepcion.DatosInvalidosException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class EntidadTest {

    @Test
    @DisplayName("Dos entidades del mismo tipo con el mismo id son iguales")
    void igualdadPorId() {
        Municipio uno = new Municipio("Quilmes", "Buenos Aires");
        Municipio otro = new Municipio("Quilmes", "Buenos Aires");
        assertNotEquals(uno, otro, "sin id solo es igual a sí misma");
        assertEquals(uno, uno);

        uno.setId(7L);
        otro.setId(7L);
        assertEquals(uno, otro);
        assertEquals(uno.hashCode(), otro.hashCode());

        otro.setId(8L);
        assertNotEquals(uno, otro);
    }

    @Test
    @DisplayName("Entidades de tipos distintos no son iguales aunque compartan id")
    void tiposDistintos() {
        Municipio municipio = new Municipio("Quilmes", "Buenos Aires");
        Barrio barrio = new Barrio("Bernal", municipio);
        municipio.setId(1L);
        barrio.setId(1L);

        assertNotEquals(municipio, barrio);
    }

    @Test
    @DisplayName("Los roles salen de la subclase de usuario")
    void rolesPorSubclase() {
        Municipio municipio = new Municipio("Quilmes", "Buenos Aires");
        AreaMunicipal area = new AreaMunicipal("Alumbrado", "alumbrado@quilmes.gob.ar", municipio);

        assertEquals(Rol.CIUDADANO, new Ciudadano("30111222", "Ana", "Pérez", "ana@mail.com", null).getRol());
        assertEquals(Rol.ADMINISTRADOR, new Administrador("20555666", "Elena", "Ruiz", "elena@mail.com", null).getRol());
        assertEquals(Rol.AGENTE_MUNICIPAL,
                new AgenteMunicipal("25333444", "Carla", "Gómez", "carla@mail.com", null, area).getRol());
    }

    @Test
    @DisplayName("Un usuario con DNI o email inválido no se puede crear, ni un agente sin área")
    void usuarioInvalido() {
        assertThrows(DatosInvalidosException.class, () -> new Ciudadano("123", "Ana", "Pérez", "ana@mail.com", null));
        assertThrows(DatosInvalidosException.class, () -> new Ciudadano("30111222", "Ana", "Pérez", "ana", null));
        assertThrows(DatosInvalidosException.class,
                () -> new AgenteMunicipal("25333444", "Carla", "Gómez", "carla@mail.com", null, null));
    }

    @Test
    @DisplayName("La prioridad sube de a un nivel y CRITICA es el tope")
    void prioridad() {
        assertEquals(Prioridad.MEDIA, Prioridad.BAJA.siguiente());
        assertEquals(Prioridad.CRITICA, Prioridad.ALTA.siguiente());
        assertEquals(Prioridad.CRITICA, Prioridad.CRITICA.siguiente());
        assertEquals(Prioridad.ALTA, Prioridad.mayor(Prioridad.ALTA, Prioridad.MEDIA));
        assertEquals(Prioridad.ALTA, Prioridad.mayor(Prioridad.BAJA, Prioridad.ALTA));
    }
}
