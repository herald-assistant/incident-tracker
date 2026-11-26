import {Component, computed, DestroyRef, inject, input, linkedSignal, model, OnInit, output, signal} from '@angular/core';
import {Dialog, DialogModule, DialogRef} from '@angular/cdk/dialog';
import {CdkDrag, CdkDragDrop, CdkDropList, moveItemInArray} from '@angular/cdk/drag-drop';
import {MatTooltipModule} from '@angular/material/tooltip';
import {takeUntilDestroyed} from '@angular/core/rxjs-interop';
import {HttpErrorResponse} from '@angular/common/http';
import {Subscription} from 'rxjs';
import {ChoiceSelectComponent} from '../../components/choice-select/choice-select';
import {TextFieldComponent} from '../../components/text-field/text-field';
import {downloadJsonFile, readJsonFile, sanitizeFileNamePart} from '../../core/utils/json-file.utils';
import {AssistantAuthProfiles} from './assistant-auth-profiles';
import {AssistantOrderHandle} from './assistant-order-handle';
import {AUTH_TYPES, authErrors, newAuthProfile} from './assistant-auth';
import {missingProfileResources} from './assistant-profiles';
import {OpenApiContractViewer} from './openapi-contract-viewer';
import {OpenApiOperationPicker, OpenApiPickerResult} from './openapi-operation-picker';
import {assistantErrorMessage, SystemAssistantApi} from './system-assistant-api.service';
import {ASSISTANT_CONFIGURATION_FILE_MAX_BYTES, ASSISTANT_CONFIGURATION_FILE_NAME, configurationFile, parseConfigurationFile} from './system-assistant-configuration-file';
import {SYSTEM_ASSISTANT_WINDOW} from './system-assistant-ingress.service';
import {AssistantApiDefinition, AssistantAuthProfile, AssistantConfiguration, AssistantOidcConnection, AssistantProfile, AssistantProfileOption, AssistantSkill, AssistantTool} from './system-assistant.models';

const EMPTY_CONFIGURATION: AssistantConfiguration = {apis: [], skills: [], profiles: [], authProfiles: []};

function sameConfigurationValue(left: unknown, right: unknown): boolean {
  // Optional Auth fields loaded as null are displayed and edited as empty strings.
  if (left === right || (left === null && right === '') || (left === '' && right === null)) return true;
  if (!left || !right || typeof left !== 'object' || typeof right !== 'object') return false;
  const keys = Object.keys(left);
  return keys.length === Object.keys(right).length && keys.every(key =>
    Object.hasOwn(right, key) && sameConfigurationValue(Reflect.get(left, key), Reflect.get(right, key)));
}

@Component({
  selector: 'app-assistant-configuration-editor',
  imports: [AssistantAuthProfiles, AssistantOrderHandle, CdkDrag, CdkDropList, ChoiceSelectComponent, DialogModule, MatTooltipModule, TextFieldComponent],
  templateUrl: './assistant-configuration-editor.html',
  styleUrl: './assistant-configuration-editor.scss'
})
export class AssistantConfigurationEditor implements OnInit {
  readonly workspaceView = input.required<string>();
  readonly tabRequested = output<string>();
  readonly error = model('');
  readonly notice = model('');
  readonly authBusyId = model('');
  readonly loaded = output<AssistantProfileOption[]>();
  readonly loadFailed = output<HttpErrorResponse>();
  private readonly api = inject(SystemAssistantApi);
  private readonly destroyRef = inject(DestroyRef);
  private readonly browser = inject(SYSTEM_ASSISTANT_WINDOW);
  private toolsSubscription?: Subscription;
  private readonly dialogs = inject(Dialog);
  private picker?: DialogRef<OpenApiPickerResult>;
  private viewer?: DialogRef<void>;
  private readonly savedConfiguration = signal<AssistantConfiguration | null>(null);
  readonly configuration = linkedSignal(() => this.savedConfiguration() ?? EMPTY_CONFIGURATION);
  readonly dirty = computed(() => {
    const saved = this.savedConfiguration();
    // With an unreadable configuration, even an explicitly imported empty draft can repair it.
    return saved ? this.differsFrom(saved) : this.configuration() !== EMPTY_CONFIGURATION;
  });
  readonly editingApiId = signal<string | null>(null);
  // Skill IDs are editable, including temporarily blank or duplicate values in a draft.
  private readonly skillRowKeys = new WeakMap<AssistantSkill, string>();
  private nextSkillRowKey = 0;
  readonly expandedDefinitions = signal<Record<'skills' | 'profiles' | 'authProfiles', ReadonlySet<string>>>({
    skills: new Set(), profiles: new Set(), authProfiles: new Set()
  });
  readonly tools = signal<AssistantTool[]>([]);
  readonly toolsLoading = signal(false);
  readonly toolsError = signal('');
  readonly contractActivity = computed(() => new Map(this.configuration().apis.map(api => {
    const tools = this.tools().filter(tool => tool.apiId === api.id);
    const count = this.toolsLoading() || this.toolsError() ? null : tools.length;
    const tooltip = this.toolsLoading() ? 'Odświeżanie aktywnych operacji…' : this.toolsError()
      || (tools.length ? `Aktywne operacje: ${tools.length}\n${tools.map(tool => `${tool.method} ${tool.path}`).join('\n')}`
        : 'Brak aktywnych operacji. Zapisz kontrakt, aby udostępnić jego operacje.');
    return [api.id, {count, tooltip}] as const;
  })));
  readonly configurationEntryCount = computed(() => {
    const kind = this.workspaceView() === 'contracts' ? 'apis' : this.workspaceView() === 'auth' ? 'authProfiles'
      : this.workspaceView() === 'profiles' ? 'profiles' : 'skills';
    return this.configuration()[kind].length;
  });
  readonly saveError = signal('');
  readonly saving = signal(false);
  readonly importing = signal(false);
  readonly configurationBusy = computed(() => this.saving() || this.importing());
  readonly authConnections = signal<Record<string, AssistantOidcConnection>>({});
  readonly authStatusError = signal('');
  readonly authValidationVisible = signal(false);
  private authStatusGeneration = 0;

  constructor() {
    this.destroyRef.onDestroy(() => { this.picker?.close(); this.viewer?.close(); });
  }

  differsFrom(snapshot: AssistantConfiguration | undefined): boolean {
    return !sameConfigurationValue(this.configuration(), snapshot);
  }

  ngOnInit(): void {
    this.api.configuration().pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: value => {
        this.savedConfiguration.set(value);
        this.loaded.emit(value.profiles);
        this.loadTools();
        this.loadAuthStatus();
      },
      error: error => this.loadFailed.emit(error)
    });
  }

  loadTools(): void {
    this.toolsSubscription?.unsubscribe();
    this.toolsLoading.set(true); this.toolsError.set('');
    this.toolsSubscription = this.api.tools().pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: tools => { this.tools.set(tools); this.toolsLoading.set(false); },
      error: () => { this.toolsLoading.set(false); this.toolsError.set('Nie udało się pobrać aktywnych operacji. Odśwież stronę, aby spróbować ponownie.'); }
    });
  }
  addProfile(): void {
    if (this.configurationBusy() || this.configuration().profiles.length >= 20) return;
    const id = crypto.randomUUID();
    this.configuration.update(value => ({...value, profiles: [...value.profiles,
      {id, name: 'Nowy profil', urlPattern: '*', apiBindings: [], skillIds: []}]}));
    this.setDefinitionExpanded('profiles', id, true);
    this.notice.set('');
  }
  updateProfile(index: number, key: 'name' | 'urlPattern', content: string): void {
    if (this.configurationBusy()) return;
    this.configuration.update(value => ({...value, profiles: value.profiles.map((item, i) => i === index ? {...item, [key]: content} : item)}));
    this.notice.set('');
  }
  toggleProfileResource(index: number, key: 'skillIds', id: string, checked: boolean): void {
    if (this.configurationBusy()) return;
    this.configuration.update(value => ({...value, profiles: value.profiles.map((item, i) => i === index
      ? {...item, [key]: checked ? [...new Set([...item[key], id])] : item[key].filter(current => current !== id)} : item)}));
    this.notice.set('');
  }
  profileWarning(profile: AssistantProfile): string { return missingProfileResources(profile, this.configuration()); }
  profileSummary(profile: AssistantProfile): string {
    const scope = profile.urlPattern === '*' ? 'Wszystkie adresy' : profile.urlPattern || 'Brak wzorca URL';
    return `${scope} · Skille: ${profile.skillIds.length} · API: ${profile.apiBindings.length}`;
  }
  profileResources(profile: AssistantProfile, kind: 'apis' | 'skills') {
    const resources = this.configuration()[kind].map(item => ({id: item.id, name: item.name}));
    const ids = kind === 'apis' ? profile.apiBindings.map(binding => binding.apiId) : profile.skillIds;
    return [...resources, ...ids.filter(id => !resources.some(item => item.id === id)).map(id => ({id, name: 'Brak: ' + id}))];
  }
  addSkill(): void {
    if (this.configurationBusy()) return;
    const id = `skill_${crypto.randomUUID().slice(0, 8)}`;
    const skill = {id, name: 'Nowy skill', content: ''};
    this.configuration.update(value => ({...value, skills: [...value.skills, skill]}));
    this.setDefinitionExpanded('skills', this.skillRowKey(skill), true);
    this.notice.set('');
  }
  skillRowKey(skill: AssistantSkill): string {
    let key = this.skillRowKeys.get(skill);
    if (key === undefined) {
      key = String(this.nextSkillRowKey++);
      this.skillRowKeys.set(skill, key);
    }
    return key;
  }
  updateSkill(index: number, key: 'id' | 'name' | 'content', content: string): void {
    if (this.configurationBusy()) return;
    const current = this.configuration().skills[index];
    const edited = {...current, [key]: content};
    this.skillRowKeys.set(edited, this.skillRowKey(current));
    this.configuration.update(value => ({...value, skills: value.skills.map((item, i) => i === index ? edited : item)}));
    this.notice.set('');
  }
  toggleApiName(id: string): void {
    if (!this.configurationBusy()) this.editingApiId.set(this.editingApiId() === id ? null : id);
  }
  updateApiName(id: string, name: string): void {
    if (this.configurationBusy() || !this.configuration().apis.some(api => api.id === id)) return;
    this.configuration.update(value => ({...value, apis: value.apis.map(api => api.id === id ? {...api, name} : api)}));
    this.notice.set('');
  }
  apiNameError(name: string): string {
    return !name.trim() ? 'Podaj nazwę API.' : name.length > 100 ? 'Nazwa API może mieć do 100 znaków.' : '';
  }
  dropDefinition(kind: keyof AssistantConfiguration, event: CdkDragDrop<unknown>): void {
    if (event.isPointerOverContainer && event.previousContainer === event.container
        && this.configuration()[kind][event.previousIndex] === event.item.data) {
      this.moveDefinition(kind, event.previousIndex, event.currentIndex);
    }
  }
  moveDefinition(kind: keyof AssistantConfiguration, from: number, to: number): void {
    const entries = [...this.configuration()[kind]];
    if (this.configurationBusy() || from === to || !entries[from] || !entries[to]) return;
    moveItemInArray(entries, from, to);
    this.configuration.update(value => ({...value, [kind]: entries}));
    this.notice.set('');
  }
  remove(kind: 'apis' | 'skills' | 'profiles', index: number): void {
    if (this.configurationBusy()) return;
    if (kind === 'apis' && this.editingApiId() === this.configuration().apis[index]?.id) this.editingApiId.set(null);
    if (kind === 'skills') {
      this.setDefinitionExpanded('skills', this.skillRowKey(this.configuration().skills[index]), false);
    } else if (kind === 'profiles') this.setDefinitionExpanded('profiles', this.configuration().profiles[index].id, false);
    this.configuration.update(value => ({ ...value, [kind]: value[kind].filter((_, i) => i !== index) }));
    this.notice.set('');
  }
  exportConfiguration(): void {
    if (this.configurationBusy()) return;
    downloadJsonFile(ASSISTANT_CONFIGURATION_FILE_NAME, configurationFile(this.configuration()));
  }
  async importConfiguration(event: Event): Promise<void> {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (!file || this.configurationBusy()) { input.value = ''; return; }
    this.importing.set(true); this.error.set(''); this.notice.set('');
    try {
      if (file.size > ASSISTANT_CONFIGURATION_FILE_MAX_BYTES) throw new Error('Plik konfiguracji może mieć do 100000000 bajtów.');
      const value = await readJsonFile(file, 'Plik konfiguracji nie zawiera poprawnego JSON-a.');
      if (this.destroyRef.destroyed) return;
      const configuration = parseConfigurationFile(value);
      this.editingApiId.set(null);
      this.expandedDefinitions.set({skills: new Set(), profiles: new Set(), authProfiles: new Set()});
      this.configuration.set(configuration); this.saveError.set('');
      if (!this.dirty()) this.notice.set('Wczytana konfiguracja jest zgodna z zapisaną.');
      else this.notice.set(`Wczytano konfigurację do szkicu (kontrakty: ${configuration.apis.length}, skille: ${configuration.skills.length}, profile: ${configuration.profiles.length}, Auth: ${configuration.authProfiles.length}). Zapisz, aby ją aktywować.`);
      this.tabRequested.emit('skills');
    } catch (error) {
      if (!this.destroyRef.destroyed) this.error.set(error instanceof Error ? error.message : 'Nie udało się odczytać pliku konfiguracji.');
    } finally {
      input.value = '';
      if (!this.destroyRef.destroyed) this.importing.set(false);
    }
  }
  async readNewOpenApi(event: Event): Promise<void> {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0]; if (!file) return;
    try {
      if (file.size > 2_000_000) throw new Error('Plik źródłowy OpenAPI może mieć do 2000000 bajtów.');
      this.openPicker(await file.text(), file.name);
    } catch (error) { this.error.set(error instanceof Error ? error.message : 'Nie udało się odczytać pliku.'); }
    input.value = '';
  }
  private openPicker(yaml: string, sourceName: string): void {
    if (this.configurationBusy() || this.destroyRef.destroyed || this.picker || this.configuration().apis.length >= 12) return;
    this.error.set('');
    const id = `api_${crypto.randomUUID().slice(0, 8)}`;
    this.picker = this.dialogs.open<OpenApiPickerResult>(OpenApiOperationPicker, {
      data: {id, yaml, sourceName}, ariaLabelledBy: 'openApiPickerTitle',
      autoFocus: 'first-tabbable', restoreFocus: true, closeOnNavigation: true
    });
    this.picker.closed.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(result => {
      this.picker = undefined;
      if (!result) return;
      this.configuration.update(value => ({...value, apis: [...value.apis, {id, name: result.title, yaml: result.yaml}]}));
      this.notice.set(`Operacje w zestawie: ${result.operationCount}. Zapisz konfigurację, aby je udostępnić.`);
    });
  }
  viewContract(contract: AssistantApiDefinition): void {
    if (this.viewer) return;
    this.viewer = this.dialogs.open<void>(OpenApiContractViewer, {
      data: contract, ariaLabelledBy: 'openApiViewerTitle', autoFocus: 'first-tabbable', restoreFocus: true, closeOnNavigation: true
    });
    this.viewer.closed.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => this.viewer = undefined);
  }
  exportContract(contract: AssistantApiDefinition): void {
    if (this.configurationBusy()) return;
    const url = URL.createObjectURL(new Blob([contract.yaml], {type: 'application/yaml;charset=utf-8'}));
    const link = document.createElement('a');
    link.href = url; link.download = `${sanitizeFileNamePart(contract.name)}.yaml`;
    try {
      document.body.appendChild(link); link.click();
    } finally {
      link.remove(); window.setTimeout(() => URL.revokeObjectURL(url), 0);
    }
  }
  save(): void {
    if (this.configurationBusy()) return;
    const invalidApi = this.configuration().apis.find(api => this.apiNameError(api.name));
    if (invalidApi) {
      this.editingApiId.set(invalidApi.id); this.tabRequested.emit('contracts');
      this.saveError.set(this.apiNameError(invalidApi.name)); return;
    }
    this.authValidationVisible.set(true);
    if (this.configuration().authProfiles.some(profile => Object.keys(authErrors(profile)).length)) {
      for (const profile of this.configuration().authProfiles) {
        if (Object.keys(authErrors(profile)).length) this.setDefinitionExpanded('authProfiles', profile.id, true);
      }
      this.tabRequested.emit('auth'); this.saveError.set('Uzupełnij zaznaczone pola uwierzytelniania.'); return;
    }
    this.saving.set(true); this.error.set(''); this.saveError.set(''); this.notice.set('');
    const draft = this.configuration();
    this.api.save(draft).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: value => {
        value.skills.forEach((skill, index) => this.skillRowKeys.set(skill, this.skillRowKey(draft.skills[index])));
        this.savedConfiguration.set(value); this.loaded.emit(value.profiles); this.saving.set(false);
        this.notice.set('Zapisano. Kolejna tura użyje bieżącego zestawu API i skilli.'); this.loadTools();
      },
      error: error => { this.saving.set(false); this.saveError.set(assistantErrorMessage(error)); }
    });
  }
  addAuth(): void {
    if (this.configurationBusy() || this.configuration().authProfiles.length >= 20) return;
    const profile = newAuthProfile('BASIC', `auth_${crypto.randomUUID().slice(0, 8)}`, 'Nowe uwierzytelnianie', window.location.origin);
    this.updateAuthProfiles([...this.configuration().authProfiles, profile]);
    this.setDefinitionExpanded('authProfiles', profile.id, true);
  }
  toggleDefinition(kind: 'skills' | 'profiles' | 'authProfiles', id: string): void {
    if (!this.configurationBusy()) this.setDefinitionExpanded(kind, id, !this.expandedDefinitions()[kind].has(id));
  }
  private setDefinitionExpanded(kind: 'skills' | 'profiles' | 'authProfiles', id: string, expanded: boolean): void {
    this.expandedDefinitions.update(value => {
      const ids = new Set(value[kind]);
      if (expanded) ids.add(id); else ids.delete(id);
      return {...value, [kind]: ids};
    });
  }
  updateAuthProfiles(authProfiles: AssistantAuthProfile[]): void {
    if (this.configurationBusy()) return;
    this.configuration.update(value => ({...value, authProfiles})); this.notice.set('');
  }
  profileHasApi(profile: AssistantProfile, apiId: string): boolean { return profile.apiBindings.some(binding => binding.apiId === apiId); }
  profileApiAuth(profile: AssistantProfile, apiId: string): string { return profile.apiBindings.find(binding => binding.apiId === apiId)?.authProfileId || ''; }
  apiAuthOptions(profile: AssistantProfile, apiId: string) {
    const options = [{value: '', label: 'No Auth'}, ...this.configuration().authProfiles.map(auth => ({value: auth.id,
      label: auth.name || 'Nowe uwierzytelnianie', hint: AUTH_TYPES.find(type => type.value === auth.type)?.label || auth.type}))];
    const selected = this.profileApiAuth(profile, apiId);
    return selected && !options.some(option => option.value === selected)
      ? [...options, {value: selected, label: 'Niedostępne uwierzytelnianie'}] : options;
  }
  toggleProfileApi(index: number, apiId: string, checked: boolean): void {
    if (this.configurationBusy()) return;
    this.configuration.update(value => ({...value, profiles: value.profiles.map((profile, i) => i !== index ? profile : {...profile,
      apiBindings: checked ? this.profileHasApi(profile, apiId) ? profile.apiBindings : [...profile.apiBindings, {apiId, authProfileId: null}]
        : profile.apiBindings.filter(binding => binding.apiId !== apiId)})}));
    this.notice.set('');
  }
  updateProfileApiAuth(index: number, apiId: string, authProfileId: string): void {
    if (this.configurationBusy()) return;
    this.configuration.update(value => ({...value, profiles: value.profiles.map((profile, i) => i !== index ? profile : {...profile,
      apiBindings: profile.apiBindings.map(binding => binding.apiId === apiId ? {...binding, authProfileId: authProfileId || null} : binding)})}));
    this.notice.set('');
  }
  loadAuthStatus(): void {
    const generation = ++this.authStatusGeneration;
    if (!this.configuration().authProfiles.some(profile => profile.type === 'OIDC')) {
      this.authConnections.set({}); this.authStatusError.set(''); return;
    }
    this.api.authStatus().pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: statuses => { if (generation === this.authStatusGeneration) { this.authConnections.set(statuses); this.authStatusError.set(''); } },
      error: error => { if (generation === this.authStatusGeneration) this.authStatusError.set(assistantErrorMessage(error)); }
    });
  }
  loginAuth(id: string): void {
    if (this.dirty() || this.configurationBusy() || this.authBusyId()) return;
    this.authBusyId.set(id); this.saveError.set('');
    this.api.loginAuth(id).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: result => {
        if (this.dirty()) {
          this.authBusyId.set('');
          this.saveError.set('Konfiguracja zmieniła się podczas logowania. Zapisz lub cofnij zmiany i spróbuj ponownie.');
          return;
        }
        this.browser.location.assign(result.authorizationUrl);
      },
      error: error => { this.authBusyId.set(''); this.saveError.set(assistantErrorMessage(error)); }
    });
  }
  disconnectAuth(id: string): void {
    if (this.dirty() || this.configurationBusy() || this.authBusyId()) return;
    this.authBusyId.set(id); this.saveError.set('');
    this.api.disconnectAuth(id).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: () => { this.authBusyId.set(''); this.loadAuthStatus(); },
      error: error => { this.authBusyId.set(''); this.saveError.set(assistantErrorMessage(error)); }
    });
  }
}
