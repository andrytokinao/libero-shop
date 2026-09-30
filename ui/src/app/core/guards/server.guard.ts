import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { ServerConfig } from '../config/server-config.service';

/**
 * Keeps the mobile app on the server screen until it knows its server.
 *
 * <p>Placed first on the routes that talk to the server — the landing redirect and the login
 * page — so it wins over the session guards: asking an unknown server for a session would only
 * fail. In the web build `needsSetup` is always false and this lets everything through.
 */
export const serverConfiguredGuard: CanActivateFn = () => {
  const server = inject(ServerConfig);
  return server.needsSetup() ? inject(Router).parseUrl('/serveur') : true;
};
