import { Component, inject } from '@angular/core';
import { AppUiConfigService } from '../../core/services/app-ui-config.service';
import { RouterLink } from '@angular/router';

@Component({
  selector: 'app-platform-landing-page',
  imports: [RouterLink],
  templateUrl: './platform-landing.html',
  styleUrl: './platform-landing.scss'
})
export class PlatformLandingPageComponent {
  readonly uiConfig = inject(AppUiConfigService);
  constructor() { this.uiConfig.load(); }
}
