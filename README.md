# Pedidos360

Backend de entrega 2 preparado para tres EC2: aplicaciones, RabbitMQ y Kafka/Zookeeper. Usa RDS Oracle, API Gateway HTTP API y JWT de Microsoft Entra ID. El frontend se conserva en React por decisión del usuario.

La implementación activa está en `backend/` y el frontend React en `frontend-pedidos360/`. Los servicios reemplazados y la pantalla de laboratorio del prototipo se eliminaron; su versión anterior permanece en el historial Git. No hay migración automática de la base H2 anterior.

| Servicio | Puerto | Responsabilidad |
|---|---:|---|
| ms-pedidos360-bff | 8080 | Perfil y proxy con JWT hacia los servicios; entrada del Gateway |
| ms-pedidos360-catalog | 8082 | CRUD de productos, reservas de stock y compensación |
| ms-pedidos360-orders | 8083 | Pedidos, estados, coordinación de stock y outbox persistente |
| ms-pedidos360-audit | 8084 | Consumer Kafka, timeline persistente y consultas de solo lectura |
| ms-pedidos360-report | 8085 | Consumer Kafka, proyecciones, KPIs y productos más vendidos |
| ms-pedidos360-notify | 8086 | Consumer RabbitMQ: email SMTP, tickets JSON y comprobantes PDF |
| ms-pedidos360-rabbit-admin | 8087 | Administración de colas, exchanges y bindings, solo Admin |
| ms-pedidos360-kafka-admin | 8088 | Administración de tópicos, particiones y configuración, solo Admin |

Java 21, Spring Boot 3.5.16, Spring Security, JPA y OpenAPI por servicio. `common` es una biblioteca, no un servicio adicional. Cada aplicación se empaqueta como un JAR independiente.

## Compilar y probar

En PowerShell, desde `backend`:

```powershell
$env:JAVA_HOME='C:\Program Files\Java\jdk-21'
.\mvnw.cmd -B -ntp verify
```

En Linux: `cd backend && ./mvnw -B -ntp verify` (si el archivo no tiene permiso ejecutable, `chmod +x mvnw`). Los JAR quedan en `backend/ms-pedidos360-*/target/`. Las pruebas usan H2 en modo Oracle, un servidor HTTP de prueba para catálogo/JWKS y mocks para SMTP/ACK. No sustituyen una prueba con RDS ni con los brokers reales.

Frontend: `cd frontend-pedidos360`, `npm ci`, `npm run build`. Copiar `.env.example` a `.env.local` y completar los IDs del tenant existente. Se mantiene el callback `/redirect.html`; no se cambió el registro de aplicaciones de Entra.

## Ejecutar localmente

Requiere Docker con el motor Linux activo y suficiente memoria (aproximadamente 10-12 GB libres para todos los componentes).

```powershell
Copy-Item infra/local/.env.example infra/local/.env
# Completar issuer, audience y credenciales locales en infra/local/.env.
docker compose --env-file infra/local/.env -f infra/local/compose.yml up -d --build
```

Este Compose reúne los ocho servicios, un RabbitMQ, un Zookeeper, un broker Kafka, Kafka UI y Mailpit. Kafka tiene tres particiones por tópico y una réplica para este MVP. Las bases locales usan archivos H2 en volúmenes independientes. El frontend corre con `npm run dev`; `VITE_BFF_BASE_URL=http://localhost:8080`. No se cargan pedidos ni productos de ejemplo automáticamente.

- RabbitMQ management: `http://localhost:15672` (usuario/clave de `.env`).
- Kafka UI: `http://localhost:8090`.
- Correos locales (Mailpit): `http://localhost:8025`.
- Salud: `http://localhost:8080/actuator/health`.

Los endpoints OpenAPI `/v3/api-docs` y Swagger `/swagger-ui/index.html` requieren JWT Admin y se consultan directamente por red privada o local. El BFF no publica Swagger ajeno. Para recuperar el esquema con `curl`, enviar `Authorization: Bearer <token-admin>`; se puede importar ese JSON en Postman. En AWS la entrada pública es API Gateway y las EC2 no tienen IP pública.

## Reglas implementadas

- `CREADO -> ACEPTADO -> EN_PREPARACION -> DESPACHADO -> ENTREGADO`. Cancelación permitida hasta preparación; un pedido entregado o despachado no puede cancelarse. `EN_PREPARACIÓN` se acepta como alias de entrada.
- El servidor calcula precios y total desde catálogo. Stock se descuenta únicamente al aceptar y se devuelve al cancelar antes de despacho.
- Reserva y liberación son idempotentes por pedido. Bloqueos de filas y transacciones impiden stock negativo y descuentos dobles.
- Un fallo ambiguo entre orders y catalog deja una intención persistente. Se debe reintentar **la misma transición**; otras transiciones y ediciones quedan bloqueadas hasta confirmar el resultado. No existe una transacción distribuida entre los dos esquemas.
- El cliente ve sus propios pedidos. Admin/Operator ven todos; crear corresponde a Customer/Operator; edición y cambios de estado a Admin/Operator. Auditoría es de solo lectura para Admin/Auditor; reportes solo Admin.
- Los servicios validan firma, issuer, audience y expiración. El BFF reenvía el access token. React ya no convierte usuarios sin roles o con rol `User` en administradores.
- Orders persiste cambios y mensajes en la misma transacción. El outbox reintenta con backoff y exige confirmación de Kafka/RabbitMQ antes de eliminar el mensaje.
- Report usa la versión de cada pedido para ignorar duplicados y eventos antiguos. Audit deduplica por `eventId`.
- RabbitMQ tiene tres colas de trabajo, tres DLQ, exchanges direct/topic y ACK/NACK manual. Notify archiva mensajes DLQ en su volumen y los confirma después del archivo; para mostrar mensajes pendientes en la UI usar `DLQ_LOGGER_ENABLED=false`.

## Preparación para AWS

Ver [guía de despliegue](infra/docs/AWS.md), [contratos HTTP y eventos](infra/docs/CONTRATOS.md) y [matriz del caso](infra/docs/CUMPLIMIENTO.md).

Para preparar el acceso temporal y los datos de la cuenta, ver [requisitos AWS](infra/docs/REQUISITOS_AWS.md). `infra/aws/deployment-inputs.example.json` resume los parámetros no secretos; es una ficha de preparación, no un archivo de parámetros CloudFormation.

Los resultados locales y lo que falta probar en cloud están en [registro de validación](infra/docs/VALIDACION.md).

`infra/aws/cloudformation.json` define tres EC2, RDS Oracle, grupos de seguridad, roles de EC2, ALB privado, VPC Link y API Gateway con authorizer JWT. Requiere una VPC y dos subredes privadas con acceso saliente. La variante `cloudformation-lab.json` reutiliza `LabInstanceProfile` y agrega dos subredes privadas y un NAT dentro de la VPC existente. Las plantillas instalan Docker/Compose; `scripts/deploy_lab.py` despliega las aplicaciones por SSM. Crear el stack genera costos.

`infra/apps/compose.yml` agrupa los ocho microservicios y Mailpit en `ec2-apps`; solo publica el puerto del BFF. `infra/mq/compose.yml` y `infra/kafka/compose.yml` se ejecutan en sus EC2 correspondientes. Los esquemas Oracle se inicializan explícitamente; el perfil `aws` valida el esquema y no lo crea al iniciar. Cada servicio sigue siendo un proceso y una imagen independientes.

## Límites del MVP

RDS aloja cuatro usuarios/esquemas separados; cada servicio accede solo a su esquema. Las entidades JPA guardan documentos de dominio en CLOB y los endpoints filtran en memoria. Es funcional para la entrega; antes de crecer se necesitan tablas de dominio normalizadas, índices por fechas/estado, paginación y políticas de retención.

Las entregas de mensajería son al menos una vez. La deduplicación de notify se guarda en un volumen persistente por comando. SMTP no ofrece una operación atómica con ese recibo: si el proceso cae después de enviar email y antes de escribir el recibo, el reintento puede duplicar el correo. Los tickets son archivos para cocina; no hay integración con una impresora física. Los PDF son comprobantes internos, sin integración tributaria. Se implementa email, no webpush (el caso permite email/push).

RabbitMQ y Kafka tienen una sola instancia de broker para el MVP; los volúmenes conservan datos al reiniciar contenedores, pero no hay alta disponibilidad ante pérdida del host. AMQP, Kafka y HTTP interno operan en red privada restringida por Security Groups. Los clústeres de la pauta final se dejan para entrega 3; Kubernetes no forma parte de esta entrega.

El estado de las pruebas AWS se registra en `infra/docs/VALIDACION.md`. Mailpit recibe los correos de demostración dentro de `ec2-apps`, sin contratar un proveedor SMTP. React sigue siendo una diferencia explícita respecto del requisito Angular del documento.
