import { STOCK_BAJO, formatearPrecio } from '../../catalogo';
import type { Producto } from '../../tipos';
import { Icono } from '../Icono';

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
        <span className="producto-inicial" aria-hidden="true">{producto.nombre.charAt(0).toUpperCase()}</span>
        <div className="producto-titulo">
          <h3>{producto.nombre}</h3>
          <span className="chip neutro">{producto.categoria}</span>
        </div>
      </div>
      {producto.descripcion && <p className="producto-descripcion">{producto.descripcion}</p>}
      <div className="producto-datos">
        <span className="producto-precio">{formatearPrecio(producto.precio)}</span>
        <span className={stockBajo ? 'stock stock-bajo' : 'stock'}>
          {stockBajo && <Icono nombre="alerta" tamano={13} />}
          {producto.stock} en stock
        </span>
      </div>
      {(onEditar || onEliminar) && (
        <div className="producto-acciones">
          {onEditar && (
            <button
              className="boton secundario chico"
              disabled={deshabilitado}
              onClick={() => onEditar(producto)}
            >
              <Icono nombre="editar" tamano={15} />
              Editar
            </button>
          )}
          {onEliminar && (
            <button
              className="boton peligro chico"
              disabled={deshabilitado}
              onClick={() => onEliminar(producto)}
            >
              <Icono nombre="basura" tamano={15} />
              Eliminar
            </button>
          )}
        </div>
      )}
    </article>
  );
}
