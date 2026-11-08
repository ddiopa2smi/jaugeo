# Demarre l'API (port 8080) et le front (port 5173) dans deux fenetres PowerShell.
$racine = Split-Path -Parent $PSScriptRoot

Start-Process powershell -ArgumentList "-NoExit", "-Command",
    ". '$racine\scripts\env-dev.ps1'; Set-Location '$racine\jaugeo-api'; mvn spring-boot:run"

Start-Process powershell -ArgumentList "-NoExit", "-Command",
    "Set-Location '$racine\jaugeo-web'; if (-not (Test-Path node_modules)) { npm ci }; npm run dev"

Write-Host "API : http://localhost:8080/swagger-ui.html"
Write-Host "Application : http://localhost:5173 (disponible des que l'API a demarre)"
