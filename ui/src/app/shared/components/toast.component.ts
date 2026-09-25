import { Component, inject } from '@angular/core';
import { ToastService } from '../../core/services/toast.service';

@Component({
  selector: 'app-toast',
  standalone: true,
  template: `
    @if (toasts.message(); as message) {
      <div class="toast" role="status" (click)="toasts.dismiss()">{{ message }}</div>
    }
  `,
})
export class ToastComponent {
  protected readonly toasts = inject(ToastService);
}
