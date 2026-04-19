import { Component, computed, input } from '@angular/core';

import {
  AnalysisFlowDiagram,
  AnalysisFlowDiagramEdge,
  AnalysisFlowDiagramNode
} from '../../core/models/analysis.models';

interface FlowSegment {
  edge: AnalysisFlowDiagramEdge;
  toNode: AnalysisFlowDiagramNode | null;
}

@Component({
  selector: 'app-analysis-flow-diagram',
  imports: [],
  templateUrl: './analysis-flow-diagram.html',
  styleUrl: './analysis-flow-diagram.scss'
})
export class AnalysisFlowDiagramComponent {
  readonly diagram = input<AnalysisFlowDiagram | null>(null);

  protected readonly nodeMap = computed(() => {
    const map = new Map<string, AnalysisFlowDiagramNode>();
    for (const node of this.diagram()?.nodes || []) {
      map.set(node.id, node);
    }
    return map;
  });

  protected readonly leadingNode = computed<AnalysisFlowDiagramNode | null>(() => {
    const diagram = this.diagram();
    if (!diagram?.nodes?.length) {
      return null;
    }

    const sortedEdges = [...(diagram.edges || [])].sort((left, right) => left.sequence - right.sequence);
    if (sortedEdges.length === 0) {
      return diagram.nodes[0] || null;
    }

    return this.nodeMap().get(sortedEdges[0].fromNodeId) || diagram.nodes[0] || null;
  });

  protected readonly segments = computed<FlowSegment[]>(() =>
    [...(this.diagram()?.edges || [])]
      .sort((left, right) => left.sequence - right.sequence)
      .map((edge) => ({
        edge,
        toNode: this.nodeMap().get(edge.toNodeId) || null
      }))
  );

  protected readonly orphanNodes = computed<AnalysisFlowDiagramNode[]>(() => {
    const diagram = this.diagram();
    if (!diagram?.nodes?.length) {
      return [];
    }

    const referencedNodeIds = new Set<string>();
    for (const edge of diagram.edges || []) {
      referencedNodeIds.add(edge.fromNodeId);
      referencedNodeIds.add(edge.toNodeId);
    }

    return diagram.nodes.filter((node) => !referencedNodeIds.has(node.id));
  });
}
