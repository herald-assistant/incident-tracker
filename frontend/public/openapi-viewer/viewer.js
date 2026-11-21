(function () {
  'use strict';
  var ui;
  var selection = null;
  var listeners = new Set();
  function validSelection(value) {
    return value === null || (Array.isArray(value) && value.every(function (item) {
      return item && typeof item.method === 'string' && typeof item.path === 'string'
        && typeof item.checked === 'boolean' && typeof item.disabled === 'boolean'
        && (item.unavailableReason === null || typeof item.unavailableReason === 'string');
    }));
  }
  function publishSelection(next) {
    selection = next;
    document.documentElement.classList.toggle('tdw-selection-mode', next !== null);
    listeners.forEach(function (listener) { listener(next); });
  }
  function SelectionPlugin(system) {
    var React = system.React;
    return {wrapComponents: {operation: function (Original) {
      return function SelectableOperation(props) {
        var state = React.useState(selection);
        React.useEffect(function () {
          listeners.add(state[1]);
          return function () { listeners.delete(state[1]); };
        }, []);
        var reasonId = React.useId();
        var path = props.operation.get('path');
        var method = String(props.operation.get('method')).toUpperCase();
        var item = state[0] && state[0].find(function (candidate) { return candidate.method === method && candidate.path === path; });
        var rowRef = React.useRef(null);
        React.useLayoutEffect(function () {
          var row = rowRef.current;
          var summary = row && row.querySelector('.opblock-summary');
          if (!summary) return;
          function alignCheckbox() {
            var bounds = summary.getBoundingClientRect();
            if (bounds.height) row.style.setProperty('--tdw-summary-center', (bounds.top - row.getBoundingClientRect().top + bounds.height / 2) + 'px');
          }
          alignCheckbox();
          if (typeof window.ResizeObserver !== 'function') return;
          var observer = new window.ResizeObserver(alignCheckbox);
          observer.observe(summary); observer.observe(row);
          return function () { observer.disconnect(); };
        }, [!!item, item && item.unavailableReason]);
        if (!item) return React.createElement(Original, props);
        return React.createElement('div', {className: 'tdw-operation-row', ref: rowRef},
          React.createElement('input', {type: 'checkbox', className: 'tdw-operation-checkbox',
            'aria-label': 'Udostępnij ' + method + ' ' + path,
            'aria-describedby': item.unavailableReason ? reasonId : undefined,
            checked: item.checked, disabled: item.disabled,
            onChange: function (event) {
              if (!item.disabled) window.parent.postMessage({type: 'TDW_OPENAPI_SELECTION_CHANGE', method: method, path: path, checked: event.target.checked}, '*');
            }}),
          React.createElement('div', {className: 'tdw-operation-documentation'},
            item.unavailableReason ? React.createElement('p', {id: reasonId, className: 'tdw-operation-unavailable'}, item.unavailableReason) : null,
            React.createElement(Original, props)));
      };
    }}};
  }
  window.addEventListener('keydown', function (event) {
    if (event.key === 'Escape') {
      event.preventDefault(); window.parent.postMessage({type: 'TDW_OPENAPI_VIEWER_CLOSE'}, '*');
    }
  }, true);
  window.addEventListener('message', function (event) {
    if (event.source !== window.parent || !event.data) return;
    var data = event.data;
    if (data.type === 'TDW_OPENAPI_SELECTION_STATE') {
      if (ui && validSelection(data.selection)) publishSelection(data.selection);
      return;
    }
    if (data.type !== 'TDW_OPENAPI_DOCUMENT' || !validSelection(data.selection) || !data.specification || typeof data.specification !== 'object' || Array.isArray(data.specification)) return;
    var status = document.getElementById('viewer-status');
    try {
      publishSelection(data.selection);
      if (ui) {
        ui.specActions.updateJsonSpec(data.specification);
        return;
      }
      ui = window.SwaggerUIBundle({
        dom_id: '#swagger-ui', spec: data.specification, plugins: [SelectionPlugin],
        supportedSubmitMethods: [], tryItOutEnabled: false,
        validatorUrl: null, queryConfigEnabled: false, withCredentials: false,
        deepLinking: false, displayOperationId: true, docExpansion: 'list',
        defaultModelsExpandDepth: selection ? -1 : 1, filter: !selection, displayRequestDuration: false,
        requestInterceptor: function () { throw new Error('Podgląd kontraktu nie wykonuje żądań HTTP.'); }
      });
      if (status) status.remove();
    } catch (error) {
      if (status) status.textContent = 'Nie udało się wyświetlić kontraktu OpenAPI. Odśwież stronę i spróbuj ponownie.';
    }
  });
}());
