package sn.digitalperf.jaugeo.audit.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Lecture du journal d'audit. Acces JDBC direct : la table est partitionnee et en insertion seule,
 * elle n'est pas geree par JPA.
 */
@Tag(name = "Audit")
@RestController
@RequestMapping("/api/v1/audit")
@PreAuthorize("hasAuthority('AUDIT_CONSULTER')")
class AuditController {

    record EntreeAudit(long id, OffsetDateTime horodatage, String schema, String table, String operation,
                       String cle, String utilisateur, String ancienneValeur, String nouvelleValeur) {
    }

    record PageAudit(List<EntreeAudit> contenu, int page, int taille, long total) {
    }

    private final JdbcClient jdbc;

    AuditController(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Operation(summary = "Journal d'audit, du plus récent au plus ancien")
    @GetMapping
    PageAudit journal(@RequestParam(required = false) String table,
                      @RequestParam(required = false) String utilisateur,
                      @RequestParam(defaultValue = "0") int page,
                      @RequestParam(defaultValue = "50") int taille) {
        int p = Math.max(page, 0);
        int t = Math.min(Math.max(taille, 1), 200);
        String filtre = " where (cast(:table as text) is null or table_nom = :table)"
                + " and (cast(:utilisateur as text) is null or utilisateur ilike :utilisateur)";
        String tableFiltre = vide(table);
        String utilisateurFiltre = vide(utilisateur) == null ? null : "%" + utilisateur.trim() + "%";

        long total = jdbc.sql("select count(*) from audit.journal_audit" + filtre)
                .param("table", tableFiltre)
                .param("utilisateur", utilisateurFiltre)
                .query(Long.class).single();

        List<EntreeAudit> contenu = jdbc.sql("""
                        select id, horodatage, schema_nom, table_nom, operation, cle, utilisateur,
                               ancienne_valeur::text as ancienne, nouvelle_valeur::text as nouvelle
                        from audit.journal_audit""" + filtre + " order by horodatage desc, id desc limit :limite offset :decalage")
                .param("table", tableFiltre)
                .param("utilisateur", utilisateurFiltre)
                .param("limite", t)
                .param("decalage", (long) p * t)
                .query((rs, i) -> new EntreeAudit(rs.getLong("id"), rs.getObject("horodatage", OffsetDateTime.class),
                        rs.getString("schema_nom"), rs.getString("table_nom"), rs.getString("operation"),
                        rs.getString("cle"), rs.getString("utilisateur"), rs.getString("ancienne"),
                        rs.getString("nouvelle")))
                .list();
        return new PageAudit(contenu, p, t, total);
    }

    @Operation(summary = "Tables présentes dans le journal (pour le filtre)")
    @GetMapping("/tables")
    List<String> tables() {
        return jdbc.sql("select distinct table_nom from audit.journal_audit order by 1").query(String.class).list();
    }

    private static String vide(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
