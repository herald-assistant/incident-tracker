import { ComponentFixture } from '@angular/core/testing';
import { TestbedHarnessEnvironment } from '@angular/cdk/testing/testbed';
import { MatSelectHarness } from '@angular/material/select/testing';

export function choice<T>(fixture: ComponentFixture<T>, selector: string): Promise<MatSelectHarness> {
  return TestbedHarnessEnvironment.loader(fixture).getHarness(MatSelectHarness.with({ selector }));
}

export async function choiceLabels<T>(fixture: ComponentFixture<T>, selector: string): Promise<string[]> {
  const select = await choice(fixture, selector);
  await select.open();
  const labels = await Promise.all((await select.getOptions()).map(option => option.getText()));
  await select.close();
  return labels;
}
