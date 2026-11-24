import { ComponentFixture, TestBed } from '@angular/core/testing';

import { StockEntryComponent } from './stock-entry.component';

describe('StockEntryComponent', () => {
  let component: StockEntryComponent;
  let fixture: ComponentFixture<StockEntryComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [StockEntryComponent]
    })
    .compileComponents();

    fixture = TestBed.createComponent(StockEntryComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
