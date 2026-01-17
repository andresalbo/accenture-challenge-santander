# Script para iniciar Kafka con Docker Compose
Write-Host "============================================" -ForegroundColor Cyan
Write-Host "   Iniciando Kafka con Docker Compose" -ForegroundColor Cyan
Write-Host "============================================" -ForegroundColor Cyan

# Verificar si Docker está corriendo
$dockerRunning = docker info 2>$null
if (-not $dockerRunning) {
    Write-Host "ERROR: Docker no está corriendo. Por favor inicia Docker Desktop." -ForegroundColor Red
    exit 1
}

Write-Host "`nIniciando servicios..." -ForegroundColor Yellow
docker-compose up -d

Write-Host "`nEsperando que los servicios estén listos..." -ForegroundColor Yellow
Start-Sleep -Seconds 10

# Verificar estado de los contenedores
Write-Host "`nEstado de los contenedores:" -ForegroundColor Green
docker-compose ps

Write-Host "`n============================================" -ForegroundColor Cyan
Write-Host "   Servicios disponibles:" -ForegroundColor Cyan
Write-Host "============================================" -ForegroundColor Cyan
Write-Host "  - Kafka Broker:    localhost:9092" -ForegroundColor White
Write-Host "  - Zookeeper:       localhost:2181" -ForegroundColor White
Write-Host "  - Kafka UI:        http://localhost:8090" -ForegroundColor White
Write-Host "  - App Swagger:     http://localhost:8080/swagger-ui.html" -ForegroundColor White
Write-Host "============================================" -ForegroundColor Cyan

Write-Host "`nPara ver los logs: docker-compose logs -f kafka" -ForegroundColor Gray
Write-Host "Para detener: docker-compose down" -ForegroundColor Gray
