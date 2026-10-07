# Contratos de Pedidos360

Todas las rutas `/api` exigen access token para la API. Roles exactos del claim `roles`: `Admin`, `Operator`, `Customer`; opcional `Auditor`. El scope delegado es `access_as_user`. No se usan ID tokens para invocar backend.

## Catálogo

`GET /api/catalog/products` y `GET /api/catalog/products/{id}`: Admin/Operator/Customer (Customer necesita elegir productos al crear pedidos).

`POST /api/catalog/products`, `PUT /api/catalog/products/{id}` y `DELETE /api/catalog/products/{id}`: Admin. El borrado es lógico para permitir compensar reservas previas.

```json
{"nombre":"Café","descripcion":"Café del día","categoria":"Bebidas","precio":1500,"stock":20}
```

Internos, desde orders a catalog: `POST /api/catalog/reservations/{orderId}` con `{"items":[{"productId":1,"quantity":2}]}`, `DELETE /api/catalog/reservations/{orderId}`. No se enrutan a través del BFF público.

## Pedidos

- `POST /api/orders`: Customer/Operator.
- `GET /api/orders?status=CREADO&from=2026-10-07T00:00:00Z&to=2026-10-08T00:00:00Z`: Admin/Operator/titular.
- `GET /api/orders/{id}`: Admin/Operator/titular.
- `PUT /api/orders/{id}`: Admin/Operator; solo CREADO, mismo cuerpo que creación.
- `PUT /api/orders/{id}/status`: Admin/Operator, `{"status":"ACEPTADO"}`.
- `DELETE /api/orders/{id}`: Admin/Operator, cancelación lógica según reglas de estado.

```json
{"email":"cliente@example.com","items":[{"productId":1,"quantity":2}]}
```

Respuesta: `id`, `cliente`, `email`, `estado`, `creadoEn`, `version`, `total`, `items` con nombre/precio capturados. Identificadores numéricos dentro del rango exacto de JavaScript. El total no se acepta desde el cliente. Crear devuelve 201, borrar/cancelar devuelve 204. Una transición repetida ya confirmada devuelve 200 sin publicar otro evento. Crear pedidos no tiene deduplicación por petición; no reintentar automáticamente POST después de un timeout.

## Lecturas y perfil

- `GET /api/me`: nombre/usuario/roles, usado por el frontend.
- `GET /api/data`: diagnóstico de sesión del frontend original.
- `GET /api/report/kpis?range=last24h|last7d`: ventas entregadas en el rango, lead time promedio en minutos, pedidos activos actuales, entregados, creados, ventas por hora UTC, estados actuales y lead times individuales.
- `GET /api/report/top-products?range=last24h|last7d`: diez productos más vendidos por unidades, sobre pedidos entregados.
- `GET /api/audit/events?user=OID_O_EMAIL&type=OrderCreated&orderId=ID&from=ISO_INSTANT&to=ISO_INSTANT`: timeline filtrable.

Report/audit reflejan Kafka de manera eventual. No llaman a orders para calcular sus datos. Los rangos de ventas se aplican a la fecha de entrega; el contador de activos corresponde al estado actual global.

## Mensajería

Envelope común:

```json
{"type":"OrderAccepted","eventId":"UUID","timestamp":"ISO_INSTANT","traceId":"UUID","correlationId":"ORDER_ID","actor":"JWT_SUB","origin":"REMOTE_ADDRESS","order":{"id":1,"version":2,"estado":"ACEPTADO","items":[],"total":3000}}
```

Kafka: cada cambio publica en `orders.events` (report) y `audit.timeline` (audit), con key=orderId. Eventos: OrderCreated, OrderUpdated, OrderAccepted, OrderPreparing, OrderDispatched, OrderDelivered, OrderCancelled. Ambos tópicos tienen tres particiones y tres réplicas. Audit consume `audit.timeline` para mantener un historial independiente del agregado de reportes.

Un consumer fallido reintenta tres veces; luego envía a `orders.events.ms-pedidos360-report.DLT` o `audit.timeline.ms-pedidos360-audit.DLT`, preservando headers de error/offset/partición mediante Spring Kafka. Los mensajes siguen retenidos aunque el logger DLT confirme su lectura.

| Routing key | Cola | Tipo | Momento |
|---|---|---|---|
| email.send | q.cmd.email | EmailRequested | Cada cambio de pedido |
| kitchen.ticket | q.cmd.kitchen | KitchenTicketRequested | Aceptación |
| invoice.gen | q.cmd.invoice | InvoiceRequested | Entrega |

Cada cola tiene `.dlq`. `cmd.direct` usa las routing keys exactas; `cmd.topic` usa `email.#`, `kitchen.#`, `invoice.#` para variantes. `cmd.dead.dlx` enruta fallos a las DLQ. No se publica el mismo comando en ambos exchanges, porque eso duplicaría trabajo. Envelope `eventId` identifica un comando y se conserva durante los reintentos del outbox.

## Administración (Admin)

RabbitMQ:

- `GET /api/admin/rabbit/queues|exchanges|bindings`.
- `POST /api/admin/rabbit/queues`: `{"name":"q.demo","deadLetterExchange":"cmd.dead.dlx","deadLetterRoutingKey":"demo"}`.
- `DELETE /api/admin/rabbit/queues/{name}`.
- `POST /api/admin/rabbit/exchanges`: `{"name":"cmd.demo","type":"direct"}` (direct/topic).
- `DELETE /api/admin/rabbit/exchanges/{name}`.
- `POST` / `DELETE /api/admin/rabbit/bindings`: `{"queue":"q.demo","exchange":"cmd.demo","routingKey":"demo"}`.

Kafka:

- `GET /api/admin/kafka/cluster`, `/topics`, `/topics/{name}`, `/topics/{name}/config`.
- `POST /api/admin/kafka/topics`: `{"name":"demo.events","partitions":3,"replicas":3,"config":{"retention.ms":"604800000"}}`.
- `PUT /api/admin/kafka/topics/{name}/partitions`: `{"partitions":6}`; Kafka permite aumentar, no reducir.
- `PUT /api/admin/kafka/topics/{name}/config`: `{"retention.ms":"1209600000"}`.
- `DELETE /api/admin/kafka/topics/{name}`.

No eliminar colas/tópicos de negocio mientras los servicios estén trabajando. Las declaraciones automáticas recrean topología RabbitMQ al reiniciar notify; los tópicos se crean con el servicio Compose `init-topics`.

Errores HTTP usan ProblemDetail: 400 validación, 401 JWT inválido/ausente, 403 rol/scope insuficiente, 404 inexistente o pedido ajeno, 409 transición/stock/concurrencia, 503 dependencia no disponible.
