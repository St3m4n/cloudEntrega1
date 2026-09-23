import type { ComponentType } from 'react';
import { ROLES } from './auth/roles';
import type { Rol } from './auth/roles';
import { AuditPage } from './pages/AuditPage';
import { CatalogPage } from './pages/CatalogPage';
import { DashboardPage } from './pages/DashboardPage';
import { LabPage } from './pages/LabPage';
import { OrdersPage } from './pages/OrdersPage';
import { ReportsPage } from './pages/ReportsPage';

export interface Seccion {
  ruta: string;
  titulo: string;
  // Sin roles: accesible para cualquier usuario autenticado.
  roles?: readonly Rol[];
  Pagina: ComponentType;
}

export const SECCIONES: Seccion[] = [
  { ruta: '/dashboard', titulo: 'Inicio', Pagina: DashboardPage },
  {
    ruta: '/orders',
    titulo: 'Pedidos',
    roles: [ROLES.admin, ROLES.operador, ROLES.cliente],
    Pagina: OrdersPage,
  },
  {
    ruta: '/catalog',
    titulo: 'Catálogo',
    roles: [ROLES.admin, ROLES.operador],
    Pagina: CatalogPage,
  },
  { ruta: '/reports', titulo: 'Reportes', roles: [ROLES.admin], Pagina: ReportsPage },
  { ruta: '/audit', titulo: 'Auditoría', roles: [ROLES.admin], Pagina: AuditPage },
  { ruta: '/laboratorio', titulo: 'Laboratorio', Pagina: LabPage },
];
