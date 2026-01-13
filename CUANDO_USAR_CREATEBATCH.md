# 🚀 Cuándo y Cómo Usar el Método `createBatch`

## 📌 Casos de Uso Reales

### 1. **Importación Masiva desde CSV** 📄

**Escenario:** Tienes un archivo CSV con 500 bancos que necesitas importar.

```java
@Service
public class BankImportService {
    
    @Autowired
    private ConcurrentEntidadBancariaService concurrentService;
    
    public void importFromCSV(String filePath) {
        // Leer CSV
        List<String[]> csvRows = CSVReader.read(filePath);
        
        // Convertir a EntityRequest
        List<EntityRequest> requests = csvRows.stream()
                .map(row -> {
                    String uuid = UUID.randomUUID().toString();
                    EntidadBancaria entity = new EntidadBancaria();
                    entity.setNombre(row[0]);
                    entity.setCodigoBcra(row[1]);
                    entity.setPais(row[2]);
                    return new EntityRequest(uuid, entity);
                })
                .toList();
        
        // Procesar en paralelo
        CompletableFuture<List<CreationResult>> future = 
                concurrentService.createBatch(requests);
        
        List<CreationResult> results = future.join();
        
        // Reportar
        long created = results.stream()
                .filter(r -> r instanceof Success).count();
        
        System.out.println("Importados: " + created + " de " + results.size());
    }
}
```

**Resultado:**
- ❌ Secuencial: 500 bancos × 100ms = **50 segundos**
- ✅ Batch paralelo: **~500ms** (100x más rápido!)

---

### 2. **Sincronización con API Externa** 🌐

**Escenario:** Sincronizar con una API externa que devuelve múltiples bancos.

```java
@Service
public class ExternalSyncService {
    
    @Autowired
    private RestTemplate restTemplate;
    
    @Autowired
    private ConcurrentEntidadBancariaService concurrentService;
    
    @Scheduled(cron = "0 0 2 * * *") // Todos los días a las 2 AM
    public void syncWithExternalAPI() {
        // Obtener datos de API externa
        ExternalBank[] externalBanks = restTemplate.getForObject(
                "https://api.external.com/banks",
                ExternalBank[].class
        );
        
        // Convertir a EntityRequest
        List<EntityRequest> requests = Arrays.stream(externalBanks)
                .map(eb -> new EntityRequest(
                        eb.getExternalId(), // Usar ID externo como idempotency-key
                        mapToEntidadBancaria(eb)
                ))
                .toList();
        
        // Sincronizar en paralelo
        concurrentService.createBatch(requests).thenAccept(results -> {
            long newBanks = results.stream()
                    .filter(r -> r instanceof Success).count();
            long existing = results.stream()
                    .filter(r -> r instanceof Duplicate).count();
            
            System.out.println("Sync completado:");
            System.out.println("- Nuevos: " + newBanks);
            System.out.println("- Ya existentes: " + existing);
        });
    }
}
```

**Ventaja:** Si llegan 200 bancos de la API, los procesas todos en paralelo en segundos.

---

### 3. **Migración desde Sistema Legacy** 🏛️

**Escenario:** Migrar 10,000 registros de un sistema antiguo.

```java
@Service
public class LegacyMigrationService {
    
    @Autowired
    private LegacyDatabase legacyDB;
    
    @Autowired
    private ConcurrentEntidadBancariaService concurrentService;
    
    public void migrateAllBanks() {
        List<LegacyBank> legacyBanks = legacyDB.getAllBanks(); // 10,000 registros
        
        // Procesar en chunks de 1000 (para no saturar la BD)
        int chunkSize = 1000;
        
        for (int i = 0; i < legacyBanks.size(); i += chunkSize) {
            List<LegacyBank> chunk = legacyBanks.subList(
                    i, 
                    Math.min(i + chunkSize, legacyBanks.size())
            );
            
            List<EntityRequest> requests = chunk.stream()
                    .map(lb -> new EntityRequest(
                            lb.getLegacyId().toString(),
                            convertToEntidadBancaria(lb)
                    ))
                    .toList();
            
            // Procesar chunk en paralelo
            List<CreationResult> results = 
                    concurrentService.createBatch(requests).join();
            
            System.out.println("Chunk procesado: " + 
                    results.stream().filter(r -> r instanceof Success).count() +
                    " registros migrados");
        }
    }
}
```

**Resultado:**
- ❌ Secuencial: 10,000 × 100ms = **16 minutos**
- ✅ Batch en chunks: 10 chunks × 1 segundo = **~10 segundos**

---

### 4. **Endpoint REST para Carga Masiva** 🌍

Ya creamos el `BatchController`, ahora ejemplos de cómo llamarlo:

#### Ejemplo con cURL:

```bash
curl -X POST http://localhost:8080/api/entidades-bancarias/batch \
  -H "Content-Type: application/json" \
  -d '[
    {
      "idempotencyKey": "550e8400-e29b-41d4-a716-446655440001",
      "entity": {
        "nombre": "Banco Santander",
        "codigoBcra": "011",
        "pais": "Argentina"
      }
    },
    {
      "idempotencyKey": "550e8400-e29b-41d4-a716-446655440002",
      "entity": {
        "nombre": "Banco Galicia",
        "codigoBcra": "007",
        "pais": "Argentina"
      }
    },
    {
      "idempotencyKey": "550e8400-e29b-41d4-a716-446655440003",
      "entity": {
        "nombre": "BBVA",
        "codigoBcra": "017",
        "pais": "Argentina"
      }
    }
  ]'
```

#### Respuesta:

```json
{
  "total": 3,
  "created": 3,
  "duplicates": 0,
  "errors": 0,
  "details": [
    {
      "idempotencyKey": "550e8400-e29b-41d4-a716-446655440001",
      "status": "created",
      "data": {
        "id": "550e8400-e29b-41d4-a716-446655440001",
        "nombre": "Banco Santander"
      }
    },
    {
      "idempotencyKey": "550e8400-e29b-41d4-a716-446655440002",
      "status": "created",
      "data": {
        "id": "550e8400-e29b-41d4-a716-446655440002",
        "nombre": "Banco Galicia"
      }
    },
    {
      "idempotencyKey": "550e8400-e29b-41d4-a716-446655440003",
      "status": "created",
      "data": {
        "id": "550e8400-e29b-41d4-a716-446655440003",
        "nombre": "BBVA"
      }
    }
  ]
}
```

---

### 5. **Testing de Concurrencia** 🧪

Usar en tests para verificar que la solución maneja correctamente múltiples peticiones:

```java
@Test
public void testHighConcurrency() {
    // Crear 1000 peticiones con 100 UUIDs únicos (900 duplicados)
    List<EntityRequest> requests = new ArrayList<>();
    
    // 100 UUIDs únicos
    List<String> uniqueIds = IntStream.range(0, 100)
            .mapToObj(i -> UUID.randomUUID().toString())
            .toList();
    
    // Repetir cada UUID 10 veces = 1000 peticiones totales
    for (String id : uniqueIds) {
        for (int i = 0; i < 10; i++) {
            EntidadBancaria entity = new EntidadBancaria();
            entity.setNombre("Banco " + id.substring(0, 8));
            entity.setCodigoBcra(String.valueOf(i));
            entity.setPais("Argentina");
            requests.add(new EntityRequest(id, entity));
        }
    }
    
    // Procesar todas en paralelo
    List<CreationResult> results = 
            concurrentService.createBatch(requests).join();
    
    // Verificar: 100 éxitos, 900 duplicados
    assertEquals(100, results.stream()
            .filter(r -> r instanceof Success).count());
    assertEquals(900, results.stream()
            .filter(r -> r instanceof Duplicate).count());
}
```

---

## 📊 Cuándo NO Usar `createBatch`

### ❌ **NO usar si:**

1. **Creación de una sola entidad**
   ```java
   // MAL - overhead innecesario
   createBatch(List.of(new EntityRequest(uuid, entity)));
   
   // BIEN - usa el método simple
   createWithIdempotency(uuid, entity);
   ```

2. **Necesitas transacción global (todo o nada)**
   ```java
   // createBatch NO soporta rollback global
   // Cada entidad tiene su propia transacción
   
   // Si necesitas atomicidad total, usa otro enfoque
   ```

3. **Tu base de datos no soporta alta concurrencia**
   ```java
   // Si tienes pool de conexiones pequeño (ej: 5 conexiones)
   // y envías 1000 peticiones paralelas = problema
   
   // Solución: usar chunks pequeños o aumentar pool
   ```

4. **Tienes limitaciones estrictas de memoria**
   ```java
   // 10,000 entidades en memoria pueden ser pesadas
   // Procesa en chunks más pequeños
   ```

---

## 🎯 Comparación: Individual vs Batch

### Caso: Crear 100 entidades

#### Opción 1: Individual (100 llamadas al endpoint)
```javascript
// Frontend JavaScript
for (let i = 0; i < 100; i++) {
    await fetch('/api/entidades-bancarias/create3', {
        method: 'POST',
        headers: {
            'Idempotency-Key': uuids[i],
            'Content-Type': 'application/json'
        },
        body: JSON.stringify(entities[i])
    });
}
// Tiempo: ~10 segundos (100 round-trips HTTP)
```

#### Opción 2: Batch (1 llamada)
```javascript
// Frontend JavaScript
const batchRequest = entities.map((entity, i) => ({
    idempotencyKey: uuids[i],
    entity: entity
}));

await fetch('/api/entidades-bancarias/batch', {
    method: 'POST',
    headers: {'Content-Type': 'application/json'},
    body: JSON.stringify(batchRequest)
});
// Tiempo: ~500ms (1 round-trip HTTP, procesamiento paralelo)
```

**Resultado: 20x más rápido** 🚀

---

## 💡 Mejores Prácticas

### 1. **Usar chunks para grandes volúmenes**
```java
public void processLargeDataset(List<EntityRequest> allRequests) {
    int chunkSize = 1000; // Ajustar según tu BD
    
    for (int i = 0; i < allRequests.size(); i += chunkSize) {
        List<EntityRequest> chunk = allRequests.subList(
                i, 
                Math.min(i + chunkSize, allRequests.size())
        );
        
        concurrentService.createBatch(chunk).join();
        
        // Opcional: pausa entre chunks
        Thread.sleep(100);
    }
}
```

### 2. **Manejo de errores por lotes**
```java
List<CreationResult> results = concurrentService.createBatch(requests).join();

// Separar por tipo
List<Success> successes = results.stream()
        .filter(r -> r instanceof Success)
        .map(r -> (Success) r)
        .toList();

List<Error> errors = results.stream()
        .filter(r -> r instanceof Error)
        .map(r -> (Error) r)
        .toList();

// Reintentar solo los errores
if (!errors.isEmpty()) {
    // Lógica de reintento...
}
```

### 3. **Logging y monitoreo**
```java
List<CreationResult> results = concurrentService.createBatch(requests).join();

logger.info("Batch procesado: {} total, {} creados, {} duplicados, {} errores",
        results.size(),
        results.stream().filter(r -> r instanceof Success).count(),
        results.stream().filter(r -> r instanceof Duplicate).count(),
        results.stream().filter(r -> r instanceof Error).count()
);
```

---

## 🎉 Resumen

### Usa `createBatch` cuando:

✅ Necesitas crear **10+ entidades simultáneamente**  
✅ Importas datos desde **archivos (CSV, Excel, JSON)**  
✅ Sincronizas con **APIs externas**  
✅ Migras datos desde **sistemas legacy**  
✅ Realizas **carga inicial** de base de datos  
✅ Quieres **optimizar el tiempo** de respuesta  
✅ Tienes **suficientes recursos** (BD, CPU, memoria)  

### Beneficios:

🚀 **10-100x más rápido** que procesamiento secuencial  
🔒 **Thread-safe** con idempotencia garantizada  
⚡ **Virtual Threads** de Java 21 = escalabilidad masiva  
📊 **Resultados detallados** de cada operación  
🎯 **Production-ready** con manejo de errores robusto  

**¡El método `createBatch` es perfecto para operaciones masivas!** 🎊

