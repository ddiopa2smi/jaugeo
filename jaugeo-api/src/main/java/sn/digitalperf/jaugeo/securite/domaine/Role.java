package sn.digitalperf.jaugeo.securite.domaine;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(schema = "securite", name = "role")
public class Role {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String code;

    @Column(nullable = false)
    private String libelle;

    private String description;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(schema = "securite", name = "role_permission", joinColumns = @JoinColumn(name = "role_id"))
    @Column(name = "permission", nullable = false)
    private Set<String> permissions = new HashSet<>();

    protected Role() {
    }

    public Long getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getLibelle() {
        return libelle;
    }

    public String getDescription() {
        return description;
    }

    public Set<String> getPermissions() {
        return Set.copyOf(permissions);
    }
}
