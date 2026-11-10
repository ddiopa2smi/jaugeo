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
import org.springframework.jdbc.core.simple.JdbcClient;
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
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.access.intercept.AuthorizationFilter;

@Configuration(proxyBeanMethods = false)
@EnableMethodSecurity
@EnableConfigurationProperties(ProprietesSecurite.class)
class SecuriteConfig {

    static final String CLAIM_PERMISSIONS = "permissions";
    private static final String[] REFERENTIEL = {"/api/v1/regions", "/api/v1/stations", "/api/v1/stations/**",
            "/api/v1/produits", "/api/v1/produits/**", "/api/v1/cuves/**", "/api/v1/pompes/**", "/api/v1/pistolets/**"};

    @Bean
    SecurityFilterChain chaineSecurite(HttpSecurity http, JdbcClient jdbc) throws Exception {
        http
                // API sans etat, jeton Bearer : pas de session ni de CSRF.
                // Le cookie de rafraichissement est SameSite=Strict et limite au chemin /api/v1/auth.
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .headers(h -> h
                        .contentSecurityPolicy(csp -> csp.policyDirectives(
                                "default-src 'self'; img-src 'self' data:; style-src 'self' 'unsafe-inline'; script-src 'self' 'unsafe-inline'; frame-ancestors 'none'"))
                        // Aucune adresse de l'application transmise aux sites tiers ; HSTS d'un an sur HTTPS
                        .referrerPolicy(r -> r.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER))
                        .httpStrictTransportSecurity(hsts -> hsts.includeSubDomains(true).maxAgeInSeconds(31_536_000))
                        .permissionsPolicyHeader(p -> p.policy("geolocation=(), microphone=(), camera=(), payment=()")))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/connexion", "/api/v1/auth/rafraichir",
                                "/api/v1/auth/deconnexion").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info").permitAll()
                        .requestMatchers("/v3/api-docs", "/v3/api-docs/**", "/swagger-ui.html", "/swagger-ui/**").permitAll()
                        // Controle des droits des l'entree (avant lecture du corps), double des @PreAuthorize
                        .requestMatchers("/api/v1/utilisateurs", "/api/v1/utilisateurs/**", "/api/v1/roles")
                        .hasAuthority("UTILISATEUR_GERER")
                        .requestMatchers("/api/v1/audit", "/api/v1/audit/**").hasAuthority("AUDIT_CONSULTER")
                        .requestMatchers(HttpMethod.POST, "/api/v1/prix/**").hasAuthority("PRIX_MODIFIER")
                        .requestMatchers(HttpMethod.GET, "/api/v1/prix/**").hasAuthority("REFERENTIEL_CONSULTER")
                        // Journees : droits fins par @PreAuthorize (saisie, cloture, validation)
                        .requestMatchers(HttpMethod.POST, "/api/v1/stations/*/journees").hasAuthority("JOURNEE_SAISIR")
                        .requestMatchers(HttpMethod.GET, "/api/v1/journees/**").hasAuthority("REFERENTIEL_CONSULTER")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/journees/*/releves").hasAuthority("JOURNEE_SAISIR")
                        .requestMatchers(HttpMethod.POST, "/api/v1/journees/*/cloture").hasAuthority("JOURNEE_CLOTURER")
                        .requestMatchers(HttpMethod.POST, "/api/v1/journees/*/validation", "/api/v1/journees/*/renvoi")
                        .hasAuthority("JOURNEE_VALIDER")
                        // Stocks : jaugeages et receptions saisis dans la journee ; complements regles par la logistique
                        .requestMatchers(HttpMethod.PUT, "/api/v1/journees/*/jaugeages").hasAuthority("JOURNEE_SAISIR")
                        .requestMatchers("/api/v1/journees/*/receptions", "/api/v1/journees/*/receptions/*")
                        .hasAuthority("JOURNEE_SAISIR")
                        .requestMatchers(HttpMethod.POST, "/api/v1/stock/complements/*/reglement").hasAuthority("RECEPTION_VALIDER")
                        .requestMatchers(HttpMethod.GET, "/api/v1/stock/**").hasAuthority("REFERENTIEL_CONSULTER")
                        // Depenses : saisies dans la journee, validees au-dela du plafond ; natures gerees par l'administration
                        .requestMatchers(HttpMethod.POST, "/api/v1/journees/*/depenses/*/decision").hasAuthority("DEPENSE_VALIDER")
                        .requestMatchers(HttpMethod.GET, "/api/v1/journees/*/depenses").hasAuthority("REFERENTIEL_CONSULTER")
                        .requestMatchers("/api/v1/journees/*/depenses", "/api/v1/journees/*/depenses/**").hasAuthority("JOURNEE_SAISIR")
                        .requestMatchers(HttpMethod.GET, "/api/v1/depenses/**").hasAuthority("REFERENTIEL_CONSULTER")
                        .requestMatchers("/api/v1/depenses/natures", "/api/v1/depenses/natures/*").hasAuthority("REFERENTIEL_GERER")
                        // Tresorerie : versements du gerant, verification comptable, arretes de caisse
                        .requestMatchers(HttpMethod.POST, "/api/v1/tresorerie/versements/*/verification",
                                "/api/v1/tresorerie/manquants/*/reglement", "/api/v1/tresorerie/manquants/*/paiements")
                        .hasAuthority("VERSEMENT_VALIDER")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/tresorerie/manquants/*/echeancier").hasAuthority("VERSEMENT_VALIDER")
                        .requestMatchers(HttpMethod.GET, "/api/v1/tresorerie/**", "/api/v1/journees/*/tresorerie",
                                "/api/v1/stations/*/caisse", "/api/v1/stations/*/arretes").hasAuthority("REFERENTIEL_CONSULTER")
                        .requestMatchers("/api/v1/tresorerie/versements", "/api/v1/tresorerie/versements/**")
                        .hasAuthority("VERSEMENT_SAISIR")
                        // Rapprochement : releves et lettrage par la comptabilite
                        .requestMatchers("/api/v1/tresorerie/formats-releve", "/api/v1/tresorerie/formats-releve/*",
                                "/api/v1/tresorerie/releves", "/api/v1/tresorerie/rapprochement/**").hasAuthority("VERSEMENT_VALIDER")
                        .requestMatchers(HttpMethod.POST, "/api/v1/stations/*/arretes").hasAuthority("CAISSE_CONTROLER")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/stations/*/plafond-especes").hasAuthority("CAISSE_CONTROLER")
                        .requestMatchers(HttpMethod.GET, "/api/v1/journees/*/encaissements").hasAuthority("REFERENTIEL_CONSULTER")
                        .requestMatchers("/api/v1/journees/*/encaissements").hasAuthority("JOURNEE_SAISIR")
                        // Clients : bons et recouvrements especes saisis dans la journee ; comptes geres par la comptabilite
                        .requestMatchers("/api/v1/journees/*/bons", "/api/v1/journees/*/bons/*", "/api/v1/journees/*/recouvrements",
                                "/api/v1/journees/*/recouvrements/*", "/api/v1/journees/*/cartes", "/api/v1/journees/*/cartes/*")
                        .hasAuthority("JOURNEE_SAISIR")
                        // Derogation de la direction sur un bon en attente (plafond, echeance, engin, contrat)
                        .requestMatchers(HttpMethod.POST, "/api/v1/clients/bons/*/decision").hasAuthority("CREDIT_DEROGER")
                        .requestMatchers(HttpMethod.GET, "/api/v1/clients", "/api/v1/clients/**").hasAuthority("REFERENTIEL_CONSULTER")
                        .requestMatchers("/api/v1/clients", "/api/v1/clients/**").hasAuthority("CLIENT_GERER")
                        // Syntheses mensuelles (feuilles d'analyse) : lecture et exports ; observations du controle de gestion
                        .requestMatchers(HttpMethod.GET, "/api/v1/syntheses/**").hasAuthority("REFERENTIEL_CONSULTER")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/syntheses/commentaires").hasAuthority("CLOTURE_MENSUELLE")
                        .requestMatchers(HttpMethod.GET, "/api/v1/tableau-bord/**").hasAuthority("TABLEAU_BORD_RESEAU")
                        .requestMatchers(HttpMethod.GET, "/api/v1/appro/**").hasAuthority("TABLEAU_BORD_RESEAU")
                        .requestMatchers("/api/v1/appro/factures", "/api/v1/appro/factures/**")
                        .hasAnyAuthority("RECEPTION_VALIDER", "VERSEMENT_VALIDER")
                        .requestMatchers("/api/v1/appro/**").hasAuthority("RECEPTION_VALIDER")
                        // Lubrifiants et boutique : entrees annoncees par la logistique, confirmees et vendues a la station
                        .requestMatchers(HttpMethod.POST, "/api/v1/stations/*/articles/entrees", "/api/v1/articles/entrees/*/annulation")
                        .hasAuthority("RECEPTION_VALIDER")
                        .requestMatchers(HttpMethod.POST, "/api/v1/articles/entrees/*/reception").hasAuthority("JOURNEE_SAISIR")
                        .requestMatchers(HttpMethod.POST, "/api/v1/stations/*/articles/comptages")
                        .hasAnyAuthority("JOURNEE_SAISIR", "JOURNEE_VALIDER")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/journees/*/articles").hasAuthority("JOURNEE_SAISIR")
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
                .addFilterAfter(new FiltreMotDePasseAChanger(), BearerTokenAuthenticationFilter.class)
                // Saisies rejouees par la tablette apres une coupure : jamais executees deux fois
                .addFilterAfter(new FiltreIdempotence(jdbc), AuthorizationFilter.class);
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
