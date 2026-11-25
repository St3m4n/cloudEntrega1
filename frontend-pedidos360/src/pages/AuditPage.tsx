import { useEffect, useState } from 'react';
import { mensajeDe, useBff } from '../bff';
import { Icono } from '../components/Icono';
import type { NombreIcono } from '../components/Icono';
import { OrderStatusBadge } from '../components/orders/OrderStatusBadge';
import { Cargando, EncabezadoPagina, EstadoVacio } from '../components/ui';
import { ESTADOS_PEDIDO, TIPOS_EVENTO } from '../pedidos';
import type { EstadoPedido } from '../tipos';

interface Event { eventId: string; type: string; timestamp: string; actor: string; origin: string; correlationId: string; order: { id: number; estado: string } }

const TIPOS: Record<string, { texto: string; icono: NombreIcono; clase: string }> = {
  OrderCreated: { texto: 'Pedido creado', icono: 'pedidos', clase: 'estado-creado' },
  OrderAccepted: { texto: 'Pedido aceptado', icono: 'check', clase: 'estado-aceptado' },
  OrderPreparing: { texto: 'En preparación', icono: 'reloj', clase: 'estado-en_preparacion' },
  OrderDispatched: { texto: 'Pedido despachado', icono: 'camion', clase: 'estado-despachado' },
  OrderDelivered: { texto: 'Pedido entregado', icono: 'check', clase: 'estado-entregado' },
  OrderCancelled: { texto: 'Pedido cancelado', icono: 'alerta', clase: 'estado-cancelado' },
  OrderUpdated: { texto: 'Pedido editado', icono: 'editar', clase: 'estado-creado' },
};

const corto = (texto: string) => (texto.length > 14 ? `${texto.slice(0, 12)}…` : texto);

export function AuditPage() {
  const llamar = useBff(); const [events, setEvents] = useState<Event[] | null>(null); const [error, setError] = useState(''); const [user, setUser] = useState(''); const [type, setType] = useState(''); const [from, setFrom] = useState(''); const [to, setTo] = useState(''); const [orderId, setOrderId] = useState(''); const [revision, setRevision] = useState(0);
  useEffect(() => {
    let active = true; const params = new URLSearchParams(); if (user) params.set('user', user); if (type) params.set('type', type); if (orderId) params.set('orderId', orderId); if (from) params.set('from', new Date(`${from}T00:00:00`).toISOString()); if (to) params.set('to', new Date(`${to}T23:59:59.999`).toISOString());
    llamar<Event[]>(`/api/audit/events?${params}`).then(e => { if (active) { setEvents(e); setError(''); } }).catch(e => { if (active) setError(mensajeDe(e)); }); return () => { active = false; };
  }, [llamar, user, type, from, to, orderId, revision]);

  return <>
    <EncabezadoPagina titulo="Auditoría" descripcion="Historial de cada pedido: quién, qué, cuándo y desde dónde. Lo registra el @KafkaListener de audit.timeline."
      acciones={events && <span className="chip neutro">{events.length} {events.length === 1 ? 'evento' : 'eventos'}</span>} />
    <div className="filtros">
      <label>Usuario (ID o correo) <input value={user} onChange={e => setUser(e.target.value)} placeholder="Todos" /></label>
      <label>Pedido <input type="number" min="1" value={orderId} onChange={e => setOrderId(e.target.value)} placeholder="N.º de pedido" /></label>
      <label>Tipo <select value={type} onChange={e => setType(e.target.value)}><option value="">Todos</option>{[...TIPOS_EVENTO, 'OrderUpdated'].map(t => <option key={t} value={t}>{TIPOS[t]?.texto ?? t}</option>)}</select></label>
      <label>Desde <input type="date" value={from} onChange={e => setFrom(e.target.value)} /></label>
      <label>Hasta <input type="date" value={to} onChange={e => setTo(e.target.value)} /></label>
      <span className="filtros-espaciador" />
      <button className="boton secundario" onClick={() => setRevision(n => n + 1)}><Icono nombre="actualizar" />Actualizar</button>
    </div>
    {error && <p role="alert" className="texto-error">{error}</p>}
    {events === null && !error && <Cargando texto="Cargando eventos..." />}
    {events?.length === 0 && <EstadoVacio titulo="No hay eventos para los filtros seleccionados" icono="auditoria" />}
    {!!events?.length && <ol className="linea-tiempo">
      {events.map(e => {
        const tipo = TIPOS[e.type] ?? { texto: e.type, icono: 'capas' as NombreIcono, clase: 'estado-creado' };
        const estadoValido = ESTADOS_PEDIDO.some(x => x.valor === e.order.estado);
        return <li className="evento" key={e.eventId}>
          <span className={`evento-icono ${tipo.clase}`}><Icono nombre={tipo.icono} tamano={16} /></span>
          <div className="evento-cuerpo">
            <div className="evento-titulo">
              <strong>{tipo.texto}</strong>
              <span className="chip neutro">Pedido #{e.order.id}</span>
              {estadoValido ? <OrderStatusBadge estado={e.order.estado as EstadoPedido} /> : <span className="chip neutro">{e.order.estado}</span>}
              <span className="evento-hora">{new Date(e.timestamp).toLocaleString('es-CL')}</span>
            </div>
            <div className="evento-detalles">
              <span><code>{e.type}</code></span>
              <span title={e.actor}><Icono nombre="usuario" tamano={13} />{corto(e.actor)}</span>
              <span><Icono nombre="servidor" tamano={13} />{e.origin}</span>
              <span>Correlación <code>{e.correlationId}</code></span>
            </div>
          </div>
        </li>;
      })}
    </ol>}
  </>;
}
