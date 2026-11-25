# Guía de presentación: Pedidos360, Entrega 3 (Kafka)

## Arquitectura Kafka

- **Clúster:** 3 ZooKeeper (ensemble) y 3 brokers en `ec2-kafka`, con un Docker Compose (`infra/kafka/compose.yml`, generado por `scripts/create_infra.py`). Los brokers usan los puertos 9092, 9093 y 9094. Las aplicaciones se conectan a los tres (`KAFKA_BOOTSTRAP_SERVERS`).
- **Tópicos:** todos tienen 3 particiones, factor de replicación 3 y `min.insync.replicas=2`. El productor usa `acks=all`, así que la escritura sigue funcionando si cae un broker.

| Tópico | Produce | Consume (grupo) | Política |
|---|---|---|---|
| `orders.events` | orders (`OrderEventPublisher`) | report (`pedidos360-report`) | delete, 7 días |
| `audit.timeline` | orders (`OrderEventPublisher`) | audit (`pedidos360-audit`) | compact + delete, 30 días |
| `orders.events.ms-pedidos360-report.DLT` | report (`DefaultErrorHandler`) | report (`pedidos360-report-dlt`) | delete, 14 días |
| `audit.timeline.ms-pedidos360-audit.DLT` | audit (`DefaultErrorHandler`) | audit (`pedidos360-audit-dlt`) | delete, 14 días |

El grupo `pedidos360-kafka-monitor` (kafka-admin) lee los cuatro tópicos para mostrarlos en el front sin interferir con los consumidores de negocio.

```text
React ─► API Gateway (JWT) ─► BFF ─► orders ── outbox ──► OrderEventPublisher (JsonSerializer, clave = id pedido)
                                                            │
                                    ┌───────────────────────┴────────────────────────┐
                               orders.events                                    audit.timeline
                                    │                                                 │
                 @KafkaListener OrderEventListener (3 hilos)      @KafkaListener AuditEventListener (3 hilos)
                                    │                                                 │
                     ProjectionService (Oracle) ─► /api/report       AuditService (Oracle) ─► /api/audit
                                    │ error                                           │ error
                 DefaultErrorHandler ─► orders.events.…report.DLT     DefaultErrorHandler ─► audit.timeline.…audit.DLT
                                    └──────────── KafkaMessageMonitor (@KafkaListener, kafka-admin) ───┘
                                                               │
                                              GET /api/admin/kafka/messages ─► página "Kafka" en React
```

## Rúbrica: qué mostrar y dónde está

| Indicador (peso) | Qué mostrar | Archivos |
|---|---|---|
| Configuración del cliente Kafka (12) | Bootstrap con 3 brokers, serializers JSON, `acks=all`, idempotencia, timeouts, commit manual por registro, `CooperativeStickyAssignor`, `client-id` | `backend/ms-pedidos360-*/src/main/resources/application.properties` (orders, audit, report y kafka-admin) |
| Productores y consumidores con DTO (12) | `OrderEvent`/`OrderSnapshot` con Bean Validation. orders escribe en ambos tópicos; audit y report leen cada uno | `backend/common/.../common/kafka/OrderEvent.java`, `OrderSnapshot.java`, `KafkaTopics.java` |
| Microservicio administrador (6) | Crear, listar, describir, borrar tópicos; particiones; configuración; clúster | `ms-pedidos360-kafka-admin/.../KafkaAdminController.java` |
| Productores con DTO y serializers (14) | `KafkaTemplate<String, OrderEvent>` + `JsonSerializer`. Clave = id de pedido, para que cada pedido vaya a una partición y conserve el orden. Espera el ACK y luego borra del outbox | `ms-pedidos360-orders/.../OrderEventPublisher.java`, `Outbox.java` |
| Consumidores con `@KafkaListener` (14) | Listener separado de la lógica de dominio: el listener adapta y el servicio aplica las reglas | audit: `AuditEventListener` → `AuditService`; report: `OrderEventListener` → `ProjectionService` |
| Manejo de errores (12) | `ErrorHandlingDeserializer` (JSON inválido), `@Valid` sobre el DTO, `DefaultErrorHandler` con backoff 1 s/2 s/4 s, errores de formato y validación sin reintento, DLT con cabeceras y motivo (`x-dlt-reason`), logs con tópico, partición y offset | `backend/common/.../StreamingConfig.java`, `*DeadLetterListener.java` |
| Administración de tópicos (12) | Particiones, replicación, retención y cleanup por flujo, en código y en el compose | `ms-pedidos360-kafka-admin/.../TopicsConfig.java`, `infra/kafka/compose.yml` (init-topics) |
| Consumer groups (12) | Un grupo por servicio (cada uno recibe todos los eventos). Dentro del grupo, 3 consumidores se reparten las 3 particiones. Lag por partición | Propiedades `app.kafka.group-id` y `app.kafka.concurrency`; página Kafka, sección "Consumer groups" |
| Administración de tópicos y grupos (6) | Listar grupos, miembros y lag; mover offsets (earliest o latest); eliminar grupo | `ms-pedidos360-kafka-admin/.../ConsumerGroupsController.java` |

## Demostración en el front

1. Inicia sesión con un usuario Admin y abre **Kafka** en el menú. Muestra: 3 brokers, los tópicos con réplicas `[1,2,3]` e ISR completo, retención y cleanup.
2. Como Customer, crea un pedido. Como Operator, acéptalo y avanza su estado.
3. En **Kafka**, las pestañas `orders.events` y `audit.timeline` muestran los mensajes nuevos con partición, offset, clave (id del pedido) y tipo de evento. Todos los eventos de un pedido quedan en la misma partición.
4. **Auditoría** y **Reportes** muestran los datos que guardaron los `@KafkaListener`.
5. Pulsa **"JSON mal formado → orders.events"**. La pestaña `DLT · orders.events.ms-pedidos360-report.DLT` muestra el mensaje con el motivo `JSON inválido: JsonEOFException…`, el tópico, partición y offset de origen, y el grupo que lo rechazó.
6. Pulsa **"Evento inválido → audit.timeline"**. El DLT de audit muestra `DTO inválido: order.estado must not be blank; order.id must be greater than 0; order.version must not be null`.
7. En **Consumer groups**: `pedidos360-audit` y `pedidos360-report` aparecen en STABLE, con 3 consumidores y lag 0. También aparecen los grupos `-dlt` y el del monitor.

## Cómo se obtienen los datos con Spring Kafka y `@KafkaListener`

1. **Publicación:** `OrdersController` guarda el pedido y el evento en el outbox, dentro de la misma transacción. `Outbox` cada 2 s llama a `OrderEventPublisher`, que convierte el evento a `OrderEvent`, lo valida y hace `kafkaTemplate.send(tópico, idPedido, evento).get()`. El `JsonSerializer` lo convierte a JSON. Si el broker no confirma, el mensaje sigue en el outbox y se reintenta.
2. **Consumo:** el contenedor de Spring Kafka hace `poll()` y entrega cada registro al `ErrorHandlingDeserializer`, que delega en el `JsonDeserializer` con tipo `OrderEvent`. El método `@KafkaListener(topics = KafkaTopics.ORDER_EVENTS, groupId = "pedidos360-report", concurrency = "3")` recibe `@Payload @Valid OrderEvent`, ya convertido y validado (`KafkaListenerConfigurer` registra el validador). El offset se confirma registro por registro (`ack-mode=record`) solo cuando el método termina bien.
3. **Errores:** si el método lanza una excepción, el `DefaultErrorHandler` decide qué hacer:
   - Un error transitorio, como una base de datos caída, se reintenta 3 veces (1 s, 2 s, 4 s).
   - Un error de formato o de validación pasa directo al `DeadLetterPublishingRecoverer`, que publica el mensaje original en `<tópico>.<aplicación>.DLT`. Agrega las cabeceras `kafka_dlt-original-topic/partition/offset/consumer-group`, la excepción y `x-dlt-reason`.
4. **Front:** `KafkaMessageMonitor` (kafka-admin) es otro `@KafkaListener` con grupo propio que lee los 4 tópicos como texto. Implementa `ConsumerSeekAware` para volver al inicio al asignarse las particiones y guarda los últimos 50 mensajes por tópico. React consulta `GET /api/admin/kafka/messages` cada 5 s, pasando por API Gateway y el BFF con el token del Admin.

## Preguntas probables

- **¿Por qué grupos distintos?** Cada servicio necesita todos los eventos. Con el mismo grupo, se repartirían los mensajes entre servicios.
- **¿Para qué la concurrencia 3?** Hay 3 particiones: cada hilo toma una y se procesan en paralelo. Si se agregan instancias, el grupo rebalancea las particiones.
- **¿Qué pasa si cae un broker?** Hay 3 réplicas y `min.insync.replicas=2`, así que con `acks=all` se sigue escribiendo con 2 brokers. Los líderes se reasignan.
- **¿Duplicados?** El productor es idempotente. audit deduplica por `eventId` y report ignora versiones iguales o anteriores del pedido.
- **¿Por qué `compact,delete` en `audit.timeline`?** Kafka conserva el último evento por pedido para reconstrucciones, y el historial completo queda en la tabla de auditoría en Oracle.
- **¿Cómo se reprocesa?** Desde kafka-admin: `POST /api/admin/kafka/consumer-groups/{grupo}/offsets` con `earliest`, con el grupo detenido.

## Kafka UI por túnel SSM (opcional)

Se necesita el Session Manager Plugin de AWS (una sola vez):

```powershell
winget install --id Amazon.SessionManagerPlugin -e
```

Después, en una terminal nueva:

```powershell
$kafkaId = (Get-Content tmp/lab/runtime.json | ConvertFrom-Json).instances.kafka
aws ssm start-session --target $kafkaId --document-name AWS-StartPortForwardingSession --parameters "portNumber=8090,localPortNumber=8090" --profile pedidos360 --region us-east-1
```

Abre http://localhost:8090 mientras esa terminal siga abierta.
