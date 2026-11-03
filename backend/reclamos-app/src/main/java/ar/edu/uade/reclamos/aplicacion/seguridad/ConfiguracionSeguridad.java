package ar.edu.uade.reclamos.aplicacion.seguridad;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.savedrequest.NullRequestCache;

/**
 * Login con sesión del servidor: el navegador guarda la cookie {@code JSESSIONID} ({@code HttpOnly}) y el
 * backend, quién es el usuario. No hay JWT ni encabezado de identidad.
 *
 * <p>La cadena de filtros de Spring Security es un Chain of Responsibility: cada filtro (CSRF, sesión,
 * autorización, cierre de sesión) resuelve su parte y pasa el pedido al siguiente.
 *
 * <ul>
 *   <li><b>Rutas abiertas:</b> login, registro, los catálogos públicos (categorías, barrios, áreas) y Swagger.</li>
 *   <li><b>Todo lo demás de /api</b> exige sesión. Qué puede hacer cada rol lo sigue decidiendo el dominio.</li>
 *   <li><b>CSRF:</b> token en la cookie {@code XSRF-TOKEN}, devuelto en el encabezado {@code X-XSRF-TOKEN}.</li>
 * </ul>
 */
@Configuration(proxyBeanMethods = false)
public class ConfiguracionSeguridad {

    static final String RUTA_LOGOUT = "/api/auth/logout";

    @Bean
    public SecurityFilterChain cadenaDeSeguridad(HttpSecurity http, RespuestasDeSeguridad respuestas,
                                                 SecurityContextRepository contextos) throws Exception {
        http
                .csrf(csrf -> csrf
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .csrfTokenRequestHandler(new CsrfParaAplicacionDeUnaPagina()))
                .securityContext(contexto -> contexto.securityContextRepository(contextos))
                .authorizeHttpRequests(rutas -> rutas
                        .requestMatchers(HttpMethod.POST, "/api/auth/login", "/api/auth/registro").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/categorias", "/api/barrios", "/api/areas").permitAll()
                        .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs", "/v3/api-docs/**",
                                "/v3/api-docs.yaml", "/error").permitAll()
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().permitAll())
                .exceptionHandling(errores -> errores
                        .authenticationEntryPoint(respuestas)
                        .accessDeniedHandler(respuestas))
                // Un 401 no crea sesión para recordar el pedido: la interfaz no es un formulario de Spring.
                .requestCache(cache -> cache.requestCache(new NullRequestCache()))
                .logout(salida -> salida
                        .logoutUrl(RUTA_LOGOUT)
                        .deleteCookies("JSESSIONID")
                        .logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler(HttpStatus.NO_CONTENT)))
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable);
        return http.build();
    }

    /** Dónde vive la sesión del usuario: en la sesión HTTP del servidor. El login la guarda ahí. */
    @Bean
    public SecurityContextRepository repositorioDeContextos() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    public PasswordEncoder codificadorDeContrasenas() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager autenticador(UserDetailsService cargaDeUsuarios, PasswordEncoder contrasenas) {
        DaoAuthenticationProvider proveedor = new DaoAuthenticationProvider();
        proveedor.setPasswordEncoder(contrasenas);
        proveedor.setUserDetailsService(cargaDeUsuarios);
        return new ProviderManager(proveedor);
    }
}
