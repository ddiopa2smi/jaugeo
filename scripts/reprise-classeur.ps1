# Reprise du classeur « SUIVI DES STATIONS » dans Jaugeo (rejoue chaque journee par l'API puis ecrit le rapport).
#
#   .\scripts\reprise-classeur.ps1 -Simulation                       # lecture du classeur seule, rapport des particularites
#   .\scripts\reprise-classeur.ps1 -Du 2026-01-01 -Au 2026-02-28     # reprise de janvier-fevrier sur http://localhost:8080
#   .\scripts\reprise-classeur.ps1 -Stations NIA,HYD -Api http://serveur:8080
#
# L'API doit tourner et la base ne doit contenir aucune journee pour les stations reprises (la reprise commence a la
# premiere journee a ouvrir). Le mot de passe de l'administrateur est demande s'il n'est pas dans JAUGEO_MDP_ADMIN ;
# il n'est jamais passe en argument ni ecrit sur le disque.
# Code de sortie : 0 reprise conforme, 2 ecarts ou arret (voir le rapport), 1 erreur.
param(
    [string]$Classeur = "",
    [string]$Du = "2026-01-01",
    [string]$Au = "2026-02-28",
    [string]$Api = "http://localhost:8080",
    [string[]]$Stations = @(),
    [string]$Admin = "admin",
    [string]$Rapport = "rapport-reprise.md",
    [switch]$Simulation
)

$racine = Split-Path -Parent $PSScriptRoot
if (-not $Classeur) { $Classeur = Join-Path $racine "SUIVI DES STATIONS 2026.xlsx" }
if (-not (Test-Path $Classeur)) { Write-Error "Classeur introuvable : $Classeur"; exit 1 }
$Classeur = (Resolve-Path $Classeur).Path
if (-not [IO.Path]::IsPathRooted($Rapport)) { $Rapport = Join-Path (Get-Location).Path $Rapport }

. (Join-Path $PSScriptRoot "env-dev.ps1") | Out-Null

Push-Location (Join-Path $racine "jaugeo-api")
try {
    # Compilation et chemin des dependances (une seule fois tant que le pom ne change pas)
    mvn -q compile
    if ($LASTEXITCODE -ne 0) { Write-Error "Compilation impossible."; exit 1 }
    $cpFichier = "target\reprise-classpath.txt"
    if (-not (Test-Path $cpFichier) -or (Get-Item $cpFichier).LastWriteTime -lt (Get-Item pom.xml).LastWriteTime) {
        mvn -q dependency:build-classpath "-Dmdep.outputFile=$cpFichier" "-Dmdep.includeScope=runtime"
        if ($LASTEXITCODE -ne 0) { Write-Error "Dependances introuvables."; exit 1 }
    }
    $cp = "target\classes;" + (Get-Content $cpFichier -Raw).Trim()

    $arguments = @("--classeur", $Classeur, "--du", $Du, "--au", $Au, "--api", $Api, "--admin", $Admin, "--rapport", $Rapport)
    if ($Stations.Count -gt 0) { $arguments += @("--stations", ($Stations -join ",")) }
    if ($Simulation) {
        $arguments += "--simulation"
    } elseif (-not $env:JAUGEO_MDP_ADMIN) {
        $secret = Read-Host "Mot de passe de $Admin" -AsSecureString
        $env:JAUGEO_MDP_ADMIN = [Runtime.InteropServices.Marshal]::PtrToStringAuto(
            [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secret))
        $efface = $true
    }
    java "-Dfile.encoding=UTF-8" "-Dstdout.encoding=UTF-8" -cp $cp sn.digitalperf.jaugeo.reprise.RepriseClasseur @arguments
    $code = $LASTEXITCODE
} finally {
    if ($efface) { Remove-Item Env:\JAUGEO_MDP_ADMIN -ErrorAction SilentlyContinue }
    Pop-Location
}
switch ($code) {
    0 { Write-Host ($(if ($Simulation) { "Simulation terminee" } else { "Reprise conforme" }) + ". Rapport : $Rapport") -ForegroundColor Green }
    2 { Write-Host "Reprise terminee avec des ecarts ou un arret : voir $Rapport" -ForegroundColor Yellow }
    default { Write-Host "Reprise en erreur (code $code)." -ForegroundColor Red }
}
exit $code
