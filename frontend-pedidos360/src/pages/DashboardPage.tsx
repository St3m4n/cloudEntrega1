import { Link } from 'react-router';
import { useEffect, useState } from 'react';
import { rolesDe, usePerfil } from '../auth/perfil';
import { ROLES, tieneAlgunRol } from '../auth/roles';
import { mensajeDe, useBff, useCuenta } from '../bff';
import { STOCK_BAJO, formatearPrecio, useProductos } from '../catalogo';
import { Icono } from '../components/Icono';
import type { NombreIcono } from '../components/Icono';
import { OrderList } from '../components/orders/OrderList';
import { Aviso, Cargando, Kpi } from '../components/ui';
import type { Pedido, Producto } from '../tipos';

function usePanel<T>(ruta: string) {
  const llamar = useBff(); const [datos, setDatos] = useState<T | null>(null); const [error, setError] = useState('');
  useEffect(() => { let activo = true; llamar<T>(ruta).then(d => { if (activo) setDatos(d); }).catch(e => { if (activo) setError(mensajeDe(e)); }); return () => { activo = false; }; }, [llamar, ruta]);
  return { datos, error };
}

export function DashboardPage() {
  const { estado, reintentar } = usePerfil();
  const cuenta = useCuenta();
  const roles = rolesDe(estado);

  const esAdmin = roles.includes(ROLES.admin);
  const esOperador = roles.includes(ROLES.operador);
  const esCliente = roles.includes(ROLES.cliente);
  const catalogo = useProductos(tieneAlgunRol(roles, [ROLES.admin, ROLES.operador]));

  const nombre = estado.tipo === 'listo' ? estado.perfil.nombre : null;

  return (
    <>
      <div className="bienvenida">
        <div>
          <h1>Hola, {nombre ?? cuenta?.name ?? cuenta?.username ?? ''}</h1>
          <p className="subtitulo">Resumen de actividad según tu rol.</p>
        </div>
        <span className="chip">{new Date().toLocaleDateString('es-CL', { weekday: 'long', day: 'numeric', month: 'long' })}</span>
      </div>

      {estado.tipo === 'cargando' && <Cargando texto="Cargando tu perfil..." />}

      {estado.tipo === 'error' && (
        <Aviso tipo="error" titulo="No se pudo obtener tu perfil desde el BFF">
          <p>{estado.mensaje}</p>
          <button className="boton secundario" onClick={reintentar}>
            Reintentar
          </button>
        </Aviso>
      )}

      {estado.tipo === 'listo' && roles.length === 0 && (
        <Aviso tipo="info" titulo="Tu usuario no tiene roles asignados">
          <p>
            Pide a un administrador que te asigne un rol (Administrador, Operador o Cliente)
            en la aplicación empresarial api-fullstack de Microsoft Entra ID. Después cierra
            sesión y vuelve a entrar para obtener un token con el rol.
          </p>
        </Aviso>
      )}

      {esAdmin && <ResumenAdmin productos={catalogo.productos} error={catalogo.error} />}
      {esOperador && <ResumenOperador productos={catalogo.productos} error={catalogo.error} />}
      {esCliente && <ResumenCliente />}
    </>
  );
}

interface ResumenProps {
  productos: Producto[] | null;
  error: string | null;
}

function valorCatalogo(productos: Producto[] | null, error: string | null, calcular: (p: Producto[]) => number) {
  if (error) return 'Error';
  return productos ? calcular(productos) : '...';
}

function ResumenAdmin({ productos, error }: ResumenProps) {
  const panel = usePanel<{ ordersCreated: number; sales: number }>('/api/report/kpis?range=last24h');
  return (
    <section className="seccion">
      <h2 className="seccion-titulo">Indicadores globales</h2>
      <div className="kpis">
        <Kpi icono="catalogo" titulo="Productos en catálogo" valor={valorCatalogo(productos, error, (p) => p.length)} />
        <Kpi
          icono="capas"
          titulo="Unidades en stock"
          valor={valorCatalogo(productos, error, (p) => p.reduce((suma, x) => suma + x.stock, 0))}
        />
        <Kpi icono="pedidos" titulo="Pedidos en 24 horas" valor={panel.datos?.ordersCreated ?? '…'} />
        <Kpi icono="dinero" titulo="Ventas en 24 horas" valor={panel.datos ? formatearPrecio(panel.datos.sales) : '…'} nota="Pedidos entregados" />
      </div>
      {error && <p className="texto-error">Catálogo: {error}</p>}
      {panel.error && <p role="alert" className="texto-error">{panel.error}</p>}
      <div className="accesos">
        <Acceso a="/reports" icono="reportes" titulo="Reportes" texto="Ventas, lead time y productos más vendidos." />
        <Acceso a="/audit" icono="auditoria" titulo="Auditoría" texto="Historial de eventos de cada pedido." />
        <Acceso a="/kafka" icono="kafka" titulo="Kafka" texto="Tópicos, DLT y consumer groups en vivo." />
      </div>
    </section>
  );
}

function ResumenOperador({ productos, error }: ResumenProps) {
  const panel = usePanel<Pedido[]>('/api/orders');
  return (
    <section className="seccion">
      <h2 className="seccion-titulo">Operación</h2>
      <div className="kpis">
        <Kpi icono="camion" titulo="Pedidos en curso" valor={panel.datos?.filter(p => !['ENTREGADO', 'CANCELADO'].includes(p.estado)).length ?? '…'} />
        <Kpi icono="reloj" titulo="Pendientes de aceptar" valor={panel.datos?.filter(p => p.estado === 'CREADO').length ?? '…'} />
        <Kpi
          icono="alerta"
          titulo={`Productos con stock bajo (< ${STOCK_BAJO})`}
          valor={valorCatalogo(productos, error, (p) => p.filter((x) => x.stock < STOCK_BAJO).length)}
        />
      </div>
      {panel.error && <p role="alert" className="texto-error">{panel.error}</p>}
      <div className="accesos">
        <Acceso a="/orders" icono="pedidos" titulo="Pedidos" texto="Acepta, prepara y despacha pedidos." />
        <Acceso a="/catalog" icono="catalogo" titulo="Catálogo" texto="Productos, precios y stock." />
      </div>
    </section>
  );
}

function ResumenCliente() {
  const panel = usePanel<Pedido[]>('/api/orders');
  return (
    <section className="seccion">
      <h2 className="seccion-titulo">Tus últimos pedidos</h2>
      {panel.error && <p role="alert" className="texto-error">{panel.error}</p>}
      <OrderList pedidos={panel.datos?.slice(0, 5) ?? []} />
      <div className="accesos">
        <Acceso a="/orders" icono="pedidos" titulo="Mis pedidos" texto="Crea un pedido o revisa su estado." />
      </div>
    </section>
  );
}

function Acceso({ a, icono, titulo, texto }: { a: string; icono: NombreIcono; titulo: string; texto: string }) {
  return (
    <Link to={a} className="acceso">
      <span className="acceso-icono"><Icono nombre={icono} /></span>
      <div>
        <strong>{titulo}</strong>
        <span>{texto}</span>
      </div>
    </Link>
  );
}
