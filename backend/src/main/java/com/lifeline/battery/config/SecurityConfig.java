package com.lifeline.battery.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.WebAuthenticationDetails;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;

import java.security.SecureRandom;
import java.util.Base64;

@Configuration
public class SecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

    /** Devices authenticate with their own bearer token (checked in DeviceService), never with a cookie. */
    @Bean
    @Order(1)
    SecurityFilterChain deviceApi(HttpSecurity http) throws Exception {
        return http
                .securityMatcher("/api/device/**")
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> a
                        .requestMatchers(HttpMethod.POST, "/api/device/register", "/api/device/readings").permitAll()
                        .anyRequest().denyAll())
                .exceptionHandling(e -> e.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .build();
    }

    /** The dashboard uses a session cookie with CSRF protection. */
    @Bean
    @Order(2)
    SecurityFilterChain dashboard(HttpSecurity http) throws Exception {
        return http
                .authorizeHttpRequests(a -> a
                        .requestMatchers("/api/auth/me", "/error").permitAll()
                        .requestMatchers("/api/**", "/ws/**").authenticated()
                        .requestMatchers(HttpMethod.GET, "/**").permitAll()
                        .anyRequest().denyAll())
                .csrf(c -> c.spa())
                .formLogin(f -> f
                        .loginProcessingUrl("/api/auth/login")
                        .successHandler((req, res, auth) -> res.setStatus(HttpStatus.NO_CONTENT.value()))
                        .failureHandler((req, res, ex) -> res.setStatus(ex instanceof LockedException
                                ? HttpStatus.TOO_MANY_REQUESTS.value()
                                : HttpStatus.UNAUTHORIZED.value())))
                .logout(l -> l
                        .logoutUrl("/api/auth/logout")
                        .logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler(HttpStatus.NO_CONTENT)))
                .exceptionHandling(e -> e.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .build();
    }

    /** Checks the password, with a limit on wrong tries from one address. */
    @Bean
    AuthenticationProvider limitedLogin(LifelineProperties properties, AttemptLimiter limiter) {
        PasswordEncoder encoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(dashboardUser(properties, encoder)) {
            @Override
            public Authentication authenticate(Authentication login) {
                String key = "login " + ((WebAuthenticationDetails) login.getDetails()).getRemoteAddress();
                if (!limiter.allow(key)) {
                    throw new LockedException("Too many tries");
                }
                Authentication result = super.authenticate(login);
                limiter.succeeded(key);
                return result;
            }
        };
        provider.setPasswordEncoder(encoder);
        return provider;
    }

    private static UserDetailsService dashboardUser(LifelineProperties properties, PasswordEncoder encoder) {
        String username = properties.dashboard().username();
        String password = properties.dashboard().password();
        if (password == null || password.isBlank()) {
            byte[] random = new byte[12];
            new SecureRandom().nextBytes(random);
            password = Base64.getUrlEncoder().withoutPadding().encodeToString(random);
            log.warn("lifeline.dashboard.password is not set. Dashboard login for this run: {} / {}", username, password);
        }
        return new InMemoryUserDetailsManager(
                User.withUsername(username).password(encoder.encode(password)).roles("DASHBOARD").build());
    }
}
