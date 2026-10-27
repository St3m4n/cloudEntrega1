import type { EstadoPedido } from './tipos';

export const ESTADOS_PEDIDO: { valor: EstadoPedido; etiqueta: string }[] = [
  { valor: 'CREADO', etiqueta: 'Creado' },
  { valor: 'ACEPTADO', etiqueta: 'Aceptado' },
  { valor: 'EN_PREPARACION', etiqueta: 'En preparación' },
  { valor: 'DESPACHADO', etiqueta: 'Despachado' },
  { valor: 'ENTREGADO', etiqueta: 'Entregado' },
  { valor: 'CANCELADO', etiqueta: 'Cancelado' },
];

export const TIPOS_EVENTO = [
  'OrderCreated',
  'OrderAccepted',
  'OrderPreparing',
  'OrderDispatched',
  'OrderDelivered',
  'OrderCancelled',
];
