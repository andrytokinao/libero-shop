import { DatePipe } from '@angular/common';
import { Component, inject } from '@angular/core';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AuthService } from './core/services/auth.service';
import { PageTitleStrategy } from './core/services/page-title.strategy';
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
  protected readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  protected readonly pageTitle = inject(PageTitleStrategy).pageTitle;
  protected readonly today = new Date();

  protected logout(): void {
    this.auth.logout().subscribe(() => void this.router.navigateByUrl('/login'));
  }
}
