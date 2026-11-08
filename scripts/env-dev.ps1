# Prepare la session PowerShell courante pour Jaugeo (a lancer avec un point : . .\scripts\env-dev.ps1)
# Sur ce poste, JAVA_HOME systeme pointe vers un JDK 8 : on force le JDK 17 pour cette session uniquement.

$jdk = $env:JAUGEO_JDK
if (-not $jdk) {
    $jdk = Get-ChildItem "C:\Program Files\Java" -Directory -Filter "jdk-17*" -ErrorAction SilentlyContinue |
        Sort-Object Name -Descending | Select-Object -First 1 -ExpandProperty FullName
}
if (-not $jdk -or -not (Test-Path "$jdk\bin\java.exe")) {
    Write-Error "JDK 17 introuvable. Installez-le ou definissez JAUGEO_JDK (ex. C:\Program Files\Java\jdk-17)."
    return
}
$env:JAVA_HOME = $jdk
$env:Path = "$jdk\bin;" + $env:Path

if (-not (Get-Command mvn -ErrorAction SilentlyContinue) -and (Test-Path "C:\apache-maven-3.9.6\bin")) {
    $env:Path = "C:\apache-maven-3.9.6\bin;" + $env:Path
}

# Base de developpement : PostgreSQL 18 local sur le port 5433 (valeurs par defaut de application.yml).
# Decommentez pour utiliser la base Docker (docker compose up -d) sur le port 5434 :
# $env:JAUGEO_DB_URL = "jdbc:postgresql://localhost:5434/jaugeo"

Write-Host "JAVA_HOME = $env:JAVA_HOME" -ForegroundColor Green
Write-Host "Java : $((Get-Item "$jdk\bin\java.exe").VersionInfo.ProductVersion)"
