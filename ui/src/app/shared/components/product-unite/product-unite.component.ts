import {Component, Input} from '@angular/core';
import {NgTemplateOutlet} from "@angular/common";
import {FormsModule, ReactiveFormsModule} from "@angular/forms";
import {unitsOf} from '../../../core/sale/sale-unit';
import {Product} from '../../../core/models';

@Component({
  selector: 'app-product-unite',
  standalone: true,
  imports: [
    NgTemplateOutlet,
    ReactiveFormsModule,
    FormsModule
  ],
  templateUrl: './product-unite.component.html',
  styleUrl: './product-unite.component.scss'
})
export class ProductUniteComponent {
  @Input() product: Product | undefined ;

  protected readonly unitsOf = unitsOf;
}
