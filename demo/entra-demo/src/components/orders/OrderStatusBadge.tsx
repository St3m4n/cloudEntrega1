import { ESTADOS_PEDIDO } from '../../pedidos';
import type { EstadoPedido } from '../../tipos';

export function OrderStatusBadge({ estado }: { estado: EstadoPedido }) {
  const etiqueta = ESTADOS_PEDIDO.find((e) => e.valor === estado)?.etiqueta ?? estado;
  return <span className={`insignia estado-${estado.toLowerCase()}`}>{etiqueta}</span>;
}
