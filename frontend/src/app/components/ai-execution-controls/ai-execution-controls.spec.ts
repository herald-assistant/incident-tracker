import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TestbedHarnessEnvironment } from '@angular/cdk/testing/testbed';
import { MatSelectHarness } from '@angular/material/select/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { AnalysisAiModelOptionsResponse } from '../../core/models/analysis.models';
import { AiExecutionControlsComponent } from './ai-execution-controls';

function catalog(): AnalysisAiModelOptionsResponse {
  return {
    defaultModel: 'crm-analysis', defaultReasoningEffort: 'medium',
    defaultReasoningEfforts: ['low', 'medium', 'high'],
    models: [
      { id: 'crm-analysis', name: 'CRM Analysis', supportsReasoningEffort: true,
        reasoningEfforts: ['none', 'minimal', 'low', 'medium', 'high', 'xhigh', 'max', 'adaptive'],
        defaultReasoningEffort: 'medium' },
      { id: 'auto', name: 'Auto', supportsReasoningEffort: false,
        reasoningEfforts: [], defaultReasoningEffort: '' }
    ]
  };
}

describe('AiExecutionControlsComponent', () => {
  let fixture: ComponentFixture<AiExecutionControlsComponent>;
  let model: MatSelectHarness;
  let effort: MatSelectHarness;
  let modelChanged = vi.fn<(value: string) => void>();
  let effortChanged = vi.fn<(value: string) => void>();

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AiExecutionControlsComponent], providers: [provideNoopAnimations()]
    }).compileComponents();
    fixture = TestBed.createComponent(AiExecutionControlsComponent);
    fixture.componentRef.setInput('catalog', catalog());
    fixture.componentRef.setInput('model', 'crm-analysis');
    fixture.componentRef.setInput('reasoningEffort', 'medium');
    fixture.componentRef.setInput('modelInputId', 'crm-model');
    fixture.componentRef.setInput('reasoningInputId', 'crm-effort');
    modelChanged = vi.fn<(value: string) => void>();
    effortChanged = vi.fn<(value: string) => void>();
    fixture.componentInstance.modelChange.subscribe(modelChanged);
    fixture.componentInstance.reasoningEffortChange.subscribe(effortChanged);
    fixture.detectChanges();
    const loader = TestbedHarnessEnvironment.loader(fixture);
    model = await loader.getHarness(MatSelectHarness.with({ selector: '#crm-model' }));
    effort = await loader.getHarness(MatSelectHarness.with({ selector: '#crm-effort' }));
  });

  it('shows model names once, with IDs in tooltips and accessible field labels', async () => {
    expect(await model.getValueText()).toBe('CRM Analysis');
    expect(await effort.getValueText()).toBe('Średni');
    expect(fixture.nativeElement.querySelector('#crm-model').getAttribute('aria-label')).toBe('Model AI');
    expect(fixture.nativeElement.querySelector('#crm-effort').getAttribute('aria-label')).toBe('Reasoning effort');
    expect(fixture.nativeElement.querySelector('#crm-model .choice-select__value').title).toBe('crm-analysis');
    await model.open();
    const option = (await model.getOptions({ text: 'CRM Analysis' }))[0];
    expect(await (await option.host()).getAttribute('title')).toBe('crm-analysis');
    expect(await model.getOptions()).toHaveLength(2);
  });

  it('translates effort names while emitting original values, including unknown future levels', async () => {
    await effort.open();
    expect(await Promise.all((await effort.getOptions()).map(option => option.getText()))).toEqual([
      'Wyłączony', 'Minimalny', 'Niski', 'Średni', 'Wysoki', 'Bardzo wysoki', 'Maksymalny', 'adaptive'
    ]);
    await effort.clickOptions({ text: 'Wysoki' });
    expect(effortChanged).toHaveBeenCalledExactlyOnceWith('high');
    await effort.open();
    await effort.clickOptions({ text: 'adaptive' });
    expect(effortChanged).toHaveBeenLastCalledWith('adaptive');
    expect(modelChanged).not.toHaveBeenCalled();
  });

  it('leaves model selection and resetting effort to the feature, and forwards touched events', async () => {
    const modelTouched = vi.fn();
    const effortTouched = vi.fn();
    fixture.componentInstance.modelTouched.subscribe(modelTouched);
    fixture.componentInstance.reasoningTouched.subscribe(effortTouched);
    await model.open();
    await model.clickOptions({ text: 'Auto' });
    expect(modelChanged).toHaveBeenCalledExactlyOnceWith('auto');
    expect(effortChanged).not.toHaveBeenCalled();
    expect(fixture.componentInstance.reasoningEffort()).toBe('medium');
    expect(modelTouched).toHaveBeenCalled();
    await effort.open();
    await effort.close();
    expect(effortTouched).toHaveBeenCalled();
  });

  it.each([
    ['loading', 'Ładowanie modeli…', 'Ładowanie poziomów…'],
    ['unavailable', 'Katalog modeli niedostępny', 'Katalog modeli niedostępny'],
    ['empty', 'Brak dostępnych modeli', 'Brak dostępnych modeli']
  ])('locks both controls for %s without losing restored preferences', async (state, modelText, effortText) => {
    if (state === 'empty') fixture.componentRef.setInput('catalog', { ...catalog(), models: [] });
    else fixture.componentRef.setInput(state, true);
    fixture.detectChanges();
    expect(await model.isDisabled()).toBe(true);
    expect(await effort.isDisabled()).toBe(true);
    expect(await model.getValueText()).toBe(modelText);
    expect(await effort.getValueText()).toBe(effortText);
    expect(fixture.componentInstance.model()).toBe('crm-analysis');
    expect(fixture.componentInstance.reasoningEffort()).toBe('medium');
    fixture.componentRef.setInput('catalog', catalog());
    fixture.componentRef.setInput('loading', false);
    fixture.componentRef.setInput('unavailable', false);
    fixture.detectChanges();
    expect(await model.getValueText()).toBe('CRM Analysis');
    expect(await effort.getValueText()).toBe('Średni');
    expect(modelChanged).not.toHaveBeenCalled();
    expect(effortChanged).not.toHaveBeenCalled();
  });

  it.each([
    ['crm-analysis', 'Domyślny: CRM Analysis'],
    ['crm-configured', 'Domyślny: crm-configured']
  ])('explains the implicit default %s without selecting Auto or emitting a value', async (defaultModel, text) => {
    fixture.componentRef.setInput('catalog', { ...catalog(), defaultModel });
    fixture.componentRef.setInput('model', '');
    fixture.componentRef.setInput('reasoningEffort', '');
    fixture.detectChanges();
    expect(await model.getValueText()).toBe(text);
    expect(await effort.getValueText()).toBe('Domyślny: Średni');
    await model.open();
    expect(await (await model.getOptions({ text }))[0].isDisabled()).toBe(true);
    expect(await model.getOptions({ text: 'Auto' })).toHaveLength(1);
    expect(fixture.componentInstance.model()).toBe('');
    expect(modelChanged).not.toHaveBeenCalled();
    expect(effortChanged).not.toHaveBeenCalled();
  });

  it('distinguishes missing selection, unsupported reasoning and an empty effort catalog', async () => {
    fixture.componentRef.setInput('catalog', { ...catalog(), defaultModel: '', defaultReasoningEfforts: [] });
    fixture.componentRef.setInput('model', '');
    fixture.componentRef.setInput('reasoningEffort', '');
    fixture.detectChanges();
    expect(await model.getValueText()).toBe('Wybierz model AI');
    expect(await effort.getValueText()).toBe('Najpierw wybierz model');
    expect(await effort.isDisabled()).toBe(true);
    fixture.componentRef.setInput('model', 'auto');
    fixture.detectChanges();
    expect(await model.getValueText()).toBe('Auto');
    expect(await effort.getValueText()).toBe('Model nie udostępnia reasoning');
    expect(await effort.isDisabled()).toBe(true);
    const emptyEfforts = catalog();
    emptyEfforts.models[0].reasoningEfforts = [];
    fixture.componentRef.setInput('catalog', emptyEfforts);
    fixture.componentRef.setInput('model', 'crm-analysis');
    fixture.detectChanges();
    expect(await effort.getValueText()).toBe('Brak dostępnych poziomów');
    expect(await effort.isDisabled()).toBe(true);
    expect(effortChanged).not.toHaveBeenCalled();
  });

  it('allows returning to the implicit default only when the feature permits it', async () => {
    fixture.componentRef.setInput('allowDefaultSelection', true);
    fixture.detectChanges();
    await model.open();
    await model.clickOptions({ text: 'Domyślny: CRM Analysis' });
    expect(modelChanged).toHaveBeenCalledExactlyOnceWith('');
    await effort.open();
    await effort.clickOptions({ text: 'Domyślny: Średni' });
    expect(effortChanged).toHaveBeenCalledExactlyOnceWith('');
  });

  it('keeps imported model and effort values that are absent from the current catalog', async () => {
    fixture.componentRef.setInput('model', 'crm-previous');
    fixture.componentRef.setInput('reasoningEffort', 'previous-level');
    fixture.detectChanges();
    expect(await model.getValueText()).toBe('crm-previous');
    expect(await effort.getValueText()).toBe('previous-level');
    expect(await effort.isDisabled()).toBe(true);
    await model.open();
    expect(await (await model.getOptions({ text: 'crm-previous' }))[0].isDisabled()).toBe(true);
    expect(modelChanged).not.toHaveBeenCalled();
    expect(effortChanged).not.toHaveBeenCalled();
  });

  it('honors independent field locks and exposes an API error accessibly', async () => {
    fixture.componentRef.setInput('modelDisabled', true);
    fixture.detectChanges();
    expect(await model.isDisabled()).toBe(true);
    expect(await effort.isDisabled()).toBe(false);
    fixture.componentRef.setInput('modelDisabled', false);
    fixture.componentRef.setInput('reasoningDisabled', true);
    fixture.detectChanges();
    expect(await model.isDisabled()).toBe(false);
    expect(await effort.isDisabled()).toBe(true);
    fixture.componentRef.setInput('disabled', true);
    fixture.detectChanges();
    expect(await model.isDisabled()).toBe(true);
    expect(await effort.isDisabled()).toBe(true);
    fixture.componentRef.setInput('error', 'Nie można pobrać katalogu CRM.');
    fixture.detectChanges();
    expect(await model.getValueText()).toBe('Katalog modeli niedostępny');
    expect(fixture.nativeElement.querySelector('#crm-model').getAttribute('aria-describedby')).toContain('crm-model-error');
    expect(fixture.nativeElement.textContent).toContain('Nie można pobrać katalogu CRM.');
    expect(modelChanged).not.toHaveBeenCalled();
    expect(effortChanged).not.toHaveBeenCalled();
  });

  it('keeps accessible model and effort selection in the compact toolbar', async () => {
    fixture.componentRef.setInput('compact', true); fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('#crm-model').getAttribute('aria-label')).toBe('Model AI');
    expect(fixture.nativeElement.querySelector('#crm-effort').getAttribute('aria-label')).toBe('Reasoning effort');
    expect(fixture.nativeElement.querySelector('.choice-select--descriptive')).toBeNull();
    expect(await model.getValueText()).toBe('CRM Analysis');
    await effort.open(); await (await effort.getOptions({text: 'Wysoki'}))[0].click();
    expect(effortChanged).toHaveBeenCalledWith('high');
    expect(modelChanged).not.toHaveBeenCalled();
    fixture.componentRef.setInput('disabled', true); fixture.detectChanges();
    expect(await model.isDisabled()).toBe(true); expect(await effort.isDisabled()).toBe(true);
  });
});
