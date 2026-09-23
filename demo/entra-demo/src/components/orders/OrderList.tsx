import { formatearPrecio } from '../../catalogo';
import type { Pedido } from '../../tipos';
import { EstadoVacio } from '../ui';
import { OrderStatusBadge } from './OrderStatusBadge';

export function OrderList({ pedidos }: { pedidos: Pedido[] }) {
  if (pedidos.length === 0) {
    return (
      <EstadoVacio titulo="No hay pedidos para mostrar">
        Los pedidos se cargarán desde ms-pedidos360-orders a través del BFF cuando ese
        microservicio esté disponible.
      </EstadoVacio>
    );
  }

  return (
    <div className="tabla-contenedor">
      <table className="tabla">
        <thead>
          <tr>
            <th>N.º</th>
            <th>Cliente</th>
            <th>Fecha</th>
            <th>Total</th>
            <th>Estado</th>
          </tr>
        </thead>
        <tbody>
          {pedidos.map((pedido) => (
            <tr key={pedido.id}>
              <td>{pedido.id}</td>
              <td>{pedido.cliente}</td>
              <td>{new Date(pedido.creadoEn).toLocaleString('es-CL')}</td>
              <td>{formatearPrecio(pedido.total)}</td>
              <td>
                <OrderStatusBadge estado={pedido.estado} />
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
