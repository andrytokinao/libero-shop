import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { SessionService } from '../services/session.service';

/** Sends `/` to the landing page of whoever is currently impersonated. */
export const homeRedirectGuard: CanActivateFn = () => {
  const session = inject(SessionService);
  return inject(Router).parseUrl(session.homePath());
};

/**
 * Keeps each role inside its own section: reaching another role's page bounces
 * back to your own home. The owning segment comes from the route's `segment` data.
 */
export const roleGuard: CanActivateFn = (route) => {
  const session = inject(SessionService);
  const segment = route.data['segment'] as string;
  return session.owns(segment) ? true : inject(Router).parseUrl(session.homePath());
};
