import type { ReactNode } from 'react';

export function Cargando({ texto = 'Cargando...' }: { texto?: string }) {
  return <p className="cargando" role="status">{texto}</p>;
}

export function Aviso({
  tipo,
  titulo,
  children,
}: {
  tipo: 'info' | 'error';
  titulo: string;
  children?: ReactNode;
}) {
  return (
    <div className={`aviso aviso-${tipo}`} role={tipo === 'error' ? 'alert' : 'note'}>
      <strong>{titulo}</strong>
      {children && <div className="aviso-cuerpo">{children}</div>}
    </div>
  );
}

export function EstadoVacio({ titulo, children }: { titulo: string; children?: ReactNode }) {
  return (
    <div className="vacio">
      <p className="vacio-titulo">{titulo}</p>
      {children && <p className="vacio-texto">{children}</p>}
    </div>
  );
}

export function Kpi({ titulo, valor, nota }: { titulo: string; valor: ReactNode; nota?: string }) {
  return (
    <div className="kpi">
      <span className="kpi-titulo">{titulo}</span>
      <span className="kpi-valor">{valor}</span>
      {nota && <span className="kpi-nota">{nota}</span>}
    </div>
  );
}

export function EncabezadoPagina({
  titulo,
  descripcion,
  acciones,
}: {
  titulo: string;
  descripcion?: string;
  acciones?: ReactNode;
}) {
  return (
    <div className="encabezado">
      <div>
        <h1>{titulo}</h1>
        {descripcion && <p className="subtitulo">{descripcion}</p>}
      </div>
      {acciones && <div className="encabezado-acciones">{acciones}</div>}
    </div>
  );
}
