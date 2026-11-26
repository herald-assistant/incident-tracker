import {CdkDragHandle} from '@angular/cdk/drag-drop';
import {Component, input, output} from '@angular/core';
import {MatMenuModule} from '@angular/material/menu';

@Component({
  selector: 'app-assistant-order-handle',
  imports: [CdkDragHandle, MatMenuModule],
  template: `
    <button type="button" cdkDragHandle [matMenuTriggerFor]="orderMenu" [disabled]="disabled() || count() < 2"
      [attr.aria-label]="'Zmień kolejność: ' + name()" title="Przeciągnij lub wybierz kierunek przesunięcia">
      <span class="material-symbols-outlined" aria-hidden="true">drag_indicator</span>
    </button>
    <mat-menu #orderMenu="matMenu">
      <button mat-menu-item [disabled]="disabled() || index() === 0" (click)="moveTo.emit(index() - 1)">Przesuń w górę</button>
      <button mat-menu-item [disabled]="disabled() || index() === count() - 1" (click)="moveTo.emit(index() + 1)">Przesuń w dół</button>
    </mat-menu>`,
  styles: `
    :host { display: inline-flex; }
    button[cdkDragHandle] { display: grid; place-items: center; width: 24px; height: 32px; padding: 0; margin: 0;
      background: transparent; border: 0; border-radius: 6px; color: var(--color-muted); cursor: grab; }
    button[cdkDragHandle]:active { cursor: grabbing; }
    button[cdkDragHandle]:focus-visible { outline: 2px solid var(--color-primary); outline-offset: -2px; }
    button[cdkDragHandle]:disabled { cursor: default; opacity: .45; }
    .material-symbols-outlined { font-size: 20px; }`
})
export class AssistantOrderHandle {
  readonly name = input.required<string>();
  readonly index = input.required<number>();
  readonly count = input.required<number>();
  readonly disabled = input.required<boolean>();
  readonly moveTo = output<number>();
}
