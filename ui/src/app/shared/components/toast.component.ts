import { Component, inject } from '@angular/core';
import { ToastService } from '../../core/services/toast.service';

@Component({
  selector: 'app-toast',
  standalone: true,
  template: `
    @if (toasts.message(); as message) {
      <div class="toast" role="status" (click)="toasts.dismiss()">
        <span>{{ message }}</span>
        @for (action of toasts.actions(); track action.label) {
          <button class="toast-action" type="button" (click)="$event.stopPropagation(); toasts.act(action)">
            {{ action.label }}
          </button>
        }
      </div>
    }
  `,
})
export class ToastComponent {
  protected readonly toasts = inject(ToastService);
}
