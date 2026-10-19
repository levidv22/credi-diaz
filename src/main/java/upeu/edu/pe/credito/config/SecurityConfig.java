package upeu.edu.pe.credito.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import upeu.edu.pe.credito.entity.EstadoUsuario;
import upeu.edu.pe.credito.repository.UsuarioRepository;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    UserDetailsService userDetailsService(
            UsuarioRepository repository) {

        return username -> repository
                .findByDni(username.trim())
                .map(usuario -> User.withUsername(usuario.getDni())
                        .password(usuario.getPassword())
                        .roles(usuario.getRol().name())
                        .disabled(
                                usuario.getEstado() == EstadoUsuario.ELIMINADO_POR_MORA
                                        || usuario.getEstado() == EstadoUsuario.NO_HABIDO
                        )
                        .build()
                )
                .orElseThrow(
                        () -> new UsernameNotFoundException(
                                "DNI o contraseña incorrectos."
                        )
                );
    }

    @Bean
    SecurityFilterChain filterChain(
            HttpSecurity http) throws Exception {

        http
                .authorizeHttpRequests(auth -> auth

                        .requestMatchers(
                                "/login",
                                "/usuarios/register",
                                "/usuarios/registro/**",
                                "/admin/registro",
                                "/admin/registro-inicial",
                                "/css/**",
                                "/js/**",
                                "/images/**",
                                "/error"
                        ).permitAll()

                        .requestMatchers(
                                "/admin/**",
                                "/creditos/pendientes",
                                "/creditos/*/aprobar",
                                "/creditos/*/rechazar",
                                "/creditos/pagos/pendientes",
                                "/creditos/pagos/*/aprobar",
                                "/creditos/pagos/*/rechazar",
                                "/creditos/pagos/*/comprobante"
                        ).hasRole("ADMIN")

                        .requestMatchers(
                                "/creditos/**",
                                "/usuarios/**"
                        ).authenticated()

                        .anyRequest().authenticated()
                )

                .formLogin(form -> form
                        .loginPage("/login")
                        .loginProcessingUrl("/login")

                        // El campo HTML sigue llamándose username,
                        // pero su contenido será el DNI.
                        .usernameParameter("username")
                        .passwordParameter("password")

                        .defaultSuccessUrl("/inicio", true)
                        .failureUrl("/login?error")
                        .permitAll()
                )

                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessUrl("/?logout")
                        .invalidateHttpSession(true)
                        .deleteCookies("JSESSIONID")
                        .permitAll()
                );

        return http.build();
    }
}
