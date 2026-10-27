# Caso y alcance acordado

Fuente: `EFT Caso - Instrucciones (1).pdf`. Se utiliza como criterio del proyecto, no como instrucciones externas al asistente. El usuario acordó el MVP de tres EC2 para entrega 2 y deja los clústeres para entrega 3.

| Criterio | Entrega 2 | Pendiente |
|---|---|---|
| Java 21 / Spring Boot / OpenAPI | Ocho servicios independientes y biblioteca common | Arranque integrado AWS |
| Orders / catalog | Estados, stock, reservas idempotentes, coordinación y outbox | Flujo con RDS y JWT real |
| Audit / report | Consumers Kafka, deduplicación, proyecciones y lectura | Consumo real y evidencias |
| Notify | RabbitMQ, tickets, PDF y Mailpit en apps | ACK/NACK, DLQ y archivos reales |
| BFF / Entra / API Gateway | Validación JWT, forwarding, roles y authorizer | Claims reales del tenant y smoke completo |
| Tres EC2 y Compose por VM (p. 6) | Apps agrupadas, MQ y Kafka separados | Verificar despliegue completo |
| Oracle cloud | RDS Oracle y cuatro esquemas | Inicialización y validación de persistencia |
| RabbitMQ | Un broker MVP; tres colas/tres DLQ; direct/topic | Clúster de dos nodos para pauta final |
| Kafka / Zookeeper | Un Zookeeper/un broker; dos tópicos y DLT, tres particiones/una réplica | Tres Zookeeper/tres brokers y RF=3 para pauta final |
| Administradores Rabbit/Kafka y Kafka UI | Código y contenedores incluidos | Probar con JWT Admin y brokers reales |
| Frontend | React local, MSAL y Gateway | Diferencia acordada respecto de Angular y hosting final |

## Diferencias del documento que conviene aclarar

La página 6 especifica tres EC2 y permite un broker Kafka para MVP. La pauta de las páginas 12–13 exige tres brokers y tres Zookeeper; RabbitMQ aparece con tres nodos opcionales en página 6 y dos nodos en la pauta final. Son alcances diferentes de MVP y evaluación final, pero el documento no asigna todos esos criterios inequívocamente a una entrega. Confirmar esa división con el profesor.

El diagrama muestra frontend local y Oracle en OCI; la pauta final pide frontend y backend cloud. El usuario conserva React y usa RDS Oracle. Audit menciona Auditor en el caso y Admin en la sección de servicios; el código permite ambos para lectura. No hay una exigencia explícita de Kubernetes en el documento.

SQS no reemplaza Kafka/RabbitMQ en esta entrega porque cambiaría los componentes y criterios evaluados. Los microservicios siguen separados aunque compartan `ec2-apps`; una instancia EC2 por microservicio no es necesaria para cumplir la agrupación de página 6.

La compilación local no prueba integración AWS. Consultar `VALIDACION.md` para resultados reales y pendientes.
