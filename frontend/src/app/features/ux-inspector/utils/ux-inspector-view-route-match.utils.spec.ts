import { UxInspectorViewOption } from '../models/ux-inspector.models';
import { matchCapturedRouteToView } from './ux-inspector-view-route-match.utils';

describe('matchCapturedRouteToView', () => {
  it('selects the exact static route ahead of a parameter route', () => {
    expect(matchCapturedRouteToView('/contacts/new', [
      view('contact-details', '/contacts/:contactId'),
      view('contact-create', '/contacts/new')
    ])?.viewId).toBe('contact-create');
  });

  it('matches a runtime identifier and the redacted capture placeholder to a route parameter', () => {
    const views = [view('contact-details', '/contacts/:contactId')];

    expect(matchCapturedRouteToView('/contacts/customer-a7', views)?.viewId).toBe('contact-details');
    expect(matchCapturedRouteToView('/contacts/:value', views)?.viewId).toBe('contact-details');
  });

  it('uses a wildcard only when no more specific route matches', () => {
    expect(matchCapturedRouteToView('/contacts/customer-a7/history', [
      view('fallback', '/contacts/**'),
      view('contact-history', '/contacts/:contactId/history')
    ])?.viewId).toBe('contact-history');
  });

  it('does not guess when the best score is shared by multiple views', () => {
    expect(matchCapturedRouteToView('/contacts/customer-a7', [
      view('contact-by-id', '/contacts/:contactId'),
      view('contact-by-key', '/contacts/:contactKey')
    ])).toBeNull();
  });

  it('does not select a view when no route matches', () => {
    expect(matchCapturedRouteToView('/accounts', [view('contact-list', '/contacts')])).toBeNull();
  });

  it('normalizes trailing slashes and encoded static segments', () => {
    expect(matchCapturedRouteToView('/contact%20groups/', [
      view('contact-groups', '/contact groups')
    ])?.viewId).toBe('contact-groups');
  });
});

function view(viewId: string, routePattern: string): UxInspectorViewOption {
  return { viewId, routePattern, label: viewId, status: 'READY', limitations: [] };
}
