import {
  ConfigDriftViewerExportEnvelope,
  ConfigDriftViewerJobStateSnapshot
} from '../models/config-drift-viewer.models';

export function buildConfigDriftViewerExportEnvelope(
  job: ConfigDriftViewerJobStateSnapshot,
  exportedAt: string
): ConfigDriftViewerExportEnvelope {
  if (!job.components.some((component) => component.result) || !isTerminal(job.status)) {
    throw new Error('Eksport wymaga zakończonego wyniku Config Drift Viewer.');
  }
  return {
    schema: 'tdw.config-drift-viewer-export',
    version: 1,
    exportedAt,
    payload: {
      type: 'config-drift-viewer-analysis',
      resultContract: 'config-drift-viewer-result-v1',
      job
    }
  };
}

export function buildConfigDriftViewerExportFileName(
  job: ConfigDriftViewerJobStateSnapshot
): string {
  const componentPart = `${job.systemIds.length}-components`;
  return `config-drift-viewer-${safePart(componentPart)}-${safePart(job.sourceBranch)}-to-${safePart(job.targetBranch)}.json`;
}

function isTerminal(status: string): boolean {
  return ['COMPLETED', 'COMPLETED_WITH_LIMITATIONS', 'FAILED'].includes(status);
}

function safePart(value: string): string {
  return value.replace(/[^A-Za-z0-9._-]+/g, '-');
}
