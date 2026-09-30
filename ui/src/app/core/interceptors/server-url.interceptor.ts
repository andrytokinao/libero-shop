import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { ServerConfig } from '../config/server-config.service';

/**
 * Sends the `/api` calls to the configured server.
 *
 * <p>Registered last, on purpose. The interceptors before it decide on the relative path — the
 * token is attached to `/api` and nothing else, errors are silenced by path — and must keep
 * seeing the path whatever the server; only the request that actually leaves carries the
 * address. The API services keep writing `/api/...` and never learn there is a choice.
 */
export const serverUrlInterceptor: HttpInterceptorFn = (request, next) => {
  const url = inject(ServerConfig).apiUrl(request.url);
  return next(url === request.url ? request : request.clone({ url }));
};
