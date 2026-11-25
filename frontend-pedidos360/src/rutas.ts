import type { ComponentType } from 'react';
import { ROLES } from './auth/roles';
import type { Rol } from './auth/roles';
import type { NombreIcono } from './components/Icono';
import { AuditPage } from './pages/AuditPage';
import { CatalogPage } from './pages/CatalogPage';
import { DashboardPage } from './pages/DashboardPage';
import { KafkaPage } from './pages/KafkaPage';
import { OrdersPage } from './pages/OrdersPage';
import { ReportsPage } from './pages/ReportsPage';

export interface Seccion {
  ruta: string;
  titulo: string;
  icono: NombreIcono;
  // Sin roles: accesible para cualquier usuario autenticado.
  roles?: readonly Rol[];
  Pagina: ComponentType;
}

export const SECCIONES: Seccion[] = [
  { ruta: '/dashboard', titulo: 'Inicio', icono: 'inicio', Pagina: DashboardPage },
  {
    ruta: '/orders',
    titulo: 'Pedidos',
    icono: 'pedidos',
    roles: [ROLES.admin, ROLES.operador, ROLES.cliente],
    Pagina: OrdersPage,
  },
  {
    ruta: '/catalog',
    titulo: 'Catálogo',
    icono: 'catalogo',
    roles: [ROLES.admin, ROLES.operador],
    Pagina: CatalogPage,
  },
  { ruta: '/reports', titulo: 'Reportes', icono: 'reportes', roles: [ROLES.admin], Pagina: ReportsPage },
  { ruta: '/audit', titulo: 'Auditoría', icono: 'auditoria', roles: [ROLES.admin], Pagina: AuditPage },
  { ruta: '/kafka', titulo: 'Kafka', icono: 'kafka', roles: [ROLES.admin], Pagina: KafkaPage },
];
