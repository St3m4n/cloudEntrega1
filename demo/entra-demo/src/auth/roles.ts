export const ROLES = {
  admin: 'Admin',
  operador: 'Operator',
  cliente: 'Customer',
} as const;

export type Rol = (typeof ROLES)[keyof typeof ROLES];

export const NOMBRE_ROL: Record<string, string> = {
  Admin: 'Administrador',
  Operator: 'Operador',
  Customer: 'Cliente',
};

export function tieneAlgunRol(
  roles: readonly string[],
  permitidos: readonly string[],
): boolean {
  return permitidos.some((rol) => roles.includes(rol));
}
