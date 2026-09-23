import { rolesDe, usePerfil } from '../auth/perfil';
import { ROLES, tieneAlgunRol } from '../auth/roles';
import { OrderList } from '../components/orders/OrderList';
import { OrderStatusBadge } from '../components/orders/OrderStatusBadge';
import { Aviso, EncabezadoPagina } from '../components/ui';
import { ESTADOS_PEDIDO } from '../pedidos';

const FLUJO = ESTADOS_PEDIDO.filter((e) => e.valor !== 'CANCELADO');

export function OrdersPage() {
  const roles = rolesDe(usePerfil().estado);
  const puedeCrear = tieneAlgunRol(roles, [ROLES.cliente, ROLES.operador]);
  const puedeCambiarEstado = tieneAlgunRol(roles, [ROLES.admin, ROLES.operador]);

  return (
    <>
      <EncabezadoPagina
        titulo="Pedidos"
        descripcion="Listado, creación y seguimiento de pedidos."
        acciones={
          puedeCrear && (
            <button className="boton primario" disabled title="Disponible con ms-pedidos360-orders">
              Nuevo pedido
            </button>
          )
        }
      />

      <Aviso tipo="info" titulo="Módulo pendiente de backend">
        <p>
          Esta pantalla consumirá ms-pedidos360-orders a través del BFF (JWT → API Gateway →
          BFF → orders). {puedeCambiarEstado
            ? 'Con tu rol podrás cambiar el estado de los pedidos.'
            : 'Con tu rol podrás crear pedidos y seguir su estado.'}
        </p>
      </Aviso>

      <fieldset className="filtros" disabled>
        <legend>Filtros</legend>
        <label>
          Estado
          <select defaultValue="">
            <option value="">Todos</option>
            {ESTADOS_PEDIDO.map((e) => (
              <option key={e.valor} value={e.valor}>
                {e.etiqueta}
              </option>
            ))}
          </select>
        </label>
        <label>
          Desde
          <input type="date" />
        </label>
        <label>
          Hasta
          <input type="date" />
        </label>
      </fieldset>

      <OrderList pedidos={[]} />

      <section className="seccion">
        <h2>Flujo de estados</h2>
        <div className="flujo">
          {FLUJO.map((e, i) => (
            <span key={e.valor} className="flujo-paso">
              {i > 0 && <span className="flujo-flecha" aria-hidden="true">→</span>}
              <OrderStatusBadge estado={e.valor} />
            </span>
          ))}
        </div>
        <p className="nota">
          Un pedido puede pasar a <OrderStatusBadge estado="CANCELADO" /> antes de ser entregado.
          No se puede despachar un pedido que no fue aceptado, y el stock se descuenta al aceptarlo.
        </p>
      </section>
    </>
  );
}
