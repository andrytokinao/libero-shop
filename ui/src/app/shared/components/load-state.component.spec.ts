import { TestBed } from '@angular/core/testing';
import { Subject } from 'rxjs';
import { ApiResource, apiResource } from '../../core/api/api-resource';
import { LoadStateComponent, showsEmpty } from './load-state.component';

describe('LoadStateComponent', () => {
  let answers: Subject<string[]>[];
  let resource: ApiResource<string[]>;

  function render(): HTMLElement {
    const fixture = TestBed.createComponent(LoadStateComponent);
    fixture.componentRef.setInput('of', resource);
    fixture.componentRef.setInput('what', 'les factures');
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  beforeEach(() => {
    answers = [];
    resource = apiResource<string[]>([], () => {
      const answer = new Subject<string[]>();
      answers.push(answer);
      return answer;
    });
  });

  it('dit « Chargement… » avant la première réponse, sans message « Aucun »', () => {
    expect(render().textContent).toContain('Chargement…');
    expect(showsEmpty(resource)).toBeFalse();
  });

  it('ne dit plus rien une fois la liste arrivée, même vide', () => {
    answers[0].next([]);

    expect(render().textContent?.trim()).toBe('');
    expect(showsEmpty(resource)).toBeTrue();
  });

  it('dit l échec et propose de réessayer, qui redemande la liste', () => {
    answers[0].error(new Error('hors ligne'));
    const host = render();

    expect(host.textContent).toContain('Impossible de charger les factures');
    expect(showsEmpty(resource)).toBeFalse();
    (host.querySelector('button') as HTMLButtonElement).click();
    expect(answers.length).toBe(2);
  });

  it('garde la liste déjà là quand une mise à jour échoue, en le signalant', () => {
    answers[0].next(['F-1']);
    resource.reload();
    answers[1].error(new Error('hors ligne'));

    expect(render().textContent).toContain('la liste affichée peut être ancienne');
    expect(resource.value()).toEqual(['F-1']);
  });

  it('une réponse après un échec efface le message', () => {
    answers[0].error(new Error('hors ligne'));
    resource.reload();
    answers[1].next([]);

    expect(resource.failed()).toBeFalse();
    expect(render().textContent?.trim()).toBe('');
  });
});
