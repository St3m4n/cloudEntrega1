import { useEffect, useState } from 'react';
import { mensajeDe, useBff } from '../bff';
import { formatearPrecio } from '../catalogo';
import { EncabezadoPagina, Kpi } from '../components/ui';
interface Kpis { sales: number; averageLeadTimeMinutes: number; activeOrders: number; deliveredOrders: number; salesByHour: Record<string, number>; states: Record<string, number>; leadTimes: { id: number; minutes: number }[] }
interface Top { productId: number; nombre: string; quantity: number }
export function ReportsPage() {
  const llamar = useBff(); const [range, setRange] = useState('last24h'); const [kpis, setKpis] = useState<Kpis | null>(null); const [top, setTop] = useState<Top[]>([]); const [error, setError] = useState('');
  useEffect(() => {
    let active = true;
    async function load() { try { const [k, t] = await Promise.all([llamar<Kpis>(`/api/report/kpis?range=${range}`), llamar<Top[]>(`/api/report/top-products?range=${range}`)]); if (active) { setKpis(k); setTop(t); setError(''); } } catch (e) { if (active) setError(mensajeDe(e)); } }
    void load(); const timer = window.setInterval(() => void load(), 10000); return () => { active = false; window.clearInterval(timer); };
  }, [llamar, range]);
  return <><EncabezadoPagina titulo="Reportes y KPIs" descripcion="Ventas de pedidos entregados. Actualización cada 10 segundos." acciones={<select value={range} onChange={e => setRange(e.target.value)}><option value="last24h">Últimas 24 horas</option><option value="last7d">Últimos 7 días</option></select>} />
    {error && <p role="alert" className="texto-error">{error}</p>}
    <div className="kpis"><Kpi titulo="Ventas" valor={kpis ? formatearPrecio(kpis.sales) : '…'} /><Kpi titulo="Lead time promedio" valor={kpis ? `${kpis.averageLeadTimeMinutes.toFixed(1)} min` : '…'} /><Kpi titulo="Pedidos activos" valor={kpis?.activeOrders ?? '…'} /><Kpi titulo="Entregados" valor={kpis?.deliveredOrders ?? '…'} /></div>
    <section className="tarjeta"><h2>Ventas por hora</h2>{Object.entries(kpis?.salesByHour ?? {}).map(([h, v]) => <p key={h}>{new Date(h).toLocaleString('es-CL')} · {formatearPrecio(v)} <meter min={0} max={Math.max(1, ...Object.values(kpis?.salesByHour ?? {}))} value={v} /></p>)}</section>
    <section className="tarjeta"><h2>Tiempo hasta entrega</h2>{(kpis?.leadTimes ?? []).map(l => <p key={l.id}>Pedido {l.id}: {l.minutes.toFixed(1)} minutos</p>)}</section>
    <section className="tarjeta"><h2>Productos más vendidos</h2>{top.length ? <ol>{top.map(p => <li key={p.productId}>{p.nombre}: {p.quantity} unidades</li>)}</ol> : <p>No hay ventas en este rango.</p>}</section>
  </>;
}
