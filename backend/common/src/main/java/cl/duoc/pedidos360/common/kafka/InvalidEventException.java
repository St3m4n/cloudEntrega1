package cl.duoc.pedidos360.common.kafka;

/** Evento con formato correcto pero inconsistente con el negocio. No se reintenta: va directo al DLT. */
public class InvalidEventException extends RuntimeException {
    public InvalidEventException(String message) {
        super(message);
    }
}
