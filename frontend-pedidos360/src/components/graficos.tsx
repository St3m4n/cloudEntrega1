// Gráficos de una sola serie en HTML/CSS: un solo tono (el de la marca), barras finas
// con extremo redondeado, tooltip al pasar el mouse y tabla equivalente para accesibilidad.

export interface Punto {
  etiqueta: string;
  valor: number;
}

function TablaDatos({ datos, formato, columna }: { datos: Punto[]; formato: (v: number) => string; columna: string }) {
  return (
    <details className="grafico-tabla">
      <summary>Ver como tabla</summary>
      <table className="tabla compacta">
        <thead><tr><th>{columna}</th><th className="numero">Valor</th></tr></thead>
        <tbody>{datos.map(d => <tr key={d.etiqueta}><td>{d.etiqueta}</td><td className="numero">{formato(d.valor)}</td></tr>)}</tbody>
      </table>
    </details>
  );
}

/** Barras horizontales: magnitud por categoría (productos, pedidos). */
export function BarrasHorizontales({ datos, formato, columna }: { datos: Punto[]; formato: (v: number) => string; columna: string }) {
  const maximo = Math.max(1, ...datos.map(d => d.valor));
  return (
    <>
      <ul className="barras-h">
        {datos.map(d => (
          <li key={d.etiqueta} className="barra-h" tabIndex={0}>
            <span className="barra-h-etiqueta" title={d.etiqueta}>{d.etiqueta}</span>
            <span className="barra-h-pista">
              <span className="barra-h-relleno" style={{ width: `${Math.max(2, (d.valor / maximo) * 100)}%` }} />
              <span className="barra-h-valor">{formato(d.valor)}</span>
            </span>
            <span className="tooltip" role="tooltip"><strong>{d.etiqueta}</strong>{formato(d.valor)}</span>
          </li>
        ))}
      </ul>
      <TablaDatos datos={datos} formato={formato} columna={columna} />
    </>
  );
}

function marcas(maximo: number): number[] {
  const paso = 10 ** Math.floor(Math.log10(maximo));
  const tope = Math.ceil(maximo / paso) * paso;
  return [0, tope / 2, tope];
}

/** Columnas: evolución en el tiempo (ventas por hora). */
export function Columnas({
  datos,
  formato,
  formatoEje,
  columna,
}: {
  datos: Punto[];
  formato: (v: number) => string;
  formatoEje: (v: number) => string;
  columna: string;
}) {
  const ejes = marcas(Math.max(1, ...datos.map(d => d.valor)));
  const tope = ejes[ejes.length - 1];
  const mayor = datos.reduce((m, d) => (d.valor > m.valor ? d : m), datos[0]);
  return (
    <>
      <div className="columnas">
        <div className="columnas-eje" aria-hidden="true">
          {[...ejes].reverse().map(v => <span key={v}>{formatoEje(v)}</span>)}
        </div>
        <div className="columnas-area">
          {ejes.map(v => <span key={v} className="columnas-guia" style={{ bottom: `${(v / tope) * 100}%` }} />)}
          {datos.map(d => (
            <div key={d.etiqueta} className="columna" tabIndex={0}>
              <div className="columna-barra" style={{ height: `${Math.max(1.5, (d.valor / tope) * 100)}%` }}>
                {d === mayor && <span className="columna-valor">{formato(d.valor)}</span>}
              </div>
              <span className="columna-etiqueta">{d.etiqueta}</span>
              <span className="tooltip" role="tooltip"><strong>{d.etiqueta}</strong>{formato(d.valor)}</span>
            </div>
          ))}
        </div>
      </div>
      <TablaDatos datos={datos} formato={formato} columna={columna} />
    </>
  );
}
