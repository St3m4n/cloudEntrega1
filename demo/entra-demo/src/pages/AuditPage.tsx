import { useEffect, useState } from 'react';
import { mensajeDe, useBff } from '../bff';
import { EncabezadoPagina } from '../components/ui';
import { TIPOS_EVENTO } from '../pedidos';
interface Event { eventId: string; type: string; timestamp: string; actor: string; origin: string; correlationId: string; order: { id: number; estado: string } }
export function AuditPage() {
  const llamar = useBff(); const [events, setEvents] = useState<Event[]>([]); const [error, setError] = useState(''); const [user, setUser] = useState(''); const [type, setType] = useState(''); const [from, setFrom] = useState(''); const [to, setTo] = useState(''); const [orderId, setOrderId] = useState(''); const [revision, setRevision] = useState(0);
  useEffect(() => {
    let active = true; const params = new URLSearchParams(); if (user) params.set('user', user); if (type) params.set('type', type); if (orderId) params.set('orderId', orderId); if (from) params.set('from', new Date(`${from}T00:00:00`).toISOString()); if (to) params.set('to', new Date(`${to}T23:59:59.999`).toISOString());
    llamar<Event[]>(`/api/audit/events?${params}`).then(e => { if (active) { setEvents(e); setError(''); } }).catch(e => { if (active) setError(mensajeDe(e)); }); return () => { active = false; };
  }, [llamar, user, type, from, to, orderId, revision]);
  return <><EncabezadoPagina titulo="Auditoría" descripcion="Historial de pedidos: quién, qué, cuándo y desde dónde." />
    <fieldset className="filtros"><legend>Filtros</legend><label>Usuario (ID o correo) <input value={user} onChange={e => setUser(e.target.value)} /></label><label>Pedido <input type="number" min="1" value={orderId} onChange={e => setOrderId(e.target.value)} /></label><label>Tipo <select value={type} onChange={e => setType(e.target.value)}><option value="">Todos</option>{[...TIPOS_EVENTO, 'OrderUpdated'].map(t => <option key={t}>{t}</option>)}</select></label><label>Desde <input type="date" value={from} onChange={e => setFrom(e.target.value)} /></label><label>Hasta <input type="date" value={to} onChange={e => setTo(e.target.value)} /></label><button className="boton secundario" onClick={() => setRevision(n => n + 1)}>Actualizar</button></fieldset>
    {error && <p role="alert" className="texto-error">{error}</p>}
    {events.length ? <ol>{events.map(e => <li className="tarjeta" key={e.eventId}><strong>{e.type}</strong> · pedido {e.order.id} · {e.order.estado}<p>{new Date(e.timestamp).toLocaleString('es-CL')} · actor {e.actor} · origen {e.origin}</p><small>Correlación: {e.correlationId}</small></li>)}</ol> : <p>No hay eventos para los filtros seleccionados.</p>}
  </>;
}
