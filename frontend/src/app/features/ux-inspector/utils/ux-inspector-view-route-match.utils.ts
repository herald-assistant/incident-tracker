import { matchRouteToView } from '../../../core/utils/view-route-match.utils';
import { UxInspectorViewOption } from '../models/ux-inspector.models';

export function matchCapturedRouteToView(
  capturedPath: string,
  views: UxInspectorViewOption[],
  componentBoundaryTags: string[] = []
): UxInspectorViewOption | null {
  return matchRouteToView(capturedPath, views, componentBoundaryTags);
}
