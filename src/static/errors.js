
/* Strict mode FTW! */
'use strict';

this.InstantErrors = function() {
  /* The error box node */
  var errorBox = null;
  /* Whether we are inside a handleBackground() call. */
  var insideHandleBackground = false;
  /* The module contents */
  var InstantErrors = {
    /* Callback for handleBackground() */
    _onBackgroundError: null,
    /* Initialize module */
    init: function() {
      var homeNode = document.getElementById('errors');
      errorBox = homeNode.querySelector('.error-box');
      var dismiss = errorBox.querySelector('.dismiss');
      dismiss.addEventListener('click', InstantErrors.hide);
      window.onerror = InstantErrors.handle;
      window.onunhandledrejection = InstantErrors.handlePromise;
    },
    /* Display an error report with the given additional information */
    show: function(extraText) {
      errorBox.querySelector('pre').textContent = '' + extraText;
      errorBox.parentNode.style.display = '';
    },
    /* Convenience combination of format(), show(), and console.error() */
    showError: function(exc, context) {
      var displayContext = context;
      if (displayContext == null) displayContext = 'Unexpected error!';
      try {
        console.error(displayContext, exc);
      } catch (e) {}
      InstantErrors.show(InstantErrors.format(exc, context));
    },
    /* Dismiss the error reporter again */
    hide: function() {
      errorBox.querySelector('pre').textContent = '';
      errorBox.parentNode.style.display = 'none';
    },
    /* Callback function for window.onerror */
    handle: function(message, source, lineno, colno, error) {
      InstantErrors.showError(error);
    },
    /* Callback function for window.onunhandledrejection */
    handlePromise: function(event) {
      InstantErrors.handleBackground(event.reason,
        'Promise (' + event.promise + ') rejected:');
    },
    /* Handle a non-fatal error */
    handleBackground: function(error, context) {
      var handler = InstantErrors._onBackgroundError;
      if (typeof handler != 'function' || insideHandleBackground) {
        InstantErrors.showError(error, context);
        return;
      }
      insideHandleBackground = true;
      try {
        handler(error, context);
      } catch (e) {
        InstantErrors.showError(e, 'Unexpected error while handling errors!');
      } finally {
        insideHandleBackground = false;
      }
    },
    /* Format an exception object into an error report text */
    format: function(exc, context) {
      var ret = '';
      if (context) ret += context + ' ';
      ret += exc;
      if (typeof exc == 'object') {
        if ('fileName' in exc) {
          ret += '\n[at ' + exc.fileName;
          if ('lineNumber' in exc) {
            ret += ':' + exc.lineNumber;
          } else {
            ret += ':?';
          }
          if ('columnNumber' in exc) {
            ret += ':' + exc.columnNumber;
          }
          ret += ']';
        }
        if ('stack' in exc) {
          ret += '\n' + exc.stack;
        }
      }
      return ret;
    }
  };
  InstantErrors.init();
  return InstantErrors;
}();
