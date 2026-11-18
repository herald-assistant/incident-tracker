import { DIALOG_DATA, DialogRef } from '@angular/cdk/dialog';
import { DOCUMENT } from '@angular/common';
import {
  afterNextRender,
  ChangeDetectionStrategy,
  Component,
  computed,
  DestroyRef,
  ElementRef,
  inject,
  signal,
  viewChild
} from '@angular/core';

import type { FormTourStep } from '../../core/models/form-tour';
import { formTourTargetBounds, positionFormTour, type FormTourLayout } from './form-tour-layout';

interface FormTourStop {
  readonly step: FormTourStep;
  readonly element: HTMLElement;
}

interface FormTourViewport {
  readonly left: number;
  readonly top: number;
  readonly width: number;
  readonly height: number;
}

export function collectFormTourStops(
  scope: HTMLElement,
  steps: readonly FormTourStep[]
): readonly FormTourStop[] {
  const anchors = new Map(
    Array.from(scope.querySelectorAll<HTMLElement>('[data-form-tour]'), (element) =>
      [element.getAttribute('data-form-tour'), element] as const
    )
  );
  return steps.flatMap((step) => {
    const element = anchors.get(step.anchor);
    return element ? [{ step, element }] : [];
  });
}

@Component({
  selector: 'app-form-tour',
  templateUrl: './form-tour.html',
  styleUrl: './form-tour.scss',
  host: {
    '[class.form-tour--animated]': 'animateLayout()',
    '[style.left.px]': 'viewport().left',
    '[style.top.px]': 'viewport().top',
    '[style.width.px]': 'viewport().width',
    '[style.height.px]': 'viewport().height'
  },
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class FormTourComponent {
  private readonly document = inject(DOCUMENT);
  private readonly window = this.document.defaultView;
  private readonly dialog = inject<DialogRef<void>>(DialogRef);
  private readonly panel = viewChild.required<ElementRef<HTMLElement>>('panel');
  private readonly openedDetails = new Set<HTMLDetailsElement>();
  private targetElements: readonly Element[] = [];
  private resizeObserver: ResizeObserver | null = null;
  private layoutFrame: number | null = null;
  private stepLayoutPending = false;

  protected readonly stops = inject<readonly FormTourStop[]>(DIALOG_DATA);
  protected readonly index = signal(0);
  protected readonly current = computed(() => this.stops[this.index()]);
  protected readonly layout = signal<FormTourLayout | null>(null);
  protected readonly viewport = signal(this.readViewport());
  protected readonly animateLayout = signal(false);

  private readonly scheduleLayout = (): void => {
    if (this.layoutFrame !== null || !this.window) {
      return;
    }
    this.layoutFrame = this.window.requestAnimationFrame(() => {
      this.layoutFrame = null;
      this.updateLayout();
    });
  };

  constructor() {
    afterNextRender(() => {
      this.document.addEventListener('scroll', this.scheduleLayout, { capture: true, passive: true });
      this.window?.addEventListener('resize', this.scheduleLayout);
      this.window?.visualViewport?.addEventListener('resize', this.scheduleLayout);
      this.window?.visualViewport?.addEventListener('scroll', this.scheduleLayout);
      if (typeof ResizeObserver !== 'undefined') {
        this.resizeObserver = new ResizeObserver(this.scheduleLayout);
        this.resizeObserver.observe(this.panel().nativeElement);
      }
      this.showStep(0, 1);
    });

    inject(DestroyRef).onDestroy(() => {
      this.document.removeEventListener('scroll', this.scheduleLayout, true);
      this.window?.removeEventListener('resize', this.scheduleLayout);
      this.window?.visualViewport?.removeEventListener('resize', this.scheduleLayout);
      this.window?.visualViewport?.removeEventListener('scroll', this.scheduleLayout);
      this.resizeObserver?.disconnect();
      if (this.layoutFrame !== null) {
        this.window?.cancelAnimationFrame(this.layoutFrame);
      }
      for (const details of this.openedDetails) {
        details.open = false;
      }
    });
  }

  protected previous(): void {
    this.showStep(this.index() - 1, -1);
  }

  protected next(): void {
    this.showStep(this.index() + 1, 1);
  }

  protected close(): void {
    this.dialog.close();
  }

  private showStep(index: number, direction: -1 | 1): void {
    while (this.stops[index] && !this.stops[index].element.isConnected) {
      index += direction;
    }
    const next = this.stops[index];
    if (!next) {
      if (direction === 1) {
        this.close();
      }
      return;
    }

    for (const element of this.targetElements) {
      this.resizeObserver?.unobserve(element);
    }
    const changingStep = index !== this.index();
    this.index.set(index);
    const target = next.element;
    let details = target.parentElement?.closest<HTMLDetailsElement>('details:not([open])');
    while (details) {
      details.open = true;
      this.openedDetails.add(details);
      details = details.parentElement?.closest<HTMLDetailsElement>('details:not([open])');
    }
    this.targetElements = this.window?.getComputedStyle(target).display === 'contents'
      ? Array.from(target.children)
      : [target];
    this.targetElements[0]?.scrollIntoView?.({ block: 'center', inline: 'nearest', behavior: 'instant' });
    for (const element of this.targetElements) {
      this.resizeObserver?.observe(element);
    }
    if (changingStep) {
      this.animateLayout.set(true);
      this.stepLayoutPending = true;
    } else if (!this.layout()) {
      this.updateLayout();
    }
    this.scheduleLayout();
  }

  private readViewport(): FormTourViewport {
    const visible = this.window?.visualViewport;
    const root = this.document.documentElement;
    return {
      left: visible?.offsetLeft ?? 0,
      top: visible?.offsetTop ?? 0,
      width: visible?.width || root.clientWidth || this.window?.innerWidth || 0,
      height: visible?.height || root.clientHeight || this.window?.innerHeight || 0
    };
  }

  private updateLayout(): void {
    const viewport = this.readViewport();
    this.viewport.set(viewport);
    const target = formTourTargetBounds(this.targetElements.map((element) => element.getBoundingClientRect()));
    const nextLayout = positionFormTour(
      viewport,
      this.panel().nativeElement.getBoundingClientRect(),
      new DOMRect(target.left - viewport.left, target.top - viewport.top, target.width, target.height)
    );

    if (this.stepLayoutPending) {
      this.stepLayoutPending = false;
      if (!nextLayout.spotlight && this.layout()?.spotlight) {
        // Give the viewport one more frame to report the new position after scrolling.
        this.scheduleLayout();
        return;
      }
    }
    this.layout.set(nextLayout);
  }
}
