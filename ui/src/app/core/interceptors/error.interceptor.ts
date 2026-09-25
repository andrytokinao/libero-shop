import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { throwError } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { ApiError } from '../models';
import { AuthService } from '../services/auth.service';
import { ToastService } from '../services/toast.service';

/** The session probe answers 401 by design; it must not trigger a redirect of its own. */
const SILENT_PATHS = ['/api/auth/session', '/api/auth/login'];

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

  return next(request).pipe(
    catchError((error: HttpErrorResponse) => {
      if (SILENT_PATHS.some((path) => request.url.startsWith(path))) {
        return throwError(() => error);
      }

      const body = error.error as ApiError | null;
      const message = body?.message;

      switch (error.status) {
        case 0:
          toasts.show('Serveur injoignable. Verifiez que le backend est demarre.');
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
        default:
          toasts.show(message ?? `Erreur ${error.status} lors de l'appel au serveur.`);
      }

      return throwError(() => error);
    }),
  );
};
