import { Link } from 'react-router';
import { useEffect, useState } from 'react';
import { rolesDe, usePerfil } from '../auth/perfil';
import { ROLES, tieneAlgunRol } from '../auth/roles';
import { mensajeDe, useBff, useCuenta } from '../bff';
import { STOCK_BAJO, useProductos } from '../catalogo';
import { OrderList } from '../components/orders/OrderList';
import { Aviso, Cargando, EncabezadoPagina, Kpi } from '../components/ui';
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
      <EncabezadoPagina
        titulo={`Hola, ${nombre ?? cuenta?.name ?? cuenta?.username ?? ''}`}
        descripcion="Resumen de actividad según tu rol."
      />

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
      <h2>Indicadores globales</h2>
      <div className="kpis">
        <Kpi titulo="Productos en catálogo" valor={valorCatalogo(productos, error, (p) => p.length)} />
        <Kpi
          titulo="Unidades en stock"
          valor={valorCatalogo(productos, error, (p) => p.reduce((suma, x) => suma + x.stock, 0))}
        />
        <Kpi titulo="Pedidos en 24 horas" valor={panel.datos?.ordersCreated ?? '…'} />
        <Kpi titulo="Ventas en 24 horas (CLP)" valor={panel.datos?.sales ?? '…'} />
      </div>
      {error && <p className="texto-error">Catálogo: {error}</p>}
      {panel.error && <p role="alert" className="texto-error">{panel.error}</p>}
      <p>
        <Link to="/reports">Ver reportes</Link> · <Link to="/audit">Ver auditoría</Link>
      </p>
    </section>
  );
}

function ResumenOperador({ productos, error }: ResumenProps) {
  const panel = usePanel<Pedido[]>('/api/orders');
  return (
    <section className="seccion">
      <h2>Operación</h2>
      <div className="kpis">
        <Kpi titulo="Pedidos en curso" valor={panel.datos?.filter(p => !['ENTREGADO', 'CANCELADO'].includes(p.estado)).length ?? '…'} />
        <Kpi titulo="Pedidos pendientes de aceptar" valor={panel.datos?.filter(p => p.estado === 'CREADO').length ?? '…'} />
        <Kpi
          titulo={`Productos con stock bajo (< ${STOCK_BAJO})`}
          valor={valorCatalogo(productos, error, (p) => p.filter((x) => x.stock < STOCK_BAJO).length)}
        />
      </div>
      {panel.error && <p role="alert" className="texto-error">{panel.error}</p>}
      <p>
        <Link to="/orders">Ir a pedidos</Link> · <Link to="/catalog">Ir al catálogo</Link>
      </p>
    </section>
  );
}

function ResumenCliente() {
  const panel = usePanel<Pedido[]>('/api/orders');
  return (
    <section className="seccion">
      <h2>Tus últimos pedidos</h2>
      {panel.error && <p role="alert" className="texto-error">{panel.error}</p>}
      <OrderList pedidos={panel.datos?.slice(0, 5) ?? []} />
      <p>
        <Link to="/orders">Ver todos mis pedidos</Link>
      </p>
    </section>
  );
}
