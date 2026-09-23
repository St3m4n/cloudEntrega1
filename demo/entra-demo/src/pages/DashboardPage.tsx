import { Link } from 'react-router';
import { rolesDe, usePerfil } from '../auth/perfil';
import { ROLES, tieneAlgunRol } from '../auth/roles';
import { useCuenta } from '../bff';
import { STOCK_BAJO, useProductos } from '../catalogo';
import { OrderList } from '../components/orders/OrderList';
import { Aviso, Cargando, EncabezadoPagina, Kpi } from '../components/ui';
import type { Producto } from '../tipos';

const PENDIENTE = 'Requiere ms-pedidos360-orders';

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
  return (
    <section className="seccion">
      <h2>Indicadores globales</h2>
      <div className="kpis">
        <Kpi titulo="Productos en catálogo" valor={valorCatalogo(productos, error, (p) => p.length)} />
        <Kpi
          titulo="Unidades en stock"
          valor={valorCatalogo(productos, error, (p) => p.reduce((suma, x) => suma + x.stock, 0))}
        />
        <Kpi titulo="Pedidos del día" valor="—" nota={PENDIENTE} />
        <Kpi titulo="Ventas del día" valor="—" nota="Requiere ms-pedidos360-report" />
      </div>
      {error && <p className="texto-error">Catálogo: {error}</p>}
      <p>
        <Link to="/reports">Ver reportes</Link> · <Link to="/audit">Ver auditoría</Link>
      </p>
    </section>
  );
}

function ResumenOperador({ productos, error }: ResumenProps) {
  return (
    <section className="seccion">
      <h2>Operación</h2>
      <div className="kpis">
        <Kpi titulo="Pedidos en curso" valor="—" nota={PENDIENTE} />
        <Kpi titulo="Pedidos pendientes de aceptar" valor="—" nota={PENDIENTE} />
        <Kpi
          titulo={`Productos con stock bajo (< ${STOCK_BAJO})`}
          valor={valorCatalogo(productos, error, (p) => p.filter((x) => x.stock < STOCK_BAJO).length)}
        />
      </div>
      <p>
        <Link to="/orders">Ir a pedidos</Link> · <Link to="/catalog">Ir al catálogo</Link>
      </p>
    </section>
  );
}

function ResumenCliente() {
  return (
    <section className="seccion">
      <h2>Tus últimos pedidos</h2>
      <OrderList pedidos={[]} />
      <p>
        <Link to="/orders">Ver todos mis pedidos</Link>
      </p>
    </section>
  );
}
