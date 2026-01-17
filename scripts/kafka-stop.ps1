# Script para detener Kafka
Write-Host "============================================" -ForegroundColor Cyan
Write-Host "   Deteniendo Kafka" -ForegroundColor Cyan
Write-Host "============================================" -ForegroundColor Cyan

docker-compose down

Write-Host "`nServicios detenidos." -ForegroundColor Green

# Preguntar si quiere eliminar volúmenes
$removeVolumes = Read-Host "`n¿Desea eliminar los volúmenes de datos? (s/N)"
if ($removeVolumes -eq "s" -or $removeVolumes -eq "S") {
    docker-compose down -v
    Write-Host "Volúmenes eliminados." -ForegroundColor Yellow
}
