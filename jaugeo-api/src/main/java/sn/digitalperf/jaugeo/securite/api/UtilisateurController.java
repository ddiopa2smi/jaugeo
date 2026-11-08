package sn.digitalperf.jaugeo.securite.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import sn.digitalperf.jaugeo.securite.application.UtilisateurService;
import sn.digitalperf.jaugeo.securite.application.UtilisateurService.AvecMotDePasseProvisoire;
import sn.digitalperf.jaugeo.securite.domaine.Role;
import sn.digitalperf.jaugeo.securite.domaine.Utilisateur;
import sn.digitalperf.jaugeo.securite.domaine.Utilisateur.Perimetre;

@Tag(name = "Utilisateurs et rôles")
@RestController
@RequestMapping("/api/v1")
@PreAuthorize("hasAuthority('UTILISATEUR_GERER')")
class UtilisateurController {

    record UtilisateurDto(UUID id, String identifiant, String nomComplet, String email, String telephone,
                          String role, String roleLibelle, Perimetre perimetre, List<Long> stationIds, boolean actif,
                          boolean doitChangerMotDePasse, boolean verrouille, Instant derniereConnexion, Integer version) {
        static UtilisateurDto de(Utilisateur u) {
            return new UtilisateurDto(u.getUuid(), u.getIdentifiant(), u.getNomComplet(), u.getEmail(),
                    u.getTelephone(), u.getRole().getCode(), u.getRole().getLibelle(), u.getPerimetre(),
                    u.getStationIds().stream().sorted().toList(), u.isActif(), u.isDoitChangerMotDePasse(),
                    u.estVerrouille(Instant.now()), u.getDerniereConnexion(), u.getVersion());
        }
    }

    record RoleDto(String code, String libelle, String description, List<String> permissions) {
        static RoleDto de(Role r) {
            return new RoleDto(r.getCode(), r.getLibelle(), r.getDescription(),
                    r.getPermissions().stream().sorted().toList());
        }
    }

    record CreationUtilisateur(
            @NotBlank @Size(min = 3, max = 50)
            @Pattern(regexp = "^[a-zA-Z0-9._-]+$", message = "Lettres, chiffres, point, tiret et souligné uniquement")
            String identifiant,
            @NotBlank @Size(max = 100) String nomComplet,
            @Email @Size(max = 150) String email,
            @Size(max = 30) String telephone,
            @NotBlank String role,
            @NotNull Perimetre perimetre,
            Set<Long> stationIds) {
    }

    record ModificationUtilisateur(
            @NotBlank @Size(max = 100) String nomComplet,
            @Email @Size(max = 150) String email,
            @Size(max = 30) String telephone,
            @NotBlank String role,
            @NotNull Perimetre perimetre,
            Set<Long> stationIds,
            boolean actif,
            @NotNull Integer version) {
    }

    record ReponseMotDePasseProvisoire(UtilisateurDto utilisateur, String motDePasseProvisoire) {
        static ReponseMotDePasseProvisoire de(AvecMotDePasseProvisoire r) {
            return new ReponseMotDePasseProvisoire(UtilisateurDto.de(r.utilisateur()), r.motDePasseProvisoire());
        }
    }

    private final UtilisateurService service;

    UtilisateurController(UtilisateurService service) {
        this.service = service;
    }

    @Operation(summary = "Liste des utilisateurs")
    @GetMapping("/utilisateurs")
    List<UtilisateurDto> lister() {
        return service.lister().stream().map(UtilisateurDto::de).toList();
    }

    @Operation(summary = "Créer un utilisateur (renvoie un mot de passe provisoire, affiché une seule fois)")
    @PostMapping("/utilisateurs")
    @ResponseStatus(HttpStatus.CREATED)
    ReponseMotDePasseProvisoire creer(@Valid @RequestBody CreationUtilisateur c) {
        return ReponseMotDePasseProvisoire.de(service.creer(new UtilisateurService.Creation(c.identifiant(),
                c.nomComplet(), c.email(), c.telephone(), c.role(), c.perimetre(), c.stationIds())));
    }

    @Operation(summary = "Modifier un utilisateur")
    @PutMapping("/utilisateurs/{id}")
    UtilisateurDto modifier(@PathVariable UUID id, @Valid @RequestBody ModificationUtilisateur m,
                            @AuthenticationPrincipal Jwt jwt) {
        return UtilisateurDto.de(service.modifier(id, new UtilisateurService.Modification(m.nomComplet(), m.email(),
                m.telephone(), m.role(), m.perimetre(), m.stationIds(), m.actif(), m.version()),
                jwt.getClaimAsString("identifiant")));
    }

    @Operation(summary = "Réinitialiser le mot de passe (déverrouille le compte, ferme ses sessions)")
    @PostMapping("/utilisateurs/{id}/reinitialiser-mot-de-passe")
    ReponseMotDePasseProvisoire reinitialiser(@PathVariable UUID id) {
        return ReponseMotDePasseProvisoire.de(service.reinitialiserMotDePasse(id));
    }

    @Operation(summary = "Rôles disponibles et leurs permissions")
    @GetMapping("/roles")
    List<RoleDto> roles() {
        return service.roles().stream().map(RoleDto::de).toList();
    }
}
