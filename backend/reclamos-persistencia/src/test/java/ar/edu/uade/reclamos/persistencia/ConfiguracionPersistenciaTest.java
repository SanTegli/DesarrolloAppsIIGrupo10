package ar.edu.uade.reclamos.persistencia;

import ar.edu.uade.reclamos.persistencia.adaptador.*;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootConfiguration
@EnableAutoConfiguration
@EntityScan("ar.edu.uade.reclamos.dominio.modelo")
@EnableJpaRepositories("ar.edu.uade.reclamos.persistencia.jpa")
@Import({ReclamoRepositoryJpa.class, UsuarioRepositoryJpa.class, MunicipioRepositoryJpa.class,
        BarrioRepositoryJpa.class, CategoriaRepositoryJpa.class, AreaMunicipalRepositoryJpa.class,
        NotificacionRepositoryJpa.class})
class ConfiguracionPersistenciaTest {
}

