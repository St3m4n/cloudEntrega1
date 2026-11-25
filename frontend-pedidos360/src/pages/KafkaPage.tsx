import { useCallback, useEffect, useState } from 'react';
import { mensajeDe, useBff } from '../bff';
import { Icono } from '../components/Icono';
import { Cargando, EncabezadoPagina, EstadoVacio, Kpi, Panel } from '../components/ui';

interface DltInfo {
  originalTopic?: string;
  originalPartition?: string;
  originalOffset?: string;
  consumerGroup?: string;
  exception?: string;
  cause?: string;
  message?: string;
  reason?: string;
}
interface Mensaje {
  topic: string;
  partition: number;
  offset: number;
  key: string | null;
  timestamp: string;
  value: string | null;
  deadLetter: DltInfo | null;
}
interface VistaTopico { deadLetter: boolean; messages: Mensaje[] }
interface Mensajes { topics: Record<string, VistaTopico>; updatedAt: string }
interface Cluster { clusterId: string; brokers: { id: number; host: string; port: number }[] }
interface Particion { partition: number; leader: number; replicas: number[]; isr: number[] }
interface Topico { name: string; partitions: Particion[]; config: Record<string, string> }
interface Grupo { groupId: string; state: string; simple: boolean }
interface DetalleGrupo {
  groupId: string;
  state: string;
  assignor: string;
  coordinator: string | null;
  members: { memberId: string; clientId: string; host: string; partitions: string[] }[];
  partitions: { topic: string; partition: number; committedOffset: number | null; endOffset: number; lag: number | null; clientId: string | null }[];
  totalLag: number;
}
interface EnvioPrueba { topic: string; partition: number; offset: number; kind: string }

const TOPICOS_PRINCIPALES = ['orders.events', 'audit.timeline'];
const CONFIG_VISIBLE = ['cleanup.policy', 'retention.ms', 'min.insync.replicas'];
const BASE = '/api/admin/kafka';

function resumenEvento(valor: string | null): string {
  if (!valor) return '(vacío)';
  try {
    const evento = JSON.parse(valor) as { type?: string; order?: { id?: number; estado?: string; version?: number } };
    const pedido = evento.order ? ` · pedido ${evento.order.id ?? '?'} · ${evento.order.estado || 'sin estado'}` : ' · sin pedido';
    const version = evento.order?.version ? ` · v${evento.order.version}` : '';
    return `${evento.type ?? 'sin tipo'}${pedido}${version}`;
  } catch {
    return 'JSON inválido';
  }
}

function causaCorta(info: DltInfo): string {
  if (info.reason) return info.reason;
  const clase = (info.cause ?? info.exception ?? '').split('.').pop() ?? '';
  const texto = (info.message ?? '').replace(/\s+/g, ' ');
  return `${clase}${texto ? `: ${texto.slice(0, 220)}${texto.length > 220 ? '…' : ''}` : ''}`;
}

function dias(ms?: string): string {
  if (!ms) return '-';
  const valor = Number(ms);
  return Number.isFinite(valor) && valor > 0 ? `${Math.round(valor / 86_400_000)} días` : ms;
}

export function KafkaPage() {
  const llamar = useBff();
  const [mensajes, setMensajes] = useState<Mensajes | null>(null);
  const [cluster, setCluster] = useState<Cluster | null>(null);
  const [topicos, setTopicos] = useState<Topico[]>([]);
  const [grupos, setGrupos] = useState<DetalleGrupo[]>([]);
  const [seleccion, setSeleccion] = useState('orders.events');
  const [error, setError] = useState('');
  const [envio, setEnvio] = useState<EnvioPrueba | null>(null);
  const [enviando, setEnviando] = useState(false);

  const cargarMensajes = useCallback(async () => {
    try { setMensajes(await llamar<Mensajes>(`${BASE}/messages`)); setError(''); } catch (e) { setError(mensajeDe(e)); }
  }, [llamar]);

  const cargarGrupos = useCallback(async () => {
    try {
      const lista = await llamar<Grupo[]>(`${BASE}/consumer-groups`);
      const propios = lista.filter(g => g.groupId.startsWith('pedidos360'));
      setGrupos(await Promise.all(propios.map(g => llamar<DetalleGrupo>(`${BASE}/consumer-groups/${encodeURIComponent(g.groupId)}`))));
    } catch (e) { setError(mensajeDe(e)); }
  }, [llamar]);

  const cargarTopologia = useCallback(async () => {
    try {
      setCluster(await llamar<Cluster>(`${BASE}/cluster`));
      const nombres = (await llamar<string[]>(`${BASE}/topics`)).filter(t => !t.startsWith('_')).sort();
      setTopicos(await Promise.all(nombres.map(async name => {
        const [detalle, config] = await Promise.all([
          llamar<{ name: string; partitions: Particion[] }>(`${BASE}/topics/${encodeURIComponent(name)}`),
          llamar<Record<string, string>>(`${BASE}/topics/${encodeURIComponent(name)}/config`),
        ]);
        return { ...detalle, config };
      })));
    } catch (e) { setError(mensajeDe(e)); }
  }, [llamar]);

  useEffect(() => {
    void cargarMensajes(); void cargarGrupos(); void cargarTopologia();
    const rapido = window.setInterval(() => void cargarMensajes(), 5000);
    const lento = window.setInterval(() => void cargarGrupos(), 10000);
    return () => { window.clearInterval(rapido); window.clearInterval(lento); };
  }, [cargarMensajes, cargarGrupos, cargarTopologia]);

  async function enviarInvalido(topic: string, kind: 'malformed-json' | 'invalid-event') {
    setEnviando(true);
    try {
      setEnvio(await llamar<EnvioPrueba>(`${BASE}/test-events`, { method: 'POST', body: JSON.stringify({ topic, kind }) }));
      setError('');
      window.setTimeout(() => { void cargarMensajes(); void cargarGrupos(); }, 2500);
    } catch (e) { setError(mensajeDe(e)); } finally { setEnviando(false); }
  }

  const vistas = mensajes?.topics ?? {};
  const totalDlt = Object.values(vistas).filter(v => v.deadLetter).reduce((n, v) => n + v.messages.length, 0);
  const actual = vistas[seleccion];

  return <>
    <EncabezadoPagina titulo="Kafka" descripcion="Mensajes reales de los tópicos y sus DLT leídos con @KafkaListener, consumer groups y configuración. Se actualiza cada 5 segundos."
      acciones={<button className="boton secundario" onClick={() => { void cargarMensajes(); void cargarGrupos(); void cargarTopologia(); }}><Icono nombre="actualizar" />Actualizar</button>} />
    {error && <p role="alert" className="texto-error">{error}</p>}

    <div className="kpis">
      <Kpi icono="servidor" titulo="Brokers" valor={cluster ? cluster.brokers.length : '…'} nota={cluster?.brokers.map(b => `#${b.id} ${b.host}:${b.port}`).join(' · ')} />
      <Kpi icono="pedidos" titulo="orders.events" valor={vistas['orders.events']?.messages.length ?? '…'} nota="Últimos mensajes (máx. 50)" />
      <Kpi icono="auditoria" titulo="audit.timeline" valor={vistas['audit.timeline']?.messages.length ?? '…'} nota="Últimos mensajes (máx. 50)" />
      <Kpi icono="alerta" titulo="En DLT" valor={mensajes ? totalDlt : '…'} nota="Mensajes rechazados por los consumidores" />
    </div>

    <Panel className="kafka-seccion" titulo="Probar manejo de errores"
      descripcion="Publica un mensaje defectuoso. El consumidor lo rechaza sin reintentar (error de formato o validación) y el DefaultErrorHandler lo envía al DLT con la causa en sus cabeceras.">
      <div className="kafka-pruebas">
        {TOPICOS_PRINCIPALES.map(t => <div key={t} className="kafka-prueba">
          <span>Tópico <code>{t}</code></span>
          <div className="botonera">
            <button className="boton peligro chico" disabled={enviando} onClick={() => void enviarInvalido(t, 'malformed-json')}>JSON mal formado → {t}</button>
            <button className="boton secundario chico" disabled={enviando} onClick={() => void enviarInvalido(t, 'invalid-event')}>Evento inválido → {t}</button>
          </div>
        </div>)}
      </div>
      {envio && <p className="kafka-nota"><Icono nombre="check" />Enviado a <code>{envio.topic}</code> partición {envio.partition}, offset {envio.offset} ({envio.kind === 'malformed-json' ? 'JSON mal formado' : 'no cumple el DTO'}). Revisa la pestaña DLT.</p>}
    </Panel>

    <Panel className="kafka-seccion" titulo="Mensajes por tópico" descripcion="Leídos por KafkaMessageMonitor, un @KafkaListener con grupo propio que no interfiere con los consumidores de negocio.">
      <div className="kafka-pestanas" role="tablist">
        {Object.entries(vistas).map(([nombre, vista]) => <button key={nombre} role="tab" aria-selected={seleccion === nombre}
          className={`kafka-pestana${seleccion === nombre ? ' activa' : ''}${vista.deadLetter ? ' dlt' : ''}`} onClick={() => setSeleccion(nombre)}>
          {vista.deadLetter && <Icono nombre="alerta" tamano={14} />}{vista.deadLetter ? 'DLT · ' : ''}{nombre} <span className="kafka-contador">{vista.messages.length}</span>
        </button>)}
      </div>
      {!actual ? <Cargando /> : actual.messages.length === 0 ? <EstadoVacio titulo="Sin mensajes en este tópico" icono="kafka" /> :
        <div className="kafka-tabla-contenedor"><table className="kafka-tabla">
          <thead><tr><th>Partición</th><th>Offset</th><th>Clave</th><th>Fecha</th><th>{actual.deadLetter ? 'Origen y causa' : 'Evento'}</th><th>Payload</th></tr></thead>
          <tbody>{actual.messages.map(m => <tr key={`${m.partition}-${m.offset}`}>
            <td>{m.partition}</td><td>{m.offset}</td><td><code>{m.key ?? '-'}</code></td>
            <td>{new Date(m.timestamp).toLocaleString('es-CL', { day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit', second: '2-digit' })}</td>
            <td>{m.deadLetter
              ? <><strong>{m.deadLetter.originalTopic} · p{m.deadLetter.originalPartition} · offset {m.deadLetter.originalOffset}</strong><br /><small className="texto-suave">grupo {m.deadLetter.consumerGroup}</small><span className="kafka-causa">{causaCorta(m.deadLetter)}</span></>
              : resumenEvento(m.value)}</td>
            <td><details><summary>ver</summary><pre className="kafka-payload">{m.value}</pre></details></td>
          </tr>)}</tbody>
        </table></div>}
    </Panel>

    <Panel className="kafka-seccion" titulo="Consumer groups" descripcion="Cada microservicio tiene su grupo: todos reciben todos los eventos (publicación/suscripción) y, dentro de un grupo, las 3 particiones se reparten entre sus consumidores.">
      {grupos.length === 0 ? <EstadoVacio titulo="Sin grupos activos" icono="capas" /> : <div className="kafka-grupos">{grupos.map(g => <article key={g.groupId} className="kafka-grupo">
        <h3>{g.groupId}</h3>
        <p><span className={`kafka-estado ${g.state === 'STABLE' ? 'ok' : ''}`}>{g.state}</span><span>{g.members.length} {g.members.length === 1 ? 'consumidor' : 'consumidores'}</span><span>· lag total <strong>{g.totalLag}</strong></span>{g.assignor && <span>· {g.assignor}</span>}</p>
        <div className="kafka-tabla-contenedor"><table className="kafka-tabla compacta"><thead><tr><th>Tópico</th><th>P</th><th>Commit</th><th>Fin</th><th>Lag</th><th>Consumidor</th></tr></thead>
          <tbody>{g.partitions.map(p => <tr key={`${p.topic}-${p.partition}`}><td>{p.topic}</td><td>{p.partition}</td><td>{p.committedOffset ?? '-'}</td><td>{p.endOffset}</td><td>{p.lag ?? '-'}</td><td><small>{p.clientId ?? '-'}</small></td></tr>)}</tbody>
        </table></div>
      </article>)}</div>}
    </Panel>

    <Panel className="kafka-seccion" titulo="Tópicos y configuración" descripcion="Particiones, réplicas, ISR y políticas de retención de cada tópico.">
      <div className="kafka-tabla-contenedor"><table className="kafka-tabla">
        <thead><tr><th>Tópico</th><th>Particiones</th><th>Líder · réplicas · ISR</th>{CONFIG_VISIBLE.map(c => <th key={c}>{c}</th>)}</tr></thead>
        <tbody>{topicos.map(t => <tr key={t.name}>
          <td><code>{t.name}</code></td><td>{t.partitions.length}</td>
          <td><div className="replicas">{t.partitions.map(p => <div key={p.partition}>p{p.partition}: líder {p.leader} <span>· réplicas [{p.replicas.join(',')}] · ISR [{p.isr.join(',')}]</span></div>)}</div></td>
          <td>{t.config['cleanup.policy'] ?? '-'}</td><td>{dias(t.config['retention.ms'])}</td><td>{t.config['min.insync.replicas'] ?? '-'}</td>
        </tr>)}</tbody>
      </table></div>
    </Panel>
  </>;
}
