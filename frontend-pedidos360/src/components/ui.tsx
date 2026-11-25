import type { ReactNode } from 'react';
import { Icono } from './Icono';
import type { NombreIcono } from './Icono';

export function Cargando({ texto = 'Cargando...' }: { texto?: string }) {
  return (
    <p className="cargando" role="status">
      <span className="spinner" aria-hidden="true" />
      {texto}
    </p>
  );
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
      <Icono nombre={tipo === 'error' ? 'alerta' : 'check'} />
      <div>
        <strong>{titulo}</strong>
        {children && <div className="aviso-cuerpo">{children}</div>}
      </div>
    </div>
  );
}

export function EstadoVacio({
  titulo,
  icono = 'capas',
  children,
}: {
  titulo: string;
  icono?: NombreIcono;
  children?: ReactNode;
}) {
  return (
    <div className="vacio">
      <span className="vacio-icono"><Icono nombre={icono} tamano={22} /></span>
      <p className="vacio-titulo">{titulo}</p>
      {children && <p className="vacio-texto">{children}</p>}
    </div>
  );
}

export function Kpi({
  titulo,
  valor,
  nota,
  icono,
}: {
  titulo: string;
  valor: ReactNode;
  nota?: string;
  icono?: NombreIcono;
}) {
  return (
    <div className="kpi">
      <div className="kpi-cabecera">
        <span className="kpi-titulo">{titulo}</span>
        {icono && <span className="kpi-icono"><Icono nombre={icono} /></span>}
      </div>
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

/** Tarjeta con título y acciones opcionales a la derecha. */
export function Panel({
  titulo,
  descripcion,
  acciones,
  children,
  className = '',
}: {
  titulo?: string;
  descripcion?: string;
  acciones?: ReactNode;
  children: ReactNode;
  className?: string;
}) {
  return (
    <section className={`tarjeta panel ${className}`}>
      {(titulo || acciones) && (
        <div className="panel-cabecera">
          <div>
            {titulo && <h2>{titulo}</h2>}
            {descripcion && <p className="subtitulo">{descripcion}</p>}
          </div>
          {acciones && <div className="panel-acciones">{acciones}</div>}
        </div>
      )}
      {children}
    </section>
  );
}
