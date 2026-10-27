# Frontend Pedidos360

React, TypeScript, Vite y MSAL. Incluye login, panel por rol, catálogo, pedidos, reportes y auditoría. Todas las llamadas de negocio pasan por el BFF/API Gateway y adjuntan el access token de la API.

## Ejecutar

```powershell
Copy-Item .env.example .env.local
# Completar los tres IDs de Entra y VITE_BFF_BASE_URL.
npm ci
npm run dev
```

Usar `VITE_BFF_BASE_URL=http://localhost:8080` en local y la URL pública de API Gateway en AWS, sin agregar `/api`. El callback existente de MSAL es `/redirect.html`; conservarlo registrado en la SPA del tenant.

## Verificar

```powershell
npm run build
npm run lint
```

Roles recibidos del backend: Admin, Operator y Customer. No se asignan privilegios por defecto cuando el token no contiene roles. La autorización efectiva también se aplica en cada microservicio.

El login, los guards y la navegación se conservan. Se retiraron la pantalla de laboratorio y los recursos sin uso de la plantilla inicial. Ver [documentación del proyecto](../README.md) para iniciar servicios y configurar AWS.
