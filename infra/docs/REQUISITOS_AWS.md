# Requisitos para retomar AWS

Cuenta Learner Lab, región `us-east-1`, saldo inicial informado US$50. Se usa el profile local AWS CLI `pedidos360` y `LabInstanceProfile`. Renovar las credenciales temporales en el profile antes de retomar; hacen falta access key, secret key y session token. No enviarlas por chat ni guardarlas en Git.

Ya se recibieron los IDs públicos Tenant, SPA Client y API Client, y se configuró el frontend local. Falta validar el access token real y los roles `Admin`, `Operator`, `Customer`; no hacen falta contraseñas de usuarios ni client secrets.

El plan usa tres EC2 (apps `t3.large`, MQ `t3.small`, Kafka `t3.medium`), RDS Oracle `db.t3.medium`, ALB privado, VPC Link, API Gateway y NAT. Las ocho aplicaciones comparten la EC2 apps con límites de memoria. Las clases requieren confirmar costo y autorización de la sesión del laboratorio. No dejar el stack activo hasta el 21 de octubre.

Se requiere acceso a CloudFormation, EC2/VPC, ECR, ELB, API Gateway, RDS Oracle y Systems Manager/Parameter Store; `iam:PassRole` para el profile autorizado. La plantilla lab reutiliza el rol existente y no crea roles nuevos. La VPC/subred pública deben tener Internet Gateway y DNS; los dos CIDR privados del ejemplo deben estar libres.

Se conserva Oracle. Si el laboratorio bloquea su creación, registrar el error y acordar cualquier cambio de motor antes de implementarlo. Mailpit corre dentro de apps y evita configurar correo externo para entrega 2. React continúa local.

Para el próximo ensayo: renovar profile, revisar recursos remanentes y saldo, reconstruir el stack con la configuración local, comprobar Oracle/brokers/aplicaciones y realizar smoke con JWT reales. Seguir `AWS.md`; no regenerar contraseñas de una base existente ni imprimir `tmp/lab/runtime.json`.

La clase de presentación es el 21 de octubre de 2026, de 13:10 a 16:10 (America/Santiago). Mantener margen previo para levantar y verificar servicios y vigencia de la sesión del laboratorio durante toda la defensa.
