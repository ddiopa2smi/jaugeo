package sn.digitalperf.jaugeo.securite.config;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;

@Configuration(proxyBeanMethods = false)
@EnableMethodSecurity
@EnableConfigurationProperties(ProprietesSecurite.class)
class SecuriteConfig {

    static final String CLAIM_PERMISSIONS = "permissions";
    private static final String[] REFERENTIEL = {"/api/v1/regions", "/api/v1/stations", "/api/v1/stations/**",
            "/api/v1/produits", "/api/v1/produits/**", "/api/v1/cuves/**", "/api/v1/pompes/**", "/api/v1/pistolets/**"};

    @Bean
    SecurityFilterChain chaineSecurite(HttpSecurity http) throws Exception {
        http
                // API sans etat, jeton Bearer : pas de session ni de CSRF.
                // Le cookie de rafraichissement est SameSite=Strict et limite au chemin /api/v1/auth.
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .headers(h -> h.contentSecurityPolicy(csp -> csp.policyDirectives(
                        "default-src 'self'; img-src 'self' data:; style-src 'self' 'unsafe-inline'; script-src 'self' 'unsafe-inline'; frame-ancestors 'none'")))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/connexion", "/api/v1/auth/rafraichir",
                                "/api/v1/auth/deconnexion").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info").permitAll()
                        .requestMatchers("/v3/api-docs", "/v3/api-docs/**", "/swagger-ui.html", "/swagger-ui/**").permitAll()
                        // Controle des droits des l'entree (avant lecture du corps), double des @PreAuthorize
                        .requestMatchers("/api/v1/utilisateurs", "/api/v1/utilisateurs/**", "/api/v1/roles")
                        .hasAuthority("UTILISATEUR_GERER")
                        .requestMatchers("/api/v1/audit", "/api/v1/audit/**").hasAuthority("AUDIT_CONSULTER")
                        .requestMatchers(HttpMethod.POST, REFERENTIEL).hasAuthority("REFERENTIEL_GERER")
                        .requestMatchers(HttpMethod.PUT, REFERENTIEL).hasAuthority("REFERENTIEL_GERER")
                        .requestMatchers(HttpMethod.GET, REFERENTIEL).hasAuthority("REFERENTIEL_CONSULTER")
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().denyAll())
                .oauth2ResourceServer(o -> o
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(convertisseurJwt()))
                        .authenticationEntryPoint(nonAuthentifie()))
                .exceptionHandling(e -> e
                        .authenticationEntryPoint(nonAuthentifie())
                        .accessDeniedHandler(accesRefuse()))
                .addFilterAfter(new FiltreMotDePasseAChanger(), BearerTokenAuthenticationFilter.class);
        return http.build();
    }

    private static JwtAuthenticationConverter convertisseurJwt() {
        JwtGrantedAuthoritiesConverter permissions = new JwtGrantedAuthoritiesConverter();
        permissions.setAuthoritiesClaimName(CLAIM_PERMISSIONS);
        permissions.setAuthorityPrefix("");
        JwtAuthenticationConverter convertisseur = new JwtAuthenticationConverter();
        convertisseur.setJwtGrantedAuthoritiesConverter(permissions);
        convertisseur.setPrincipalClaimName("identifiant");
        return convertisseur;
    }

    private static AuthenticationEntryPoint nonAuthentifie() {
        return (requete, reponse, ex) -> ecrireProbleme(reponse, HttpServletResponse.SC_UNAUTHORIZED,
                "NON_AUTHENTIFIE", "Session absente ou expirée. Reconnectez-vous.");
    }

    private static AccessDeniedHandler accesRefuse() {
        return (requete, reponse, ex) -> ecrireProbleme(reponse, HttpServletResponse.SC_FORBIDDEN,
                "ACCES_REFUSE", "Vous n'avez pas le droit d'effectuer cette action.");
    }

    static void ecrireProbleme(HttpServletResponse reponse, int statut, String code, String message)
            throws java.io.IOException {
        reponse.setStatus(statut);
        reponse.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        reponse.setCharacterEncoding(StandardCharsets.UTF_8.name());
        reponse.getWriter().write("{\"status\":" + statut + ",\"code\":\"" + code + "\",\"detail\":\"" + message + "\"}");
    }

    @Bean
    SecretKey cleJwt(ProprietesSecurite proprietes) {
        return new SecretKeySpec(proprietes.jwtSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    @Bean
    JwtEncoder jwtEncoder(SecretKey cleJwt) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(cleJwt));
    }

    @Bean
    JwtDecoder jwtDecoder(SecretKey cleJwt, ProprietesSecurite proprietes) {
        NimbusJwtDecoder decodeur = NimbusJwtDecoder.withSecretKey(cleJwt).macAlgorithm(MacAlgorithm.HS256).build();
        decodeur.setJwtValidator(JwtValidators.createDefaultWithIssuer(proprietes.jwtEmetteur()));
        return decodeur;
    }

    @Bean
    PasswordEncoder encodeurMotDePasse() {
        // Delegation : bcrypt par defaut, algorithme stocke dans le hash ({bcrypt}...) pour pouvoir migrer
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    Clock horloge() {
        return Clock.systemUTC();
    }
}
