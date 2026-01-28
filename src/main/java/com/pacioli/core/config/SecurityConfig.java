package com.pacioli.core.config;

import com.pacioli.core.filters.JwtRequestFilter;
import com.pacioli.core.services.CustomUserDetailsService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

@Configuration
public class SecurityConfig {

    private final CustomUserDetailsService userDetailsService;
    private final JwtRequestFilter jwtRequestFilter;

    public SecurityConfig(CustomUserDetailsService userDetailsService, JwtRequestFilter jwtRequestFilter) {
        this.userDetailsService = userDetailsService;
        this.jwtRequestFilter = jwtRequestFilter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource())) // Enable CORS
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(authz -> authz
                        .requestMatchers("/error").authenticated()
                        .requestMatchers("/auth/login", "/auth/register", "/auth/change-password").permitAll()
                        .requestMatchers("/api/analytics/**").hasAuthority("PACIOLI")
                        .requestMatchers("/ws").permitAll() // Allow initial handshake
                        .requestMatchers("/topic/**").authenticated()
                        .anyRequest().authenticated()
                )
                .logout(logout -> logout
                        .logoutUrl("/auth/logout")
                        .logoutSuccessUrl("/auth/login?logout")
                        .permitAll()
                );

        // Add JwtRequestFilter before UsernamePasswordAuthenticationFilter
        http.addFilterBefore(jwtRequestFilter, UsernamePasswordAuthenticationFilter.class);

        // Add the mobile app filter
        http.addFilterBefore(mobileAppFilter(), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        // Allow specific origins for web clients
        configuration.setAllowedOrigins(List.of(
                "http://localhost:5173",
                "https://neurocompta.com",
                "https://pacioli.neurocompta.com",
                "http://146.190.141.243:3000",
                "http://146.190.141.243",
                "http://10.0.2.2:8081",   // Android emulator
                "http://10.0.2.2:8080",   // Android emulator alternate
                "http://localhost:8081"    // iOS simulator
        ));

        configuration.setAllowCredentials(true);
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of(
                "Authorization", "Cache-Control", "Content-Type", "X-Frame-Options",
                "Origin", "Accept", "X-Requested-With", "X-App-Platform", "Platform"
        ));
        configuration.setExposedHeaders(List.of("Authorization", "Content-Disposition"));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public OncePerRequestFilter mobileAppFilter() {
        return new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
                    throws ServletException, IOException {

                // Check for mobile app identifier header
                String appPlatform = request.getHeader("X-App-Platform");

                if (appPlatform != null && (appPlatform.equals("react-native-android") ||
                        appPlatform.equals("react-native-ios"))) {
                    // Mobile app request - set CORS headers directly for mobile app
                    response.setHeader("Access-Control-Allow-Origin", "*");
                    response.setHeader("Access-Control-Allow-Methods", "GET, POST, PUT, PATCH, DELETE, OPTIONS");
                    response.setHeader("Access-Control-Allow-Headers",
                            "Authorization, Cache-Control, Content-Type, X-Frame-Options, Origin, Accept, X-Requested-With, X-App-Platform, Platform");
                    response.setHeader("Access-Control-Expose-Headers", "Authorization, Content-Disposition");

                    // If it's a preflight request, return OK immediately
                    if ("OPTIONS".equals(request.getMethod())) {
                        response.setStatus(HttpServletResponse.SC_OK);
                        return;
                    }
                }

                filterChain.doFilter(request, response);
            }
        };
    }

    @Bean
    public OncePerRequestFilter frameOptionsFilter() {
        return new OncePerRequestFilter() {
            private final List<String> allowedHosts = Arrays.asList(
                    "https://neurocompta.com",
                    "https://www.neurocompta.com",
                    "http://146.190.141.243:3000",
                    "http://localhost:5173/"
            );

            @Override
            protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
                    throws ServletException, IOException {
                String origin = request.getHeader("Origin");
                if (origin != null && allowedHosts.contains(origin)) {
                    response.setHeader("X-Frame-Options", "ALLOW-FROM " + origin);
                } else {
                    response.setHeader("X-Frame-Options", "DENY");
                }
                filterChain.doFilter(request, response);
            }
        };
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
        authProvider.setUserDetailsService(userDetailsService);
        authProvider.setPasswordEncoder(passwordEncoder());
        return authProvider;
    }

    @Bean
    public WebSecurityCustomizer webSecurityCustomizer() {
        return (web) -> web.ignoring().requestMatchers("/css/**", "/js/**", "/images/**");
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authConfig) throws Exception {
        return authConfig.getAuthenticationManager();
    }
}