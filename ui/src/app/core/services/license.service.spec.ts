import { TestBed } from '@angular/core/testing';
import { Subject, of } from 'rxjs';
import { LicenseApi } from '../api/license.api';
import { LicenseStatus } from '../models';
import { LicenseService } from './license.service';

describe('LicenseService — double clic', () => {
  let installs: Subject<LicenseStatus>[];
  let renewals: number;
  let license: LicenseService;

  beforeEach(() => {
    installs = [];
    renewals = 0;
    TestBed.configureTestingModule({
      providers: [
        {
          provide: LicenseApi,
          useValue: {
            status: () => of(null),
            install: () => {
              const answer = new Subject<LicenseStatus>();
              installs.push(answer);
              return answer;
            },
            renew: () => {
              renewals++;
              return new Subject<LicenseStatus>();
            },
          },
        },
      ],
    });
    license = TestBed.inject(LicenseService);
  });

  it('un second clic sur « Installer » pendant l envoi n envoie pas le fichier deux fois', () => {
    license.install('{}').subscribe();
    license.install('{}').subscribe();

    expect(installs.length).toBe(1);
    expect(license.busy()).toBeTrue();
  });

  it('une fois la réponse arrivée, on peut installer de nouveau', () => {
    license.install('{}').subscribe();
    installs[0].next({ state: 'ACTIVE' } as unknown as LicenseStatus);

    license.install('{}').subscribe();
    expect(installs.length).toBe(2);
  });

  it('« Vérifier maintenant » ne part qu une fois tant que la première tentative n a pas répondu', () => {
    license.renew().subscribe();
    license.renew().subscribe();

    expect(renewals).toBe(1);
  });
});
