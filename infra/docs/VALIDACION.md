# Validación local - 7 de octubre de 2026

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

## Limpieza del repositorio - 8 de octubre de 2026

Se eliminaron los tres servicios del prototipo `demo`, el laboratorio del frontend y sus recursos sin uso. React se trasladó a `frontend-pedidos360` y se retiró el endpoint de demostración `/api/data` del BFF.

- TypeScript `tsc -b`, build de Vite y Oxlint: OK desde la nueva ubicación.
- `mvnw.cmd -B -ntp -pl ms-pedidos360-bff -am verify`: BUILD SUCCESS; dos pruebas JWT, sin fallos ni errores.
- No quedan referencias activas a las rutas y servicios eliminados. `git diff --check`: OK.
- Esta revisión no desplegó recursos ni verificó conexiones reales a AWS.

## MVP de tres EC2 y ensayo interrumpido - 8 de octubre de 2026

- Maven `verify` completo: BUILD SUCCESS, 16:29 America/Santiago, duración 2 min 17 s; ocho servicios compilados y 21 pruebas sin fallos.
- Configuración activa adaptada a apps agrupadas, un RabbitMQ y un Zookeeper/broker Kafka, tres particiones y RF=1. Los clústeres quedan para entrega 3.
- cfn-lint: ambas plantillas válidas en us-east-1 (la advertencia W1011 de contraseña NoEcho en variante lab se excluyó explícitamente). Compose local/MQ/Kafka: configuración válida.
- AWS: las tres EC2 llegaron a running y SSM Online. RDS Oracle estaba creating. Se enviaron arranques MQ/Kafka por SSM; no se confirmó su finalización ni su salud.
- El usuario pidió cerrar al terminar la clase. Se cancelaron los comandos de arranque, se solicitó detener las tres EC2 y se autorizó eliminar el stack vacío. CloudFormation confirmó DELETE_IN_PROGRESS.
- No se inicializaron esquemas ni se desplegaron las aplicaciones en este ensayo. Quedan pendientes RDS, ocho servicios en ejecución, JWT real, flujo Gateway, colas/DLQ, Kafka y Mailpit. No afirmar que AWS está listo para la presentación.
- Cierre comprobado: CloudFormation DELETE_COMPLETE. No quedan EC2 Pedidos360 pendientes/activas/detenidas, instancias RDS, balanceadores ni IP elástica asignada. Los NAT del ensayo figuran deleted. Se eliminó `/pedidos360/lab/runtime` de Parameter Store. Se conservan código, configuración local ignorada e imágenes ECR (publicación de los ocho servicios con tag `mvp-20261008` completada).
