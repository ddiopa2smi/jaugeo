# Demarre l'API (port 8080) et le front (port 5173) dans deux fenetres PowerShell.
# Le front ecoute aussi sur le reseau local (--host) pour tester sur un telephone ou une tablette du meme Wi-Fi ;
# le pare-feu Windows doit autoriser le port 5173 en entree (regle a creer une fois, en administrateur) :
#   New-NetFirewallRule -DisplayName "Jaugeo dev (Vite 5173)" -Direction Inbound -Protocol TCP -LocalPort 5173 -Action Allow -Profile Domain,Private
$racine = Split-Path -Parent $PSScriptRoot

Start-Process powershell -ArgumentList "-NoExit", "-Command",
    ". '$racine\scripts\env-dev.ps1'; Set-Location '$racine\jaugeo-api'; mvn spring-boot:run"

Start-Process powershell -ArgumentList "-NoExit", "-Command",
    "Set-Location '$racine\jaugeo-web'; if (-not (Test-Path node_modules)) { npm ci }; npm run dev -- --host"

Write-Host "API : http://localhost:8080/swagger-ui.html"
Write-Host "Application : http://localhost:5173 (disponible des que l'API a demarre)"

# Adresses a ouvrir depuis un telephone ou une tablette (Wi-Fi ou Ethernet uniquement, sans les cartes virtuelles)
$cartes = Get-NetAdapter -ErrorAction SilentlyContinue |
    Where-Object { $_.Status -eq 'Up' -and $_.InterfaceDescription -notmatch 'Virtual|VirtualBox|VMware|Hyper-V|Loopback|TAP' } |
    Select-Object -ExpandProperty ifIndex
$adresses = Get-NetIPAddress -AddressFamily IPv4 -ErrorAction SilentlyContinue |
    Where-Object { $cartes -contains $_.InterfaceIndex -and $_.IPAddress -notmatch '^(127|169\.254)\.' } |
    Select-Object -ExpandProperty IPAddress
foreach ($ip in $adresses) { Write-Host "Sur le reseau local : http://${ip}:5173" }
