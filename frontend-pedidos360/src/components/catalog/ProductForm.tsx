import { useState } from 'react';
import type { FormEvent } from 'react';
import type { Producto, ProductoDatos } from '../../tipos';

interface Props {
  inicial?: Producto;
  guardando: boolean;
  onGuardar: (datos: ProductoDatos) => void;
  onCancelar: () => void;
}

export function ProductForm({ inicial, guardando, onGuardar, onCancelar }: Props) {
  const [nombre, setNombre] = useState(inicial?.nombre ?? '');
  const [categoria, setCategoria] = useState(inicial?.categoria ?? '');
  const [descripcion, setDescripcion] = useState(inicial?.descripcion ?? '');
  const [precio, setPrecio] = useState(inicial ? String(inicial.precio) : '');
  const [stock, setStock] = useState(inicial ? String(inicial.stock) : '');

  function enviar(evento: FormEvent) {
    evento.preventDefault();
    onGuardar({
      nombre: nombre.trim(),
      categoria: categoria.trim(),
      descripcion: descripcion.trim() || null,
      precio: Number(precio),
      stock: Number(stock),
    });
  }

  return (
    <form className="tarjeta formulario" onSubmit={enviar}>
      <h2>{inicial ? `Editar: ${inicial.nombre}` : 'Nuevo producto'}</h2>
      <div className="campos">
        <label>
          Nombre
          <input
            value={nombre}
            onChange={(e) => setNombre(e.target.value)}
            required
            maxLength={100}
          />
        </label>
        <label>
          Categoría
          <input
            value={categoria}
            onChange={(e) => setCategoria(e.target.value)}
            required
            maxLength={50}
          />
        </label>
        <label className="campo-ancho">
          Descripción
          <input
            value={descripcion}
            onChange={(e) => setDescripcion(e.target.value)}
            maxLength={255}
          />
        </label>
        <label>
          Precio (CLP)
          <input
            type="number"
            min={0}
            step={1}
            value={precio}
            onChange={(e) => setPrecio(e.target.value)}
            required
          />
        </label>
        <label>
          Stock
          <input
            type="number"
            min={0}
            step={1}
            value={stock}
            onChange={(e) => setStock(e.target.value)}
            required
          />
        </label>
      </div>
      <div className="formulario-acciones">
        <button type="submit" className="boton primario" disabled={guardando}>
          {guardando ? 'Guardando...' : 'Guardar'}
        </button>
        <button type="button" className="boton secundario" onClick={onCancelar} disabled={guardando}>
          Cancelar
        </button>
      </div>
    </form>
  );
}
