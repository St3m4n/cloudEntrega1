# Despliegue AWS, un servicio por EC2

Estos pasos son operaciones que ejecuta el responsable de la cuenta. En esta revisión se prepararon archivos, código y pruebas; no se creó ni modificó infraestructura AWS ni el tenant Entra.

## Identidad existente

Conservar Tenant ID, SPA Client ID, API Client ID, redirect `/redirect.html` y scope `api://<API_CLIENT_ID>/access_as_user`. `ENTRA_ISSUER_URI` debe ser el issuer **del access token real para la API**; el caso usa `https://login.microsoftonline.com/<TENANT_ID>/v2.0`.

`ENTRA_AUDIENCE` y el parámetro `EntraAudience` de CloudFormation deben coincidir exactamente con `aud`. No asumir que siempre es `api://...`: los tokens v2 de Entra pueden contener el GUID de la API. La URI usada al pedir scope y el claim audience son conceptos distintos. API Gateway compara `iss`, `aud`, firma y expiración; además las rutas exigen `access_as_user`. Ver [JWT Authorizer AWS](https://docs.aws.amazon.com/apigateway/latest/developerguide/http-api-jwt-authorizer.html).

Spring Boot valida el mismo issuer/audience en **cada servicio**. App roles exactos: `Admin`, `Operator`, `Customer`, y opcional `Auditor`. Si el tenant solo asigna `User`, no se transforma en Admin; debe asignarse un rol real a la aplicación API para demostrar autorización. No hay secretos de cliente en el frontend ni se debe poner un client secret en Vite.

## Infraestructura

Preparar una VPC existente con dos subredes privadas en distintas AZ, conectividad saliente (NAT o endpoints necesarios) y resolución DNS. El software necesita acceso a Maven/Docker/ECR, Entra y SMTP. La plantilla no crea VPC/NAT y no tiene ingress SSH; la administración usa AWS Systems Manager.

Revisar `infra/aws/cloudformation.json` antes de ejecutar. Crea ocho EC2 de aplicaciones, una EC2 para RabbitMQ y una para Kafka/Zookeeper/UI; RDS Oracle SE2 License Included, ALB interno, VPC Link, HTTP API y authorizer. Las aplicaciones no se arrancan automáticamente; UserData instala Docker y Compose. El RDS tiene backups, cifrado, protección de borrado y snapshot al eliminar/reemplazar. Comprobar disponibilidad y costo de las clases/Oracle en la región seleccionada. El rol EC2 permite SSM y lectura ECR, no lectura del secreto maestro RDS.

Ejemplo PowerShell (sustituir todos los valores):

```powershell
aws cloudformation deploy --template-file infra/aws/cloudformation.json --stack-name pedidos360 --region YOUR_REGION --capabilities CAPABILITY_IAM --parameter-overrides VpcId=vpc-REPLACE PrivateSubnets=subnet-ONE,subnet-TWO EntraIssuer=https://login.microsoftonline.com/TENANT/v2.0 EntraAudience=EXACT_API_AUD FrontendOrigin=http://localhost:5173
aws cloudformation describe-stacks --stack-name pedidos360 --region YOUR_REGION --query 'Stacks[0].Outputs'
```

La integración usa API Gateway -> VPC Link -> listener ALB privado -> BFF:8080. `overwrite:path=$request.path` evita que se agregue el stage a los paths de los servicios. Ver [integraciones privadas HTTP API](https://docs.aws.amazon.com/apigateway/latest/developerguide/http-api-develop-integrations-private.html).

Security Groups: Gateway/VPC Link puede llamar al ALB; ALB solo entra al BFF. Los servicios hablan entre ellos en 8080-8088; solo las EC2 de apps acceden a RDS:1521, Rabbit:5672-5673/15672-15673 y Kafka:9092-9094. Las EC2 no tienen IP pública. No exponer Zookeeper, Kafka UI ni panel RabbitMQ a Internet.

## RDS Oracle

Usar el endpoint privado de `OracleEndpoint`, puerto 1521, nombre de BD `P360`. Amazon RDS admite Oracle; este cambio de ubicación conserva el motor del caso, aunque elimina OCI de la arquitectura original. Ver [RDS Oracle](https://docs.aws.amazon.com/AmazonRDS/latest/UserGuide/CHAP_Oracle.html).

1. Conectar por red privada con SQL Developer/SQLcl usando el usuario maestro y el secreto indicado en `MasterSecretArn` (obtenido por un administrador autorizado).
2. Completar y ejecutar `infra/db/create-users.sql.example` para crear `P360_CATALOG`, `P360_ORDERS`, `P360_AUDIT`, `P360_REPORT`. No compartir contraseñas entre servicios.
3. Conectar como cada usuario y ejecutar `infra/db/schema-oracle.sql` una vez en cada esquema.
4. Configurar por aplicación `DB_URL=jdbc:oracle:thin:@//ENDPOINT:1521/P360`, su `DB_USER`, `DB_PASSWORD` y `SPRING_PROFILES_ACTIVE=aws`. El modo aws usa `ddl-auto=validate`: si falta la tabla, el arranque falla para evitar modificaciones implícitas.

BFF, notify y administradores no tienen datasource. No darles permisos RDS. H2 solo se usa localmente y en pruebas; no utilizarlo en las EC2 de persistencia. El prototipo H2 anterior no se importa automáticamente al nuevo esquema.

## Construir imágenes

En un equipo con Docker activo y AWS CLI autenticado:

```powershell
.\scripts\publish-images.ps1 -AccountId YOUR_12_DIGIT_ACCOUNT -Region YOUR_REGION -Tag 1.0.0
```

El script crea repositorios `pedidos360/<servicio>` si faltan, construye ocho imágenes y las publica en ECR. Falla ante errores de autenticación/build/push. Nunca se ejecuta al compilar con Maven. No publica el frontend; este continúa local según la arquitectura solicitada.

## Mensajería

En la EC2 de MQ copiar `infra/mq/compose.yml`, `rabbitmq.conf` y una `.env` basada en `.env.example`. Establecer contraseña, cookie aleatoria compartida y `MANAGEMENT_BIND` a la IP privada de esa EC2 (los administradores necesitan llegar al panel HTTP desde su EC2). Ejecutar:

```bash
docker compose --env-file .env up -d
docker compose exec rabbit1 rabbitmqctl cluster_status
```

Confirmar `rabbit@rabbit1` y `rabbit@rabbit2` activos antes de iniciar notify. El healthcheck exige ambos nodos; las colas se declaran desde notify. Quorum queues con dos réplicas requieren ambos nodos; esto cumple el número de nodos de la pauta pero no tolera perder uno. Ver [quorum queues](https://www.rabbitmq.com/docs/quorum-queues).

En la EC2 Kafka copiar `infra/kafka/compose.yml` y `.env`; establecer `KAFKA_HOST` a la IP/DNS privada de **esa** EC2, accesible desde todas las apps. No usar localhost en AWS. Ejecutar:

```bash
docker compose --env-file .env up -d
docker compose logs init-topics
docker compose exec kafka1 kafka-topics --bootstrap-server kafka1:29092 --describe --topic orders.events
docker compose exec kafka1 kafka-topics --bootstrap-server kafka1:29092 --describe --topic audit.timeline
```

Cada tópico debe mostrar tres particiones, replication factor 3 e ISR de tres brokers. Las apps usan la misma IP con puertos 9092, 9093, 9094; los listeners entre contenedores usan `kafka1/2/3:29092`. Hay tres Zookeeper, tres brokers y Kafka UI. Los volúmenes preservan datos al recrear contenedores. Nunca usar `docker compose down -v` si se deben conservar mensajes.

Kafka UI y paneles RabbitMQ se pueden abrir mediante port forwarding de SSM hacia los puertos loopback de la EC2. Para RabbitMQ management ligado a IP privada, usar forwarding a host remoto/IP privada. Ningún panel tiene acceso público en la plantilla.

## Aplicaciones

Para cada EC2, copiar **solo** el Compose de su servicio (`infra/apps/<nombre>/compose.yml`) y una `.env` basada en `infra/apps/.env.example`. Completar endpoints privados con las salidas CloudFormation, issuer/audience, ECR y credenciales. Omitir variables DB en servicios sin persistencia. En notify configurar SMTP real: por ejemplo credenciales SMTP de SES y remitente verificado; no asumir que ya está habilitado. En SES sandbox también se requieren destinatarios verificados.

```bash
chmod 600 .env
aws ecr get-login-password --region YOUR_REGION | docker login --username AWS --password-stdin YOUR_ACCOUNT.dkr.ecr.YOUR_REGION.amazonaws.com
docker compose --env-file .env pull
docker compose --env-file .env up -d
docker compose logs --tail=100
```

Arrancar en orden: brokers e init-topics; catalog; notify; orders; audit/report; administradores; BFF. Si un token válido llega al BFF y un servicio falla, devuelve 503 o el error de dominio recibido. Los mensajes pendientes se reintentan al recuperar el broker. Tras cambiar credenciales/endpoints recrear la aplicación con `docker compose up -d --force-recreate`.

Notify conserva recibos, tickets, PDF y mensajes DLQ en su volumen `/app/data/notify`. No perder ese volumen al actualizar. Es un único consumer por servicio para la entrega; escalarlo a varias EC2 requiere almacenamiento/ledger compartido para la deduplicación.

## Frontend y prueba de extremo a extremo

Mantener React; configurar su `.env.local` con los IDs existentes y `VITE_BFF_BASE_URL=<ApiUrl>` (sin `/api` al final). Reiniciar Vite. Si se cambia el origen del frontend, actualizar CORS tanto en el stack como en `CORS_ORIGINS`, y registrar el callback correspondiente en Entra. El frontend obtiene **accessToken**, lo envía al Gateway y los roles se consultan en `/api/me`.

Para smoke usar tres tokens reales (Admin, Operator, Customer), solicitados para la API y con scope `access_as_user`, en variables de entorno `P360_ADMIN_TOKEN`, `P360_OPERATOR_TOKEN`, `P360_CUSTOMER_TOKEN`. Ejecutar:

```powershell
.\scripts\smoke.ps1 -BaseUrl https://YOUR_API_ID.execute-api.YOUR_REGION.amazonaws.com
```

La prueba crea un producto/pedido de prueba, recorre el flujo, comprueba aceptación repetida sin doble descuento, espera auditoría/reportes y valida tres brokers. Deja evidencias de la entrega. Los correos se envían a `smoke@example.com`; para un destinatario controlado editar el script antes de usar SMTP real. Para demostrar fallos: mensaje malformado hacia `cmd.direct/email.send` pasa a DLQ; un evento inválido en Kafka pasa a DLT tras reintentos. Los comandos reales de cocina y PDF se comprueban en el volumen notify.

Separar las validaciones: Maven/React/Compose/cfn-lint verifican artefactos locales; solo esta prueba con el stack levantado demuestra integración AWS, RDS, tenant y brokers reales. Ninguna de esas pruebas cloud se ha ejecutado aún.
