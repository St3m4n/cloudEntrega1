import { useMemo, useState } from 'react';
import { rolesDe, usePerfil } from '../auth/perfil';
import { ROLES } from '../auth/roles';
import { mensajeDe, useBff } from '../bff';
import { catalogoApi, useProductos } from '../catalogo';
import { ProductCard } from '../components/catalog/ProductCard';
import { ProductForm } from '../components/catalog/ProductForm';
import { Aviso, Cargando, EncabezadoPagina, EstadoVacio } from '../components/ui';
import type { Producto, ProductoDatos } from '../tipos';

type Edicion = { modo: 'nuevo' } | { modo: 'editar'; producto: Producto } | null;

export function CatalogPage() {
  const { estado } = usePerfil();
  const esAdmin = rolesDe(estado).includes(ROLES.admin);
  const llamar = useBff();
  const api = useMemo(() => catalogoApi(llamar), [llamar]);
  const { productos, error, cargando, recargar } = useProductos();

  const [edicion, setEdicion] = useState<Edicion>(null);
  const [ocupado, setOcupado] = useState(false);
  const [errorAccion, setErrorAccion] = useState('');
  const [busqueda, setBusqueda] = useState('');

  const filtrados = useMemo(() => {
    const texto = busqueda.trim().toLowerCase();
    if (!productos || !texto) return productos ?? [];
    return productos.filter(
      (p) => p.nombre.toLowerCase().includes(texto) || p.categoria.toLowerCase().includes(texto),
    );
  }, [productos, busqueda]);

  async function ejecutar(accion: () => Promise<unknown>) {
    setOcupado(true);
    setErrorAccion('');
    try {
      await accion();
      recargar();
      return true;
    } catch (e) {
      setErrorAccion(mensajeDe(e));
      return false;
    } finally {
      setOcupado(false);
    }
  }

  async function guardar(datos: ProductoDatos) {
    if (!edicion) return;
    const ok = await ejecutar(() =>
      edicion.modo === 'nuevo' ? api.crear(datos) : api.actualizar(edicion.producto.id, datos),
    );
    if (ok) setEdicion(null);
  }

  async function eliminar(producto: Producto) {
    if (!window.confirm(`¿Eliminar "${producto.nombre}" del catálogo?`)) return;
    await ejecutar(() => api.eliminar(producto.id));
  }

  return (
    <>
      <EncabezadoPagina
        titulo="Catálogo de productos"
        descripcion="Productos, precios y stock disponible (ms-pedidos360-catalogo)."
        acciones={
          esAdmin && (
            <button
              className="boton primario"
              disabled={ocupado || edicion !== null}
              onClick={() => setEdicion({ modo: 'nuevo' })}
            >
              Nuevo producto
            </button>
          )
        }
      />

      {!esAdmin && (
        <Aviso tipo="info" titulo="Solo lectura">
          <p>La creación y edición del catálogo corresponde al rol Administrador.</p>
        </Aviso>
      )}

      {errorAccion && <Aviso tipo="error" titulo="No se pudo completar la operación">{errorAccion}</Aviso>}

      {edicion && (
        <ProductForm
          key={edicion.modo === 'editar' ? edicion.producto.id : 'nuevo'}
          inicial={edicion.modo === 'editar' ? edicion.producto : undefined}
          guardando={ocupado}
          onGuardar={(datos) => void guardar(datos)}
          onCancelar={() => setEdicion(null)}
        />
      )}

      <div className="barra-herramientas">
        <input
          type="search"
          className="buscador"
          placeholder="Buscar por nombre o categoría"
          aria-label="Buscar productos"
          value={busqueda}
          onChange={(e) => setBusqueda(e.target.value)}
        />
        <button className="boton secundario" disabled={ocupado} onClick={recargar}>
          Actualizar
        </button>
      </div>

      {cargando && <Cargando texto="Cargando productos..." />}
      {error && (
        <Aviso tipo="error" titulo="No se pudo cargar el catálogo">
          {error}
        </Aviso>
      )}

      {productos && filtrados.length === 0 && (
        <EstadoVacio titulo="No hay productos para mostrar">
          {busqueda ? 'Prueba con otra búsqueda.' : 'El catálogo está vacío.'}
        </EstadoVacio>
      )}

      <div className="grilla">
        {filtrados.map((producto) => (
          <ProductCard
            key={producto.id}
            producto={producto}
            deshabilitado={ocupado || edicion !== null}
            onEditar={esAdmin ? (p) => setEdicion({ modo: 'editar', producto: p }) : undefined}
            onEliminar={esAdmin ? (p) => void eliminar(p) : undefined}
          />
        ))}
      </div>
    </>
  );
}
