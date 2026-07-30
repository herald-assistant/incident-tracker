import {
  RuntimeConfigurationExportEnvelope,
  RuntimeConfigurationVerificationJobStateSnapshot
} from '../models/runtime-configuration-verification.models';

export function buildRuntimeConfigurationExportEnvelope(
  job: RuntimeConfigurationVerificationJobStateSnapshot,
  exportedAt: string
): RuntimeConfigurationExportEnvelope {
  if (!job.result || !isTerminal(job.status)) {
    throw new Error('Eksport wymaga zakończonego wyniku Runtime Configuration Verification.');
  }
  return {
    schema: 'tdw.runtime-configuration-verification-export',
    version: 1,
    exportedAt,
    payload: {
      type: 'runtime-configuration-verification-analysis',
      resultContract: 'runtime-configuration-verification-result-v1',
      job
    }
  };
}

export function buildRuntimeConfigurationExportFileName(
  job: RuntimeConfigurationVerificationJobStateSnapshot
): string {
  return `runtime-configuration-${safePart(job.systemId)}-${safePart(job.sourceBranch)}-to-${safePart(job.targetBranch)}.json`;
}

function isTerminal(status: string): boolean {
  return ['COMPLETED', 'COMPLETED_WITH_LIMITATIONS', 'FAILED'].includes(status);
}

function safePart(value: string): string {
  return value.replace(/[^A-Za-z0-9._-]+/g, '-');
}
