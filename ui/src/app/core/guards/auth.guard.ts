import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { map } from 'rxjs/operators';
import { AuthService } from '../services/auth.service';

/**
 * Routing guards.
 *
 * <p>All three resolve the session first, so a hard refresh on a deep link is decided on
 * the real role rather than on an empty client state. They are a navigation aid: the API
 * re-checks every call, and a user who edits the URL gains nothing but a redirect.
 */

/** Requires a session; sends anonymous visitors to the login page. */
export const authGuard: CanActivateFn = (_route, state) => {
  const auth = inject(AuthService);
  const router = inject(Router);
  return auth.ensureLoaded().pipe(
    map((session) =>
      session.authenticated
        ? true
        // returnUrl so the user lands where they meant to go, not on a generic home.
        : router.createUrlTree(['/login'], { queryParams: { returnUrl: state.url } }),
    ),
  );
};

/** Keeps each role inside its own section, using the `segment` declared on the route. */
export const roleGuard: CanActivateFn = (route, state) => {
  const auth = inject(AuthService);
  const router = inject(Router);
  const segment = route.data['segment'] as string;

  return auth.ensureLoaded().pipe(
    map((session) => {
      if (!session.authenticated) {
        return router.createUrlTree(['/login'], { queryParams: { returnUrl: state.url } });
      }
      return auth.owns(segment) ? true : router.parseUrl(session.homePath);
    }),
  );
};

/** Sends `/` — and anything unrecognised — to the landing page of the current role. */
export const homeRedirectGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  return auth.ensureLoaded().pipe(map((session) => router.parseUrl(session.homePath)));
};

/** Keeps a signed-in user off the login page. */
export const anonymousOnlyGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  return auth.ensureLoaded().pipe(
    map((session) => (session.authenticated ? router.parseUrl(session.homePath) : true)),
  );
};
