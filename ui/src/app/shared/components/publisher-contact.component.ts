import { Component } from '@angular/core';
import { PUBLISHER } from '../../core/config/publisher';

/**
 * The publisher's name and the three ways to reach them.
 *
 * <p>Shown wherever the screen says "send this to the publisher": a renewal code is no use
 * to a shop owner who does not know who that is.
 */
@Component({
  selector: 'app-publisher-contact',
  standalone: true,
  template: `
    <dl class="dl publisher-contact">
      <dt>Éditeur</dt>
      <dd>{{ publisher.name }}</dd>
      <dt>Téléphone</dt>
      <dd>
        <a [href]="publisher.phoneLink">{{ publisher.phone }}</a>
      </dd>
      <dt>E-mail</dt>
      <dd>
        <a [href]="publisher.emailLink">{{ publisher.email }}</a>
      </dd>
      <dt>Site</dt>
      <dd>
        <a [href]="publisher.websiteLink" target="_blank" rel="noopener">{{ publisher.website }}</a>
      </dd>
    </dl>
  `,
  styles: `
    .publisher-contact {
      margin: 14px 0 0;
    }
  `,
})
export class PublisherContactComponent {
  protected readonly publisher = PUBLISHER;
}
