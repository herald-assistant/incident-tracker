import { Component, input } from '@angular/core';
import { SpecialThemeOverviewBanner } from './special-theme.service';

@Component({
  selector: 'app-special-theme-banner',
  templateUrl: './special-theme-banner.html',
  styleUrl: './special-theme-banner.scss'
})
export class SpecialThemeBannerComponent {
  readonly banner = input.required<SpecialThemeOverviewBanner>();
  readonly headingId = input.required<string>();
}
