import { ComponentFixture, TestBed } from '@angular/core/testing';

import { ProductUniteComponent } from './product-unite.component';

describe('ProductUniteComponent', () => {
  let component: ProductUniteComponent;
  let fixture: ComponentFixture<ProductUniteComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ProductUniteComponent]
    })
    .compileComponents();

    fixture = TestBed.createComponent(ProductUniteComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
