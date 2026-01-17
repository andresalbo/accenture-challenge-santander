# Script de prueba para endpoints de Kafka
param(
    [string]$BaseUrl = "http://localhost:8080"
)

Write-Host "============================================" -ForegroundColor Cyan
Write-Host "   Pruebas de Endpoints Kafka" -ForegroundColor Cyan
Write-Host "============================================" -ForegroundColor Cyan

# Generar UUID para idempotency
$uuid = [guid]::NewGuid().ToString()
Write-Host "`n1. UUID generado: $uuid" -ForegroundColor Yellow

# Crear entidad
Write-Host "`n2. Creando entidad via Kafka (POST /create2)..." -ForegroundColor Yellow
$headers = @{
    "Idempotency-Key" = $uuid
    "Content-Type" = "application/json"
}
$body = @{
    nombre = "Banco Kafka Test"
    codigoBcra = "KAFKA" + (Get-Random -Maximum 999)
    pais = "Argentina"
} | ConvertTo-Json

try {
    $createResponse = Invoke-RestMethod -Uri "$BaseUrl/api/entidades-bancarias/create2" -Method POST -Headers $headers -Body $body
    Write-Host "Respuesta:" -ForegroundColor Green
    $createResponse | ConvertTo-Json -Depth 10
    
    $messageId = $createResponse.messageId
    
    # Esperar un poco para que se procese
    Write-Host "`n3. Esperando procesamiento..." -ForegroundColor Yellow
    Start-Sleep -Seconds 2
    
    # Consultar estado por messageId
    Write-Host "`n4. Consultando estado por messageId (GET /kafka/status/$messageId)..." -ForegroundColor Yellow
    $statusResponse = Invoke-RestMethod -Uri "$BaseUrl/api/entidades-bancarias/kafka/status/$messageId"
    Write-Host "Respuesta:" -ForegroundColor Green
    $statusResponse | ConvertTo-Json -Depth 10
    
    # Consultar estado por idempotencyKey
    Write-Host "`n5. Consultando estado por idempotencyKey (GET /kafka/status/by-key/$uuid)..." -ForegroundColor Yellow
    $statusByKeyResponse = Invoke-RestMethod -Uri "$BaseUrl/api/entidades-bancarias/kafka/status/by-key/$uuid"
    Write-Host "Respuesta:" -ForegroundColor Green
    $statusByKeyResponse | ConvertTo-Json -Depth 10
    
    # Obtener estadísticas
    Write-Host "`n6. Obteniendo estadísticas (GET /kafka/statistics)..." -ForegroundColor Yellow
    $statsResponse = Invoke-RestMethod -Uri "$BaseUrl/api/entidades-bancarias/kafka/statistics"
    Write-Host "Respuesta:" -ForegroundColor Green
    $statsResponse | ConvertTo-Json
    
    # Listar todos los mensajes
    Write-Host "`n7. Listando todos los mensajes (GET /kafka/messages)..." -ForegroundColor Yellow
    $messagesResponse = Invoke-RestMethod -Uri "$BaseUrl/api/entidades-bancarias/kafka/messages"
    Write-Host "Total de mensajes: $($messagesResponse.Count)" -ForegroundColor Green
    
    # Si la entidad fue creada, consultar la entidad
    if ($statusResponse.entityId) {
        Write-Host "`n8. Consultando entidad creada (GET /$($statusResponse.entityId))..." -ForegroundColor Yellow
        $entityResponse = Invoke-RestMethod -Uri "$BaseUrl/api/entidades-bancarias/$($statusResponse.entityId)"
        Write-Host "Respuesta:" -ForegroundColor Green
        $entityResponse | ConvertTo-Json
    }
    
    # Probar solicitud duplicada
    Write-Host "`n9. Probando solicitud duplicada (mismo UUID)..." -ForegroundColor Yellow
    try {
        $duplicateResponse = Invoke-RestMethod -Uri "$BaseUrl/api/entidades-bancarias/create2" -Method POST -Headers $headers -Body $body
        Write-Host "Respuesta:" -ForegroundColor Green
        $duplicateResponse | ConvertTo-Json -Depth 10
    } catch {
        Write-Host "Error esperado (duplicado): $($_.Exception.Message)" -ForegroundColor Magenta
    }
    
    Write-Host "`n============================================" -ForegroundColor Cyan
    Write-Host "   Pruebas completadas exitosamente" -ForegroundColor Green
    Write-Host "============================================" -ForegroundColor Cyan
    
} catch {
    Write-Host "`nERROR: $($_.Exception.Message)" -ForegroundColor Red
    Write-Host "Detalles: $($_.ErrorDetails.Message)" -ForegroundColor Red
}

Write-Host "`nAccede a Kafka UI en: http://localhost:8090" -ForegroundColor Gray
Write-Host "Accede a Swagger en: $BaseUrl/swagger-ui.html" -ForegroundColor Gray
