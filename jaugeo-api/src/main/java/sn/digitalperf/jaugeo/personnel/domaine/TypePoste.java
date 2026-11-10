package sn.digitalperf.jaugeo.personnel.domaine;

/** Postes d'une journee, dans l'ordre ou ils se succedent. */
public enum TypePoste {
    MATIN("Matin"), SOIR("Soir"), NUIT("Nuit");

    private final String libelle;

    TypePoste(String libelle) {
        this.libelle = libelle;
    }

    public String libelle() {
        return libelle;
    }
}
