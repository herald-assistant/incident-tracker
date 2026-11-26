import {Component, input, output} from '@angular/core';
import {DatePipe} from '@angular/common';
import {CdkDrag} from '@angular/cdk/drag-drop';
import {MatTooltipModule} from '@angular/material/tooltip';
import {TextFieldComponent} from '../../components/text-field/text-field';
import {ChoiceSelectComponent} from '../../components/choice-select/choice-select';
import {AssistantApiDefinition, AssistantProfile, AssistantAuthProfile, AssistantAuthType, AssistantOidcConnection} from './system-assistant.models';
import {AUTH_TYPES, authErrors, newAuthProfile} from './assistant-auth';
import {AssistantOrderHandle} from './assistant-order-handle';

@Component({
  selector: 'app-assistant-auth-profiles',
  imports: [AssistantOrderHandle, CdkDrag, TextFieldComponent, ChoiceSelectComponent, DatePipe, MatTooltipModule],
  templateUrl: './assistant-auth-profiles.html',
  styleUrl: './assistant-auth-profiles.scss'
})
export class AssistantAuthProfiles {
  readonly profiles = input.required<AssistantAuthProfile[]>();
  readonly assistantProfiles = input<AssistantProfile[]>([]);
  readonly apis = input.required<AssistantApiDefinition[]>();
  readonly disabled = input(false);
  readonly dirty = input(false);
  readonly showErrors = input(false);
  readonly busyId = input('');
  readonly connections = input<Record<string, AssistantOidcConnection>>({});
  readonly expandedIds = input<ReadonlySet<string>>(new Set());
  readonly expansionToggled = output<string>();
  readonly profilesChange = output<AssistantAuthProfile[]>();
  readonly orderChanged = output<{from: number; to: number}>();
  readonly login = output<string>();
  readonly disconnect = output<string>();
  readonly types = AUTH_TYPES;
  readonly methods = [{value: 'client_secret_basic', label: 'Basic — nagłówek'}, {value: 'client_secret_post', label: 'Parametry formularza'}];
  readonly oidcMethods = [{value: 'none', label: 'Bez sekretu — klient publiczny'}, ...this.methods];
  typeLabel(type: AssistantAuthType): string { return this.types.find(item => item.value === type)?.label || type; }

  update(index: number, key: keyof AssistantAuthProfile, value: string): void {
    if (this.disabled()) return;
    this.profilesChange.emit(this.profiles().map((profile, i) => {
      if (i !== index) return profile;
      if (key === 'type') return newAuthProfile(value as AssistantAuthType, profile.id, profile.name, window.location.origin);
      const next = {...profile, [key]: value};
      if (key === 'clientSecret' && profile.type === 'OIDC') next.clientAuthentication = !value.trim() ? 'none'
        : profile.clientAuthentication === 'none' ? 'client_secret_basic' : profile.clientAuthentication;
      return next;
    }));
  }
  usages(id: string): string { return this.assistantProfiles().flatMap(profile => profile.apiBindings.filter(binding => binding.authProfileId === id)
    .map(binding => `${profile.name} / ${this.apis().find(api => api.id === binding.apiId)?.name || binding.apiId}`)).join(', '); }
  remove(index: number): void {
    if (!this.disabled() && !this.usages(this.profiles()[index].id)) this.profilesChange.emit(this.profiles().filter((_, i) => i !== index));
  }
  error(profile: AssistantAuthProfile, key: string): string { return this.showErrors() ? authErrors(profile)[key] || '' : ''; }
}
