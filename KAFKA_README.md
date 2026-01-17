# Integración Kafka - EntidadBancaria

## Descripción

Esta implementación delega el procesamiento de creación de entidades bancarias (`create2`) a un broker Kafka, permitiendo procesamiento asíncrono y desacoplado.

## Arquitectura

```
┌─────────────┐      ┌─────────────┐      ┌─────────────┐      ┌──────────┐
│   Cliente   │─────▶│  Controller │─────▶│   Kafka     │─────▶│ Consumer │
│   (REST)    │      │  /create2   │      │   Broker    │      │          │
└─────────────┘      └─────────────┘      └─────────────┘      └────┬─────┘
                            │                                       │
                            ▼                                       ▼
                     ┌─────────────┐                         ┌──────────┐
                     │  Tracking   │◀────────────────────────│    DB    │
                     │  Service    │                         │  (H2)    │
                     └─────────────┘                         └──────────┘
```

## Requisitos

- Docker Desktop instalado y corriendo
- Java 21
- Maven

## Inicio Rápido

### 1. Levantar Kafka con Docker

```powershell
# Opción 1: Usando el script
.\scripts\kafka-start.ps1

# Opción 2: Manualmente
docker-compose up -d
```

### 2. Verificar que Kafka está corriendo

```powershell
docker-compose ps
```

Deberías ver:
- `zookeeper` - healthy
- `kafka` - healthy
- `kafka-ui` - running

### 3. Acceder a Kafka UI

Abre en el navegador: **http://localhost:8090**

### 4. Iniciar la aplicación Spring Boot

```powershell
mvn spring-boot:run
```

## Endpoints

### Crear Entidad (Asíncrono via Kafka)

```bash
POST /api/entidades-bancarias/create2
Header: Idempotency-Key: <UUID>
Content-Type: application/json

{
  "nombre": "Banco Santander",
  "codigoBcra": "SANT001",
  "pais": "Argentina"
}
```

**Respuesta (202 Accepted):**
```json
{
  "message": "Request accepted for processing",
  "messageId": "abc123-...",
  "idempotencyKey": "uuid-...",
  "status": "PENDING",
  "trackingUrl": "/api/entidades-bancarias/kafka/status/abc123-..."
}
```

### Consultar Estado por MessageId

```bash
GET /api/entidades-bancarias/kafka/status/{messageId}
```

**Respuesta:**
```json
{
  "messageId": "abc123-...",
  "idempotencyKey": "uuid-...",
  "status": "COMPLETED",
  "entityId": "uuid-...",
  "entityUrl": "/api/entidades-bancarias/uuid-...",
  "createdAt": "2024-01-15T10:30:00Z"
}
```

### Consultar Estado por Idempotency-Key

```bash
GET /api/entidades-bancarias/kafka/status/by-key/{idempotencyKey}
```

### Listar Todos los Mensajes

```bash
GET /api/entidades-bancarias/kafka/messages
```

### Obtener Estadísticas

```bash
GET /api/entidades-bancarias/kafka/statistics
```

**Respuesta:**
```json
{
  "total": 10,
  "pending": 1,
  "processing": 0,
  "completed": 8,
  "failed": 0,
  "duplicate": 1
}
```

### Limpiar Mensajes Antiguos

```bash
POST /api/entidades-bancarias/kafka/cleanup?maxAgeMinutes=60
```

## Estados de Mensajes

| Estado | Descripción |
|--------|-------------|
| `PENDING` | Mensaje enviado a Kafka, esperando ser procesado |
| `PROCESSING` | Consumer está procesando el mensaje |
| `COMPLETED` | Entidad creada exitosamente en la BD |
| `FAILED` | Error durante el procesamiento |
| `DUPLICATE` | Solicitud duplicada (idempotency-key ya procesado) |

## Topics de Kafka

| Topic | Descripción |
|-------|-------------|
| `entidad-bancaria-create` | Solicitudes de creación de entidades |
| `entidad-bancaria-result` | Resultados del procesamiento |

## Configuración

### application.properties

```properties
# Kafka
spring.kafka.bootstrap-servers=localhost:9092
spring.kafka.consumer.group-id=entidad-bancaria-consumer-group
spring.kafka.consumer.auto-offset-reset=earliest
```

### docker-compose.yml

- **Zookeeper**: Puerto 2181
- **Kafka Broker**: Puerto 9092 (externo), 29092 (interno)
- **Kafka UI**: Puerto 8090

## Comandos Útiles

```powershell
# Ver logs de Kafka
docker-compose logs -f kafka

# Ver logs del consumer
docker-compose logs -f kafka | Select-String "entidad-bancaria"

# Listar topics
docker exec kafka kafka-topics --list --bootstrap-server localhost:9092

# Consumir mensajes de un topic
docker exec kafka kafka-console-consumer --bootstrap-server localhost:9092 --topic entidad-bancaria-create --from-beginning

# Detener todo
docker-compose down

# Detener y eliminar datos
docker-compose down -v
```

## Prueba Completa

```powershell
# 1. Generar UUID para idempotency-key
$uuid = [guid]::NewGuid().ToString()
Write-Host "UUID: $uuid"

# 2. Crear entidad
$headers = @{
    "Idempotency-Key" = $uuid
    "Content-Type" = "application/json"
}
$body = @{
    nombre = "Banco Test"
    codigoBcra = "TEST001"
    pais = "Argentina"
} | ConvertTo-Json

$response = Invoke-RestMethod -Uri "http://localhost:8080/api/entidades-bancarias/create2" -Method POST -Headers $headers -Body $body
$response | ConvertTo-Json

# 3. Consultar estado
$messageId = $response.messageId
Invoke-RestMethod -Uri "http://localhost:8080/api/entidades-bancarias/kafka/status/$messageId" | ConvertTo-Json

# 4. Ver la entidad creada (después de procesamiento)
Invoke-RestMethod -Uri "http://localhost:8080/api/entidades-bancarias" | ConvertTo-Json
```

## Troubleshooting

### Kafka no conecta

```powershell
# Verificar que los contenedores estén corriendo
docker-compose ps

# Reiniciar servicios
docker-compose restart
```

### Mensajes no se procesan

1. Verificar logs del consumer en la aplicación
2. Verificar en Kafka UI (http://localhost:8090) que los mensajes lleguen al topic
3. Verificar que el consumer group esté activo

### Error de serialización

Asegurarse que `spring.kafka.consumer.properties.spring.json.trusted.packages` incluya el paquete de DTOs.
