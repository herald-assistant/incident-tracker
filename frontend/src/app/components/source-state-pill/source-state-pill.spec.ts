import { ComponentFixture, TestBed } from '@angular/core/testing';

import { SourceStatePillComponent } from './source-state-pill';

describe('SourceStatePillComponent', () => {
  let fixture: ComponentFixture<SourceStatePillComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [SourceStatePillComponent] }).compileComponents();
    fixture = TestBed.createComponent(SourceStatePillComponent);
  });

  it('should render DEFAULT and CUSTOM with the established workspace styling', () => {
    fixture.componentRef.setInput('state', 'DEFAULT');
    fixture.detectChanges();
    const pill = fixture.nativeElement.querySelector('.workspace-settings-source') as HTMLElement;
    expect(pill.textContent?.trim()).toBe('DEFAULT');
    expect(pill.classList.contains('workspace-settings-source--custom')).toBe(false);

    fixture.componentRef.setInput('state', 'CUSTOM');
    fixture.detectChanges();
    expect(pill.textContent?.trim()).toBe('CUSTOM');
    expect(pill.classList.contains('workspace-settings-source--custom')).toBe(true);
  });
});
