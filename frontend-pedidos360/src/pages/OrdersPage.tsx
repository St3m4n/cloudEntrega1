import { useEffect, useState } from 'react';
import { rolesDe, usePerfil } from '../auth/perfil';
import { ROLES, tieneAlgunRol } from '../auth/roles';
import { mensajeDe, useBff, useCuenta } from '../bff';
import { formatearPrecio, useProductos } from '../catalogo';
import { Icono } from '../components/Icono';
import { OrderStatusBadge } from '../components/orders/OrderStatusBadge';
import { Aviso, Cargando, EncabezadoPagina, EstadoVacio } from '../components/ui';
import { ESTADOS_PEDIDO } from '../pedidos';
import type { EstadoPedido, Pedido } from '../tipos';

const SIGUIENTES: Record<EstadoPedido, EstadoPedido[]> = {
  CREADO: ['ACEPTADO', 'CANCELADO'], ACEPTADO: ['EN_PREPARACION', 'CANCELADO'],
  EN_PREPARACION: ['DESPACHADO', 'CANCELADO'], DESPACHADO: ['ENTREGADO'], ENTREGADO: [], CANCELADO: [],
};
const ACCION: Record<EstadoPedido, string> = {
  CREADO: 'Crear', ACEPTADO: 'Aceptar', EN_PREPARACION: 'Pasar a preparación',
  DESPACHADO: 'Despachar', ENTREGADO: 'Marcar entregado', CANCELADO: 'Cancelar',
};
const FLUJO: EstadoPedido[] = ['CREADO', 'ACEPTADO', 'EN_PREPARACION', 'DESPACHADO', 'ENTREGADO'];
const etiqueta = (estado: EstadoPedido) => ESTADOS_PEDIDO.find(e => e.valor === estado)?.etiqueta ?? estado;

function Progreso({ estado }: { estado: EstadoPedido }) {
  const cancelado = estado === 'CANCELADO';
  const actual = cancelado ? -1 : FLUJO.indexOf(estado);
  return (
    <ol className={`progreso${cancelado ? ' cancelado' : ''}`} aria-label={`Estado: ${etiqueta(estado)}`}>
      {FLUJO.map((paso, i) => (
        <li key={paso} className={`${i <= actual ? 'hecho' : ''}${i === actual ? ' actual' : ''}`}>
          <span className="progreso-punto">{i <= actual && <Icono nombre="check" tamano={12} />}</span>
          <span>{etiqueta(paso)}</span>
        </li>
      ))}
    </ol>
  );
}

export function OrdersPage() {
  const llamar = useBff(); const cuenta = useCuenta(); const roles = rolesDe(usePerfil().estado);
  const puedeCrear = tieneAlgunRol(roles, [ROLES.cliente, ROLES.operador]);
  const puedeCambiar = tieneAlgunRol(roles, [ROLES.admin, ROLES.operador]);
  const catalogo = useProductos();
  const [pedidos, setPedidos] = useState<Pedido[] | null>(null); const [error, setError] = useState('');
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
      setCrear(false); setEditando(null); setCantidades({}); setRevision(n => n + 1); catalogo.recargar();
    } catch (e) { setError(mensajeDe(e)); } finally { setBusy(false); }
  }
  async function cambiar(id: number, status: string) {
    setBusy(true); setError('');
    try { await llamar(`/api/orders/${id}/status`, { method: 'PUT', body: JSON.stringify({ status }) }); setRevision(n => n + 1); catalogo.recargar(); }
    catch (e) { setError(mensajeDe(e)); } finally { setBusy(false); }
  }
  function fijarCantidad(id: number, valor: number, maximo: number) {
    setCantidades(c => ({ ...c, [id]: Math.max(0, Math.min(maximo, Number.isFinite(valor) ? valor : 0)) }));
  }

  const productos = catalogo.productos ?? [];
  const total = productos.reduce((suma, p) => suma + (cantidades[p.id] ?? 0) * p.precio, 0);
  const unidades = Object.values(cantidades).reduce((suma, q) => suma + q, 0);

  return <>
    <EncabezadoPagina titulo="Pedidos" descripcion="Creación, edición y seguimiento de pedidos."
      acciones={puedeCrear && !crear && <button className="boton primario" onClick={() => { setCrear(true); setEditando(null); setCantidades({}); }}><Icono nombre="mas" />Nuevo pedido</button>} />

    {error && <Aviso tipo="error" titulo="No se pudo completar la operación">{error}</Aviso>}

    {crear && <form className="tarjeta formulario" onSubmit={e => { e.preventDefault(); void guardar(); }}>
      <div className="panel-cabecera">
        <div>
          <h2>{editando === null ? 'Nuevo pedido' : `Editar pedido ${editando}`}</h2>
          <p className="subtitulo">Elige productos y cantidades. El stock se reserva al guardar.</p>
        </div>
      </div>
      <label className="campo" style={{ maxWidth: 360 }}>Email de notificación
        <input type="email" required value={email} onChange={e => setEmail(e.target.value)} />
      </label>
      {catalogo.error && <p role="alert" className="texto-error">{catalogo.error}</p>}
      {catalogo.cargando && !productos.length && <Cargando texto="Cargando productos..." />}
      <div className="nuevo-pedido-lista">
        {productos.map(p => {
          const q = cantidades[p.id] ?? 0;
          return <div key={p.id} className={`linea-producto${q > 0 ? ' seleccionada' : ''}`}>
            <div><strong>{p.nombre}</strong><small>{formatearPrecio(p.precio)} · {p.stock} en stock</small></div>
            <div className="cantidad">
              <button type="button" aria-label={`Quitar ${p.nombre}`} disabled={q <= 0} onClick={() => fijarCantidad(p.id, q - 1, p.stock)}><Icono nombre="menos" tamano={14} /></button>
              <input aria-label={`Cantidad de ${p.nombre}`} type="number" min="0" max={p.stock} value={q} onChange={e => fijarCantidad(p.id, Number(e.target.value), p.stock)} />
              <button type="button" aria-label={`Agregar ${p.nombre}`} disabled={q >= p.stock} onClick={() => fijarCantidad(p.id, q + 1, p.stock)}><Icono nombre="mas" tamano={14} /></button>
            </div>
            <span className="linea-subtotal">{q > 0 ? formatearPrecio(q * p.precio) : '—'}</span>
          </div>;
        })}
      </div>
      <div className="resumen-pedido">
        <span className="texto-suave">{unidades} {unidades === 1 ? 'unidad' : 'unidades'} · Total <strong>{formatearPrecio(total)}</strong></span>
        <div className="formulario-acciones">
          <button type="button" className="boton secundario" onClick={() => { setCrear(false); setEditando(null); }}>Cancelar</button>
          <button className="boton primario" disabled={busy || catalogo.cargando || unidades === 0}><Icono nombre="check" />Guardar pedido</button>
        </div>
      </div>
    </form>}

    <div className="filtros">
      <div className="pastillas" role="group" aria-label="Filtrar por estado">
        <button className={`pastilla${estado === '' ? ' activa' : ''}`} onClick={() => setEstado('')}>Todos</button>
        {ESTADOS_PEDIDO.map(e => <button key={e.valor} className={`pastilla${estado === e.valor ? ' activa' : ''}`} onClick={() => setEstado(e.valor)}>{e.etiqueta}</button>)}
      </div>
      <span className="filtros-espaciador" />
      <label>Desde <input type="date" value={desde} onChange={e => setDesde(e.target.value)} /></label>
      <label>Hasta <input type="date" value={hasta} onChange={e => setHasta(e.target.value)} /></label>
      <button className="boton secundario" onClick={() => setRevision(n => n + 1)}><Icono nombre="actualizar" />Actualizar</button>
    </div>

    {pedidos === null && !error && <Cargando texto="Cargando pedidos..." />}
    {pedidos?.length === 0 && <EstadoVacio titulo="No hay pedidos para mostrar" icono="pedidos">{estado || desde || hasta ? 'Prueba con otros filtros.' : 'Cuando se cree un pedido aparecerá aquí.'}</EstadoVacio>}

    <div className="pedidos">
      {pedidos?.map(p => <article className="tarjeta pedido" key={p.id}>
        <div className="pedido-cabecera">
          <div>
            <div className="pedido-id"><h2>Pedido #{p.id}</h2><OrderStatusBadge estado={p.estado} /></div>
            <div className="pedido-meta">
              <span><Icono nombre="usuario" tamano={14} />{p.cliente}</span>
              <span><Icono nombre="reloj" tamano={14} />{new Date(p.creadoEn).toLocaleString('es-CL')}</span>
              <span>Versión {p.version}</span>
            </div>
          </div>
          <div className="pedido-total"><span>Total</span><strong>{formatearPrecio(p.total)}</strong></div>
        </div>
        <Progreso estado={p.estado} />
        <div className="tabla-contenedor">
          <table className="tabla compacta">
            <thead><tr><th>Producto</th><th className="numero">Cantidad</th><th className="numero">Precio</th><th className="numero">Subtotal</th></tr></thead>
            <tbody>{p.items.map(i => <tr key={i.productId}><td>{i.nombre}</td><td className="numero">{i.quantity}</td><td className="numero">{formatearPrecio(i.precio)}</td><td className="numero">{formatearPrecio(i.precio * i.quantity)}</td></tr>)}</tbody>
          </table>
        </div>
        {puedeCambiar && SIGUIENTES[p.estado].length > 0 && <div className="pedido-pie">
          {p.estado === 'CREADO' && <button className="boton fantasma" disabled={busy} onClick={() => { setEditando(p.id); setEmail(p.email); setCantidades(Object.fromEntries(p.items.map(i => [i.productId, i.quantity]))); setCrear(true); window.scrollTo({ top: 0, behavior: 'smooth' }); }}><Icono nombre="editar" />Editar</button>}
          {[...SIGUIENTES[p.estado]].sort((a, b) => Number(b === 'CANCELADO') - Number(a === 'CANCELADO')).map(s =><button key={s} className={`boton ${s === 'CANCELADO' ? 'peligro' : 'primario'}`} disabled={busy} onClick={() => void cambiar(p.id, s)}>
            {s !== 'CANCELADO' && <Icono nombre="flecha" />}{ACCION[s]}
          </button>)}
        </div>}
      </article>)}
    </div>
  </>;
}
