package ru.yandex.practicum.gateway.security;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.userdetails.MapReactiveUserDetailsService;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.NoOpPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.server.SecurityWebFilterChain;

import java.util.ArrayList;
import java.util.List;

@Configuration
@EnableWebFluxSecurity
@EnableConfigurationProperties(GatewaySecurityProperties.class)
public class GatewaySecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return NoOpPasswordEncoder.getInstance();
    }

    @Bean
    public MapReactiveUserDetailsService userDetailsService(GatewaySecurityProperties properties,
                                                           PasswordEncoder passwordEncoder) {
        List<UserDetails> users = new ArrayList<>();
        if (properties.getUsers() != null) {
            for (GatewaySecurityProperties.User userProps : properties.getUsers()) {
                if (userProps == null || userProps.getUsername() == null || userProps.getPassword() == null) {
                    continue;
                }
                List<String> roles = userProps.getRoles() != null ? userProps.getRoles() : List.of();
                String[] roleArray = roles.stream()
                        .map(String::trim)
                        .filter(r -> !r.isEmpty())
                        .toArray(String[]::new);

                UserDetails userDetails = User.withUsername(userProps.getUsername())
                        .password(passwordEncoder.encode(userProps.getPassword()))
                        .roles(roleArray)
                        .build();
                users.add(userDetails);
            }
        }
        if (users.isEmpty()) {
            UserDetails userDetails = User.withUsername("ivan")
                    .password(passwordEncoder.encode("ivan"))
                    .roles("USER")
                    .build();
            users.add(userDetails);
            UserDetails adminDetails = User.withUsername("anna")
                    .password(passwordEncoder.encode("anna"))
                    .roles("USER", "ADMIN")
                    .build();
            users.add(adminDetails);
        }
        return new MapReactiveUserDetailsService(users);
    }

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .httpBasic(Customizer.withDefaults())
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .logout(ServerHttpSecurity.LogoutSpec::disable)
                .cors(Customizer.withDefaults())
                .authorizeExchange(exchange -> exchange
                        .pathMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .pathMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html", "/webjars/**").permitAll()

                        .pathMatchers(HttpMethod.GET, "/api/products/**").permitAll()
                        .pathMatchers(HttpMethod.GET, "/api/categories/**").permitAll()
                        .pathMatchers(HttpMethod.GET, "/api/inventory/**").permitAll()

                        .pathMatchers(HttpMethod.POST, "/api/orders/**").hasRole("USER")
                        .pathMatchers(HttpMethod.GET, "/api/orders/by-email/**").hasRole("USER")
                        .pathMatchers(HttpMethod.GET, "/api/orders/by-email").hasRole("USER")
                        .pathMatchers(HttpMethod.GET, "/api/orders/*").hasRole("USER")
                        .pathMatchers(HttpMethod.GET, "/api/orders").hasRole("ADMIN")

                        .pathMatchers(HttpMethod.POST, "/api/products/**", "/api/categories/**", "/api/inventory/**").hasRole("ADMIN")
                        .pathMatchers(HttpMethod.PUT, "/api/products/**", "/api/categories/**", "/api/inventory/**").hasRole("ADMIN")
                        .pathMatchers(HttpMethod.PATCH, "/api/products/**", "/api/categories/**", "/api/inventory/**").hasRole("ADMIN")
                        .pathMatchers(HttpMethod.DELETE, "/api/products/**", "/api/categories/**", "/api/inventory/**").hasRole("ADMIN")

                        .anyExchange().denyAll()
                )
                .build();
    }
}
