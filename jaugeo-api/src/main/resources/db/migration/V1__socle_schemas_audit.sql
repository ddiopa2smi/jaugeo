-- =====================================================================
-- Jaugeo - V1 : schemas par module metier et journal d'audit
-- =====================================================================

CREATE SCHEMA IF NOT EXISTS referentiel;
CREATE SCHEMA IF NOT EXISTS tarification;
CREATE SCHEMA IF NOT EXISTS exploitation;
CREATE SCHEMA IF NOT EXISTS stock;
CREATE SCHEMA IF NOT EXISTS tresorerie;
CREATE SCHEMA IF NOT EXISTS clients;
CREATE SCHEMA IF NOT EXISTS depenses;
CREATE SCHEMA IF NOT EXISTS approvisionnement;
CREATE SCHEMA IF NOT EXISTS personnel;
CREATE SCHEMA IF NOT EXISTS controle;
CREATE SCHEMA IF NOT EXISTS reporting;
CREATE SCHEMA IF NOT EXISTS alertes;
CREATE SCHEMA IF NOT EXISTS securite;
CREATE SCHEMA IF NOT EXISTS audit;

-- ---------------------------------------------------------------------
-- Journal d'audit : insertion seule, partitionne par mois
-- ---------------------------------------------------------------------
CREATE TABLE audit.journal_audit (
    id               bigint GENERATED ALWAYS AS IDENTITY,
    horodatage       timestamptz NOT NULL DEFAULT now(),
    schema_nom       varchar(63) NOT NULL,
    table_nom        varchar(63) NOT NULL,
    operation        varchar(6)  NOT NULL CHECK (operation IN ('INSERT', 'UPDATE', 'DELETE')),
    cle              varchar(64),
    utilisateur      varchar(100) NOT NULL,
    ancienne_valeur  jsonb,
    nouvelle_valeur  jsonb,
    PRIMARY KEY (id, horodatage)
) PARTITION BY RANGE (horodatage);

-- Partition par defaut : recoit tout ce qui ne tombe dans aucune partition mensuelle
CREATE TABLE audit.journal_audit_defaut PARTITION OF audit.journal_audit DEFAULT;

CREATE INDEX ix_journal_audit_table ON audit.journal_audit (schema_nom, table_nom, horodatage DESC);
CREATE INDEX ix_journal_audit_cle ON audit.journal_audit (table_nom, cle);

-- Cree les partitions mensuelles de l'annee donnee (appelable chaque annee)
CREATE OR REPLACE FUNCTION audit.creer_partitions_annee(p_annee integer)
RETURNS void LANGUAGE plpgsql AS $$
DECLARE
    m integer;
    debut date;
    nom text;
BEGIN
    FOR m IN 1..12 LOOP
        debut := make_date(p_annee, m, 1);
        nom := format('journal_audit_%s_%s', p_annee, lpad(m::text, 2, '0'));
        IF to_regclass('audit.' || nom) IS NULL THEN
            EXECUTE format(
                'CREATE TABLE audit.%I PARTITION OF audit.journal_audit FOR VALUES FROM (%L) TO (%L)',
                nom, debut, (debut + interval '1 month')::date);
        END IF;
    END LOOP;
END;
$$;

SELECT audit.creer_partitions_annee(2026);
SELECT audit.creer_partitions_annee(2027);

-- Le journal est infalsifiable : ni modification ni suppression
CREATE OR REPLACE FUNCTION audit.interdire_modification()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'Le journal d''audit est en insertion seule (operation % refusee)', TG_OP;
END;
$$;

CREATE TRIGGER tg_journal_audit_lecture_seule
    BEFORE UPDATE OR DELETE ON audit.journal_audit
    FOR EACH ROW EXECUTE FUNCTION audit.interdire_modification();

-- ---------------------------------------------------------------------
-- Declencheur generique : trace ancienne et nouvelle valeur.
-- L'utilisateur est pris, dans l'ordre : variable de session app.utilisateur,
-- colonne modifie_par de la ligne, utilisateur PostgreSQL.
-- ---------------------------------------------------------------------
CREATE OR REPLACE FUNCTION audit.journaliser()
RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE
    -- Les secrets ne sont jamais recopies dans le journal
    v_ancien  jsonb := CASE WHEN TG_OP IN ('UPDATE', 'DELETE') THEN to_jsonb(OLD) - 'mot_de_passe_hash' END;
    v_nouveau jsonb := CASE WHEN TG_OP IN ('INSERT', 'UPDATE') THEN to_jsonb(NEW) - 'mot_de_passe_hash' END;
    v_ligne   jsonb := coalesce(v_nouveau, v_ancien);
    -- Colonnes techniques de suivi de connexion : leur seule variation n'est pas un evenement d'audit
    v_ignorees text[] := ARRAY['echecs_connexion', 'verrouille_jusqua', 'derniere_connexion'];
BEGIN
    -- Une mise a jour sans changement metier n'est pas journalisee
    IF TG_OP = 'UPDATE' AND (v_ancien - v_ignorees) = (v_nouveau - v_ignorees) THEN
        RETURN NEW;
    END IF;

    INSERT INTO audit.journal_audit (schema_nom, table_nom, operation, cle, utilisateur, ancienne_valeur, nouvelle_valeur)
    VALUES (
        TG_TABLE_SCHEMA,
        TG_TABLE_NAME,
        TG_OP,
        coalesce(v_ligne ->> 'uuid', v_ligne ->> 'id'),
        coalesce(nullif(current_setting('app.utilisateur', true), ''), v_ligne ->> 'modifie_par', current_user),
        v_ancien,
        v_nouveau);

    RETURN coalesce(NEW, OLD);
END;
$$;

-- Pose le declencheur d'audit sur une table
CREATE OR REPLACE FUNCTION audit.activer_audit(p_table regclass)
RETURNS void LANGUAGE plpgsql AS $$
BEGIN
    EXECUTE format(
        'CREATE TRIGGER tg_audit AFTER INSERT OR UPDATE OR DELETE ON %s FOR EACH ROW EXECUTE FUNCTION audit.journaliser()',
        p_table);
END;
$$;
