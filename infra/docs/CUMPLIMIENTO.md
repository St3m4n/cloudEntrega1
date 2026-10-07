# Comparación con el caso y pauta del profesor

Fuente: `EFT Caso - Instrucciones (1).pdf`, entregado por el usuario. Se aplican los requisitos del caso como criterios del proyecto; no como instrucciones externas al asistente. La pauta agrega requisitos que no aparecen en el diagrama resumido.

| Criterio | Estado del código/configuración | Evidencia pendiente |
|---|---|---|
| Backend Spring Boot 3+ / Java 21 / OpenAPI | Ocho servicios independientes en backend, common reutilizable | Arranque y consultas en EC2 |
| Pedidos CRUD / estados / stock | Implementado; cancelación y borrado lógico, transacciones y reservas idempotentes | Smoke integrado RDS |
| Catálogo productos/stock/precio | Implementado y conectado a React | Cargar productos reales en RDS |
| Notify sin DB | Consumer RabbitMQ, SMTP, tickets JSON, comprobantes PDF; recibos en volumen | Configurar SMTP y revisar archivos generados |
| Report / audit separados | Consumers Kafka y esquemas propios; endpoints de lectura | Consumo real en AWS |
| BFF separado | JWT y proxy, conserva rutas del frontend | Validar con tenant real |
| IDaaS Azure / JWT / roles | Validación en todos los servicios; forwarding JWT; pruebas de firma/issuer/audience/expiración | Configurar variables con tenant existente |
| API Gateway HTTP API + JWT | CloudFormation con authorizer/scopes, VPC Link y ALB interno | Crear stack y ejecutar smoke |
| EC2 / Docker Compose | Una EC2 y Compose por servicio, hosts separados para MQ/Kafka | Desplegar imágenes y completar `.env` |
| Oracle cloud | Driver Oracle y SQL de cuatro esquemas; RDS Oracle | Provisionar RDS y aplicar esquema |
| Dos nodos RabbitMQ en clúster | Compose, cookie compartida, descubrimiento estático y quorum queues | Verificar cluster_status en ejecución |
| Tres colas / cada DLQ | email/kitchen/invoice con sus tres DLQ | Demostrar ACK, NACK y archivo DLQ |
| Direct y topic | cmd.direct, cmd.topic y bindings de cada flujo | Demostrar routing en panel/administrador |
| Servicio administrador RabbitMQ | Crear/listar/borrar colas y exchanges; crear/listar/borrar bindings | Probar con broker real y JWT Admin |
| Tres Zookeeper y tres brokers | Compose completo con volúmenes | Verificar salud/ISR reales |
| Dos tópicos, 3 particiones / 3 réplicas | orders.events, audit.timeline; también dos DLT | Describe de tópicos reales |
| Consumers / producers Kafka | Orders outbox produce; audit/report consumen; reintentos y DLT | Demostrar entrega y fallos |
| Servicio administrador Kafka | Tópicos, particiones, configs y consulta de clúster | Probar con Kafka real y JWT Admin |
| Kafka UI | Servicio Compose con acceso local/SSM | Evidencia de administración en defensa |
| Front Angular 18+ | **Diferencia acordada:** se mantiene React/MSAL por solicitud explícita | El profesor puede evaluar esta diferencia |
| BD Oracle en OCI del diagrama | **Diferencia solicitada:** RDS Oracle en AWS conserva el motor | Confirmar aceptación académica del proveedor |
| Front desplegado cloud (pauta) | React sigue local; código compila y llama a Gateway | Hosting frontend si se exige en esta entrega |

## Por qué no se reemplazó por SQS

El usuario propuso SQS como posibilidad. El documento exige RabbitMQ para colas y Kafka/Zookeeper para streaming; la pauta evalúa dos nodos RabbitMQ, direct/topic, tres colas/DLQ, administradores y Kafka UI. SQS no implementa esos componentes. Se preparó la opción que conserva los criterios explícitos. No hay adaptador SQS ni se afirma que esté implementado.

El caso menciona un broker para MVP, pero la pauta exige tres brokers y tres Zookeeper: los Compose siguen el criterio más específico de evaluación. La figura omite los administradores; fueron incluidos porque aparecen en la pauta.

## Validación y alcance

La compilación y las pruebas locales no demuestran una infraestructura desplegada. Se deben registrar por separado resultados de Maven, build React, validación Compose/CloudFormation y evidencias cloud (EC2 activas, RDS conectado, JWT real, RabbitMQ, ISR Kafka, Gateway y SMTP). Las limitaciones de consistencia eventual, almacenamiento en CLOB, SMTP y disponibilidad están en el README.
