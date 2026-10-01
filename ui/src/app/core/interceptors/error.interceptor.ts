import { HttpContextToken, HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { throwError } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { ServerConfig } from '../config/server-config.service';
import { ApiError } from '../models';
import { AuthService } from '../services/auth.service';
import { ToastService } from '../services/toast.service';

/**
 * The session probe answers 401 by design; it must not trigger a redirect of its own. The
 * mobile update check runs in the background: its failures are nobody's business but its own.
 */
const SILENT_PATHS = ['/api/auth/session', '/api/auth/login', '/api/mobile/update'];

/**
 * Set on a request whose failure is handled where it was made — a background poll, for one:
 * a toast every few seconds would say nothing the screen does not already show.
 */
export const SILENT_ERRORS = new HttpContextToken<boolean>(() => false);

/**
 * Turns an HTTP failure into something the user can act on.
 *
 * <p>Every error body from the API carries a French `message` written for a cashier, so
 * the rule here is to show it rather than invent wording. Three statuses get special
 * treatment: 401 ends the session and returns to the login page, 402 is the license
 * module saying the installation has gone read-only, and 0 means the backend is simply
 * not answering.
 */
export const errorInterceptor: HttpInterceptorFn = (request, next) => {
  const toasts = inject(ToastService);
  const auth = inject(AuthService);
  const router = inject(Router);
  const server = inject(ServerConfig);

  return next(request).pipe(
    catchError((error: HttpErrorResponse) => {
      if (
        request.context.get(SILENT_ERRORS) ||
        SILENT_PATHS.some((path) => request.url.startsWith(path))
      ) {
        return throwError(() => error);
      }

      const body = error.error as ApiError | null;
      const message = body?.message;

      switch (error.status) {
        case 0:
          // On a phone the usual cause is the address, not the server: the Wi-Fi changed, or
          // the server's IP moved. Said where it can be fixed.
          toasts.show(
            server.isMobile
              ? `Serveur ${server.host() ?? ''} injoignable. Vérifiez le Wi-Fi, ou changez l'adresse dans Paramètres › Serveur.`
              : 'Serveur injoignable. Verifiez que le backend est demarre.',
          );
          break;
        case 401:
          auth.clear();
          toasts.show(message ?? 'Votre session a expire. Merci de vous reconnecter.');
          void router.navigate(['/login'], { queryParams: { returnUrl: router.url } });
          break;
        case 402:
          // The license module's own status: writes are refused, reads still work.
          toasts.show(message ?? 'Licence expiree : l\'application est en lecture seule.');
          break;
        case 501:
          // Only POST /api/license/renew answers this, and it is not a failure: online
          // renewal simply is not configured here. The default branch would have shown
          // "Erreur 501", sending someone looking for a fault that does not exist.
          toasts.show(
            "Le renouvellement en ligne n'est pas active sur cette installation. " +
              'Utilisez le code de renouvellement.',
          );
          break;
        default:
          toasts.show(message ?? `Erreur ${error.status} lors de l'appel au serveur.`);
      }

      return throwError(() => error);
    }),
  );
};
