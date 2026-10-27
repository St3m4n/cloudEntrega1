import { useEffect, useState } from 'react';
import { rolesDe, usePerfil } from '../auth/perfil';
import { ROLES, tieneAlgunRol } from '../auth/roles';
import { mensajeDe, useBff, useCuenta } from '../bff';
import { formatearPrecio, useProductos } from '../catalogo';
import { OrderStatusBadge } from '../components/orders/OrderStatusBadge';
import { EncabezadoPagina } from '../components/ui';
import { ESTADOS_PEDIDO } from '../pedidos';
import type { EstadoPedido, Pedido } from '../tipos';
const SIGUIENTES: Record<EstadoPedido, EstadoPedido[]> = {
  CREADO: ['ACEPTADO', 'CANCELADO'], ACEPTADO: ['EN_PREPARACION', 'CANCELADO'],
  EN_PREPARACION: ['DESPACHADO', 'CANCELADO'], DESPACHADO: ['ENTREGADO'], ENTREGADO: [], CANCELADO: [],
};
export function OrdersPage() {
  const llamar = useBff(); const cuenta = useCuenta(); const roles = rolesDe(usePerfil().estado);
  const puedeCrear = tieneAlgunRol(roles, [ROLES.cliente, ROLES.operador]);
  const puedeCambiar = tieneAlgunRol(roles, [ROLES.admin, ROLES.operador]);
  const catalogo = useProductos();
  const [pedidos, setPedidos] = useState<Pedido[]>([]); const [error, setError] = useState('');
  const [estado, setEstado] = useState(''); const [desde, setDesde] = useState(''); const [hasta, setHasta] = useState('');
  const [revision, setRevision] = useState(0); const [busy, setBusy] = useState(false); const [crear, setCrear] = useState(false);
  const [email, setEmail] = useState(cuenta?.username ?? ''); const [cantidades, setCantidades] = useState<Record<number, number>>({});
  const [editando, setEditando] = useState<number | null>(null);
  useEffect(() => {
    const controller = new AbortController(); const query = new URLSearchParams();
    if (estado) query.set('status', estado);
    if (desde) query.set('from', new Date(`${desde}T00:00:00`).toISOString());
    if (hasta) query.set('to', new Date(`${hasta}T23:59:59.999`).toISOString());
    llamar<Pedido[]>(`/api/orders?${query}`, { signal: controller.signal }).then(p => { if (!controller.signal.aborted) { setPedidos(p); setError(''); } }).catch(e => { if (!controller.signal.aborted) setError(mensajeDe(e)); });
    return () => controller.abort();
  }, [llamar, estado, desde, hasta, revision]);
  async function guardar() {
    setBusy(true); setError('');
    try {
      const items = Object.entries(cantidades).filter(([, q]) => q > 0).map(([id, quantity]) => ({ productId: Number(id), quantity }));
      if (!items.length) throw new Error('Selecciona al menos un producto.');
      await llamar(`/api/orders${editando === null ? '' : `/${editando}`}`, { method: editando === null ? 'POST' : 'PUT', body: JSON.stringify({ email, items }) });
      setCrear(false); setEditando(null); setCantidades({}); setRevision(n => n + 1);
    } catch (e) { setError(mensajeDe(e)); } finally { setBusy(false); }
  }
  async function cambiar(id: number, status: string) {
    setBusy(true); setError('');
    try { await llamar(`/api/orders/${id}/status`, { method: 'PUT', body: JSON.stringify({ status }) }); setRevision(n => n + 1); catalogo.recargar(); }
    catch (e) { setError(mensajeDe(e)); } finally { setBusy(false); }
  }
  return <>
    <EncabezadoPagina titulo="Pedidos" descripcion="Creación, edición y seguimiento de pedidos." acciones={puedeCrear && <button className="boton primario" onClick={() => { setCrear(!crear); setEditando(null); setCantidades({}); }}>Nuevo pedido</button>} />
    {error && <p role="alert" className="texto-error">{error}</p>}
    {crear && <form className="tarjeta" onSubmit={e => { e.preventDefault(); void guardar(); }}>
      <h2>{editando === null ? 'Nuevo pedido' : `Editar pedido ${editando}`}</h2>
      <label>Email de notificación <input type="email" required value={email} onChange={e => setEmail(e.target.value)} /></label>
      {catalogo.error && <p role="alert">{catalogo.error}</p>}
      {(catalogo.productos ?? []).map(p => <label key={p.id} style={{ display: 'block', margin: '0.8rem 0' }}>
        {p.nombre} · {formatearPrecio(p.precio)} · stock {p.stock} <input aria-label={`Cantidad de ${p.nombre}`} type="number" min="0" max={p.stock} value={cantidades[p.id] ?? 0} onChange={e => setCantidades(c => ({ ...c, [p.id]: Number(e.target.value) }))} />
      </label>)}
      <button className="boton primario" disabled={busy || catalogo.cargando}>Guardar pedido</button>
      <button type="button" className="boton secundario" onClick={() => setCrear(false)}>Cerrar</button>
    </form>}
    <fieldset className="filtros"><legend>Filtros</legend>
      <label>Estado <select value={estado} onChange={e => setEstado(e.target.value)}><option value="">Todos</option>{ESTADOS_PEDIDO.map(e => <option key={e.valor} value={e.valor}>{e.etiqueta}</option>)}</select></label>
      <label>Desde <input type="date" value={desde} onChange={e => setDesde(e.target.value)} /></label>
      <label>Hasta <input type="date" value={hasta} onChange={e => setHasta(e.target.value)} /></label>
      <button className="boton secundario" onClick={() => setRevision(n => n + 1)}>Actualizar</button>
    </fieldset>
    {!pedidos.length && <p>No hay pedidos para mostrar.</p>}
    {pedidos.map(p => <article className="tarjeta" key={p.id} style={{ marginBottom: '1rem' }}>
      <h2>Pedido {p.id} <OrderStatusBadge estado={p.estado} /></h2><p>{p.cliente} · {new Date(p.creadoEn).toLocaleString('es-CL')} · {formatearPrecio(p.total)}</p>
      <ul>{p.items.map(i => <li key={i.productId}>{i.nombre} × {i.quantity}</li>)}</ul>
      {puedeCambiar && SIGUIENTES[p.estado].map(s => <button className="boton secundario" disabled={busy} key={s} onClick={() => void cambiar(p.id, s)}>{ESTADOS_PEDIDO.find(e => e.valor === s)?.etiqueta}</button>)}
      {puedeCambiar && p.estado === 'CREADO' && <button className="boton secundario" disabled={busy} onClick={() => { setEditando(p.id); setEmail(p.email); setCantidades(Object.fromEntries(p.items.map(i => [i.productId, i.quantity]))); setCrear(true); }}>Editar</button>}
    </article>)}
  </>;
}
