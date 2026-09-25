import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { AppComponent } from './app.component';
import { routes } from './app.routes';

describe('AppComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AppComponent],
      providers: [provideRouter(routes)],
    }).compileComponents();
  });

  it('renders the sidebar with the current role and its menu', () => {
    const fixture = TestBed.createComponent(AppComponent);
    fixture.detectChanges();

    const sidebar = fixture.nativeElement.querySelector('.side') as HTMLElement;
    expect(sidebar.textContent).toContain('Vaha');
    expect(sidebar.textContent).toContain('Responsable de caisse');
    expect(sidebar.querySelectorAll('nav.menu a').length).toBe(5);
  });
});
