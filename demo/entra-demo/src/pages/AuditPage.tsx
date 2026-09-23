import { Aviso, EncabezadoPagina, EstadoVacio } from '../components/ui';
import { TIPOS_EVENTO } from '../pedidos';

export function AuditPage() {
  return (
    <>
      <EncabezadoPagina
        titulo="Auditoría"
        descripcion="Línea de tiempo de eventos de negocio: quién, qué, cuándo y desde dónde."
      />

      <Aviso tipo="info" titulo="Módulo pendiente de backend">
        <p>
          Los eventos se obtendrán desde ms-pedidos360-audit (solo lectura), que persiste lo
          publicado en el tópico Kafka audit.timeline.
        </p>
      </Aviso>

      <fieldset className="filtros" disabled>
        <legend>Filtros</legend>
        <label>
          Usuario
          <input type="text" placeholder="usuario@dominio" />
        </label>
        <label>
          Desde
          <input type="date" />
        </label>
        <label>
          Hasta
          <input type="date" />
        </label>
        <label>
          Tipo de evento
          <select defaultValue="">
            <option value="">Todos</option>
            {TIPOS_EVENTO.map((t) => (
              <option key={t} value={t}>
                {t}
              </option>
            ))}
          </select>
        </label>
      </fieldset>

      <EstadoVacio titulo="No hay eventos para mostrar">
        La línea de tiempo se completará cuando ms-pedidos360-audit esté disponible.
      </EstadoVacio>
    </>
  );
}
