import { DatePipe } from '@angular/common';
import { Component, inject } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { PageTitleStrategy } from './core/services/page-title.strategy';
import { SessionService } from './core/services/session.service';
import { ToastComponent } from './shared/components/toast.component';

/** Application shell: role sidebar, top bar, routed page and toast host. */
@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet, RouterLink, RouterLinkActive, DatePipe, ToastComponent],
  templateUrl: './app.component.html',
  styleUrl: './app.component.scss',
})
export class AppComponent {
  protected readonly session = inject(SessionService);
  protected readonly pageTitle = inject(PageTitleStrategy).pageTitle;
  protected readonly today = new Date();

  protected onUserChange(event: Event): void {
    this.session.switchUser(Number((event.target as HTMLSelectElement).value));
  }
}
