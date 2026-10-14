# Registro de validación local

## Ejecución del 14 de octubre de 2026

### Integración local posterior

#### Comprobaciones realizadas sin cuentas del tenant

Se ejecutó `scripts/check-local.ps1` tras aplicar la corrección del issuer: **26 comprobaciones HTTP aprobadas**, código de salida 0. El script solamente consulta endpoints; no crea pedidos, productos ni mensajes.

| Comprobación real | Evidencia y alcance |
|---|---|
| Salud de ocho microservicios | HTTP 200 y estado `UP` en BFF, catalog, orders, audit, report, notify, rabbit-admin y kafka-admin |
| Endpoints protegidos sin token | HTTP 401 en los ocho servicios; se consultaron perfil, productos, pedidos, auditoría, KPIs, OpenAPI de notify y administración de brokers |
| Mismos endpoints con bearer malformado | HTTP 401 en los ocho servicios; no sustituye probar firma, expiración ni roles con JWT reales |
| Mailpit | HTTP 200 en `/api/v1/messages`; disponibilidad de la API, sin enviar correo |
| Kafka UI | HTTP 200 en su página inicial |
| RabbitMQ | Dos nodos activos con RabbitMQ 4.1.8; sin alarmas ni particiones de red según `cluster_status` |
| Colas RabbitMQ | `q.cmd.email`, `q.cmd.kitchen`, `q.cmd.invoice` y sus tres `.dlq`: quorum, un consumidor por cola, cero mensajes pendientes o sin ACK |
| Exchanges/bindings | Exchanges durables `cmd.direct` (direct), `cmd.topic` (topic) y `cmd.dead.dlx` (direct); bindings para email, kitchen e invoice, incluidos sus destinos DLQ |
| Tópicos Kafka | `orders.events`, `audit.timeline` y sus dos DLT: tres particiones, factor de replicación 3, `min.insync.replicas=2`; las doce particiones tienen líder y tres réplicas en ISR |
| Grupos consumidores Kafka | `pedidos360-audit`, `pedidos360-report` y sus grupos `-dlq-log` conectados, con particiones asignadas; log-end-offset 0, sin eventos de negocio; offset/lag sin establecer (`-`) |
| Descubrimiento Entra | Consulta pública de OpenID correcta, issuer coincidente y URL JWKS declarada; no se obtuvo ningún token ni se autenticó un usuario |

Se detectó que el frontend ya tenía un tenant con formato GUID válido mientras que el backend conservaba el identificador anterior y su issuer carecía de `/v2.0`. Se sincronizó `ENTRA_ISSUER_URI` con el tenant del frontend y se recrearon los ocho contenedores, conservando volúmenes. No se registran valores de credenciales en este documento.

Una primera consulta con bearer malformado a orders agotó el tiempo de espera de 10 segundos. Su log mostró un error de descubrimiento del issuer anterior. Tras corregirlo, el script completo con límite de 30 segundos pasó; no se considera validada la autenticación real por este resultado.

El usuario confirmó que llega a la pantalla de inicio de sesión de Microsoft, pero no dispone de una cuenta del tenant para continuar. No se ejecutó `smoke.ps1`, ni se crearon datos de negocio, ni se inyectaron eventos o se detuvieron brokers. Quedan pendientes:

- JWT reales y permisos Admin/Operator/Customer, titularidad de pedidos y proxy autenticado del BFF.
- Creación/entrega/cancelación, stock e idempotencia con los servicios reales; outbox y proyecciones audit/report con mensajes reales.
- Envío SMTP a Mailpit desde notify, tickets y PDF generados por el flujo.
- Duplicados, fallos de brokers, reintentos, DLQ/DLT y recuperación; que las colas/tópicos existan no demuestra esos comportamientos.
- Persistencia tras reinicio y despliegue AWS con Oracle, SMTP de producción y API Gateway.
- Actualización de la dependencia vulnerable `source-map-js`, ya registrada abajo.

#### Arranque y resolución de incidencias

Se completó el arranque de los contenedores tras descargar las imágenes y compilar los ocho servicios. Los puertos del host 8080 (ocupado) y 8082 (reservado/en uso por Windows) impedían iniciar BFF y catálogo. Se hicieron configurables con `BFF_HOST_PORT` y `CATALOG_HOST_PORT`; esta máquina usa 18080 y 18082. La URL del frontend se actualizó a `http://localhost:18080`.

Compose terminó correctamente con `up -d --no-build`. Los tres brokers Kafka y ambos nodos RabbitMQ figuraban saludables; `init-topics` terminó con código 0. Las consultas HTTP a `/actuator/health` de los ocho servicios devolvieron `UP` (BFF 18080, catálogo 18082, restantes 8083–8088). Esto confirma arranque y salud, pero aún no el flujo de negocio con tokens ni las notificaciones.

### Ejecución automatizada inicial, previa al arranque

Rama: `docs/guia-contribucion`. Entorno: Windows/PowerShell, Java 21.0.9, Maven Wrapper 3.9.9, Node 25.7.0 y npm 11.10.1. Procedimiento reproducible: [PRUEBAS.md](PRUEBAS.md).

| Comprobación | Resultado |
|---|---|
| Backend: `mvnw.cmd -B -ntp verify` | `BUILD SUCCESS`; diez módulos del reactor, incluidos los ocho servicios; duración 1 min 34 s |
| Informes Surefire XML | 21 pruebas; 0 fallos, 0 errores, 0 omitidas |
| BFF / catálogo / pedidos / auditoría / reportes / notificaciones | 2 / 5 / 6 / 2 / 3 / 3 pruebas aprobadas |
| Frontend: `npm ci` | Dependencias instaladas desde el lockfile |
| Frontend: `npm run lint` | Sin diagnósticos |
| Frontend: `npm run build` | TypeScript y Vite correctos |
| Compose local: `config --quiet` con `.env.example` | Configuración válida; sin iniciar contenedores |
| Parser PowerShell: `smoke.ps1`, `publish-images.ps1` | Sin errores de sintaxis |
| Docker | Motor Linux disponible: Docker Desktop 4.62.0, Engine 29.2.1 |
| Frontend: `npm audit --json` | Una vulnerabilidad alta en la dependencia transitiva `source-map-js`; comando termina con código 1 |

Audit identifica [GHSA-68fv-2mgg-jv7q](https://github.com/advisories/GHSA-68fv-2mgg-jv7q), para versiones `>=1.0.0 <1.2.2`, e indica que hay una corrección disponible. No se modificaron dependencias ni el lockfile; queda pendiente evaluar la actualización y volver a validar el frontend.

Mockito emite advertencias sobre carga dinámica de agentes. PowerShell marcó su salida stderr como `NativeCommandError` y la sesión devolvió código 1, aunque Maven registró `BUILD SUCCESS`. El resultado de las pruebas se confirmó leyendo los seis XML de Surefire. Los mensajes de broker desconectado y envelope inválido corresponden a escenarios negativos cubiertos por pruebas aprobadas.

No existen aún `infra/local/.env` ni `demo/entra-demo/.env.local`. No se ejecutó el smoke ni se iniciaron contenedores: falta completar la configuración de Entra, credenciales locales y access tokens Admin/Operator/Customer. Que Docker esté disponible y Compose sea válido no demuestra el funcionamiento de los servicios.

Pendientes: flujo con brokers reales y JWT del tenant, correo/tickets/PDF en integración, permisos de interfaz, recuperación/DLQ y validación AWS con Oracle y API Gateway. Las pruebas automatizadas siguen usando H2 y dependencias simuladas. No se volvió a ejecutar cfn-lint en esta sesión.

## Ejecución del 7 de octubre de 2026

Último `mvnw.cmd -B -ntp verify`: **BUILD SUCCESS**, 15:13 America/Santiago, duración 2 min 8 s. Se compilaron y empaquetaron los ocho microservicios con Java 21.0.4 y Spring Boot 3.5.16.

| Pruebas | Cantidad | Resultado |
|---|---:|---|
| JWT reales y proxy BFF: firma, issuer, audience, expiración y rol | 2 | OK |
| Catálogo: JWT/roles, reservas/liberación idempotentes, rollback, validación y concurrencia | 5 | OK |
| Pedidos: estados, titularidad, errores de stock, intención persistida y outbox | 6 | OK |
| Auditoría: deduplicación/filtros y acceso de solo lectura | 2 | OK |
| Reportes: eventos antiguos/duplicados, ventas, lead time y validación | 3 | OK |
| Notificaciones: deduplicación SMTP, NACK para DLQ y PDF legible | 3 | OK |
| **Total** | **21** | **0 fallos / 0 errores / 0 omitidas** |

Además:

- TypeScript `tsc -b` y Vite production build: OK.
- Oxlint sobre `src` y `vite.config.ts`: OK, sin diagnósticos.
- `docker compose config --quiet`: OK para local, MQ, Kafka y los ocho Compose de EC2. Se usaron archivos `.env.example`; no credenciales reales.
- `cfn-lint 1.57.2` sobre `infra/aws/cloudformation.json`, modelo regional `us-east-1`: exit 0, sin diagnósticos. Esto verifica estructura/esquema/reglas estáticas, no la disponibilidad o permisos de una cuenta específica.
- PowerShell parser sobre `publish-images.ps1` y `smoke.ps1`: sin errores de sintaxis. Los scripts no se ejecutaron contra AWS.

El motor Docker Desktop Linux no estaba activo (`docker version` no pudo conectar al named pipe), por lo que **no se ejecutaron contenedores ni los clústeres**. Las pruebas de stock/datos usan H2 en modo Oracle; las pruebas de brokers usan mocks o invocan consumers directamente. La firma JWT se valida con una clave RSA/JWKS local, no con el tenant del usuario.

Pendiente de validar en despliegue: conexión real RDS Oracle, clúster RabbitMQ y quorum queues, Kafka/Zookeeper/ISR/DLT, envío SMTP, JWT del tenant real y ruta API Gateway -> ALB -> BFF -> servicio. Ver `AWS.md` y `scripts/smoke.ps1`.
