import { Pipe, PipeTransform } from '@angular/core';

/** Formats an amount the way the mock-up does: `12 500 Ar`. */
@Pipe({ name: 'ariary', standalone: true })
export class AriaryPipe implements PipeTransform {
  transform(value: number | null | undefined): string {
    return `${(value ?? 0).toLocaleString('fr-FR')} Ar`;
  }
}
