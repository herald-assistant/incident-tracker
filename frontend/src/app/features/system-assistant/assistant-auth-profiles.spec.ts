import {TestBed} from '@angular/core/testing';
import {TestbedHarnessEnvironment} from '@angular/cdk/testing/testbed';
import {MatSelectHarness} from '@angular/material/select/testing';
import {AssistantAuthProfiles} from './assistant-auth-profiles';
import {newAuthProfile} from './assistant-auth';
import {configurationFile, parseConfigurationFile} from './system-assistant-configuration-file';
import {AssistantAuthProfile} from './system-assistant.models';

describe('Assistant Auth profiles', () => {
  it('edits inline, switches type without retaining previous credentials, and shows plain values', async () => {
    const fixture = TestBed.createComponent(AssistantAuthProfiles);
    const basic = {...newAuthProfile('BASIC', 'crm', 'CRM access', 'http://localhost'), username: 'operator', password: 'public-test-password'};
    fixture.componentRef.setInput('profiles', [basic]); fixture.componentRef.setInput('apis', []);
    let emitted: AssistantAuthProfile[] = [];
    fixture.componentInstance.profilesChange.subscribe(value => { emitted = value; fixture.componentRef.setInput('profiles', value); });
    fixture.detectChanges();
    expect([...fixture.nativeElement.querySelectorAll('input')].some((input: any) => input.value === 'public-test-password' && input.type === 'text')).toBe(true);
    expect(fixture.nativeElement.querySelector('[role=dialog]')).toBeNull();
    const type = await TestbedHarnessEnvironment.loader(fixture).getHarness(MatSelectHarness);
    await type.open(); await type.clickOptions({text: /Bearer/});
    expect(emitted[0].type).toBe('BEARER'); expect(emitted[0].password).toBeNull();
    expect(emitted[0].id).toBe('crm'); expect(emitted[0].name).toBe('CRM access');
    fixture.componentInstance.update(0, 'token', 'public-token'); fixture.detectChanges();
    expect(emitted[0].token).toBe('public-token');
    expect(fixture.nativeElement.textContent).toContain('Sam token, bez prefiksu Bearer'); fixture.destroy();
  });

  it('blocks deletion of a used profile and requires save before OIDC login', () => {
    const fixture = TestBed.createComponent(AssistantAuthProfiles);
    const profile = newAuthProfile('OIDC', 'crm', 'CRM OIDC', 'http://localhost');
    fixture.componentRef.setInput('profiles', [profile]);
    fixture.componentRef.setInput('apis', [{id: 'crm_api', name: 'CRM API', yaml: ''}]);
    fixture.componentRef.setInput('assistantProfiles', [{id: 'work', name: 'CRM work', urlPattern: '*', apiBindings: [{apiId: 'crm_api', authProfileId: 'crm'}], skillIds: []}]);
    fixture.componentRef.setInput('dirty', true); fixture.componentRef.setInput('showErrors', true);
    const changed = vi.fn(); fixture.componentInstance.profilesChange.subscribe(changed); fixture.detectChanges();
    fixture.componentInstance.remove(0); expect(changed).not.toHaveBeenCalled();
    const buttons = [...fixture.nativeElement.querySelectorAll('button')] as HTMLButtonElement[];
    expect(buttons.find(button => button.textContent?.trim() === 'Usuń')!.disabled).toBe(true);
    expect(buttons.find(button => button.textContent?.trim() === 'Zaloguj')!.disabled).toBe(true);
    expect(fixture.nativeElement.textContent).toContain('CRM API');
    expect(fixture.nativeElement.textContent).toContain('Podaj client ID.');
    fixture.componentRef.setInput('dirty', false); fixture.detectChanges();
    const loggedIn = vi.fn(); fixture.componentInstance.login.subscribe(loggedIn);
    buttons.find(button => button.textContent?.trim() === 'Zaloguj')!.click(); expect(loggedIn).toHaveBeenCalledWith('crm'); fixture.destroy();
  });

  it('round trips all four plain auth types and assignments, omitting runtime tokens and rejecting the old V1 shape', () => {
    const profiles = (['BASIC', 'BEARER', 'OAUTH2', 'OIDC'] as const).map(type => ({...newAuthProfile(type, type, type, 'http://localhost'),
      password: 'public-test-password', token: 'public-token', clientSecret: 'public-secret'}));
    const configuration = {apis: [{id: 'crm', name: 'CRM', yaml: 'yaml'}], skills: [], profiles: [{id: 'work', name: 'CRM work', urlPattern: '*', apiBindings: [{apiId: 'crm', authProfileId: 'BASIC'}], skillIds: []}], authProfiles: profiles};
    const draft = {...configuration, runtimeToken: 'do-not-export'};
    const exported = configurationFile(draft);
    expect(JSON.stringify(exported)).toContain('public-test-password');
    expect(JSON.stringify(exported)).not.toContain('do-not-export');
    expect(parseConfigurationFile(JSON.parse(JSON.stringify(exported)))).toEqual(configuration);
    expect(() => parseConfigurationFile({version: 1, configuration: {apis: [], skills: [], profiles: []}})).toThrow();
  });
});
