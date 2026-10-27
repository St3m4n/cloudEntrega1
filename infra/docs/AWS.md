# AWS: entrega 2 con tres EC2

Topología acordada: `ec2-apps` ejecuta los ocho microservicios y Mailpit; `ec2-mq` ejecuta RabbitMQ; `ec2-kafka` ejecuta Zookeeper, un broker Kafka y Kafka UI. Cada VM tiene su propio Docker Compose. Kafka utiliza tres particiones y una réplica para el MVP. Los clústeres se reservan para entrega 3; no se usa Kubernetes.

Entrada pública: React local -> API Gateway HTTP API con JWT -> VPC Link -> ALB interno -> BFF:8080. Los demás puertos de aplicaciones quedan dentro de la red Docker. RDS Oracle tiene cuatro esquemas y solo acepta tráfico de aplicaciones. Security Groups permiten AMQP:5672, Rabbit management:15672 y Kafka:9092 desde aplicaciones. No hay SSH ni paneles públicos.

## Archivos

- `infra/apps/compose.yml`: ocho aplicaciones y Mailpit, límites de memoria y rotación de logs.
- `infra/apps/common.env.example`: configuración común, sin contraseñas de esquemas.
- `infra/apps/.env.example`: repositorio/tag ECR y cuatro contraseñas para interpolación por servicio.
- `infra/mq/compose.yml` y `infra/kafka/compose.yml`: brokers MVP.
- `infra/aws/cloudformation.json`: variante estándar, requiere subredes privadas con acceso saliente; crea rol y profile.
- `infra/aws/cloudformation-lab.json`: variante lab, usa `LabInstanceProfile`, crea subredes privadas/NAT en una VPC existente y permite eliminar RDS sin snapshot. Es una plantilla de ensayo; exportar datos antes de eliminar un entorno con información que deba conservarse.

## Preparación de Learner Lab

Configurar credenciales temporales de AWS CLI en el perfil `pedidos360`, con access key, secret key y session token. No enviarlas por chat ni guardarlas en Git. Confirmar `aws sts get-caller-identity --profile pedidos360 --region us-east-1`.

Copiar `infra/aws/deployment-inputs.example.json` a `tmp/lab/inputs.json`. Completar región, profile, VPC, subred pública con ruta a Internet Gateway, dos AZ y CIDR privados libres, IDs públicos del tenant y audiencia exacta del access token. La variante lab requiere la sección `lab` y los campos de red indicados en el ejemplo.

Python necesita `boto3`; se puede instalar con `python -m pip install --target tmp/awsdeps boto3`. Docker Desktop debe usar contenedores Linux. Desde la raíz:

```powershell
python scripts/create_infra.py
python scripts/create_lab_template.py
python scripts/prepare_lab_parameters.py
$env:AWS_PROFILE='pedidos360'
.\scripts\publish-images.ps1 -AccountId YOUR_ACCOUNT_ID -Region us-east-1 -Tag YOUR_TAG -SkipPublished
aws cloudformation create-stack --stack-name pedidos360-lab --template-body file://infra/aws/cloudformation-lab.json --parameters file://tmp/lab/parameters.json --profile pedidos360 --region us-east-1
python scripts/deploy_lab.py status
```

Los scripts generan contraseñas en `tmp/lab/runtime.json` y parámetros en `tmp/lab/parameters.json`, ambos ignorados por Git. No imprimir esos archivos. Conservarlos localmente para poder reutilizar las credenciales de esquemas; las credenciales temporales AWS permanecen en su profile.

## Despliegue por SSM

Esperar `CREATE_COMPLETE` y las tres instancias SSM `Online`. Oracle puede tardar varios minutos. Después:

```powershell
python scripts/deploy_lab.py prepare
python scripts/deploy_lab.py deploy --service mq
python scripts/deploy_lab.py deploy --service kafka
python scripts/deploy_lab.py deploy --service init-db
# Esperar éxito de brokers, init-topics y creación de esquemas antes de desplegar apps.
python scripts/deploy_lab.py deploy --service apps
python scripts/deploy_lab.py result --service apps --command-id COMMAND_ID
```

Cada despliegue devuelve un ID de comando; consultar su resultado antes de continuar. `prepare --brokers-only` permite iniciar MQ/Kafka mientras Oracle se crea; ejecutar nuevamente `prepare` sin esa opción cuando el stack esté completo. Las EC2 leen la configuración desde un SecureString de Parameter Store usando su rol, sin copiar credenciales AWS personales. `init-db` crea los cuatro usuarios/esquemas Oracle y sus tablas; las aplicaciones usan `ddl-auto=validate`.

El script configura el frontend con `ApiUrl`. Reiniciar Vite tras cambiar `.env.local`. Nunca poner secretos de cliente en Vite. El access token debe tener issuer/audience configurados, scope `access_as_user` y roles API `Admin`, `Operator` o `Customer` según la cuenta. Un token `User` no se convierte en Admin. Hay que verificar los claims reales del tenant antes de declarar la integración validada.

## Ensayo y presentación

Presentación: **21 de octubre de 2026, clase de 13:10 a 16:10, America/Santiago**. Preparar el entorno con suficiente antelación para aprovisionamiento y pruebas; no comenzar la creación de RDS al iniciar la clase.

Comprobar: tres EC2/SSM disponibles; RDS disponible; ocho aplicaciones sanas; ALB healthy; rechazo de llamadas sin JWT; login real; creación/aceptación de pedido sin doble descuento; timeline audit y reporte; colas/DLQ; tópicos Kafka con tres particiones/una réplica; notificaciones en Mailpit y archivos notify. Probar un reinicio controlado y repetir el flujo. Los volúmenes conservan datos al reiniciar; no utilizar `docker compose down -v`.

Con tres access tokens reales en variables de entorno `P360_ADMIN_TOKEN`, `P360_OPERATOR_TOKEN`, `P360_CUSTOMER_TOKEN`:

```powershell
.\scripts\smoke.ps1 -BaseUrl https://YOUR_API_ID.execute-api.us-east-1.amazonaws.com -ExpectedKafkaBrokers 1
```

Mailpit corre en la EC2 de aplicaciones y no requiere un proveedor de correo. Su panel en 8025 y Kafka UI en 8090 solo se consultan por SSM port forwarding. Rabbit management está ligado a la IP privada y también requiere acceso privado/SSM. No contratar SMTP real para esta entrega.

## Cierre y saldo

EC2, RDS, NAT, ALB, EBS y ECR generan cargos. Detener EC2 no detiene NAT/ALB ni elimina almacenamiento; RDS se reinicia automáticamente tras siete días detenido. Para ensayos separados por varios días, eliminar el stack de prueba vacío y conservar código/imágenes/configuración evita mantener infraestructura ociosa.

La eliminación es asíncrona: comprobar que no haya recursos `DELETE_FAILED`, instancias activas, RDS, NAT, ALB ni IP elástica retenida del stack. ECR y su almacenamiento se conservan para reconstruir. El SecureString temporal puede eliminarse al cerrar; se vuelve a crear con `prepare`.

```powershell
aws cloudformation delete-stack --stack-name pedidos360-lab --profile pedidos360 --region us-east-1
aws cloudformation describe-stack-events --stack-name pedidos360-lab --profile pedidos360 --region us-east-1
```

El estado realmente probado y los pendientes se registran en `VALIDACION.md`.
