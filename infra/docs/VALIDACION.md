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
