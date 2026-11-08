# Verification de l'API de bout en bout (29 controles) sur une base NEUVE :
# le compte admin doit encore avoir son mot de passe initial, que le script remplace par -NouveauMdp.
# Usage : .\scripts\verifier-api.ps1 [-Base http://localhost:8080] [-AdminMdp Jaugeo@2026] [-NouveauMdp ...]
param([string]$Base = "http://localhost:8080", [string]$AdminMdp = "Jaugeo@2026", [string]$NouveauMdp = "Siege-Jaugeo-2026")
$ErrorActionPreference = "Stop"
$ok = 0; $ko = 0
function Verifier($nom, $cond) { if ($cond) { $script:ok++; Write-Output "[OK] $nom" } else { $script:ko++; Write-Output "[KO] $nom" } }
Add-Type -AssemblyName System.Net.Http
# Une "session" = un CookieContainer (porte le cookie de rafraichissement)
function NouvelleSession { return New-Object System.Net.CookieContainer }
function Appel($methode, $chemin, $corps = $null, $jeton = $null, $session = $null) {
    $gest = New-Object System.Net.Http.HttpClientHandler
    $gest.UseCookies = $true
    $gest.CookieContainer = $(if ($session) { $session } else { New-Object System.Net.CookieContainer })
    $client = New-Object System.Net.Http.HttpClient($gest)
    $req = New-Object System.Net.Http.HttpRequestMessage([System.Net.Http.HttpMethod]::new($methode), "$Base$chemin")
    if ($jeton) { $req.Headers.Authorization = New-Object System.Net.Http.Headers.AuthenticationHeaderValue("Bearer", $jeton) }
    if ($corps -ne $null) {
        $req.Content = New-Object System.Net.Http.StringContent(($corps | ConvertTo-Json -Depth 5), [System.Text.Encoding]::UTF8, "application/json")
    }
    $rep = $client.SendAsync($req).GetAwaiter().GetResult()
    $txt = $rep.Content.ReadAsStringAsync().GetAwaiter().GetResult()
    $client.Dispose()
    $objet = $null
    if ($txt) { try { $objet = $txt | ConvertFrom-Json } catch { $objet = $txt } }
    return @{ statut = [int]$rep.StatusCode; corps = $objet }
}

$r = Appel GET "/actuator/health"
Verifier "Sante de l'API = UP" ($r.corps.status -eq "UP")

$r = Appel GET "/api/v1/stations"
Verifier "Sans jeton : 401 NON_AUTHENTIFIE" ($r.statut -eq 401 -and $r.corps.code -eq "NON_AUTHENTIFIE")

$r = Appel POST "/api/v1/auth/connexion" @{ identifiant = "admin"; motDePasse = "mauvais" }
Verifier "Mauvais mot de passe : 401 IDENTIFIANTS_INVALIDES" ($r.statut -eq 401 -and $r.corps.code -eq "IDENTIFIANTS_INVALIDES")

$sAdmin = NouvelleSession
$r = Appel POST "/api/v1/auth/connexion" @{ identifiant = "admin"; motDePasse = $AdminMdp } $null $sAdmin
Verifier "Connexion admin" ($r.statut -eq 200 -and $r.corps.jetonAcces)
Verifier "Admin doit changer son mot de passe" ($r.corps.utilisateur.doitChangerMotDePasse -eq $true)
$jeton = $r.corps.jetonAcces

$r = Appel GET "/api/v1/stations" $null $jeton
Verifier "Mot de passe provisoire : acces bloque (MOT_DE_PASSE_A_CHANGER)" ($r.statut -eq 403 -and $r.corps.code -eq "MOT_DE_PASSE_A_CHANGER")

$r = Appel POST "/api/v1/auth/mot-de-passe" @{ ancienMotDePasse = $AdminMdp; nouveauMotDePasse = "faible" } $jeton $sAdmin
Verifier "Mot de passe faible refuse (422)" ($r.statut -eq 422 -and $r.corps.code -eq "MOT_DE_PASSE_FAIBLE")

$r = Appel POST "/api/v1/auth/mot-de-passe" @{ ancienMotDePasse = $AdminMdp; nouveauMotDePasse = $NouveauMdp } $jeton $sAdmin
Verifier "Changement de mot de passe" ($r.statut -eq 200 -and $r.corps.utilisateur.doitChangerMotDePasse -eq $false)
$jeton = $r.corps.jetonAcces

$r = Appel GET "/api/v1/stations" $null $jeton
Verifier "6 stations visibles par l'admin" ($r.statut -eq 200 -and $r.corps.Count -eq 6)
$nia = $r.corps | Where-Object { $_.code -eq "NIA" }
$hyd = $r.corps | Where-Object { $_.code -eq "HYD" }

$r = Appel GET "/api/v1/stations/$($nia.id)/equipements" $null $jeton
$nbPistolets = ($r.corps.pompes | ForEach-Object { $_.pistolets.Count } | Measure-Object -Sum).Sum
Verifier "Niandane : 2 cuves, 2 pompes, 4 pistolets" ($r.corps.cuves.Count -eq 2 -and $r.corps.pompes.Count -eq 2 -and $nbPistolets -eq 4)
$g1 = $r.corps.pompes[0].pistolets | Where-Object { $_.code -eq "G1" }
Verifier "Index de mise en service Gasoil 1 = 88916" ([decimal]$g1.indexMiseEnService -eq 88916)

$r = Appel GET "/api/v1/produits" $null $jeton
Verifier "3 produits" ($r.corps.Count -eq 3)

# Rotation du jeton de rafraichissement et detection de rejeu
$ancienCookie = ($sAdmin.GetCookies([Uri]"$Base/api/v1/auth") | Where-Object { $_.Name -eq "jaugeo_rt" }).Value
$r = Appel POST "/api/v1/auth/rafraichir" $null $null $sAdmin
Verifier "Rafraichissement par cookie" ($r.statut -eq 200 -and $r.corps.jetonAcces)
$sRejeu = NouvelleSession
$c = New-Object System.Net.Cookie("jaugeo_rt", $ancienCookie, "/api/v1/auth", ([Uri]$Base).Host)
$sRejeu.Add($c)
$r = Appel POST "/api/v1/auth/rafraichir" $null $null $sRejeu
Verifier "Rejeu d'un ancien jeton refuse (401)" ($r.statut -eq 401)
$r = Appel POST "/api/v1/auth/rafraichir" $null $null $sAdmin
Verifier "Apres rejeu, toutes les sessions sont fermees (401)" ($r.statut -eq 401)

$sAdmin = NouvelleSession
$r = Appel POST "/api/v1/auth/connexion" @{ identifiant = "admin"; motDePasse = $NouveauMdp } $null $sAdmin
$jeton = $r.corps.jetonAcces
Verifier "Reconnexion admin" ($r.statut -eq 200)

# Gerant limite a Niandane
$ident = "gerant.nia." + (Get-Random -Maximum 99999)
$r = Appel POST "/api/v1/utilisateurs" @{ identifiant = $ident; nomComplet = "Gerant Niandane"; role = "GERANT"; perimetre = "STATIONS"; stationIds = @($nia.numero) } $jeton
Verifier "Creation d'un gerant (201, mot de passe provisoire)" ($r.statut -eq 201 -and $r.corps.motDePasseProvisoire)
$mdpProv = $r.corps.motDePasseProvisoire
$r = Appel POST "/api/v1/utilisateurs" @{ identifiant = $ident; nomComplet = "Doublon"; role = "GERANT"; perimetre = "RESEAU" } $jeton
Verifier "Identifiant en double refuse (409)" ($r.statut -eq 409)

$sG = NouvelleSession
$r = Appel POST "/api/v1/auth/connexion" @{ identifiant = $ident; motDePasse = $mdpProv } $null $sG
$r = Appel POST "/api/v1/auth/mot-de-passe" @{ ancienMotDePasse = $mdpProv; nouveauMotDePasse = "Gerant-Nia-2026" } $r.corps.jetonAcces $sG
$jG = $r.corps.jetonAcces
$r = Appel GET "/api/v1/stations" $null $jG
Verifier "Gerant : ne voit que Niandane" ($r.corps.Count -eq 1 -and $r.corps[0].code -eq "NIA")
$r = Appel GET "/api/v1/stations/$($hyd.id)" $null $jG
Verifier "Gerant : Hydrobase hors perimetre (403)" ($r.statut -eq 403)
$r = Appel POST "/api/v1/produits" @{ code = "GAZ6"; libelle = "Gaz 6 kg"; famille = "GAZ"; unite = "UNITE"; actif = $true } $jG
Verifier "Gerant : creation de produit interdite (403)" ($r.statut -eq 403)
$r = Appel GET "/api/v1/utilisateurs" $null $jG
Verifier "Gerant : gestion des utilisateurs interdite (403)" ($r.statut -eq 403)

# Regles metier du referentiel
$r = Appel POST "/api/v1/produits" @{ code = "LUB"; libelle = "Lubrifiant test"; famille = "LUBRIFIANT"; unite = "UNITE"; suiviJaugeage = $true; actif = $true } $jeton
Verifier "Produit jauge hors litre refuse (422)" ($r.statut -eq 422)
$r = Appel PUT "/api/v1/stations/$($nia.id)" @{ nom = "Station Niandane"; type = "ROUTIERE"; regionCode = "SL"; localite = "Niandane"; modeExploitation = "PROPRE"; actif = $true; version = 99 } $jeton
Verifier "Modification sur version perimee refusee (409 VERSION_OBSOLETE)" ($r.statut -eq 409 -and $r.corps.code -eq "VERSION_OBSOLETE")
$r = Appel PUT "/api/v1/stations/$($nia.id)" @{ nom = "Station Niandane"; type = "ROUTIERE"; regionCode = "SL"; localite = "Niandane (Podor)"; modeExploitation = "PROPRE"; actif = $true; version = $nia.version } $jeton
Verifier "Modification de station" ($r.statut -eq 200 -and $r.corps.localite -eq "Niandane (Podor)")

# Verrouillage apres 5 echecs
for ($i = 1; $i -le 5; $i++) { $r = Appel POST "/api/v1/auth/connexion" @{ identifiant = $ident; motDePasse = "Faux-$i-xxxxxx" } }
Verifier "5 echecs : compte verrouille" ($r.corps.code -eq "COMPTE_VERROUILLE")
$r = Appel POST "/api/v1/auth/connexion" @{ identifiant = $ident; motDePasse = "Gerant-Nia-2026" }
Verifier "Compte verrouille meme avec le bon mot de passe" ($r.statut -eq 401 -and $r.corps.code -eq "COMPTE_VERROUILLE")

# Audit
$r = Appel GET "/api/v1/audit?table=station" $null $jeton
$modif = $r.corps.contenu | Where-Object { $_.operation -eq "UPDATE" } | Select-Object -First 1
Verifier "Audit : modification de station tracee avec l'auteur admin" ($modif -and $modif.utilisateur -eq "admin")
$r = Appel GET "/api/v1/audit?table=utilisateur" $null $jeton
$fuite = $r.corps.contenu | Where-Object { $_.nouvelleValeur -match "mot_de_passe_hash" }
Verifier "Audit : aucun hash de mot de passe dans le journal" (-not $fuite)

Write-Output ""
Write-Output "Resultat : $ok OK, $ko KO"
if ($ko -gt 0) { exit 1 }
