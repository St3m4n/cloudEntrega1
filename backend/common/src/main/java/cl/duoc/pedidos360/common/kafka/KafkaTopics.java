package cl.duoc.pedidos360.common.kafka;

import java.util.List;

/** Nombres de los tópicos de Pedidos360. Son constantes para poder usarlos en {@code @KafkaListener}. */
public final class KafkaTopics {
    private KafkaTopics() {}

    /** Cambios de estado de pedidos; lo consume ms-pedidos360-report para proyecciones y KPIs. */
    public static final String ORDER_EVENTS = "orders.events";
    /** Línea de tiempo de auditoría; lo consume ms-pedidos360-audit. */
    public static final String AUDIT_TIMELINE = "audit.timeline";
    /** Dead letter topic de report: {@code <tópico>.<aplicación>.DLT}. */
    public static final String ORDER_EVENTS_DLT = ORDER_EVENTS + ".ms-pedidos360-report.DLT";
    /** Dead letter topic de audit. */
    public static final String AUDIT_TIMELINE_DLT = AUDIT_TIMELINE + ".ms-pedidos360-audit.DLT";

    public static final List<String> MAIN = List.of(ORDER_EVENTS, AUDIT_TIMELINE);
    public static final List<String> DEAD_LETTER = List.of(ORDER_EVENTS_DLT, AUDIT_TIMELINE_DLT);
    public static final List<String> ALL = List.of(ORDER_EVENTS, AUDIT_TIMELINE, ORDER_EVENTS_DLT, AUDIT_TIMELINE_DLT);

    public static String deadLetterOf(String topic, String applicationName) {
        return topic + "." + applicationName + ".DLT";
    }
}
