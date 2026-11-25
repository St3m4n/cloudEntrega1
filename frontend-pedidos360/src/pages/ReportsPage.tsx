import { useEffect, useState } from 'react';
import { mensajeDe, useBff } from '../bff';
import { formatearPrecio } from '../catalogo';
import { BarrasHorizontales, Columnas } from '../components/graficos';
import { OrderStatusBadge } from '../components/orders/OrderStatusBadge';
import { EncabezadoPagina, EstadoVacio, Kpi, Panel } from '../components/ui';
import { ESTADOS_PEDIDO } from '../pedidos';
import type { EstadoPedido } from '../tipos';

interface Kpis { sales: number; averageLeadTimeMinutes: number; activeOrders: number; deliveredOrders: number; salesByHour: Record<string, number>; states: Record<string, number>; leadTimes: { id: number; minutes: number }[] }
interface Top { productId: number; nombre: string; quantity: number }

function pesosCorto(valor: number): string {
  if (valor >= 1_000_000) return `$${(valor / 1_000_000).toLocaleString('es-CL', { maximumFractionDigits: 1 })} M`;
  if (valor >= 1_000) return `$${Math.round(valor / 1_000).toLocaleString('es-CL')} mil`;
  return `$${valor}`;
}
const minutos = (v: number) => `${v.toFixed(1)} min`;
const unidades = (v: number) => `${v} ${v === 1 ? 'unidad' : 'unidades'}`;

export function ReportsPage() {
  const llamar = useBff(); const [range, setRange] = useState('last24h'); const [kpis, setKpis] = useState<Kpis | null>(null); const [top, setTop] = useState<Top[]>([]); const [error, setError] = useState('');
  useEffect(() => {
    let active = true;
    async function load() { try { const [k, t] = await Promise.all([llamar<Kpis>(`/api/report/kpis?range=${range}`), llamar<Top[]>(`/api/report/top-products?range=${range}`)]); if (active) { setKpis(k); setTop(t); setError(''); } } catch (e) { if (active) setError(mensajeDe(e)); } }
    void load(); const timer = window.setInterval(() => void load(), 10000); return () => { active = false; window.clearInterval(timer); };
  }, [llamar, range]);

  const porHora = Object.entries(kpis?.salesByHour ?? {}).sort(([a], [b]) => a.localeCompare(b)).map(([h, v]) => {
    const fecha = new Date(h);
    const hora = fecha.toLocaleTimeString('es-CL', { hour: '2-digit', minute: '2-digit' });
    return { etiqueta: range === 'last24h' ? hora : `${fecha.toLocaleDateString('es-CL', { day: '2-digit', month: '2-digit' })} ${hora}`, valor: v };
  });
  const tiempos = (kpis?.leadTimes ?? []).map(l => ({ etiqueta: `Pedido #${l.id}`, valor: l.minutes }));
  const masVendidos = top.map(p => ({ etiqueta: p.nombre, valor: p.quantity }));
  const estados = Object.entries(kpis?.states ?? {}).filter(([, n]) => n > 0);

  return <>
    <EncabezadoPagina titulo="Reportes y KPIs" descripcion="Proyección alimentada por eventos de Kafka (orders.events). Se actualiza cada 10 segundos."
      acciones={<div className="pastillas" role="group" aria-label="Rango">
        <button className={`pastilla${range === 'last24h' ? ' activa' : ''}`} onClick={() => setRange('last24h')}>Últimas 24 horas</button>
        <button className={`pastilla${range === 'last7d' ? ' activa' : ''}`} onClick={() => setRange('last7d')}>Últimos 7 días</button>
      </div>} />
    {error && <p role="alert" className="texto-error">{error}</p>}

    <div className="kpis">
      <Kpi icono="dinero" titulo="Ventas" valor={kpis ? formatearPrecio(kpis.sales) : '…'} nota="Pedidos entregados" />
      <Kpi icono="reloj" titulo="Lead time promedio" valor={kpis ? minutos(kpis.averageLeadTimeMinutes) : '…'} nota="Desde creación hasta entrega" />
      <Kpi icono="camion" titulo="Pedidos activos" valor={kpis?.activeOrders ?? '…'} nota="Aún no entregados ni cancelados" />
      <Kpi icono="check" titulo="Entregados" valor={kpis?.deliveredOrders ?? '…'} />
    </div>

    {estados.length > 0 && <Panel titulo="Pedidos por estado">
      <div className="estados-resumen">
        {estados.map(([e, n]) => ESTADOS_PEDIDO.some(x => x.valor === e)
          ? <span key={e} className="estados-resumen"><OrderStatusBadge estado={e as EstadoPedido} /> <strong>{n}</strong></span>
          : <span key={e} className="chip neutro">{e}: {n}</span>)}
      </div>
    </Panel>}

    <Panel titulo="Ventas por hora" descripcion="Monto de pedidos entregados en cada hora.">
      {porHora.length ? <Columnas datos={porHora} formato={formatearPrecio} formatoEje={pesosCorto} columna="Hora" />
        : <EstadoVacio titulo="Sin ventas en este rango" icono="reportes">Las ventas aparecen cuando un pedido se marca como entregado.</EstadoVacio>}
    </Panel>

    <div className="rejilla-2">
      <Panel titulo="Productos más vendidos" descripcion="Unidades en pedidos entregados.">
        {masVendidos.length ? <BarrasHorizontales datos={masVendidos} formato={unidades} columna="Producto" />
          : <EstadoVacio titulo="Sin ventas en este rango" icono="catalogo" />}
      </Panel>
      <Panel titulo="Tiempo hasta entrega" descripcion="Minutos entre la creación y la entrega de cada pedido.">
        {tiempos.length ? <BarrasHorizontales datos={tiempos} formato={minutos} columna="Pedido" />
          : <EstadoVacio titulo="Sin entregas en este rango" icono="reloj" />}
      </Panel>
    </div>
  </>;
}
