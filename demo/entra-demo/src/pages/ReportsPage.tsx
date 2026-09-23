import { Aviso, EncabezadoPagina, EstadoVacio, Kpi } from '../components/ui';

const NOTA = 'Requiere ms-pedidos360-report';

const GRAFICOS = [
  { titulo: 'Ventas por hora', detalle: 'Monto vendido agrupado por hora.' },
  { titulo: 'Lead time de pedidos', detalle: 'Tiempo entre la creación y la entrega de cada pedido.' },
  { titulo: 'Productos más vendidos', detalle: 'Ranking de productos del rango seleccionado.' },
];

export function ReportsPage() {
  return (
    <>
      <EncabezadoPagina
        titulo="Reportes y KPIs"
        descripcion="Indicadores de operación en tiempo real."
        acciones={
          <select className="selector" disabled defaultValue="last24h" aria-label="Rango">
            <option value="last24h">Últimas 24 horas</option>
            <option value="last7d">Últimos 7 días</option>
          </select>
        }
      />

      <Aviso tipo="info" titulo="Módulo pendiente de backend">
        <p>
          Los datos se obtendrán desde ms-pedidos360-report (GET /api/report/kpis y
          /api/report/top-products), que consume los eventos de pedidos publicados en Kafka.
        </p>
      </Aviso>

      <div className="kpis">
        <Kpi titulo="Ventas" valor="—" nota={NOTA} />
        <Kpi titulo="Lead time promedio" valor="—" nota={NOTA} />
        <Kpi titulo="Pedidos activos" valor="—" nota={NOTA} />
        <Kpi titulo="Pedidos entregados" valor="—" nota={NOTA} />
      </div>

      <div className="grilla grilla-graficos">
        {GRAFICOS.map((g) => (
          <section key={g.titulo} className="tarjeta">
            <h2>{g.titulo}</h2>
            <EstadoVacio titulo="Sin datos">{g.detalle}</EstadoVacio>
          </section>
        ))}
      </div>
    </>
  );
}
