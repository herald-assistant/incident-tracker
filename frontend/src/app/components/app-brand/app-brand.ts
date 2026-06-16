import { Component, computed, inject } from '@angular/core';

import { AppUiConfigService } from '../../core/services/app-ui-config.service';

@Component({
  selector: 'app-brand',
  templateUrl: './app-brand.html'
})
export class AppBrandComponent {
  private readonly uiConfig = inject(AppUiConfigService);

  readonly title = computed(() => this.uiConfig.config().title);
  readonly subtitle = computed(() => this.uiConfig.config().subtitle);

  constructor() {
    this.uiConfig.load();
  }
}
