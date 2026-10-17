import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { DiningTable } from '../../core/models';
import { DiningTablesComponent } from './dining-tables.component';

describe('DiningTablesComponent — menu des tables', () => {
  const table4 = { id: 4, name: 'Table 4', token: 'abc' } as DiningTable;
  let http: HttpTestingController;

  function render() {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    http = TestBed.inject(HttpTestingController);
    const fixture = TestBed.createComponent(DiningTablesComponent);
    fixture.detectChanges();
    http.match(() => true).forEach((request) => request.flush(request.request.url.endsWith('/tables') ? [table4] : null));
    fixture.detectChanges();
    return fixture;
  }

  it('propose renommer, copier, imprimer, nouveau code et supprimer en rouge', () => {
    const fixture = render();
    const actions = fixture.componentInstance['actions'];

    expect(actions.map((action) => action.id)).toEqual(['rename', 'copy', 'print', 'regenerate', 'delete']);
    expect(actions.find((action) => action.id === 'delete')?.danger).toBeTrue();
  });

  it('« Renommer » part du nom actuel et n envoie qu un nom changé', () => {
    const component = render().componentInstance;

    component['pick'](table4, 'rename');
    expect(component['newTableName']()).toBe('Table 4');
    expect(component['canRename'](table4)).toBeFalse();

    component['newTableName'].set('  Terrasse 1 ');
    expect(component['canRename'](table4)).toBeTrue();
    component['rename'](table4);

    const request = http.expectOne((candidate) => candidate.url.endsWith('/tables/4'));
    expect(request.request.method).toBe('PUT');
    expect(request.request.body).toEqual({ name: 'Terrasse 1' });
  });

  it('le bouton « Renommer » de la fenêtre envoie bien le formulaire', async () => {
    const fixture = render();
    const component = fixture.componentInstance;
    component['pick'](table4, 'rename');
    component['newTableName'].set('Salle 1');
    fixture.detectChanges();
    await fixture.whenStable();

    (fixture.nativeElement.querySelector('form.modal button[type=submit]') as HTMLButtonElement).click();

    expect(http.expectOne((candidate) => candidate.url.endsWith('/tables/4')).request.body).toEqual({ name: 'Salle 1' });
  });

  it('« Nouveau code » et « Supprimer » demandent d abord', () => {
    const component = render().componentInstance;

    component['pick'](table4, 'delete');
    component['pick'](table4, 'regenerate');

    expect(component['deleting']()).toBe(table4);
    expect(component['regenerating']()).toBe(table4);
    http.expectNone((candidate) => candidate.method === 'DELETE');
  });
});
