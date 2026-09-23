import { STOCK_BAJO, formatearPrecio } from '../../catalogo';
import type { Producto } from '../../tipos';

interface Props {
  producto: Producto;
  onEditar?: (producto: Producto) => void;
  onEliminar?: (producto: Producto) => void;
  deshabilitado?: boolean;
}

export function ProductCard({ producto, onEditar, onEliminar, deshabilitado }: Props) {
  const stockBajo = producto.stock < STOCK_BAJO;

  return (
    <article className="tarjeta producto">
      <div className="producto-cabecera">
        <h3>{producto.nombre}</h3>
        <span className="chip">{producto.categoria}</span>
      </div>
      {producto.descripcion && <p className="producto-descripcion">{producto.descripcion}</p>}
      <div className="producto-datos">
        <span className="producto-precio">{formatearPrecio(producto.precio)}</span>
        <span className={stockBajo ? 'stock stock-bajo' : 'stock'}>
          Stock: {producto.stock}
          {stockBajo && ' (bajo)'}
        </span>
      </div>
      {(onEditar || onEliminar) && (
        <div className="producto-acciones">
          {onEditar && (
            <button
              className="boton secundario"
              disabled={deshabilitado}
              onClick={() => onEditar(producto)}
            >
              Editar
            </button>
          )}
          {onEliminar && (
            <button
              className="boton peligro"
              disabled={deshabilitado}
              onClick={() => onEliminar(producto)}
            >
              Eliminar
            </button>
          )}
        </div>
      )}
    </article>
  );
}
