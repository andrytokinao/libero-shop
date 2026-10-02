import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Subject } from 'rxjs';
import { EntityStore } from './entity-store';
import { LiveList, liveList } from './live-list';

interface Row {
  id: number;
  status: 'open' | 'done';
  label: string;
}

/**
 * The contract every live screen relies on: rows read from the store, joined by what is pushed
 * and matches, left by what stops matching — and never set back by a slower reload.
 */
describe('LiveList', () => {
  let store: EntityStore<Row>;
  let answers: Subject<Row[]>;
  let loads: number;
  let wanted: ReturnType<typeof signal<'open' | 'done'>>;
  let list: LiveList<Row>;

  beforeEach(() => {
    store = new EntityStore<Row>();
    answers = new Subject<Row[]>();
    loads = 0;
    wanted = signal('open');
    list = TestBed.runInInjectionContext(() =>
      liveList(store, {
        load: () => {
          loads++;
          answers = new Subject<Row[]>();
          return answers;
        },
        matches: (row) => row.status === wanted(),
        compare: (a, b) => b.id - a.id,
      }),
    );
  });

  it('shows what the server answered, in the list order', () => {
    answers.next([open(1), open(2)]);

    expect(ids(list)).toEqual([2, 1]);
    expect(list.loading()).toBeFalse();
  });

  it('takes in a pushed row that belongs to it, and only those', () => {
    answers.next([open(1)]);

    store.push([open(5), done(6)]);

    expect(ids(list)).toEqual([5, 1]);
  });

  it('drops a row the moment a push makes it stop matching', () => {
    answers.next([open(1), open(2)]);

    store.push([done(2)]);

    expect(ids(list)).toEqual([1]);
  });

  it('updates a row in place, without asking the server', () => {
    answers.next([open(1)]);

    store.push([{ ...open(1), label: 'renamed' }]);

    expect(list.value()[0].label).toBe('renamed');
    expect(loads).toBe(1);
  });

  it('keeps a change pushed while a reload was on its way', () => {
    answers.next([open(1)]);
    list.reload();

    store.push([done(1), open(9)]);
    // The server read its answer before the push.
    answers.next([open(1)]);

    expect(store.get(1)?.status).toBe('done');
    expect(ids(list)).toEqual([9]);
  });

  it('reloads when the store is invalidated', () => {
    answers.next([open(1)]);

    store.invalidate();
    answers.next([open(1), open(3)]);

    expect(loads).toBe(2);
    expect(ids(list)).toEqual([3, 1]);
  });

  it('follows the signals its filter reads', () => {
    answers.next([open(1), done(2)]);

    wanted.set('done');

    expect(ids(list)).toEqual([2]);
  });

  it('forgets everything on clear', () => {
    answers.next([open(1)]);

    store.clear();

    expect(ids(list)).toEqual([]);
  });
});

function open(id: number): Row {
  return { id, status: 'open', label: `row ${id}` };
}

function done(id: number): Row {
  return { id, status: 'done', label: `row ${id}` };
}

function ids(list: LiveList<Row>): number[] {
  return list.value().map((row) => row.id);
}
