import { TestBed } from '@angular/core/testing';
import { OpenApiRenderer } from './openapi-renderer';

describe('OpenApiRenderer', () => {
  it('sends documents and selection updates separately and validates child events', () => {
    const fixture = TestBed.createComponent(OpenApiRenderer);
    const specification = {openapi: '3.0.3', paths: {}};
    const item = {method: 'GET', path: '/tickets', checked: false, disabled: false, unavailableReason: null};
    fixture.componentRef.setInput('specification', specification);
    fixture.componentRef.setInput('selection', [item]); fixture.detectChanges();
    const frame = fixture.nativeElement.querySelector('iframe') as HTMLIFrameElement;
    const post = vi.spyOn(frame.contentWindow!, 'postMessage');
    frame.dispatchEvent(new Event('load')); fixture.detectChanges();
    expect(post).toHaveBeenCalledWith({type: 'TDW_OPENAPI_DOCUMENT', specification, selection: [item]}, '*');
    post.mockClear();
    fixture.componentRef.setInput('selection', [{...item, checked: true}]); fixture.detectChanges();
    expect(post).toHaveBeenCalledWith({type: 'TDW_OPENAPI_SELECTION_STATE', selection: [{...item, checked: true}]}, '*');
    const changes = vi.fn(); fixture.componentInstance.selectionChange.subscribe(changes);
    const message = {type: 'TDW_OPENAPI_SELECTION_CHANGE', method: 'GET', path: '/tickets', checked: false};
    const dispatch = (data: unknown, source: Window = frame.contentWindow!) => window.dispatchEvent(new MessageEvent('message', {source, data}));
    dispatch(message, window); dispatch({...message, path: '/unknown'}); dispatch({...message, checked: 'false'}); dispatch({...message, extra: true});
    expect(changes).not.toHaveBeenCalled(); dispatch(message);
    expect(changes).toHaveBeenCalledExactlyOnceWith({method: 'GET', path: '/tickets', checked: false});
    fixture.componentRef.setInput('selection', [{...item, disabled: true}]); fixture.detectChanges();
    dispatch(message); expect(changes).toHaveBeenCalledTimes(1);
    const close = vi.fn(); fixture.componentInstance.closeRequested.subscribe(close);
    dispatch({type: 'TDW_OPENAPI_VIEWER_CLOSE'}, window); expect(close).not.toHaveBeenCalled();
    dispatch({type: 'TDW_OPENAPI_VIEWER_CLOSE'}); expect(close).toHaveBeenCalledOnce();
    fixture.componentRef.setInput('specification', {...specification, paths: {'/tickets': {get: {}}}}); fixture.detectChanges();
    expect(post.mock.calls.at(-1)?.[0].type).toBe('TDW_OPENAPI_DOCUMENT');
    fixture.destroy(); dispatch({type: 'TDW_OPENAPI_VIEWER_CLOSE'}); expect(close).toHaveBeenCalledOnce();
  });
});
