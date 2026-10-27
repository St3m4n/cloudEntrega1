# Guía de presentación — Pedidos360, Entrega 2

## Objetivo

Demostrar el funcionamiento de la solución, la integración con AWS y Microsoft Entra ID, y el uso de RabbitMQ y Kafka.

## Preparación

- Tener activo el laboratorio AWS y el stack `pedidos360-lab`.
- Tener React ejecutándose en `http://localhost:5173`.
- Tener usuarios de prueba con los roles `Admin`, `Operator` y `Customer`.
- No mostrar contraseñas, secretos ni tokens JWT completos.
- Mantener abiertas las sesiones de port forwarding mientras se muestran las interfaces privadas.

## 1. API Manager en funcionamiento

En AWS Console:

1. Abrir **API Gateway → APIs**.
2. Seleccionar la API de Pedidos360.
3. Mostrar que está activa, que es una **HTTP API** y que tiene una URL pública.
4. Mostrar el endpoint de la API.

En esta implementación, AWS API Gateway cumple el rol de API Manager.

## 2. Configuración del API Manager

En la API mostrar:

- Rutas `/api` y `/api/{proxy+}`.
- Métodos `GET`, `POST`, `PUT` y `DELETE`.
- Authorizer JWT.
- Integración con VPC Link y ALB interno.
- CORS permitiendo `http://localhost:5173`.

Explicación:

> API Gateway recibe las solicitudes, valida el JWT y las envía mediante el VPC Link hacia el ALB y el BFF.

## 3. Frontend consumiendo el backend

1. Abrir React en `http://localhost:5173`.
2. Iniciar sesión.
3. Abrir DevTools del navegador → **Network**.
4. Entrar a Catálogo o Pedidos.
5. Seleccionar una llamada como:

```text
GET /api/catalog/products → 200 OK
```

La URL debe apuntar a API Gateway y no directamente a una EC2:

```text
https://<api-id>.execute-api.us-east-1.amazonaws.com/api/...
```

Mostrar también la cabecera:

```text
Authorization: Bearer ...
```

## 4. Validación JWT

Sin token, ejecutar en PowerShell:

```powershell
$apiUrl = (Get-Content .\tmp\lab\runtime.json | ConvertFrom-Json).outputs.ApiUrl
curl.exe -i "$apiUrl/api/me"
```

La respuesta esperada es `401 Unauthorized`.

Después iniciar sesión desde React y mostrar en DevTools:

```text
GET /api/me → 200 OK
```

Explicación:

> API Gateway rechaza solicitudes sin JWT o con tokens inválidos y acepta los tokens emitidos por Microsoft Entra ID.

## 5. Tenant y usuarios de Microsoft Entra ID

En Azure Portal:

1. Abrir **Microsoft Entra ID → Información general**.
2. Mostrar el tenant y su identificador.
3. Abrir **Usuarios → Todos los usuarios**.
4. Mostrar los usuarios de prueba.
5. Abrir **Aplicaciones empresariales → api-fullstack → Usuarios y grupos**.
6. Mostrar los roles asignados:

```text
Admin
Operator
Customer
```

## 6. OAuth 2.0/OpenID Connect

1. Iniciar sesión desde React.
2. Mostrar la ventana de Microsoft Entra ID.
3. Mostrar que el navegador vuelve a `/redirect.html`.
4. En DevTools mostrar una solicitud con `Authorization: Bearer`.

El código relevante está en:

- `frontend-pedidos360/src/authConfig.ts`: tenant, client ID, redirect URI y scope `access_as_user`.
- `frontend-pedidos360/src/token.ts`: adquisición silenciosa del access token mediante MSAL.

Explicación:

> React usa MSAL Browser para OAuth 2.0/OpenID Connect. Microsoft Entra ID emite los tokens y el frontend solicita el scope `access_as_user`.

## 7. Backend y frontend integrados

En AWS mostrar:

- CloudFormation `pedidos360-lab` en estado `UPDATE_COMPLETE`.
- Tres instancias EC2 en estado `Running`.
- RDS Oracle en estado `Available`.
- API Gateway activo.
- Contenedores ejecutándose mediante Docker Compose.

El flujo completo es:

```text
React → API Gateway → VPC Link → ALB → BFF → microservicio → RDS/RabbitMQ/Kafka
```

En esta entrega React se ejecuta localmente y el backend está en AWS. Esto coincide con el diagrama del caso, aunque la pauta final menciona frontend cloud.

## 8. RabbitMQ: colas y DLQ

En RabbitMQ Management UI → **Queues and Streams**, mostrar:

| Cola principal | DLQ |
|---|---|
| `q.cmd.email` | `q.cmd.email.dlq` |
| `q.cmd.kitchen` | `q.cmd.kitchen.dlq` |
| `q.cmd.invoice` | `q.cmd.invoice.dlq` |

Explicación:

> Cada cola representa un tipo de trabajo. Si el consumidor rechaza un mensaje, RabbitMQ lo enruta a la DLQ correspondiente para conservarlo y diagnosticarlo.

## 9. Exchanges RabbitMQ

En **Exchanges**, mostrar:

- `cmd.direct` — tipo `direct`.
- `cmd.topic` — tipo `topic`.
- `cmd.dead.dlx` — exchange de mensajes fallidos.

Bindings de `cmd.direct`:

| Routing key | Cola |
|---|---|
| `email.send` | `q.cmd.email` |
| `kitchen.ticket` | `q.cmd.kitchen` |
| `invoice.gen` | `q.cmd.invoice` |

Bindings de `cmd.topic`:

| Patrón | Cola |
|---|---|
| `email.#` | `q.cmd.email` |
| `kitchen.#` | `q.cmd.kitchen` |
| `invoice.#` | `q.cmd.invoice` |

Explicación:

> Un exchange direct exige coincidencia exacta. Un exchange topic permite patrones como `email.#`.

## 10. Prueba de una DLQ RabbitMQ

Desde `cmd.direct`, publicar un mensaje inválido con routing key `email.send`:

```json
{"foo":"fallo-demo"}
```

El consumidor `notify` lo rechazará y RabbitMQ lo enviará a:

```text
q.cmd.email.dlq
```

El consumidor de DLQ puede archivarlo rápidamente, por lo que la cola podría volver a mostrar cero mensajes después de la prueba.

## 11. Docker Compose en la nube

Desde la raíz del proyecto:

```powershell
python scripts/deploy_lab.py command `
  --service mq `
  --command "cd /opt/pedidos360/mq; docker compose --env-file .env ps"
```

La salida debe mostrar `rabbit1` activo y saludable.

También se pueden mostrar logs:

```powershell
python scripts/deploy_lab.py command `
  --service mq `
  --command "cd /opt/pedidos360/mq; docker compose --env-file .env logs --tail=20 rabbit1"
```

Explicación:

> RabbitMQ se ejecuta dentro de Docker Compose en la instancia `ec2-mq`, y el acceso administrativo se realiza mediante Systems Manager.

## 12. Kafka, si el profesor lo solicita

Preparar el túnel hacia Kafka:

```powershell
$kafkaId = (Get-Content .\tmp\lab\runtime.json | ConvertFrom-Json).instances.kafka

aws ssm start-session `
  --target $kafkaId `
  --document-name AWS-StartPortForwardingSession `
  --parameters "portNumber=8090,localPortNumber=8090" `
  --profile pedidos360 `
  --region us-east-1
```

Mantener la terminal abierta y abrir `http://localhost:8090`.

En Kafka UI mostrar:

- Cluster `pedidos360` online.
- Un broker activo.
- `orders.events`.
- `audit.timeline`.
- Los topics DLT.
- Los consumer groups de reportes y auditoría.

Explicación:

> Kafka conserva los eventos del ciclo de vida de los pedidos. Report los consume para generar indicadores y Audit los consume para mantener la trazabilidad.

Para demostrar un DLT Kafka, publicar en `orders.events` un mensaje incompleto:

```json
{"foo":"fallo-demo"}
```

Después de los reintentos, debe aparecer en:

```text
orders.events.ms-pedidos360-report.DLT
```

## 13. Túnel hacia RabbitMQ Management UI

Si se necesita abrir RabbitMQ desde el computador local:

```powershell
$runtime = Get-Content .\tmp\lab\runtime.json | ConvertFrom-Json
$mqId = $runtime.instances.mq
$mqIp = $runtime.outputs.Ec2MqPrivateIp

aws ssm start-session `
  --target $mqId `
  --document-name AWS-StartPortForwardingSessionToRemoteHost `
  --parameters "host=$mqIp,portNumber=15672,localPortNumber=15672" `
  --profile pedidos360 `
  --region us-east-1
```

Mantener la terminal abierta y abrir `http://localhost:15672`.

## 14. Flujo funcional recomendado

1. Admin crea o consulta productos.
2. Customer crea un pedido.
3. Operator acepta, prepara, despacha y entrega el pedido.
4. Mostrar que el estado se actualiza para el cliente.
5. Mostrar eventos en Kafka.
6. Mostrar colas, exchanges y DLQ en RabbitMQ.
7. Mostrar reportes o auditoría con el usuario Admin.

## 15. Preguntas frecuentes

### ¿Por qué hay tres EC2 si existen ocho microservicios?

Los ocho microservicios se ejecutan como contenedores independientes dentro de `ec2-apps`. Las otras dos instancias se reservan para RabbitMQ y Kafka/ZooKeeper. Es la agrupación definida para el MVP.

### ¿Por qué no se usa SQS?

La pauta evalúa RabbitMQ y Kafka. SQS cambiaría los componentes y no permitiría demostrar exchanges, routing keys, ACK/NACK, topics, particiones y DLT.

### ¿Qué ocurre si un consumidor falla?

- RabbitMQ conserva el mensaje en la cola si el consumidor está fuera de servicio.
- Un mensaje rechazado se envía a su DLQ.
- Kafka conserva el evento en el topic.
- Un consumidor Kafka reinicia desde su último offset.
- Si el procesamiento falla después de los reintentos, el evento va al DLT.

### ¿Existen dos nodos RabbitMQ?

En esta rama de Entrega 2 se usa un broker RabbitMQ MVP en `ec2-mq`. La ampliación a dos nodos RabbitMQ y tres brokers Kafka corresponde a la siguiente etapa. No afirmar que el entorno actual tiene un clúster de dos nodos.

### ¿El frontend está desplegado en AWS?

El frontend React se ejecuta localmente y consume el backend publicado en AWS. El backend sí está desplegado en EC2, RDS, ALB, VPC Link y API Gateway.
