# Guía de pruebas de Pedidos360

## Pruebas automatizadas del backend

Requiere Java 21 y acceso a Maven Central para descargar dependencias la primera vez. Desde la raíz, en PowerShell:

```powershell
cd backend
$env:JAVA_HOME='C:\Program Files\Java\jdk-21'
.\mvnw.cmd -B -ntp verify
```

El resultado esperado es `BUILD SUCCESS`. `verify` compila, ejecuta las pruebas y empaqueta los módulos. Los informes quedan en `backend/<modulo>/target/surefire-reports/`; los XML contienen cantidades, fallos y errores.

Las pruebas usan JUnit Jupiter, Spring Boot Test, MockMvc, Mockito y AssertJ. Comprueban JWT y permisos, reservas de stock, estados de pedidos, reintentos del outbox, deduplicación de eventos, reportes y notificaciones. Usan H2 en modo Oracle, JWKS local y dependencias simuladas: no certifican RDS, Entra ni brokers reales. Añadir regresiones en `src/test/java` con clases `*Tests.java`.

## Verificaciones del frontend

Desde `demo/entra-demo/`:

```powershell
npm ci
npm run lint
npm run build
npm audit
```

Lint revisa el código; build comprueba TypeScript y genera `dist/`. No existe una suite de pruebas automatizadas de interfaz. Audit informa vulnerabilidades y devuelve un código distinto de cero cuando las encuentra; no aplicar correcciones de dependencias automáticamente.

## Configuración y flujo local

Desde la raíz, comprobar la configuración sin iniciar contenedores:

```powershell
docker compose --env-file infra/local/.env.example -f infra/local/compose.yml config --quiet
```

Para probar el sistema completo, activar Docker Desktop con motor Linux y disponer de aproximadamente 10–12 GB de memoria libre. Si los archivos locales todavía no existen, copiar `infra/local/.env.example` a `infra/local/.env` y `demo/entra-demo/.env.example` a `demo/entra-demo/.env.local`. Completar tenant, audience, cliente SPA y credenciales locales; no guardar secretos en Git.

```powershell
docker compose --env-file infra/local/.env -f infra/local/compose.yml up -d --build
docker compose --env-file infra/local/.env -f infra/local/compose.yml ps
Invoke-RestMethod http://localhost:8080/actuator/health
```
Si Windows bloquea los puertos 8080 o 8082, definir `BFF_HOST_PORT=18080` y `CATALOG_HOST_PORT=18082` en `infra/local/.env` y volver a ejecutar Compose. Configurar `VITE_BFF_BASE_URL=http://localhost:18080` en el frontend y reiniciar Vite. Consultar salud en `http://localhost:18080/actuator/health` y ejecutar el smoke con `-BaseUrl http://localhost:18080`. Los puertos internos de los servicios se mantienen.

## Comprobaciones sin usuario del tenant

Desde la raíz, con los contenedores en ejecución:

```powershell
.\scripts\check-local.ps1
```

Lee los puertos del archivo local y realiza 26 comprobaciones: salud de ocho servicios (`200`, estado `UP`), rechazo sin token y con bearer malformado (`401`), disponibilidad de Mailpit y Kafka UI (`200`). Devuelve código 1 ante un fallo o timeout. No altera datos ni valida permisos con JWT reales.

Consultar la mensajería con las herramientas de los contenedores:

```powershell
$composeArgs=@('--env-file','infra/local/.env','-f','infra/local/compose.yml')
docker compose @composeArgs exec -T rabbit1 rabbitmqctl cluster_status
docker compose @composeArgs exec -T rabbit1 rabbitmqctl list_queues name type consumers messages messages_ready messages_unacknowledged
docker compose @composeArgs exec -T rabbit1 rabbitmqctl list_exchanges name type durable
docker compose @composeArgs exec -T rabbit1 rabbitmqctl list_bindings source_name destination_name routing_key
docker compose @composeArgs exec -T kafka1 kafka-topics --bootstrap-server kafka1:29092 --describe
docker compose @composeArgs exec -T kafka1 kafka-consumer-groups --bootstrap-server kafka1:29092 --describe --all-groups
```

Esperar ambos nodos RabbitMQ activos sin alarmas, tres colas de trabajo y tres DLQ quorum con consumidores. Los cuatro tópicos de aplicación Kafka deben tener tres particiones, tres réplicas y tres miembros ISR por partición. Audit/report deben tener grupos conectados y particiones asignadas. Si no hay eventos, offsets y lag pueden aparecer como `-`: no afirmar que el procesamiento está validado.

## Flujo con usuarios autenticados

Ejecutar `npm run dev` en el frontend e iniciar sesión con los roles correspondientes. El tenant debe coincidir en frontend y backend; `ENTRA_ISSUER_URI` debe tener formato `https://login.microsoftonline.com/<tenant>/v2.0`. Aplicar cambios del backend con Compose `up -d --no-build` y reiniciar Vite tras editar su entorno.

Configurar access tokens reales en la sesión de PowerShell, sin incluirlos en informes:

```powershell
$env:P360_ADMIN_TOKEN='<access-token-admin>'
$env:P360_OPERATOR_TOKEN='<access-token-operator>'
$env:P360_CUSTOMER_TOKEN='<access-token-customer>'
.\scripts\smoke.ps1
```

El script crea un producto y un pedido, repite la aceptación, verifica stock, entrega, auditoría, ventas y tres brokers Kafka. Deja datos de prueba persistidos. Revisar además correos en `http://localhost:8025`, tickets/PDF de notify y permisos desde la interfaz: un Customer no debe leer pedidos ajenos ni cambiar estados.

## Problemas encontrados al iniciar

- `TLS handshake timeout` al descargar imágenes: reintentar con `docker compose --parallel 1 --env-file infra/local/.env -f infra/local/compose.yml pull --ignore-buildable` y después `up -d --build`. Las capas completadas se reutilizan.
- `pull access denied` para `pedidos360/*`: estas imágenes se construyen desde el repositorio. Usar `--ignore-buildable` en pull; no requieren publicarse en Docker Hub para ejecutarlas localmente.
- Puerto bloqueado/ocupado: comprobar `Get-NetTCPConnection` y `netsh interface ipv4 show excludedportrange protocol=tcp`, y configurar los puertos alternativos descritos arriba.
- `AADSTS900023`: confirmar el ID de directorio directamente en Entra y el issuer del backend; no corregir un GUID por intuición. Llegar a la pantalla de login no demuestra autenticación ni asignación de roles.

## Evidencia y validación en AWS

Registrar comandos, entorno, cantidades y resultados en [VALIDACION.md](VALIDACION.md), distinguiendo verificaciones estáticas de pruebas con servicios reales. En AWS, seguir [AWS.md](AWS.md) y ejecutar el smoke contra el Gateway con `-BaseUrl '<ApiUrl>'`. Validar también Oracle, SMTP, DLQ y recuperación ante fallos de brokers; el smoke no cubre esos escenarios por sí solo.
