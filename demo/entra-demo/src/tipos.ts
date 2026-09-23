export interface Perfil {
  nombre: string | null;
  usuario: string | null;
  roles: string[];
}

export interface Producto {
  id: number;
  nombre: string;
  descripcion: string | null;
  categoria: string;
  precio: number;
  stock: number;
}

export type ProductoDatos = Omit<Producto, 'id'>;

export type EstadoPedido =
  | 'CREADO'
  | 'ACEPTADO'
  | 'EN_PREPARACION'
  | 'DESPACHADO'
  | 'ENTREGADO'
  | 'CANCELADO';

export interface Pedido {
  id: number;
  cliente: string;
  total: number;
  estado: EstadoPedido;
  creadoEn: string;
}
