# Levantar Pedidos360 en AWS para la presentación

Este procedimiento corresponde a la rama entrega2 y a una cuenta AWS Learner Lab en us-east-1. Levanta el MVP de tres EC2:

- ec2-apps: los ocho microservicios y Mailpit.
- ec2-mq: un RabbitMQ con Management UI.
- ec2-kafka: un Zookeeper, un broker Kafka y Kafka UI.

La configuración permite probar la entrega 2. La pauta semestral final exige dos nodos RabbitMQ y tres brokers Kafka con tres réplicas; esos componentes no están en esta rama MVP. No presentes este entorno como el clúster final sin completar esa ampliación.

El procedimiento crea recursos con costo. Inícialo con tiempo, porque RDS Oracle puede tardar entre 20 y 40 minutos. No dejes el entorno activo fuera del ensayo o la presentación.

## 1. Preparación del laboratorio

En AWS Learner Lab, abre la cuenta y renueva las credenciales temporales. Configura el perfil local pedidos360 con access key, secret key y session token. Nunca pegues esos valores en este archivo, en Git ni en el chat.

Desde PowerShell, en la raíz del repositorio:

~~~powershell
Set-Location 'C:\Users\patri\OneDrive\Escritorio\Entrega Cloud2\cloudEntrega1'
$env:AWS_PROFILE = 'pedidos360'
$env:AWS_DEFAULT_REGION = 'us-east-1'

aws sts get-caller-identity --profile pedidos360 --region us-east-1
~~~

La cuenta debe responder sin ExpiredToken. Si no tienes el perfil creado, usa los datos que muestra el panel de Learner Lab:

~~~powershell
aws configure --profile pedidos360
aws configure set aws_session_token 'PEGAR_SESSION_TOKEN_SOLO_EN_LA_TERMINAL' --profile pedidos360
~~~

Comprueba que Docker Desktop esté iniciado con contenedores Linux y que Python tenga boto3 disponible:

~~~powershell
docker version
python -c "import boto3; print('boto3 OK')"
~~~

Si boto3 no está instalado, instálalo en la carpeta temporal ignorada por Git:

~~~powershell
New-Item -ItemType Directory -Force tmp/awsdeps | Out-Null
python -m pip install --target tmp/awsdeps boto3
~~~

## 2. Datos de red y tenant

La sesión anterior utilizó estos valores en la cuenta de laboratorio. Verifícalos antes de crear el stack, porque una sesión nueva podría asignar otra VPC o subred:

| Campo | Valor utilizado |
|---|---|
| Región | us-east-1 |
| VPC | vpc-0b5fa6a8e1366da8d |
| Subred pública con Internet Gateway | subnet-00ee578de695cd5e9 |
| AZ privada A | us-east-1a |
| AZ privada B | us-east-1b |
| CIDR privado A | 172.31.96.0/20 |
| CIDR privado B | 172.31.112.0/20 |
| Instance profile | LabInstanceProfile |

Los dos CIDR privados deben estar libres dentro de la VPC. La subred pública debe tener ruta 0.0.0.0/0 al Internet Gateway; el NAT se crea allí para que las subredes privadas puedan instalar Docker, descargar imágenes y consultar Entra.

Los IDs públicos del tenant ya están configurados en el archivo local ignorado tmp/lab/inputs.json. La audiencia debe ser exactamente el claim aud del access token de la API, y el issuer debe terminar en /v2.0.

Si el archivo aún no existe:

~~~powershell
New-Item -ItemType Directory -Force tmp/lab | Out-Null
Copy-Item infra/aws/deployment-inputs.example.json tmp/lab/inputs.json
notepad tmp/lab/inputs.json
~~~

Completa solo los valores locales de cuenta, red y tenant. No reemplaces las contraseñas manualmente: el script las genera en tmp/lab/runtime.json y limita las de Oracle a 30 caracteres.

## 3. Preparar plantilla, parámetros e imágenes

Los siguientes archivos ya están en el repositorio. Regenera la variante del laboratorio si modificaste la plantilla base:

~~~powershell
python scripts/create_infra.py
python scripts/create_lab_template.py
python scripts/prepare_lab_parameters.py --inputs tmp/lab/inputs.json
~~~

El último comando crea archivos ignorados por Git:

- tmp/lab/runtime.json: contraseñas temporales de Oracle, RabbitMQ y esquemas.
- tmp/lab/parameters.json: parámetros de CloudFormation, incluida la contraseña maestra de Oracle.
- frontend-pedidos360/.env.local: tenant y configuración local inicial.

No muestres ni subas esos archivos.

Las imágenes del MVP fueron publicadas con el tag mvp-20261008 en la cuenta utilizada anteriormente. Comprueba o completa la publicación con:

~~~powershell
$accountId = aws sts get-caller-identity --profile pedidos360 --region us-east-1 --query Account --output text
.\scripts\publish-images.ps1 -AccountId $accountId -Region us-east-1 -Tag mvp-20261008 -SkipPublished
~~~

Si el script intenta construir una imagen, Docker debe estar activo. No cambies el tag después de ejecutar prepare_lab_parameters.py; ambos deben coincidir.

## 4. Crear la infraestructura

Antes de crear, comprueba que no exista otro pedidos360-lab activo:

~~~powershell
aws cloudformation describe-stacks --stack-name pedidos360-lab --profile pedidos360 --region us-east-1 --query 'Stacks[0].StackStatus' --output text
~~~

Si responde DELETE_IN_PROGRESS, espera a DELETE_COMPLETE. Si responde CREATE_COMPLETE o UPDATE_COMPLETE, reutiliza ese stack y salta a la sección 5. No borres un stack con datos que quieras conservar.

Para crear uno nuevo:

~~~powershell
aws cloudformation create-stack --stack-name pedidos360-lab --template-body file://infra/aws/cloudformation-lab.json --parameters file://tmp/lab/parameters.json --tags Key=Project,Value=pedidos360 Key=Environment,Value=learner-lab --profile pedidos360 --region us-east-1
~~~

Comprueba el avance:

~~~powershell
python scripts/deploy_lab.py status
aws cloudformation describe-stack-events --stack-name pedidos360-lab --profile pedidos360 --region us-east-1 --query 'StackEvents[0:12].[LogicalResourceId,ResourceStatus,ResourceStatusReason]' --output table
~~~

Espera CREATE_COMPLETE. Si falla, revisa primero el último evento de CloudFormation. Oracle suele ser el último recurso en terminar.

## 5. Preparar SSM y desplegar los servicios

Cuando el stack esté completo, registra sus outputs, las instancias y el SecureString temporal:

~~~powershell
python scripts/deploy_lab.py prepare
aws ssm describe-instance-information --profile pedidos360 --region us-east-1 --query 'InstanceInformationList[*].[InstanceId,PingStatus]' --output table
~~~

Las tres instancias deben aparecer como Online. Si Oracle todavía está creando pero las EC2 y SSM ya funcionan, puedes iniciar solo los brokers:

~~~powershell
python scripts/deploy_lab.py prepare --brokers-only
python scripts/deploy_lab.py deploy --service mq
python scripts/deploy_lab.py deploy --service kafka
~~~

Cuando Oracle esté disponible y el stack alcance CREATE_COMPLETE, ejecuta nuevamente prepare sin --brokers-only.

Despliega en este orden y guarda cada commandId que devuelve el script:

~~~powershell
python scripts/deploy_lab.py deploy --service mq
python scripts/deploy_lab.py deploy --service kafka
python scripts/deploy_lab.py deploy --service init-db
python scripts/deploy_lab.py deploy --service apps
~~~

init-db crea los usuarios P360_CATALOG, P360_ORDERS, P360_AUDIT y P360_REPORT, además de p360_documents. apps levanta los ocho microservicios y Mailpit en un único Compose. No despliegues cada microservicio por separado: comparten ec2-apps.

Consulta el resultado de cada comando reemplazando los valores:

~~~powershell
python scripts/deploy_lab.py result --service mq --command-id COMMAND_ID_MQ
python scripts/deploy_lab.py result --service kafka --command-id COMMAND_ID_KAFKA
python scripts/deploy_lab.py result --service init-db --command-id COMMAND_ID_INIT_DB
python scripts/deploy_lab.py result --service apps --command-id COMMAND_ID_APPS
~~~

El estado esperado es Success con código 0. Si falla apps, revisa el error completo y vuelve a ejecutar el mismo deploy después de corregir la causa; no regeneres las contraseñas de una base ya inicializada.

## 6. Verificaciones antes de presentar

Revisa los contenedores por SSM:

~~~powershell
python scripts/deploy_lab.py command --service mq --command "cd /opt/pedidos360/mq; docker compose --env-file .env ps"
python scripts/deploy_lab.py command --service kafka --command "cd /opt/pedidos360/kafka; docker compose ps"
python scripts/deploy_lab.py command --service apps --command "cd /opt/pedidos360/apps; docker compose --env-file .env ps"
python scripts/deploy_lab.py command --service apps --command "curl -fsS http://localhost:8080/actuator/health"
~~~

En ec2-mq deben estar saludables RabbitMQ y Management. En ec2-kafka deben estar saludables Zookeeper, Kafka, init-topics y Kafka UI. En ec2-apps deben estar levantados BFF, Catalog, Orders, Audit, Report, Notify, Rabbit Admin, Kafka Admin y Mailpit.

Obtén la URL del API Gateway:

~~~powershell
$apiUrl = aws cloudformation describe-stacks --stack-name pedidos360-lab --profile pedidos360 --region us-east-1 --query "Stacks[0].Outputs[?OutputKey=='ApiUrl'].OutputValue" --output text
$apiUrl
~~~

El script prepare actualiza frontend-pedidos360/.env.local con esa URL. Levanta React en otra terminal:

~~~powershell
Set-Location frontend-pedidos360
npm ci
npm run dev -- --host 127.0.0.1
~~~

Ingresa por Microsoft Entra y prueba el flujo visible:

1. Admin crea o consulta un producto en Catálogo.
2. Customer crea un pedido.
3. Operator lo acepta, prepara, despacha y entrega.
4. Se verifica que el stock no se descuente dos veces.
5. Admin revisa reportes y auditoría.
6. Mailpit muestra notificaciones; Notify conserva tickets y PDF en su volumen.

Para el smoke automatizado se necesitan tres access tokens reales del tenant, con scope access_as_user y roles Admin, Operator y Customer. Configúralos únicamente en la terminal:

~~~powershell
$env:P360_ADMIN_TOKEN = 'TOKEN_ADMIN_LOCAL'
$env:P360_OPERATOR_TOKEN = 'TOKEN_OPERATOR_LOCAL'
$env:P360_CUSTOMER_TOKEN = 'TOKEN_CUSTOMER_LOCAL'
.\scripts\smoke.ps1 -BaseUrl $apiUrl -ExpectedKafkaBrokers 1
~~~

No guardes esos tokens en archivos ni los compartas por chat. El smoke de esta rama espera un broker Kafka; no demuestra la pauta final de tres brokers.

## 7. Abrir paneles privados por SSM

Kafka UI escucha en 8090 solo dentro de ec2-kafka:

~~~powershell
$kafkaId = (Get-Content tmp/lab/runtime.json | ConvertFrom-Json).instances.kafka
aws ssm start-session --target $kafkaId --document-name AWS-StartPortForwardingSession --parameters '{"portNumber":["8090"],"localPortNumber":["8090"]}' --profile pedidos360 --region us-east-1
~~~

Abre http://localhost:8090 mientras la sesión SSM esté activa.

Rabbit Management está ligado a la IP privada de ec2-mq. Obtén esa IP desde tmp/lab/runtime.json o CloudFormation y usa el documento de forwarding hacia host remoto:

~~~powershell
$mqId = (Get-Content tmp/lab/runtime.json | ConvertFrom-Json).instances.mq
$mqIp = (Get-Content tmp/lab/runtime.json | ConvertFrom-Json).outputs.Ec2MqPrivateIp
aws ssm start-session --target $mqId --document-name AWS-StartPortForwardingSessionToRemoteHost --parameters ('{"host":["' + $mqIp + '"],"portNumber":["15672"],"localPortNumber":["15672"]}') --profile pedidos360 --region us-east-1
~~~

Abre http://localhost:15672 con el usuario y contraseña de tmp/lab/runtime.json. No uses guest/guest en AWS.

## 8. Cierre después de la presentación

Cuando termine la demostración, elimina el stack para detener EC2, RDS, NAT, ALB, VPC Link y las subredes privadas creadas por la variante lab:

~~~powershell
aws cloudformation delete-stack --stack-name pedidos360-lab --profile pedidos360 --region us-east-1
aws cloudformation wait stack-delete-complete --stack-name pedidos360-lab --profile pedidos360 --region us-east-1
aws ssm delete-parameter --name /pedidos360/lab/runtime --profile pedidos360 --region us-east-1
~~~

No elimines los repositorios ECR: contienen las imágenes que permiten reconstruir el entorno. Comprueba que no queden instancias, RDS, NAT, balanceadores ni IP elásticas de la sesión:

~~~powershell
aws ec2 describe-instances --filters Name=tag:Name,Values=pedidos360-apps,pedidos360-mq,pedidos360-kafka --profile pedidos360 --region us-east-1 --query 'Reservations[*].Instances[*].[InstanceId,State.Name]' --output table
aws rds describe-db-instances --profile pedidos360 --region us-east-1 --query 'DBInstances[*].[DBInstanceIdentifier,DBInstanceStatus]' --output table
aws ec2 describe-nat-gateways --filter Name=tag:Name,Values=pedidos360-nat --profile pedidos360 --region us-east-1 --query 'NatGateways[*].[NatGatewayId,State]' --output table
~~~

## Problemas frecuentes

- ExpiredToken: renovar las credenciales del Learner Lab y actualizar el perfil pedidos360.
- CREATE_FAILED en Oracle: revisar clase db.t3.medium, contraseña de máximo 30 caracteres y disponibilidad regional.
- EC2 Offline en SSM: esperar cloud-init, revisar que la instancia tenga LabInstanceProfile y que la subred privada tenga salida por NAT.
- Error al descargar imágenes: comprobar que el tag mvp-20261008 exista y que la instancia tenga permisos ECR.
- HTTP 401: comprobar issuer, audiencia, scope access_as_user, roles y que el frontend envíe un access token, no un ID token.
- API 503: revisar primero docker compose ps en ec2-apps, luego salud de RabbitMQ, Kafka y RDS.
- Stack DELETE_FAILED: consultar describe-stack-events, corregir el recurso retenido y repetir la eliminación; no crear otro stack con el mismo nombre hasta completar la limpieza.

