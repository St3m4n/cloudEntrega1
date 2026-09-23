import { useCallback, useEffect, useState } from 'react';
import { mensajeDe, useBff } from './bff';
import type { LlamarBff } from './bff';
import type { Producto, ProductoDatos } from './tipos';

const RUTA = '/api/catalog/products';

export const STOCK_BAJO = 30;

const formatoPeso = new Intl.NumberFormat('es-CL', {
  style: 'currency',
  currency: 'CLP',
});

export function formatearPrecio(valor: number): string {
  return formatoPeso.format(valor);
}

export function catalogoApi(llamar: LlamarBff) {
  return {
    listar: () => llamar<Producto[]>(RUTA),
    crear: (datos: ProductoDatos) =>
      llamar<Producto>(RUTA, { method: 'POST', body: JSON.stringify(datos) }),
    actualizar: (id: number, datos: ProductoDatos) =>
      llamar<Producto>(`${RUTA}/${id}`, { method: 'PUT', body: JSON.stringify(datos) }),
    eliminar: (id: number) => llamar<void>(`${RUTA}/${id}`, { method: 'DELETE' }),
  };
}

interface Resultado {
  intento: number;
  productos?: Producto[];
  error?: string;
}

export function useProductos(activo = true) {
  const llamar = useBff();
  const [intento, setIntento] = useState(0);
  const [resultado, setResultado] = useState<Resultado | null>(null);

  useEffect(() => {
    if (!activo) return;

    let cancelado = false;
    catalogoApi(llamar)
      .listar()
      .then(
        (productos) => {
          if (!cancelado) setResultado({ intento, productos });
        },
        (error: unknown) => {
          if (!cancelado) setResultado({ intento, error: mensajeDe(error) });
        },
      );
    return () => {
      cancelado = true;
    };
  }, [activo, llamar, intento]);

  const recargar = useCallback(() => setIntento((n) => n + 1), []);
  const vigente = resultado?.intento === intento;

  return {
    // Se mantienen los productos anteriores mientras se recarga.
    productos: resultado?.productos ?? null,
    error: vigente ? (resultado?.error ?? null) : null,
    cargando: activo && !vigente,
    recargar,
  };
}
