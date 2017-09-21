
/* Strict mode FTW! */
'use strict';

/* Utilities */
function $hypot(dx, dy) {
  return Math.sqrt(dx * dx + dy * dy);
}

function $id(id, elem) {
  return (elem || document).getElementById(id);
}
function $cls(cls, elem) {
  return (elem || document).getElementsByClassName(cls)[0];
}
function $clsAll(cls, elem) {
  return (elem || document).getElementsByClassName(cls);
}
function $sel(sel, elem) {
  return (elem || document).querySelector(sel);
}
function $selAll(sel, elem) {
  return (elem || document).querySelectorAll(sel);
}
function $parentWithClass(el, cls) {
  while (el && ! (el.classList && el.classList.contains(cls)))
    el = el.parentNode;
  return el;
}

function $moveCh(from, to) {
  if (! from) return;
  while (from.childNodes.length)
    to.appendChild(from.firstChild);
}

function $prefLength(text, char) {
  var ret = 0;
  while (text[ret] == char) ret++;
  return ret;
}
function $suffLength(text, char) {
  var ret = 0, l = text.length;
  while (text[l - ret - 1] == char) ret++;
  return ret;
}

function $query(str, ret, noStrip) {
  if (! ret) ret = {};
  if (! noStrip) str = str.replace(/^[?#]/, '');
  var regex = /&?([^&=]+)(?:=([^&]*))?(?=&|$)|($)/g;
  for (;;) {
    var m = regex.exec(str);
    if (! m) return null;
    if (m[3] != null) break;
    var n = decodeURIComponent(m[1]);
    var v = (m[2] == null) ? true : decodeURIComponent(m[2]);
    if (ret[n] == null) {
      ret[n] = v;
    } else if (typeof ret[n] == 'string') {
      ret[n] = [ret[n], v];
    } else {
      ret[n].push(v);
    }
  }
  return ret;
}

/* Run a callback as soon as document (or window) has loaded
 * If late is true, cb is bound to the window.onload event, else, to
 * document.onload. If the corresponding event has already passed, cb is
 * executed immediately, or, if defer is true, asynchronously. */
function $onload(cb, late, defer) {
  var re = (late) ? /complete/ : /complete|interactive/;
  var subject = (late) ? window : document;
  if (re.test(document.readyState)) {
    if (defer) {
      setTimeout(cb, 0);
    } else {
      cb();
    }
  } else {
    subject.addEventListener('load', cb);
  }
}

/* Create a DOM element */
function $makeNode(tag, className, attrs, children) {
  /* Allow omitting parameters */
  if (Array.isArray(className)) {
    if (! attrs && ! children) {
      children = className;
      attrs = null;
      className = null;
    }
  } else if (typeof className == 'object' && className != null) {
    if (! children) {
      children = attrs;
      attrs = className;
      className = null;
    }
  } else if (Array.isArray(attrs) || typeof attrs == 'string') {
    if (! children) {
      children = attrs;
      attrs = null;
    }
  }
  /* Create node */
  var ret = document.createElement(tag);
  /* Set classes */
  if (className) ret.className = className;
  /* Set additional attributes */
  if (attrs) {
    for (var name in attrs) {
      if (! attrs.hasOwnProperty(name)) continue;
      ret.setAttribute(name, attrs[name]);
    }
  }
  /* Add children */
  if (children) {
    if (typeof children == 'string') children = [children];
    for (var i = 0; i < children.length; i++) {
      var e = children[i];
      if (! e) {
        /* Allow conditional node omission */
      } else if (typeof e == 'string') {
        /* Strings become text nodes */
        ret.appendChild(document.createTextNode(e));
      } else if (typeof e != 'object') {
        /* Other primitive types are not allowed */
        throw new Error('Bad child encountered during DOM node creation');
      } else if (Array.isArray(e)) {
        /* Arrays are handled recursively */
        ret.appendChild($makeNode.apply(null, e));
      } else {
        /* Everything else is assumed to be a DOM node */
        ret.appendChild(e);
      }
    }
  }
  return ret;
}
/* Create a DocumentFragment
 * Arguments are variadic. */
function $makeFrag() {
  var ret = document.createDocumentFragment();
  for (var i = 0; i < arguments.length; i++) {
    var e = arguments[i];
    /* Duplicating handling from above */
    if (! e) {
      /* NOP */
    } else if (typeof e == 'string') {
      ret.appendChild(document.createTextNode(e));
    } else if (typeof e != 'object') {
      throw new Error('Bad child encountered during DOM node creation');
    } else if (Array.isArray(e)) {
      ret.appendChild($makeNode.apply(null, e));
    } else {
      ret.appendChild(e);
    }
  }
  return ret;
}

/* Evaluate some code and return the results
 * This is literally what one should normally not do.
 * The function takes two undeclared arguments, code and global. code is the
 * JavaScript code to execute; if global is true, the code is run in the
 * global scope (and nothing can be passed in), otherwise, it is run in a
 * function scope, and the this object is passed down to it. */
function $evalIn() {
  if (arguments[1]) {
    return (0, eval)(arguments[0]);
  } else {
    return eval(arguments[0]);
  }
}

// The latter method is used for ephemeral debugging statements and scanned
// for through the source code aggressively.
if (! console.debug) console.debug = console['log'];

/* Early preparation; define most of the functionality */
this.Instant = function() {
  /* Locale-agnostic abbreviated month name table */
  var MONTH_NAMES = { 1: 'Jan',  2: 'Feb',  3: 'Mar',  4: 'Apr',
                      5: 'May',  6: 'Jun',  7: 'Jul',  8: 'Aug',
                      9: 'Sep', 10: 'Oct', 11: 'Nov', 12: 'Dec' };
  /* Upcoming return value */
  var Instant = {};
  /* Prepare connection */
  var roomPaths = new RegExp('^(?:/dev/([a-zA-Z0-9-]+))?(/room/' +
    '([a-zA-Z](?:[a-zA-Z0-9_-]*[a-zA-Z0-9])?))/?');
  var roomMatch = roomPaths.exec(document.location.pathname);
  if (roomMatch) {
    var scheme = (document.location.protocol == 'https:') ? 'wss' : 'ws';
    var wsURL = scheme + '://' + document.location.host +
      roomMatch[2] + '/ws';
    Instant.connectionURL = wsURL;
    Instant.roomName = roomMatch[3];
    Instant.stagingLocation = roomMatch[1];
  } else {
    Instant.connectionURL = null;
    Instant.roomName = null;
    Instant.stagingLocation = null;
  }
  /* Set window/tab/whatever title */
  if (Instant.roomName) {
    Instant.baseTitle = '&' + Instant.roomName;
    Instant.titleExtension = ' \u2014 Instant';
  } else {
    Instant.baseTitle = 'Instant';
    Instant.titleExtension = '';
  }
  document.title = Instant.baseTitle + Instant.titleExtension;
  /* Left-pad a string */
  function leftpad(s, l, c) {
    s = s.toString();
    while (s.length < l) s = c + s;
    return s;
    /* Was it that hard? */
  }
  /* Format a Date object sensibly */
  function formatDate(date) {
    /* Zero-pad a number */
    function zpad(n, l) {
      return leftpad(n, l, '0');
    }
    /* Compose result */
    return (zpad(date.getFullYear(), 4) + '-' +
      MONTH_NAMES[date.getMonth() + 1] + '-' +
      zpad(date.getDate(), 2) + ' ' +
      zpad(date.getHours(), 2) + ':' +
      zpad(date.getMinutes(), 2) + ':' +
      zpad(date.getSeconds(), 2));
  }
  /* Format a Date object into a DOM node sensibly */
  function formatDateNode(date) {
    return $makeNode('time', {datetime: date.toISOString(),
      'data-timestamp': date.getTime()}, formatDate(date));
  }
  /* Run a function immediately, and then after a fixed interval */
  function repeat(callback, time) {
    callback();
    setInterval(callback, time);
  }
  /* Run a list of callbacks */
  function runList(list) {
    if (! list) return;
    var args = Array.prototype.slice.call(arguments, 1);
    for (var i = 0; i < list.length; i++) {
      try {
        list[i].apply(this, args);
      } catch (e) {
        console.error('Cannot run callback:', e);
      }
    }
  }
  /* Own identity */
  Instant.identity = function() {
    return {
      /* The session ID */
      id: null,
      /* The user ID */
      uuid: null,
      /* The (current) nickname */
      nick: null,
      /* Server version */
      serverVersion: null,
      /* Fine-grained server version */
      serverRevision: null,
      /* Initialize the identity from the data part of a
       * server-side message */
      initFields: function(data) {
        if (Instant.connection.isURLOverridden()) {
          /* NOP */
        } else if ((Instant.identity.serverVersion != null &&
             Instant.identity.serverVersion != data.version ||
             Instant.identity.serverRevision != null &&
             Instant.identity.serverRevision != data.revision)) {
          Instant.notifications.submitNew({level: 'update',
            text: 'Update available; click to reload.',
            onclick: function() {
              location.reload(true);
            },
            data: {
              updateAvailable: true,
              uiMessage: 'update'
            }});
        } else if (window._instantVersion_ &&
            (_instantVersion_.version != data.version ||
             _instantVersion_.revision != data.revision)) {
          Instant.notifications.submitNew({level: 'update',
            text: 'Your page is outdated; please refresh it manually.',
            data: {
              updateAvailable: true,
              uiMessage: 'update',
              uiMessageColor: '#808000',
              uiMessageNode: document.createTextNode('Your page is ' +
                'outdated;\nplease refresh it manually.')
            }});
        }
        Instant.identity.id = data.id;
        Instant.identity.uuid = data.uuid;
        Instant.identity.serverVersion = data.version;
        Instant.identity.serverRevision = data.revision;
        Instant._fireListeners('identity.established');
      },
      /* Broadcast or send the current nickname */
      sendNick: function(to) {
        if (! Instant.connection.isConnected() ||
            Instant.identity.nick == null)
          return;
        Instant.connection.send(to, {type: 'nick',
          nick: Instant.identity.nick, uuid: Instant.identity.uuid});
        Instant.storage.set('nickname', Instant.identity.nick);
      }
    };
  }();
  /* Connection handling */
  Instant.connection = function() {
    /* Sequence ID of outgoing messages */
    var seqid = null;
    /* The actual WebSocket */
    var ws = null;
    /* Whether the WebSocket is connected, or was in the past */
    var connected = false, wasConnected = false;
    /* Whether the default URL was overridden */
    var overridden = false;
    /* Message handlers */
    var rawHandlers = {}, handlers = {};
    /* Callbacks for individual messages */
    var callbacks = {};
    /* Send regular pings
     * lastPong is a [local time, server time] array (or null). */
    var lastPong = null;
    setInterval(function() {
      if (! Instant || ! Instant.connection ||
          ! Instant.connection.isConnected())
        return;
      var payload = null;
      if (lastPong) {
        var now = Date.now();
        var delta = lastPong[1] - lastPong[0];
        if (lastPong[0] <= now - 60000) {
          console.warn('Last ping response too far in the past; ' +
              'reconnecting...');
          Instant.connection.reconnect();
          return;
        } else {
          payload = {next: Date.now() + delta + 60000};
        }
      }
      Instant.connection.sendPing(payload);
    }, 30000);
    return {
      /* A kill switch for certain edge cases */
      _dontConnect: false,
      /* Initialize the submodule, by installing the connection status
       * widget */
      init: function() {
        /* Debugging hook */
        if (window.logInstantMessages === undefined)
          window.logInstantMessages = (!! Instant.query.get('verbose'));
        /* Apply URL override */
        var override = Instant.query.get('connect');
        if (override) {
          Instant.connectionURL = override;
          overridden = true;
        }
        /* Connect */
        Instant.connection.connect();
        /* Force update of widget */
        if (ws && ws.readyState == WebSocket.OPEN) {
          Instant.connection._connected();
        }
        /* Update related modules */
        Instant.animation.onlineStatus.update();
        Instant.userList.update();
      },
      /* Actually connect */
      connect: function() {
        if (! Instant.connectionURL || Instant.connection._dontConnect) {
          ws = null;
          return null;
        }
        /* Create WebSocket */
        ws = new WebSocket(Instant.connectionURL);
        /* Reset sequence ID */
        seqid = 0;
        /* Install event handlers */
        ws.onopen = Instant.connection._connected;
        ws.onmessage = Instant.connection._message;
        ws.onclose = Instant.connection._closed;
        ws.onerror = Instant.connection._error;
        /* Return result (additionally) */
        return ws;
      },
      /* Re-connect */
      reconnect: function() {
        if (ws) {
          /* Close old connection if necessary */
          try {
            ws.close();
          } catch (e) {
            console.warn(e);
          }
          ws.onopen = null;
          ws.onmessage = null;
          ws.onclose = null;
          ws.onerror = null;
          ws = null;
        }
        /* Ensure the close handler has been called */
        Instant.connection._closed();
        /* Connect again */
        Instant.connection.connect();
      },
      /* Handle an opened connection */
      _connected: function(event) {
        /* Update flags */
        connected = true;
        wasConnected = true;
        /* Send event */
        Instant._fireListeners('connection.open', {source: event});
        /* Send notification */
        Instant.notifications.submitNew({text: 'Connected.'});
      },
      /* Handle a message */
      _message: function(event) {
        /* Debugging hook */
        if (window.logInstantMessages)
          console.debug('[Received]', event.data);
        /* Raw message handler */
        if (Instant.connection.onRawMessage) {
          Instant.connection.onRawMessage(event);
          if (event.defaultPrevented) return;
        }
        /* Send event */
        Instant._fireListeners('connection.message', {source: event,
          _cancel: event.preventDefault.bind(event)});
        if (event.defaultPrevented) return;
        /* Extract message data */
        var msg;
        try {
          msg = JSON.parse(event.data);
        } catch (e) {
          console.warn('Cannot parse message:', e);
          return;
        }
        /* Invoke individual handler */
        var cb = callbacks[msg.seq];
        delete callbacks[msg.seq];
        if (cb) cb(msg);
        /* Invoke handlers */
        var handled = false;
        if (msg.type && rawHandlers[msg.type]) {
          handled = rawHandlers[msg.type].some(function(h) {
            try {
              return h(msg, event);
            } catch (e) {
              console.error('Could not run listener:', e);
              return false;
            }
          });
        }
        /* Switch on the message type */
        switch (msg.type) {
          case 'error': /* Error */
            console.warn('Error message:', msg);
            break;
          case 'identity': /* Own (and server's) identity */
            Instant.identity.initFields(msg.data);
            /* Say hello */
            Instant.identity.sendNick();
            /* Reset user list */
            Instant.userList.clear();
            Instant.connection.sendBroadcast({type: 'who'});
            /* Update UUID cache */
            Instant.logs.addUUID(Instant.identity.id, Instant.identity.uuid);
            /* Initiate log pull */
            Instant.logs.pull.start();
            break;
          case 'pong': /* Server responded to a ping */
            lastPong = [Date.now(), msg.timestamp];
            break;
          case 'response': /* Response to a message sent */
            /* Nothing to do */
            break;
          case 'joined': /* New user joined (might be ourself) */
            Instant.userList.add(msg.data.id, '', msg.data.uuid);
            Instant.logs.addUUID(msg.data.id, msg.data.uuid);
            break;
          case 'left': /* User left */
            Instant.userList.remove(msg.data.id);
            Instant.logs.pull._onmessage(msg);
            Instant.privmsg._onmessage(msg);
            break;
          case 'who': /* Active connection enumeration */
            /* Nothing to do */
            break;
          case 'unicast': /* Someone sent a message directly to us */
          case 'broadcast': /* Someone sent a message to everyone */
            var data = msg.data || {};
            /* Run handlers again */
            if (data.type && handlers[data.type]) {
              handled |= handlers[data.type].some(function(h) {
                try {
                  return h(msg, event);
                } catch (e) {
                  console.error('Could not run listener:', e);
                  return false;
                }
              });
            }
            switch (data.type) {
              case 'post': /* Someone sent a message */
                /* Sanitize input */
                var nick = data.nick;
                var text = data.text;
                if (typeof nick != 'string')
                  nick = '';
                if (typeof text != 'string')
                  /* HACK: Serialize text to something remotely senseful */
                  text = JSON.stringify(text);
                /* Scrape for nicknames */
                Instant.userList.add(msg.from, nick);
                /* Prepare message object */
                var ent = {id: msg.id, parent: data.parent || null,
                  timestamp: msg.timestamp, from: msg.from, nick: nick,
                  text: text};
                /* Add to logs */
                Instant.logs.add(ent);
                /* Only display message when initialized */
                var inp = Instant.input.getNode();
                if (inp) {
                  /* Prepare for scrolling */
                  var restore = Instant.input.saveScrollState();
                  /* Prepare data for display
                   * Inlining the clone process appears to be the fastest
                   * way to do it. :S */
                  var ment = {id: ent.id, parent: ent.parent,
                    timestamp: ent.timestamp, from: ent.from, nick: ent.nick,
                    text: ent.text, isNew: true};
                  /* Post message */
                  var box = Instant.pane.getBox(inp);
                  var msg = Instant.message.importMessage(ment, box);
                  /* Restore scroll state */
                  restore();
                  /* Check whether the message is offscreen */
                  Instant.animation.offscreen.check(msg);
                  /* Possibly show a notification */
                  Instant.message.createNotification(msg).then(
                    Instant.notifications.submit);
                } else {
                  /* Should not happen */
                  console.warn('Swallowing message:', ent);
                }
                break;
              case 'nick': /* Someone informs us about their nick */
                Instant.userList.add(msg.from, data.nick, data.uuid);
                if (data.uuid) Instant.logs.addUUID(msg.from, data.uuid);
                break;
              case 'who': /* Someone asks about others' nicks */
                Instant.identity.sendNick(msg.from);
                break;
              case 'log-query': /* Someone asks about our logs */
              case 'log-info': /* Someone informs us about their logs */
              case 'log-request': /* Someone requests logs from us */
              case 'log': /* Someone delivers logs to us */
              case 'delete': /* Someone wants to remove a message */
                Instant.logs.pull._onmessage(msg);
                break;
              case 'log-inquiry': /* Are we done pulling logs? */
              case 'log-done': /* We are done pulling logs? */
                /* Both for log scraper interaction; not for the JS client */
                break;
              case 'privmsg': /* Incoming private message */
                Instant.privmsg._onmessage(msg);
                break;
              default:
                if (! handled) console.warn('Unknown client message:', data);
                break;
            }
            break;
          default:
            if (! handled) console.warn('Unknown server message:', msg);
            break;
        }
      },
      /* Handle a dead connection */
      _closed: function(event) {
        /* Update flags */
        var wasConnected = connected;
        connected = false;
        lastPong = null;
        callbacks = {};
        /* Inform logs */
        Instant.logs.pull._disconnected();
        /* Send event */
        Instant._fireListeners('connection.close', {source: event});
        /* Send a notification */
        if (wasConnected)
          Instant.notifications.submitNew({text: 'Disconnected.',
            level: 'disconnect'});
        /* Re-connect */
        if (event)
          Instant.connection.reconnect();
      },
      /* Handle an auxillary error */
      _error: function(event) {
        /* Update flag */
        var wasConnected = connected;
        connected = false;
        /* Send event */
        Instant._fireListeners('connection.error', {source: event});
        /* Assuming an error means a disconnect */
        if (wasConnected)
          Instant.notifications.submitNew({text: 'Disconnected!',
            level: 'disconnect'});
        /* Cannnot really do anything */
        if (event)
          console.warn('WebSocket error:', event);
        /* Re-connect */
        if (event)
          Instant.connection.reconnect();
      },
      /* Most basic sending */
      sendRaw: function(data) {
        /* Debugging hook */
        if (window.logInstantMessages)
          console.debug('[Sending]', data);
        /* Actual sending */
        return ws.send(data);
      },
      /* Send an object whilst adding a sequence ID (in-place); return the
       * sequence ID */
      sendSeq: function(data, cb) {
        data.seq = seqid++;
        if (cb) callbacks[data.seq] = cb;
        Instant.connection.sendRaw(JSON.stringify(data));
        return data.seq;
      },
      /* Send an object and log the response when it arrives */
      sendDbg: function(data, cb) {
        Instant.connection.sendSeq(data, function(response) {
          console['log'](response);
          if (cb) cb(response);
        });
        /* Returning to avoid the browser logging an "undefined". */
        return data;
      },
      /* Send a ping with the given payload (or none at all) to the server */
      sendPing: function(data, cb) {
        var msg = {type: 'ping'};
        if (data !== undefined) msg.data = data;
        return Instant.connection.sendSeq(msg, cb);
      },
      /* Send an unicast message to the given participant with the given
       * payload */
      sendUnicast: function(to, data, cb) {
        return Instant.connection.sendSeq({type: 'unicast', to: to,
                                           data: data}, cb);
      },
      /* Send a broadcast message with the given payload */
      sendBroadcast: function(data, cb) {
        return Instant.connection.sendSeq({type: 'broadcast',
                                           data: data}, cb);
      },
      /* Send either a unicast or a broadcast */
      send: function(to, data, cb) {
        if (to) {
          return Instant.connection.sendUnicast(to, data, cb);
        } else {
          return Instant.connection.sendBroadcast(data, cb);
        }
      },
      /* Add a handler for "raw" message types */
      addRawHandler: function(type, handler) {
        Instant.connection.removeRawHandler(type, handler);
        if (! rawHandlers[type]) rawHandlers[type] = [];
        rawHandlers[type].push(handler);
      },
      /* Remove a handler for a "raw" message type */
      removeRawHandler: function(type, handler) {
        if (! rawHandlers[type]) return;
        var idx = rawHandlers[type].indexOf(handlers);
        if (idx != -1) rawHandlers.splice(idx, 1);
      },
      /* Add a handler for a unicast/broadcast message subtype */
      addHandler: function(type, handler) {
        Instant.connection.removeHandler(type, handler);
        if (! handlers[type]) handlers[type] = [];
        handlers[type].push(handler);
      },
      /* Remove a handler for a unicast/broadcast message subtype */
      removeHandler: function(type, handler) {
        if (! handlers[type]) return;
        var idx = handlers[type].indexOf(handlers);
        if (idx != -1) handlers.splice(idx, 1);
      },
      /* Check whether the client is currently connected */
      isConnected: function() {
        return connected;
      },
      /* Return whether one was connected at all */
      wasConnected: function() {
        return wasConnected;
      },
      /* Check whether the default connection URL was overridden */
      isURLOverridden: function() {
        return overridden;
      },
      /* Obtain the whole raw handler mapping */
      getRawHandlers: function() {
        return rawHandlers;
      },
      /* Get the whole handler mapping */
      getHandlers: function() {
        return handlers;
      },
      /* Event handler for WebSocket messages */
      onRawMessage: null
    };
  }();
  /* Logs! */
  Instant.logs = function() {
    /* Sorted key list */
    var keys = [];
    /* ID -> object; Session ID -> User ID (UUID) */
    var messages = {}, uuids = {};
    /* Earliest and newest log message, respectively */
    var oldestLog = null, newestLog = null;
    /* Earliest live message */
    var oldestLive = null;
    /* Our logs are up-to-date */
    var logsLive = true;
    return {
      /* Get the bounds (amount of messages, earliest message, latest
       * message) of the logs */
      bounds: function() {
        if (keys.length) {
          return {from: keys[0], to: keys[keys.length - 1],
            length: keys.length};
        } else {
          return {from: null, to: null, length: 0};
        }
      },
      /* Find where to insert the given key and return the index
       * If the key is already present, returns the index of it. */
      bisect: function(key) {
        var f = 0, t = keys.length - 1;
        /* The actual algorithm fails with no keys to compare to */
        if (! keys) return 0;
        /* Actual bisecting */
        for (;;) {
          /* Compare with bounds */
          if (key <= keys[f]) {
            return f;
          } else if (key > keys[t]) {
            return t + 1;
          } else if (t - f == 1) {
            return t;
          } else if (t - f < 1) {
            return f;
          }
          /* Shift bounds */
          var c = (f + t) >> 1;
          if (key < keys[c]) {
            t = c - 1;
          } else if (key > keys[c]) {
            f = c + 1;
          } else {
            return c;
          }
        }
      },
      /* Add the given message, replacing an old one if present */
      add: function(message) {
        var pos = Instant.logs.bisect(message.id);
        if (keys[pos] != message.id) {
          keys.splice(pos, 0, message.id);
        }
        messages[message.id] = message;
        if (logsLive && oldestLive == null)
          oldestLive = message.id;
      },
      /* Remove the message with the given ID */
      remove: function(id) {
        var pos = Instant.logs.bisect(id);
        if (keys[pos] == id) keys.splice(pos, 1);
        delete messages[id];
      },
      /* Purge everything from the logs */
      clear: function() {
        keys = [];
        messages = {};
        oldestLog = null;
        newestLog = null;
        oldestLive = null;
        logsLive = false;
      },
      /* Add multiple log entries (preferably a large amount)
       * If updateIndices is true, the oldest and the newest "log" message
       * ID-s are updated. */
      merge: function(logs, updateIndices) {
        /* No logs, no action */
        if (! logs) return;
        /* Copy keys into array; embed messages into storage */
        var lkeys = logs.map(function(m) {
          messages[m.id] = m;
          return m.id;
        });
        /* Update oldest and newest log */
        lkeys.sort();
        if ((updateIndices || logsLive) && lkeys) {
          if (oldestLog == null || oldestLog > lkeys[0])
            oldestLog = lkeys[0];
          if (newestLog == null || newestLog < lkeys[lkeys.length - 1])
            newestLog = lkeys[lkeys.length - 1];
        }
        /* Actually flush keys into key array */
        Array.prototype.push.apply(keys, lkeys);
        /* Sort */
        keys.sort();
        /* Deduplicate (it's better than nothing) */
        var last = null, taken = [];
        keys = keys.filter(function(k) {
          if (last && k == last) {
            if (! taken.length || k != taken[taken.length - 1])
              taken.push(k);
            return false;
          } else {
            last = k;
            return true;
          }
        });
        /* Calculate the messages actually added */
        var tkidx = 0;
        var added = lkeys.filter(function(k) {
          if (tkidx < taken.length && k == taken[tkidx]) {
            return false;
          } else {
            tkidx++;
            return true;
          }
        });
        /* Return messages actually added */
        return added;
      },
      /* Fetch some portion of the logs */
      get: function(from, to, length) {
        var retkeys = [];
        /* Calculate the keys' indices */
        var fromidx = (from != null) ? Instant.logs.bisect(from) : null;
        var toidx = (to != null) ? Instant.logs.bisect(to) : null;
        if (toidx != null && keys[toidx] == to) toidx++;
        /* Patch missing indices */
        if (fromidx != null && toidx != null) {
          /* Exact range -- nothing to do */
        } else if (fromidx != null) {
          /* From a given key, ... */
          if (length == null) {
            /* ...until end -- nothing to do. */
          } else {
            /* ...until maximum length. */
            toidx = fromidx + length;
          }
        } else if (toidx != null) {
          /* Up to a given key, ... */
          if (length == null) {
            /* ...from the beginning. */
            fromidx = 0;
          } else {
            /* ...from a maximum length. */
            fromidx = toidx - length;
            if (fromidx < 0) fromidx = 0;
          }
        } else if (length != null) {
          /* N most recent entries */
          toidx = keys.length;
          fromidx = toidx - length;
          if (fromidx < 0) fromidx = 0;
        } else {
          /* Everything! */
          fromidx = 0;
        }
        /* Just leaving it at null seemed not to work */
        if (toidx == null) toidx = keys.length;
        /* Extract keys; map them to messages; return those. */
        return keys.slice(fromidx, toidx).map(function(k) {
          return messages[k];
        });
      },
      /* Add the given UID-UUID pair to the registry */
      addUUID: function(uid, uuid) {
        uuids[uid] = uuid;
      },
      /* Add the given pairs to the UUID cache */
      mergeUUID: function(mapping) {
        for (var k in mapping) {
          if (! mapping.hasOwnProperty(k)) continue;
          uuids[k] = mapping[k];
        }
      },
      /* Return the UUID corresponding to the given UID, or nothing */
      getUUID: function(uid) {
        return uuids[uid];
      },
      /* Return either the whole UUID mapping, or the subset of it having
       * the items of keys as keys */
      queryUUID: function(keys) {
        var ret = {};
        if (keys) {
          keys.forEach(function(k) {
            if (uuids[k]) ret[k] = uuids[k];
          });
        } else {
          for (var k in uuids) {
            if (! uuids.hasOwnProperty(k)) continue;
            ret[k] = uuids[k];
          }
        }
        return ret;
      },
      /* Obtain the current key array. Do not modify. */
      getKeys: function() {
        return keys;
      },
      /* Obtain the current message mapping. Do not modify. */
      getMessages: function() {
        return messages;
      },
      /* Obtain the current user ID to UUID mapping. Do not modify. */
      getUUIDs: function() {
        return uuids;
      },
      /* Return the oldest "log" message ID */
      getOldestLog: function() {
        return oldestLog;
      },
      /* Return the newest "log" message ID */
      getNewestLog: function() {
        return newestLog;
      },
      /* Return the oldest "live" message ID */
      getOldestLive: function() {
        return oldestLive;
      },
      /* Submodule */
      pull: function() {
        /* Interval log fetching may not take longer than */
        var WAIT_DEADLINE = 10000;
        /* Maximum interval to wait for another peer */
        var WAIT_NEXTPEER = 1000;
        /* When to poll whether to choose new peer */
        var POLL_TIME = 100;
        /* The pane to add parent-less messages to */
        var pane = null;
        /* Waiting for responses to arrive */
        var timer = null;
        /* Time when the pull started; last time we got a log-info */
        var pullStarted = null, lastUpdate = null;
        /* Peers for oldest and newest messages */
        var oldestPeer = null, newestPeer = null;
        /* Which logs exactly are pulled */
        var pullType = {before: null, after: null};
        /* Message we want to fetch */
        var goal = null;
        return {
          /* Initialize the pane node */
          init: function(paneNode) {
            /* Verbosity level */
            if (window.logInstantLogPulling === undefined) {
              var v = (!! Instant.query.get('verbose'))
              window.logInstantLogPulling = v;
            }
            pane = paneNode;
          },
          /* Actually start pulling logs */
          _start: function() {
            if (timer == null) {
              Instant.connection.sendBroadcast({type: 'log-query'});
              pullStarted = Date.now();
              timer = setInterval(Instant.logs.pull._check, POLL_TIME);
              if (window.logInstantLogPulling)
                console.debug('[LogPull]', 'Started');
            }
            if (pullType.before)
              Instant.animation.spinner.show('logs-before');
            if (pullType.after)
              Instant.animation.spinner.show('logs-after');
          },
          /* Start a round of log pulling */
          start: function() {
            pullType.before = true;
            pullType.after = true;
            Instant.logs.pull._start();
          },
          /* Load logs before the current ones */
          more: function() {
            pullType.before = true;
            Instant.logs.pull._start();
          },
          /* Pull logs until the given message is fetched; then go to it
           * This is asynchronous, and can potentially take *much* time.
           */
          upto: function(msgid) {
            /* Check if goal already reached; otherwise set it */
            if (keys.length && keys[0] < msgid) {
              goal = null;
              var msg = Instant.message.get(msgid);
              if (msg) Instant.animation.goToMessage(msg);
            } else {
              goal = msgid;
              /* Initiate new pull */
              if (Instant.connection.isConnected())
                Instant.logs.pull.more();
            }
          },
          /* Collect log information */
          _check: function() {
            /* Abort if new data arrived while waiting (and deadline not
             * expired); stop waiting otherwise */
            var now = Date.now();
            if (now < pullStarted + WAIT_DEADLINE &&
                (lastUpdate == null || now < lastUpdate + WAIT_NEXTPEER))
              return;
            clearInterval(timer);
            pullStarted = null;
            lastUpdate = null;
            timer = null;
            /* Notify other submodules */
            Instant.logs.pull._gatherDone();
            /* Request logs! */
            var sentBefore = false, sentAfter = false;
            if (! keys.length) {
              /* Prevent pulling the same logs twice upon initial request */
              var peer = oldestPeer || newestPeer;
              if (peer) {
                Instant.connection.sendUnicast(peer.id,
                  {type: 'log-request', key: 'initial'});
                sentBefore = true;
                sentAfter = true;
                if (window.logInstantLogPulling)
                  console.debug('[LogPull]', 'Initial request to', peer);
              }
            } else {
              if (oldestPeer && pullType.before) {
                Instant.connection.sendUnicast(oldestPeer.id,
                  {type: 'log-request', to: oldestLog, key: 'before'});
                sentBefore = true;
                if (window.logInstantLogPulling)
                  console.debug('[LogPull]', 'Older request to', oldestPeer);
              }
              if (newestPeer && ! logsLive && pullType.after) {
                Instant.connection.sendUnicast(newestPeer.id,
                  {type: 'log-request', from: newestLog, key: 'after'});
                sentAfter = true;
                if (window.logInstantLogPulling)
                  console.debug('[LogPull]', 'Newer request to', newestPeer);
              }
            }
            /* Clear spinner */
            Instant.logs.pull._done(! sentBefore, ! sentAfter);
            /* Debugging */
            if (window.logInstantLogPulling)
              console.debug('[LogPull]', 'Sent requests (B/A):', sentBefore,
                            sentAfter);
          },
          /* Notify other submodules that we are done gathering log
           * advertisements */
          _gatherDone: function() {
            /* Now, most of the user list should have been enumerated */
            Instant.privmsg._updateNicks();
          },
          /* Handler for messages */
          _onmessage: function(msg) {
            if (msg.type == 'left') {
              /* Someone left */
              var upd = false;
              if (oldestPeer && oldestPeer.id == msg.data.id) {
                oldestPeer = null;
                upd = true;
              }
              if (newestPeer && newestPeer.id == msg.data.id) {
                newestPeer = null;
                upd = true;
              }
              if (upd) {
                Instant.connection.sendBroadcast({type: 'log-query'});
                lastUpdate = Date.now();
              }
              return;
            }
            var response = null, respondTo = msg.from;
            /* Check message */
            var data = msg.data;
            switch (data.type) {
              case 'log-query': /* Someone asks about our logs */
                /* FIXME: Does include "live" messages as logs. */
                response = Instant.logs.bounds();
                response.type = 'log-info';
                break;
              case 'log-info': /* Someone informs us about their logs */
                var from = data.from, to = data.to;
                if (msg.from != Instant.identity.id) {
                  if (from && (! oldestPeer || from < oldestPeer.from))
                    oldestPeer = {id: msg.from, from: from, to: to};
                  if (to && (! newestPeer || to > newestPeer.to))
                    newestPeer = {id: msg.from, from: from, to: to};
                  lastUpdate = Date.now();
                }
                if (window.logInstantLogPulling)
                  console.debug('[LogPull]', 'Got advertisement', data);
                break;
              case 'log-request': /* Someone requests logs from us */
                response = {type: 'log'};
                if (data.from != null) response.from = data.from;
                if (data.to != null) response.to = data.to;
                if (data.length != null) response.length = data.length;
                if (data.key != null) response.key = data.key;
                response.data = Instant.logs.get(data.from, data.to,
                                                 data.length);
                response.uuids = Instant.logs.queryUUID(
                  response.data.map(function(e) {
                    return e.from;
                  }));
                break;
              case 'log': /* Someone delivers logs to us */
                var before = null, after = null, count = 0;
                if (data.data) {
                  /* Actually merge logs */
                  var added = Instant.logs.merge(data.data, true);
                  /* Merge UUID-s */
                  Instant.logs.mergeUUID(data.uuids);
                  /* Future compatibility */
                  if (data.users) {
                    var uuids = {};
                    for (var key in data.users) {
                      if (! data.users.hasOwnProperty(key)) continue;
                      uuids[key] = data.users[key].uuid;
                    }
                    Instant.logs.mergeUUID(uuids);
                  }
                  /* Prepare for scrolling */
                  var restore = Instant.input.saveScrollState(true);
                  /* Import messages */
                  added.forEach(function(key) {
                    /* Sanitize input */
                    var msg = messages[key];
                    if (typeof msg.nick != 'string')
                      msg.nick = '';
                    if (typeof msg.text != 'string')
                      msg.text = JSON.stringify(msg.text);
                    /* Import message */
                    Instant.message.importMessage(msg, pane);
                  });
                  /* Scroll back */
                  restore();
                  /* Avoid stale node references */
                  Instant.animation.offscreen._updateMessages();
                  /* Detect earliest and latest message */
                  data.data.forEach(function(el) {
                    if (! before || el.id < before) before = el.id;
                    if (! after || el.id > after) after = el.id;
                  });
                  count = data.data.length;
                }
                /* Call finishing handler */
                var key = data.key;
                before = (key == 'initial' || key == 'before') ?
                  before || true : null;
                after = (key == 'initial' || key == 'after') ?
                  after || true : null;
                Instant.logs.pull._done(before, after, count);
                Instant._fireListeners('logs.new', {message: msg,
                  data: data});
                break;
              case 'delete':
                /* Nuh! */
                break;
              default:
                throw new Error('Bad message supplied to _onmessage().');
            }
            /* Send response */
            if (response != null)
              Instant.connection.send(respondTo, response);
          },
          /* Done with loading logs for whatever reasons */
          _done: function(before, after, count) {
            /* Reset things */
            if (before) {
              pullType.before = false;
              Instant.animation.spinner.hide('logs-before');
            }
            if (after) {
              pullType.after = false;
              Instant.animation.spinner.hide('logs-after');
            }
            /* Check for more logs above */
            if (count > 1)
              Instant.animation._updateLogs();
            /* Reset peers
             * A copy of newestPeer is kept for the next step. */
            var nPeer = newestPeer;
            if (before) oldestPeer = null;
            if (after) newestPeer = null;
            /* Check for more logs below */
            if (after) {
              if ((after === true || ! oldestLive || oldestLive > after) &&
                  nPeer && ! after == nPeer.to) {
                pullType.after = true;
                Instant.logs.pull._start();
              } else {
                logsLive = true;
              }
            }
            /* Restart pull if necessary */
            if (goal) Instant.logs.pull.upto(goal);
          },
          /* Handler for disconnects */
          _disconnected: function() {
            /* Logs are not up-to-date from now on */
            logsLive = false;
          }
        };
      }()
    };
  }();
  /* Nick-name handling */
  Instant.nick = function() {
    /* Nick -> Hue hash */
    var cache = {};
    return {
      /* Partially normalize a name for advanced fuzzy matching */
      seminormalize: function(name) {
        return name.replace(/\s+/g, '');
      },
      /* Normalize nick-name for fuzzy matching and @-mentions */
      normalize: function(name) {
        return name.replace(/\s+/g, '').toLowerCase();
      },
      /* Return an @-mention of the given nick */
      makeMentionText: function(name) {
        return '@' + name.replace(/[.,:;!?\s]+/g, '');
      },
      /* Actual "raw" hue hash */
      _hueHash: function(name) {
        var hash = 0;
        for (var i = 0; i < name.length; i++) {
          hash += name.charCodeAt(i) * 359 + i * 271 + 89;
          hash = (hash * hash * hash) % 360;
        }
        return hash;
      },
      /* "Cooked" hue hash with caching */
      hueHash: function(name) {
        name = Instant.nick.normalize(name);
        var hash = cache[name];
        if (! hash) {
          hash = Instant.nick._hueHash(name);
          cache[name] = hash;
        }
        return hash;
      },
      /* Get the (background) color for emote messages */
      emoteColor: function(name) {
        var hue = Instant.nick.hueHash(name);
        return 'hsl(' + hue + ', 75%, 90%)';
      },
      /* Get the (background) color associated with the nick */
      nickColor: function(name) {
        var hue = Instant.nick.hueHash(name);
        return 'hsl(' + hue + ', 75%, 80%)';
      },
      /* Get the foreground color associated with the nick */
      pingColor: function(name) {
        var hue = Instant.nick.hueHash(name);
        return 'hsl(' + hue + ', 75%, 40%)';
      },
      /* Generate a DOM node carrying the nick */
      makeNode: function(name) {
        var node = document.createElement('span');
        var hue = Instant.nick.hueHash(name);
        node.className = 'nick';
        node.textContent = name;
        node.style.backgroundColor = Instant.nick.nickColor(name);
        node.setAttribute('data-nick', name);
        return node;
      },
      /* Generate a DOM node carrying a mention of the nick
       * name is the nickname with an @-sign. */
      makeMention: function(name) {
        if (name[0] != '@') throw new Error('Bad nick for makeMention()');
        var node = document.createElement('span');
        var realName = name.substr(1);
        var hue = Instant.nick.hueHash(realName);
        node.className = 'mention';
        node.textContent = name;
        node.style.color = Instant.nick.pingColor(realName);
        node.setAttribute('data-nick', realName);
        return node;
      },
      /* Make a nickname node for an anonymous user */
      makeAnonymous: function() {
        var node = document.createElement('span');
        node.className = 'nick anonymous';
        node.textContent = 'Anonymous';
        return node;
      }
    };
  }();
  /* Message handling */
  Instant.message = function() {
    /* Message ID -> DOM node */
    var messages = {};
    var fakeMessages = {};
    /* Message pane */
    var msgPane = null;
    /* Pixel distance that differentiates a click from a drag */
    var DRAG_THRESHOLD = 4;
    return {
      /* Initialize submodule; return DOM node */
      init: function() {
        msgPane = $makeNode('div', 'message-pane', [['div', 'message-box']]);
        msgPane.addEventListener('copy',
          Instant.message._oncopy.bind(Instant.message));
        return msgPane;
      },
      /* Handle copy events */
      _oncopy: function(event) {
        function indentFor(depth) {
          return new Array(depth + 1).join('| ')
        }
        function traverse(node) {
          var depth = +node.getAttribute('data-depth');
          var pdepth = ((! node.parentNode) ? 0 :
            +node.parentNode.getAttribute('data-depth'));
          if (node.getAttribute('data-message-id')) {
            var nick = node.children[0];
            var textnode = node.children[1];
            var clr = Instant.nick.nickColor(nick.textContent);
            node.style.marginLeft = (depth - pdepth) + 'em';
            nick.style.backgroundColor = clr;
            nick.style.padding = '0 1px';
            textnode.style.whiteSpace = 'pre-wrap';
            textnode.style.wordWrap = 'break-word';
            if (node.getAttribute('data-emote')) {
              var eclr = Instant.nick.emoteColor(nick.textContent);
              textnode.style.backgroundColor = eclr;
            }
            var indent = indentFor(depth);
            textarr.push(indent);
            textarr.push('<' + nick.textContent.replace(/\s/g, ' ') + '>');
            if (node.getAttribute('data-emote'))
              textarr.push(' /me');
            textarr.push(textnode.textContent.replace(/\n/g,
              '\n' + indent + '  '));
            textarr.push('\n');
          } else {
            depth = pdepth;
          }
          var ch = node.children;
          for (var i = 0; i < ch.length; i++) {
            if (ch[i].nodeName == 'DIV') {
              traverse(ch[i]);
            } else if (ch[i].nodeName == 'P') {
              var cdepth = +ch[i].getAttribute('data-depth');
              var cindent = indentFor(cdepth);
              if (cdepth != depth) {
                ch[i].style.margin = '0 0 0 ' + (cdepth - depth) + 'em';
              } else {
                ch[i].style.margin = '0';
              }
              textarr.push(cindent + ch[i].textContent.replace(/\n/g,
                '\n' + cindent));
              textarr.push('\n');
            }
          }
        }
        var selection = document.getSelection();
        /* Collect all messages the selection covers
         * Firefox splits the selection up rather finely, possibly because of
         * the user-select:none timestamps. */
        var covered = {}, singleMessage = true;
        for (var i = 0; i < selection.rangeCount; i++) {
          var range = selection.getRangeAt(i);
          var msgs = Instant.message.getMessageNode(range.startContainer);
          if (! msgs) continue;
          var msge = Instant.message.getMessageNode(range.endContainer);
          if (! msge) continue;
          if (msgs == msge) {
            /* HACK: Check if the selection is entirely inside replies */
            var replies = $cls('replies', msgs);
            if (replies && replies.contains(range.startContainer) &&
                replies.contains(range.endContainer))
              continue;
            /* Check if part of the selection is outside the content */
            var content = $cls('content', msgs);
            if (! content.contains(range.startContainer) ||
                ! content.contains(range.endContainer))
              singleMessage = false;
          } else {
            singleMessage = false;
          }
          var msgrange = Instant.message.resolveMessageRange(msgs, msge);
          msgrange.forEach(function(m) {
            covered[m.id] = m;
          });
        }
        /* Create deduplicated array */
        var messages = [];
        for (var k in covered) {
          if (covered.hasOwnProperty(k))
            messages.push(covered[k]);
        }
        /* Special cases
         * If the entire selection is inside the content of a single message
         * (as determined per range above and per message count here), do not
         * pretty-print the clipboard to allow quoting parts of messages. */
        if (! messages.length) return;
        if (singleMessage && messages.length == 1) return;
        /* Try to make sense of it */
        var resNode = Instant.message.prepareExport(messages);
        resNode.className = 'instant-messages';
        /* Fiddle plain text and HTML from each other */
        var textarr = [];
        traverse(resNode);
        var text = textarr.join('');
        /* Poke them into the clipboard */
        event.clipboardData.setData('text/html', resNode.outerHTML);
        event.clipboardData.setData('text/plain', text.trim());
        /* Prevent browser from overwriting our data */
        event.preventDefault();
      },
      /* Prepare the given array of message node for export
       * Creates a DOM node containing new nodes representing the given
       * messages as descendants, with basic information stored in data
       * attributes. */
      prepareExport: function(messages) {
        /* Sort them by document order */
        messages.sort(Instant.message.documentCmp.bind(Instant.message));
        /* Build a tree */
        var stack = [[null, document.createElement('div'), -1]];
        var prev = null, minDepth = Infinity;
        messages.forEach(function(m) {
          /* Determine message depth */
          var indnode = $sel('[data-key=indent]', m);
          var depth = +indnode.getAttribute('data-depth');
          /* Drop stack entries up to a "common" parent */
          var top = stack[stack.length - 1];
          while (top[0] && ! top[0].contains(m)) {
            stack.pop();
            top = stack[stack.length - 1];
          }
          /* Insert an omission mark if necessary */
          if (prev && prev != Instant.message.getDocumentPrecedessor(m)) {
            var d = top[2] + 1;
            top[1].appendChild($makeNode('p', {'data-depth': d}, '...'));
            if (d < minDepth) minDepth = d;
          }
          /* Create the in-copy representation of the message */
          var copy = $makeNode('div', {'data-depth': depth,
              'data-message-id': m.getAttribute('data-id')}, [
            ['span', {'data-user-id': m.getAttribute('data-from')},
              $cls('nick', m).textContent],
            ['span', [' ', $cls('message-text', m).textContent]]
          ]);
          if (m.classList.contains('emote'))
            copy.setAttribute('data-emote', 'true');
          /* Settle state */
          top[1].appendChild(copy);
          stack.push([m, copy, depth]);
          prev = m;
          if (depth < minDepth) minDepth = depth;
        });
        /* Decrease depths */
        if (isFinite(minDepth)) {
          var nodes = $selAll('[data-depth]', stack[0][1]);
          Array.prototype.forEach.call(nodes, function(el) {
            el.setAttribute('data-depth', el.getAttribute('data-depth') -
              minDepth);
          });
        }
        /* Done */
        return stack[0][1];
      },
      /* Detect links, emphasis, smilies, etc. out of a flat string and
       * render those into a DOM node */
      parseContent: function(text) {
        /* Compatibility wrapper */
        return Instant.message.parser.parse(text);
      },
      /* Scan for @-mentions of a given nickname in a message
       * If strict is true, only literal matches of the own nick are
       * accepted, otherwise, the normalizations of the own nick and the
       * candidate are compared. */
      scanMentions: function(content, nick, strict) {
        var ret = 0, children = content.children;
        var nnick = Instant.nick.normalize(nick);
        for (var i = 0; i < children.length; i++) {
          if (children[i].classList.contains('mention')) {
            var candidate = children[i].getAttribute('data-nick');
            /* If nick is correct, one instance found */
            if (candidate == nick) {
              ret++;
            } else if (! strict && Instant.nick.normalize(candidate) ==
                nnick) {
              ret++;
            }
          } else {
            /* Scan recursively */
            ret += Instant.message.scanMentions(children[i], nick);
          }
        }
        return ret;
      },
      /* Install event handlers into the given message node */
      _installEventHandlers: function(msgNode) {
        /* Flag telling whether the input was focused */
        var inputWasFocused = false;
        /* For checking whether it was a click or a drag */
        var clickPos = null;
        /* Pressing a mouse button activates clicking mode */
        $cls('line', msgNode).addEventListener('mousedown', function(evt) {
          if (evt.button != 0) return;
          clickPos = [evt.clientX, evt.clientY];
          inputWasFocused = Instant.input.isFocused();
        });
        /* Clicking to a messages moves to it */
        $cls('line', msgNode).addEventListener('click', function(evt) {
          /* Filter out mouse drags */
          if (clickPos && $hypot(evt.clientX - clickPos[0],
              evt.clientY - clickPos[1]) >= DRAG_THRESHOLD)
            return;
          clickPos = null;
          /* Filter out clicks on links */
          if (evt.target.nodeName == 'A') return;
          /* Navigate to message */
          var doScroll = Instant.input.moveTo(msgNode);
          if (inputWasFocused) {
            Instant.input.focus();
          } else {
            document.activeElement.blur();
          }
          if (doScroll) Instant.pane.scrollIntoView(msgNode);
          evt.stopPropagation();
        });
        $cls('permalink', msgNode).addEventListener('click', function(evt) {
          var msgid = msgNode.getAttribute('data-id');
          var fragment = '#message-' + msgid;
          Instant.animation.goToMessage(msgNode);
          /* Have to simulate history entry addition to avoid the browser
           * happily finding the message and scrolling it to the very top
           * of the viewport. */
          if (document.location.hash != fragment)
            history.pushState({}, '', fragment);
          evt.preventDefault();
          evt.stopPropagation();
        });
      },
      /* Generate a fake message node */
      makeFakeMessage: function(id) {
        /* Allocate result; populate attributes */
        var msgNode = $makeNode('div', 'message message-fake',
            {id: 'message-' + id, 'data-id': id}, [
          ['div', 'line', {title: 'Message absent or not loaded (yet)'}, [
            ['span', 'time-wrapper', [
              ['time', [['a', 'permalink', {href: '#message-' + id}, 'N/A']]]
            ]],
            ['span', 'nick-wrapper', [
              ['span', 'hidden', {'data-key': 'indent'}],
              ['span', 'nick', '...']
            ]]
          ]]
        ]);
        /* Add event handlers */
        Instant.message._installEventHandlers(msgNode);
        /* Done */
        return msgNode;
      },
      /* Generate a DOM node for the specified message parameters */
      makeMessage: function(params) {
        /* Filter out emotes and whitespace; parse (remaining) content */
        var emote = /^\/me(?=\s|$)/.test(params.text);
        var text = (emote) ? params.text.substr(3) : params.text;
        var content = Instant.message.parseContent(text);
        /* Collect some values */
        var clsname = 'message';
        if (emote)
          clsname += ' emote';
        if (params.from && params.from == Instant.identity.id)
          clsname += ' mine';
        if (Instant.identity.nick != null &&
            Instant.message.scanMentions(content, Instant.identity.nick))
          clsname += ' ping';
        if (params.isNew)
          clsname += ' new';
        /* Create node */
        var msgNode = $makeNode('div', clsname, {id: 'message-' + params.id,
            'data-id': params.id}, [
          ['div', 'line', [
            ['span', 'time-wrapper', [
              ['time', [['a', 'permalink', {href: '#message-' + params.id}]]]
            ]],
            ['span', 'nick-wrapper', [
              ['span', 'hidden', {'data-key': 'indent'}],
              ['span', 'hidden', {'data-key': 'before-nick'}, '<'],
              Instant.nick.makeNode(params.nick),
              ['span', 'hidden', {'data-key': 'after-nick'}, '> ']
            ]],
            ['span', 'content', [content]]
          ]]
        ]);
        /* Fill in optional values */
        if (params.parent)
          msgNode.setAttribute('data-parent', params.parent);
        if (params.from)
          msgNode.setAttribute('data-from', params.from);
        /* Fill in timestamp */
        var timeNode = $sel('time', msgNode);
        var permalink = $cls('permalink', msgNode);
        if (typeof params.timestamp == 'number') {
          var date = new Date(params.timestamp);
          timeNode.setAttribute('datetime', date.toISOString());
          timeNode.title = formatDate(date);
          permalink.textContent = (leftpad(date.getHours(), 2, '0') + ':' +
            leftpad(date.getMinutes(), 2, '0'));
        } else {
          permalink.innerHTML = '<i>N/A</i>';
        }
        /* Add emote styles */
        if (emote) {
          $sel('[data-key=after-nick]', msgNode).textContent += '/me ';
          var cnt = $cls('content', msgNode);
          cnt.style.background = Instant.nick.emoteColor(params.nick);
        }
        /* Add event handlers */
        Instant.message._installEventHandlers(msgNode);
        /* Done */
        return msgNode;
      },
      /* Check if a node is a message at all */
      isMessage: function(node) {
        return (node && node.classList &&
                node.classList.contains('message'));
      },
      /* Get the message node containing the given node */
      getMessageNode: function(node) {
        return $parentWithClass(node, 'message');
      },
      /* Get the parent of the message */
      getParent: function(message) {
        if (! message.parentNode ||
            ! message.parentNode.classList.contains('replies') ||
            ! message.parentNode.parentNode ||
            ! Instant.message.isMessage(message.parentNode.parentNode)) {
          return message.parentNode;
        } else {
          return message.parentNode.parentNode;
        }
      },
      /* Same as getParent(), but ensure it is a message */
      getParentMessage: function(message) {
        if (! message.parentNode ||
            ! message.parentNode.classList.contains('replies') ||
            ! message.parentNode.parentNode ||
            ! Instant.message.isMessage(message.parentNode.parentNode)) {
          return null;
        } else {
          return message.parentNode.parentNode;
        }
      },
      /* Same as getParent(), but fail if the current node is not a
       * message */
      getParentOfMessage: function(message) {
        if (! Instant.message.isMessage(message))
          return null;
        return Instant.message.getParent(message);
      },
      /* Get the root of a message tree */
      getRoot: function(message) {
        var cur = Instant.message.getParent(message), root;
        do {
          root = cur;
          cur = Instant.message.getParentOfMessage(cur);
        } while (cur);
        return root;
      },
      /* Return an array of all parent messages the given node has */
      listParentMessages: function(message) {
        var ret = [];
        for (;;) {
          message = Instant.message.getParentMessage(message);
          if (! message) break;
          ret.push(message);
        }
        return ret;
      },
      /* Get the message immediately preceding the given one */
      getPrecedessor: function(message) {
        var prev = message.previousElementSibling;
        if (! prev || ! Instant.message.isMessage(prev))
          return null;
        return prev;
      },
      /* Get the message immediately following the given one */
      getSuccessor: function(message) {
        var next = message.nextElementSibling;
        if (! next || ! Instant.message.isMessage(next))
          return null;
        return next;
      },
      /* Get the message preceding the given one in document order */
      getDocumentPrecedessor: function(message) {
        var prec = Instant.message.getPrecedessor(message);
        if (prec) {
          while (Instant.message.hasReplies(prec))
            prec = Instant.message.getLastReply(prec);
          return prec;
        }
        return Instant.message.getParentMessage(message);
      },
      /* Get the message following the given in document order */
      getDocumentSuccessor: function(message) {
        if (Instant.message.hasReplies(message))
          return Instant.message.getReply(message);
        for (;;) {
          var succ = Instant.message.getSuccessor(message);
          if (succ) return succ;
          message = Instant.message.getParentMessage(message);
          if (! message) return null;
        }
      },
      /* Get the message this is a comment to, i.e., the precedessor, or the
       * parent if none, or null if neither exists */
      getCommentParent: function(message) {
        var prec = Instant.message.getPrecedessor(message);
        if (prec) return prec;
        return Instant.message.getParent(message);
      },
      /* Compare the messages (which incidentally can be arbitrary DOM nodes)
       * by document order  */
      documentCmp: function(a, b) {
        var res = a.compareDocumentPosition(b);
        return (res & Node.DOCUMENT_POSITION_FOLLOWING) ? -1 :
          (res & Node.DOCUMENT_POSITION_PRECEDING) ? 1 : 0;
      },
      /* Obtain a document-ordered array of the messages covered by the given
       * range
       * If reverseEmpty is true and b is a precedessor of a, returns an
       * empty array. If a and b are equal, returns (in any case) that
       * message as the only element. If includeFakes is true, fake messages
       * are included, otherwise not. */
      resolveMessageRange: function(a, b, reverseEmpty, includeFakes) {
        if (a == b) {
          if (includeFakes || ! a.classList.contains('message-fake')) {
            return [a];
          } else {
            return [];
          }
        }
        if (Instant.message.documentCmp(a, b) > 0) {
          if (reverseEmpty) return [];
          var x = a;
          a = b;
          b = x;
        }
        var ret = [a], cur = a;
        while (cur != b) {
          cur = Instant.message.getDocumentSuccessor(cur);
          if (Instant.message.isMessage(cur) && (includeFakes ||
              ! cur.classList.contains('message-fake')))
            ret.push(cur);
        }
        return ret;
      },
      /* Get the node hosting the replies to the given message, or the
       * message itself if it's actually not a message at all */
      _getReplyNode: function(message) {
        if (Instant.message.isMessage(message)) {
          var lc = message.lastElementChild;
          if (! lc || ! lc.classList.contains('replies'))
            return null;
          return lc;
        } else {
          return message;
        }
      },
      /* Get an array of replies to the given message (which may be empty),
       * or null if none */
      _getReplyNodeList: function(message) {
        var repl = Instant.message._getReplyNode(message);
        if (! repl) return null;
        return repl.children;
      },
      /* Return whether a message has direct replies (and therefore replies
       * at all) */
      hasReplies: function(message) {
        var children = Instant.message._getReplyNodeList(message);
        if (! children) return false;
        for (var i = 0; i < children.length; i++) {
          if (Instant.message.isMessage(children[i]))
            return true;
        }
        return false;
      },
      /* Get all the (direct) replies to a message */
      getReplies: function(message) {
        var children = Instant.message._getReplyNodeList(message), ret = [];
        if (! children) return ret;
        for (var i = 0; i < children.length; i++) {
          if (Instant.message.isMessage(children[i]))
            ret.push(children[i]);
        }
        return ret;
      },
      /* Get the nth reply to the message, counting from the beginning */
      getReply: function(message, n) {
        var replies = Instant.message._getReplyNodeList(message);
        if (! replies) return null;
        if (! n) n = 0;
        for (var i = 0, j = 0; i < replies.length; i++) {
          if (! Instant.message.isMessage(replies[i])) continue;
          if (j++ == n) return replies[i];
        }
        return null;
      },
      /* Get the nth reply to the message, counting from the end */
      getLastReply: function(message, n) {
        var replies = Instant.message._getReplyNodeList(message);
        if (! replies) return null;
        if (! n) n = 0;
        for (var i = replies.length - 1, j = 0; i >= 0; i--) {
          if (! Instant.message.isMessage(replies[i])) continue;
          if (j++ == n) return replies[i];
        }
        return null;
      },
      /* Add a reply section to a message if necessary and return it */
      makeReplies: function(message) {
        var lc = message.lastElementChild;
        if (! lc || ! lc.classList.contains('replies')) {
          var nick = $cls('nick', message);
          lc = document.createElement('div');
          lc.className = 'replies';
          if (nick.style.backgroundColor)
            lc.style.borderColor = nick.style.backgroundColor;
          message.appendChild(lc);
        }
        return lc;
      },
      /* Scan an array of messages for where to insert the given ID
       * The return value is suitable for use in the insertBefore() DOM API
       * method. If a matching node is (already) found and remove is true, it
       * is removed. */
      bisect: function(array, id, remove) {
        if (! array || ! array.length) return null;
        var f = 0, t = array.length - 1;
        var last = null;
        /* Exclude baloon (if any), or anything else before messages */
        while (f <= t && ! Instant.message.isMessage(array[f]))
          f++;
        /* Exclude input bar */
        while (t >= 0 && ! Instant.message.isMessage(array[t]))
          last = array[t--];
        if (t < f) return last;
        /* Main loop */
        for (;;) {
          /* >>1 to cast to integer
           * When we get into ranges where f + t would overflow, we
           * have problems more grave than this. */
          var c = (f + t) >> 1;
          /* Element ID-s should sort identially to message ID-s */
          if (id < array[c].id) {
            if (f == t) {
              return array[c];
            } else if (c == f) {
              /* Ensure t is not less than f */
              t = c;
            } else {
              t = c - 1;
            }
          } else if (array[c].id < id) {
            if (f == t) {
              return array[c].nextElementSibling;
            } else {
              /* c will never be equal to t unless f == t */
              f = c + 1;
            }
          } else if (remove) {
            /* Replace old node */
            var ret = array[c].nextElementSibling;
            array[c].parentNode.removeChild(array[c]);
            return ret;
          } else {
            /* Return old node */
            return array[c].nextElementSibling;
          }
        }
      },
      /* Add a reply to a message or a message to a container, sorted
       * properly */
      addReply: function(child, parent) {
        /* Parse child if necessary */
        if (typeof child == 'object' && child.nodeType === undefined)
          child = Instant.message.makeMessage(child);
        /* Retrieve parent from child */
        if (! parent) parent = child.getAttribute('data-parent');
        /* Retrieve parent by ID */
        if (typeof parent == 'string') {
          parent = messages[parent];
          if (! parent)
            throw new Error('Adding message with nonexistent parent');
        }
        /* Validate parent is a DOM node */
        if (! parent || typeof parent != 'object' ||
            parent.nodeType === undefined)
          throw new Error('Invalid parent');
        /* Complete parent resolving */
        var messageParent = null;
        if (Instant.message.isMessage(parent)) {
          messageParent = parent;
          parent = Instant.message.makeReplies(parent);
        }
        /* Add child to registry */
        if (child.classList.contains('fake')) {
          fakeMessages[child.getAttribute('data-id')] = child;
        } else {
          messages[child.getAttribute('data-id')] = child;
        }
        /* Insert child */
        var before = Instant.message.bisect(parent.children, child.id, true);
        parent.insertBefore(child, before);
        /* Return the (possibly processed) child */
        return child;
      },
      /* Integrate a message into a hierarchy
       * If noPreserve is false and another message node with the same ID is
       * present, certain attributes (i.e. CSS classes) of the "old" node are
       * carried over to the new one. */
      importMessage: function(message, root, noPreserve) {
        /* Parse content */
        if (typeof message == 'object' && message.nodeType === undefined)
          message = Instant.message.makeMessage(message);
        /* Determine or generate parent */
        var parID = message.getAttribute('data-parent'), parent = root;
        if (parID) {
          if (messages[parID]) {
            parent = messages[parID];
          } else {
            parent = Instant.message.makeFakeMessage(parID);
            Instant.message.addReply(parent, root);
          }
        }
        /* Resolve fake or "old" messages */
        var msgid = message.getAttribute('data-id');
        var fake = fakeMessages[msgid];
        var old = messages[msgid];
        if (fake || old) {
          var prev = fake || old;
          $moveCh(Instant.message._getReplyNode(prev),
                  Instant.message.makeReplies(message));
          prev.parentNode.removeChild(prev);
          if (! noPreserve) {
            if (prev.classList.contains('offscreen'))
              message.classList.add('offscreen');
          }
          Instant.input.update();
        }
        if (fake) delete fakeMessages[msgid];
        if (old) delete messages[msgid];
        /* Add message to parent */
        Instant.message.addReply(message, parent);
        /* Update indents */
        Instant.message.updateIndents(message);
        /* Done */
        return message;
      },
      /* Return the depth of the given message in the tree
       * NOTE that this may change if some (perhaps non-direct) parent of the
       *      message is a fake. */
      getDepth: function(message) {
        var ind = $sel('[data-key=indent]', message);
        if (! ind) return 0;
        return +ind.getAttribute('data-depth') || 0;
      },
      /* Update the indent string of the given message and all of its
       * children (if any; recursively) */
      updateIndents: function(message, depth) {
        if (depth == null) {
          var par = Instant.message.getParentMessage(message);
          if (par) {
            depth = Instant.message.getDepth(par) + 1;
          } else {
            depth = 0;
          }
        }
        var indent = new Array(depth + 1).join('| ');
        var ind = $sel('[data-key=indent]', message);
        ind.setAttribute('data-depth', depth);
        ind.textContent = indent;
        depth++;
        indent += '| ';
        var children = Instant.message.getReplies(message);
        for (var i = 0; i < children.length; i++) {
          Instant.message.updateIndents(children[i], depth);
        }
      },
      /* Traverse a message tree and return the nodes that match the given
       * predicate
       * Processing of messages (and, subseqently, descendants) starts with
       * the first reply (if any); it can be skipped if a message is outside
       * the "current interval"; processing happens in no particular order
       * and the return value is not sorted.
       * cb is called on the message node being processed and returns a
       * bitmask of the following values:
       *  1: Predicate matches; message in question be returned.
       *  2: Children of the message should be scanned.
       *  4: The direct precedessor of the message should be scanned.
       *  8: The direct successor of the message should be scanned.
       * 16: A faraway precedessor of the message should be scanned (for
       *     bisection).
       * 32: A faraway successor of the message should be scanned.
       */
      walk: function(node, cb) {
        /* search entries: [node, replies, fromIdx, curIdx, toIdx] (where
         * toIdx is inclusive). If the second entry is undefined, others
         * are filled in. */
        var ret = [], search = [[node]];
        /* Repeat until explicit pseudo-recursion stack empty */
        while (search.length) {
          /* Extract top-of-stack; possibly amend missing values */
          var top = search.pop();
          if (top[1] == undefined) {
            top[1] = Instant.message.getReplies(top[0]);
            top[2] = 0;
            top[3] = 0;
            top[4] = top[1].length - 1;
          }
          /* Cancel if interval empty */
          if (top[2] > top[3] || top[3] > top[4]) continue;
          var before = top[3] - 1, after = top[3] + 1;
          /* Process current node */
          var n = top[1][top[3]];
          var res = cb(n);
          /* Return message */
          if (res & 1) ret.push(n);
          /* Scan children */
          if (res & 2) search.push([n]);
          /* Scan precedessor */
          if (res & 4 && top[3] > top[2]) {
            search.push([null, top[1], top[2], before, before]);
            before--;
          }
          /* Scan successor */
          if (res & 8 && top[3] < top[4]) {
            search.push([null, top[1], after, after, top[4]]);
            after++;
          }
          /* Scan far precedessor */
          if (res & 16 && top[3] > top[2]) {
            search.push([null, top[1], top[2], (top[2] + before) >> 1,
                        before]);
          }
          /* Scan far successor */
          if (res & 32 && top[3] < top[4]) {
            search.push([null, top[1], after, (after + top[4]) >> 1,
                        top[4]]);
          }
        }
        return ret;
      },
      /* Check if the given fragment identifier is a valid message
       * identifier */
      checkFragment: function(url) {
        return (/^#message-.+$/.test(url));
      },
      /* Extract a message ID out of a fragment identifier or return it
       * unchanged */
      parseFragment: function(url) {
        if (Instant.message.checkFragment(url))
          return url.substring(9);
        return url;
      },
      /* Return the message identified by the given fragment identitier */
      forFragment: function(url) {
        if (! Instant.message.checkFragment(url)) return null;
        return Instant.message.get(url.substring(9));
      },
      /* Return the message identified by this ID, or undefined if none
       * Fake messages for the purpose of showing replies without known
       * parent count as nonexistent. */
      get: function(id) {
        return messages[id];
      },
      /* Clear the internal message registry */
      clear: function() {
        mesages = {};
        fakeMessages = {};
      },
      /* Return the message pane node */
      getMessagePane: function() {
        return msgPane;
      },
      /* Return the message box node */
      getMessageBox: function() {
        return $cls('message-box', msgPane);
      },
      /* Create a Notification object for a given message */
      createNotification: function(msg) {
        var text;
        if (msg.classList.contains('emote')) {
          text = ('* ' + $cls('nick', msg).textContent + ' ' +
                  $cls('content', msg).textContent);
        } else {
          /* HACK: Some notification systems seem to interpret the body as
           *       HTML, so the preferred "angled brackets" cannot be used
           *       (unless we want the name to be an HTML tag). */
          text = ('[' + $cls('nick', msg).textContent + '] ' +
                  $cls('content', msg).textContent);
        }
        var level = Instant.notifications.getLevel(msg);
        /* For window title etc. */
        var par = Instant.message.getCommentParent(msg);
        var isReply = (par && par.classList.contains('mine'));
        var isPing = (msg.classList.contains('ping'));
        return Instant.notifications.create({text: text,
          level: level,
          onclick: function() {
            /* Go to the message */
            Instant.input.jumpTo(msg);
            Instant.input.focus();
          },
          data: {
            unreadMessages: 1,
            unreadReplies: (isReply) ? 1 : 0,
            unreadMentions: (isPing) ? 1 : 0
          }
        });
      },
      /* Message parsing -- has an own namespace to avoid pollution */
      parser: function() {
        function smc(s) {
          return s.replace(/%PM%/g, pm).replace(/%ME%/g, me);
        }
        /* Important regexes */
        var URL_RE = '((?!javascript:)[a-zA-Z]+://)?' +
          '([a-zA-Z0-9._~-]+@)?([a-zA-Z0-9.-]+)(:[0-9]+)?(/[^>]*)?';
        var pm = '[^()\\s]*', me = '[^.,:;!?()\\s]';
        var MENTION_RE = smc('%PM%(?:\\(%PM%\\)%PM%)*(?:\\(%PM%\\)|%ME%)');
        // Avoid messing up syntax highlighting with the parenthesis.
        var PARTIAL_MENTION = smc('%PM%(?:\\(%PM%\\)%PM%)*(?:\\\x28%PM%)?');
        /* Smiley table */
        var SMILIES = {
          '+1'  : '#008000', '-1'  : '#c00000',
          '<3'  : '#c00080', '</3' : '#c00080', '<\\3': '#c00080',
          '>:)' : '#c00000', '>:]' : '#c00000', '>:}' : '#c00000',
          '>:D' : '#c00000',
          '>;)' : '#c00000', '>;]' : '#c00000', '>;}' : '#c00000',
          '>;D' : '#c00000'
        };
        var SMILEY_DEFAULT = '#c0c000';
        /* Helper: Quickly create a DOM node */
        function makeNode(text, className, color, tag) {
          var node = document.createElement(tag || 'span');
          if (className) node.className = className;
          if (color) node.style.color = color;
          if (text) node.textContent = text;
          return node;
        }
        /* Helper: Create a sigil node */
        function makeSigil(text, className) {
          return makeNode(text, 'sigil ' + className);
        }
        /* Disable a wrongly-assumed emphasis mark */
        function declassify(elem) {
          elem.disabled = true;
          var nodes = elem.nodes || [];
          for (var i = 0; i < nodes.length; i++) {
            nodes[i].classList.add('false');
          }
        }
        /* Handle end-of-line for line patterns */
        function doEOL(stack, out, i) {
          /* Terminate line-level emphasis */
          while (stack.length && stack[stack.length - 1].line) {
            var el = stack.pop();
            el.add = el.line;
            out.splice(i++, 0, {rem: el.add});
          }
          return i;
        }
        /* Counts of plugin-inserted matchers */
        var earlyMatchers = 0;
        var lateMatchers = 0;
        /* Message parsing fragments */
        var matchers = [
          { /* Leading/trailing whitespace */
            name: 'whitespace',
            re: /^\s+|\s+$/,
            cb: function(m, out) {
              out.push(makeNode(m[0], 'hidden'));
            }
          },
          { /* Room/message links */
            name: 'room',
            re: /\B&([a-zA-Z]([\w-]*[a-zA-Z0-9])?)(#([a-zA-Z0-9]+))?\b/,
            cb: function(m, out) {
              var node = makeNode(m[0], 'room-link', null, 'a');
              node.href = ('../' + m[1] + '/' +
                ((m[4]) ? '#message-' + m[4] : ''));
              if (m[1] != Instant.roomName || ! m[4])
                node.target = '_blank';
              out.push(node);
            }
          },
          { /* Hyperlinks */
            name: 'link',
            re: new RegExp('<(' + URL_RE + ')>'),
            cb: function(m, out) {
              /* Hyperlink (must contain non-word character) */
              if (! /\W/.test(m[1])) {
                out.push(m[0]);
                return;
              }
              out.push(makeSigil('<', 'link-before'));
              /* Insert http:// if necessary */
              var url = m[1];
              if (! m[2]) url = 'http://' + url;
              var node = makeNode(m[1], 'link', null, 'a');
              node.href = url;
              node.target = '_blank';
              out.push(node);
              out.push(makeSigil('>', 'link-after'));
            }
          },
          { /* @-mentions */
            name: 'mention',
            re: new RegExp('@' + MENTION_RE),
            bef: /\W|^$/, aft: /\W|^$/,
            cb: function(m, out) {
              out.push(Instant.nick.makeMention(m[0]));
            }
          },
          { /* Smilies */
            name: 'smiley',
            re: new RegExp('[+-]1|<[/\\\\]?3|' +
              '>?[:;=]\'?[D)\\]}|{\\[/\\\\(cCSPoO3]|' +
              '[SD)\\\\/\\]}|{\\[(cCoO]\'?[:=]<?|' +
              '\\^\\^|([\\^oO~])[._]\\1|>[._]?<|;[_-];|\\._\\.|' +
              '\\\\o/'),
            bef: /[\s(]|^$/, aft: /[\s.,:;!?)]|^$/,
            cb: function(m, out) {
              var c = SMILIES[m[0]] || SMILEY_DEFAULT;
              out.push(makeNode(m[0], 'smiley', c));
            }
          },
          { /* Inline monospace */
            name: 'mono',
            re: /`([^`\s]+)`|`([^`\s]+)|([^`\s]+)`/,
            bef: /[^\w`]|^$/, aft: /[^\w`]|^$/,
            cb: function(m, out) {
              /* Leading sigil */
              if (m[1] != null || m[2] != null) {
                var node = makeSigil('`', 'mono-before');
                out.push(node);
                out.push({add: 'mono', nodes: [node]});
              }
              /* Embed actual text */
              out.push(m[1] || m[2] || m[3]);
              /* Trailing sigil */
              if (m[1] != null || m[3] != null) {
                var node = makeSigil('`', 'mono-after');
                out.push({rem: 'mono', nodes: [node]});
                out.push(node);
              }
            },
            add: function() {
              return makeNode(null, 'monospace');
            }
          },
          { /* Emphasized text */
            name: 'emph',
            re: /\*+([^*\s-]+)\*+|\*+([^*\s-]+)|([^*\s-]+)\*+/,
            bef: /\W|^$/, aft: /\W|^$/,
            cb: function(m, out) {
              /* Emphasized text (again, only before has to be tested) */
              var pref = $prefLength(m[0], '*');
              var suff = $suffLength(m[0], '*');
              /* Sigils are in individual nodes so they can be selectively
               * disabled */
              for (var i = 0; i < pref; i++) {
                var node = makeSigil('*', 'emph-before');
                out.push(node);
                out.push({add: 'emph', nodes: [node]});
              }
              /* Add actual text; which one does not matter */
              out.push(m[1] || m[2] || m[3]);
              /* Same as above for trailing sigil */
              for (var i = 0; i < suff; i++) {
                var node = makeSigil('*', 'emph-after');
                out.push({rem: 'emph', nodes: [node]});
                out.push(node);
              }
            },
            add: function(stack, status) {
              var level = (status.emphLevel || 0) + 1;
              status.emphLevel = level;
              var node = makeNode(null, 'emph');
              node.setAttribute('data-level', level);
              var log = 0;
              for (var log = 1; level; log++) {
                if (level & 1) node.classList.add('emph-' + log);
                level >>= 1;
              }
              return node;
            },
            rem: function(stack, status) {
              stack.pop();
              status.emphLevel--;
            }
          },
          { /* Monospace blocks */
            name: 'monoBlock',
            re: /```\n([\s\S]*?)\n```/,
            bef: /\W|^$/, aft: /\W|^$/,
            cb: function(m, out) {
              var bnode = makeSigil('```', 'mono-block-before');
              var bnl = makeNode('\n', 'hidden');
              var anode = makeSigil('```', 'mono-block-after');
              out.push(bnode);
              out.push(bnl);
              out.push({add: 'monoBlock', nodes: [bnode, bnl]});
              out.push(makeNode(m[1] + '\n', 'monospace monospace-block'));
              out.push({rem: 'monoBlock', nodes: [anode]});
              out.push(anode);
            }
          },
          { /* Subheadings */
            name: 'heading',
            re: /^(#\s*)+/m,
            cb: function(m, out) {
              out.push(makeSigil(m[0], 'heading-marker'));
              out.push({line: 'heading'});
            },
            add: function(stack, status) {
              status.headingLevel = (status.headingLevel || 0) + 1;
              if (status.headingLevel == 1) {
                status.emphLevel = (status.emphLevel || 0) + 2;
                return makeNode(null, 'heading-line');
              }
            },
            rem: function(stack, status) {
              if (status.headingLevel-- == 1) {
                stack.pop();
                status.emphLevel -= 2;
              }
            }
          },
          { /* Quoted lines */
            name: 'quote',
            re: /^(>\s*)+/m,
            cb: function(m, out) {
              out.push(makeSigil(m[0], 'quote-marker'));
              out.push({line: 'quote'});
            },
            add: function(stack, status) {
              status.quoteLevel = (status.quoteLevel || 0) + 1;
              if (status.quoteLevel == 1) {
                status.emphLevel = (status.emphLevel || 0) + 1;
                return makeNode(null, 'quote-line');
              }
            },
            rem: function(stack, status) {
              if (status.quoteLevel-- == 1) {
                stack.pop();
                status.emphLevel--;
              }
            }
          },
          { /* Monospace line */
            name: 'term',
            re: /^\$\s*/m,
            cb: function(m, out) {
              out.push(makeSigil(m[0], 'term-marker'));
              out.push({line: 'term'});
            },
            add: function() {
              return makeNode(null, 'term-line monospace');
            }
          }
        ];
        return {
          /* Export constants */
          URL_RE: URL_RE,
          MENTION_RE: MENTION_RE,
          PARTIAL_MENTION: PARTIAL_MENTION,
          /* Helper: Quickly create a DOM node */
          makeNode: makeNode,
          /* Helper: Quickly create a sigil node */
          makeSigil: makeSigil,
          /* Parse a message into a DOM node */
          parse: function(text) {
            /* Intermediate result; current index; text length; array of
             * matches; length of matchers; status object */
            var out = [], idx = 0, len = text.length, matches = [];
            var mlen = matchers.length, status = {};
            /* Duplicate regexes; create index */
            var matcherIndex = {};
            var regexes = matchers.map(function(el) {
              if (el.name) matcherIndex[el.name] = el;
              return new RegExp(el.re.source, 'g' +
                (el.re.ignoreCase ? 'i' : '') +
                (el.re.multiline ? 'm' : ''));
            });
            /* Main loop */
            while (idx < len) {
              /* Index (into array) of foremost match so far */
              var minIdx = null;
              for (var i = 0; i < mlen; i++) {
                /* This one won't match */
                if (matches[i] == Infinity) continue;
                /* Recalculate indices where necessary */
                if (matches[i] == null || matches[i].index < idx) {
                  regexes[i].lastIndex = idx;
                  for (;;) {
                    /* Match */
                    var m = regexes[i].exec(text);
                    matches[i] = m;
                    /* No match */
                    if (m == null) {
                      matches[i] = Infinity;
                      break;
                    }
                    /* Check pre- and postconditions */
                    if (matchers[i].bef) {
                      var chBefore = ((m.index == 0) ? '' :
                        text.substr(m.index - 1, 1));
                      if (! matchers[i].bef.test(chBefore))
                        continue;
                    }
                    if (matchers[i].aft) {
                      var chAfter = text.substr(m.index + m[0].length, 1);
                      if (! matchers[i].aft.test(chAfter))
                        continue;
                    }
                    /* Match found */
                    break;
                  }
                  if (matches[i] == Infinity) continue;
                }
                /* Update minimal match */
                if (minIdx == null ||
                    matches[minIdx].index > matches[i].index)
                  minIdx = i;
              }
              /* Handle no matches */
              if (minIdx == null) {
                out.push(text.substring(idx));
                break;
              }
              /* Insert text up to match */
              if (matches[minIdx].index != idx)
                out.push(text.substring(idx, matches[minIdx].index));
              /* Process match */
              status.id = minIdx;
              var adv = matchers[minIdx].cb(matches[minIdx], out,
                                            status) || 0;
              idx = matches[minIdx].index + matches[minIdx][0].length + adv;
            }
            /* Disable stray emphasis marks
             * Those nested highlights actually form a context-free
             * grammar. */
            var stack = [];
            for (var i = 0; i < out.length; i++) {
              var e = out[i];
              /* Handle end-of-line */
              if (typeof e == 'string') {
                var idx = e.indexOf('\n');
                if (idx != -1 && stack.length &&
                    stack[stack.length - 1].line) {
                  out.splice(i++, 1, e.substring(0, idx));
                  i = doEOL(stack, out, i);
                  out.splice(i, 0, e.substring(idx));
                  continue;
                }
              }
              /* Filter such that only user-made objects remain */
              if (typeof e != 'object' || e.nodeType !== undefined) continue;
              /* Add or remove emphasis, respectively */
              if (e.add || e.line) {
                stack.push(e);
              } else if (e.rem) {
                if (stack.length) {
                  /* Check if it actually matches */
                  if (e.rem == stack[stack.length - 1].add) {
                    stack.pop();
                  } else {
                    declassify(e);
                  }
                } else {
                  declassify(e);
                }
              }
            }
            doEOL(stack, out, i);
            for (var i = 0; i < stack.length; i++) declassify(stack[i]);
            /* Render result into a DOM node */
            stack = [makeNode(null, 'message-text')];
            status = {};
            for (var i = 0; i < out.length; i++) {
              var e = out[i], top = stack[stack.length - 1];
              /* Drain non-metadata parts into the current node */
              if (typeof e == 'string') {
                top.appendChild(document.createTextNode(e));
                continue;
              } else if (e.nodeType !== undefined) {
                top.appendChild(e);
                continue;
              }
              /* Disabled emphasis nodes don't do anything */
              if (e.disabled) continue;
              /* First remove emphasis */
              if (e.rem) {
                var cb = matcherIndex[e.rem].rem;
                if (cb) {
                  cb(stack, status);
                } else {
                  stack.pop();
                }
              }
              /* Then add some */
              if (e.add) {
                var cb = matcherIndex[e.add].add;
                var node = (cb) ? cb(stack, status) : makeNode();
                if (node) {
                  top.appendChild(node);
                  stack.push(node);
                }
              }
            }
            /* Done! */
            return stack[0];
          },
          /* Reverse the transformation performed by the parser */
          extractText: function(node) {
            return node.textContent;
          },
          /* Add an early matcher */
          addEarlyMatcher: function(m) {
            matchers.splice(earlyMatchers++, 0, m);
          },
          /* Add a late matcher */
          addLateMatcher: function(m) {
            matchers.push(m);
            lateMatchers++;
          },
          /* Obtain the raw matcher array
           * Use with discretion. */
          getMatchers: function() {
            return matchers;
          },
          /* Count plugin-inserted early matchers */
          countEarlyMatchers: function() {
            return earlyMatchers;
          },
          /* Count plugin-inserted late matchers */
          countLateMatchers: function() {
            return lateMatchers;
          }
        };
      }()
    };
  }();
  /* Input bar management */
  Instant.input = function () {
    /* Match @-mentions with arbitrary text before */
    var MENTION_BEFORE = new RegExp('(?:\\W|^)\\B@(' +
      Instant.message.parser.PARTIAL_MENTION + ')?$');
    /* The DOM node containing the input bar */
    var inputNode = null;
    /* The sub-node currently focused */
    var focusedNode = null;
    /* Sequence ID for fake messages */
    var fakeSeq = 0;
    return {
      /* Initialize input bar control */
      init: function() {
        /* Helpers for below */
        function updateNick(event) {
          var name = inputNick.value;
          sizerNick.textContent = name;
          sizerNick.style.background = Instant.nick.nickColor(name);
          if (name) {
            sizerNick.style.minWidth = '';
          } else {
            sizerNick.style.minWidth = '1em';
          }
          Instant._fireListeners('input.nickEdit', {nick: name,
            source: event});
        }
        function refreshNick(event, force) {
          var oldNick = Instant.identity.nick;
          if (Instant.identity.nick == inputNick.value && ! force)
            return;
          Instant.identity.nick = inputNick.value;
          Instant.identity.sendNick();
          Instant._fireListeners('input.nickChange', {oldNick: oldNick,
            nick: inputNick.value, source: event});
        }
        function updateFocus(event) {
          focusedNode = event.target;
        }
        inputNode = $makeNode('div', 'input-bar', [
          ['div', 'input-info-cell', [
            ['span', 'alert-container', [
              ['a', 'offscreen-alert alert-above', {href: '#'}, [
                ['img', {src: '/static/arrow-up.svg'}],
              ]],
              ['a', 'offscreen-alert alert-below', {href: '#'}, [
                ['img', 'turn', {src: '/static/arrow-up.svg'}],
              ]]
            ]]
          ]],
          ['div', 'input-nick-cell', [
            ['span', 'input-nick-sizer'],
            ['input', 'input-nick', {type: 'text'}]
          ]],
          ['div', 'input-message-cell', [
            ['span', 'input-nick-prompt', [
              ['img', 'turn-left', {src: '/static/arrow-up.svg'}],
              ['span', [' Enter your nick into the colored box']]
            ]],
            ['div', 'input-group', [
              ['textarea', 'input-message', {rows: 1}],
              ['textarea', 'input-message-sizer', {rows: 1}]
            ]]
          ]]
        ]);
        /* Install event handlers */
        var inputNick = $cls('input-nick', inputNode);
        var sizerNick = $cls('input-nick-sizer', inputNode);
        var promptNick = $cls('input-nick-prompt', inputNode);
        var sizerMsg = $cls('input-message-sizer', inputNode);
        var inputMsg = $cls('input-message', inputNode);
        /* Update nick background */
        inputNick.addEventListener('input', updateNick);
        /* End nick editing on Return */
        inputNick.addEventListener('keydown', function(event) {
          if (event.keyCode == 13) { // Return
            inputMsg.focus();
            event.preventDefault();
            refreshNick(event);
          } else if (event.keyCode == 27) { // Escape
            inputNick.value = Instant.identity.nick;
            inputMsg.focus();
            event.preventDefault();
            updateNick(event);
          }
        });
        /* Update status when nick changes */
        inputNick.addEventListener('change', refreshNick);
        inputNick.addEventListener('blur', refreshNick);
        /* Reinforce nick editing prompt */
        promptNick.addEventListener('click', function() {
          inputNick.focus();
        });
        /* Auto-size input bar; remove nick setting prompt */
        inputMsg.addEventListener('input', Instant.input._updateMessage);
        /* Remove nick setting prompt */
        inputMsg.addEventListener('focus', function() {
          promptNick.style.display = 'none';
        });
        /* Handle special keys */
        inputMsg.addEventListener('keydown', Instant.input._onkeydown);
        /* Save the last focused node */
        inputNick.addEventListener('focus', updateFocus);
        inputMsg.addEventListener('focus', updateFocus);
        /* Scroll input into view when resized */
        window.addEventListener('resize', function(event) {
          Instant.pane.scrollIntoView(inputNode);
        });
        /* Read nickname from storage */
        var nick = Instant.storage.get('nickname');
        if (typeof nick == 'string' && nick) {
          inputNick.value = nick;
          refreshNick(null, true);
          focusedNode = inputMsg;
        } else if (inputNick.value) {
          /* Form auto-fill? */
          refreshNick(null, true);
          focusedNode = inputMsg;
        } else {
          focusedNode = inputNick;
        }
        inputNick.setSelectionRange(inputNick.value.length,
                                    inputNick.value.length);
        updateNick();
        return inputNode;
      },
      /* Return the input bar */
      getNode: function() {
        return inputNode;
      },
      /* Transfer focus to the input bar */
      focus: function(forceInput) {
        var node = focusedNode;
        if (! node || forceInput) node = $cls('input-message', inputNode);
        node.focus();
      },
      /* Query whether the input bar counts as focused */
      isFocused: function() {
        return inputNode.contains(document.activeElement);
      },
      /* Get the message ID of the parent of the input bar */
      getParentID: function() {
        var parent = Instant.message.getParentMessage(inputNode);
        if (! parent) return null;
        return parent.getAttribute('data-id');
      },
      /* Move the input bar into the given message/container */
      jumpTo: function(parent) {
        /* Disallow replying to loading messages
         * Seriously, kids, do you not have anything better to do? */
        if (parent.matches('#load-wrapper *')) return false;
        /* Remove marker class from old parent */
        var oldParent = Instant.message.getParentMessage(inputNode);
        if (oldParent == parent) return false;
        if (oldParent) oldParent.classList.remove('input-host');
        /* Handle message parents */
        if (Instant.message.isMessage(parent)) {
          /* Add marker class to current parent */
          parent.classList.add('input-host');
          parent = Instant.message.makeReplies(parent);
        }
        /* Actually relocate the input */
        parent.appendChild(inputNode);
        /* Handle animation */
        Instant.animation.offscreen._inputMoved();
        /* Successful */
        return true;
      },
      /* Move the input bar to the given message, or to its parent if the
       * bar is already there. */
      moveTo: function(message) {
        if (Instant.message.isMessage(message)) {
          var replies = Instant.message._getReplyNode(message);
          if (inputNode.parentNode == replies ||
              Instant.message.getPrecedessor(inputNode) != message &&
              ! Instant.message.hasReplies(message) &&
              ! Instant.message.getSuccessor(message)) {
            return Instant.input.jumpTo(Instant.message.getParent(message));
          }
        }
        return Instant.input.jumpTo(message);
      },
      /* Ensure the input is still valid after a re-parenting */
      update: function() {
        var parent = Instant.message.getParentMessage(inputNode);
        if (Instant.message.isMessage(parent))
          parent.classList.add('input-host');
      },
      /* Move the input bar relative to its current position */
      navigate: function(direction) {
        /* Find root */
        var root = Instant.message.getRoot(inputNode);
        switch (direction) {
          case 'up':
            /* Traverse parents until we have a precedessor */
            var par = inputNode, prev;
            while (par) {
              prev = Instant.message.getPrecedessor(par);
              if (prev) break;
              par = Instant.message.getParentMessage(par);
            }
            /* If no parent has a precedessor, cannot do anything */
            if (! prev) return false;
            par = prev;
            /* Descend into its children until we find one with no replies;
             * stop just beneath it */
            for (;;) {
              var ch = Instant.message.getLastReply(par);
              if (! ch || ! Instant.message.hasReplies(ch)) break;
              par = ch;
            }
            /* End up here */
            Instant.input.jumpTo(par);
            return true;
          case 'down':
            /* Special case: message without replies or successor */
            var par = Instant.message.getParentMessage(inputNode);
            if (! par) {
              return false;
            } else if (! Instant.message.hasReplies(par) &&
                ! Instant.message.getSuccessor(par)) {
              Instant.input.jumpTo(Instant.message.getParent(par));
              return true;
            }
            /* Traverse parents until we have a successor */
            var next;
            while (par) {
              next = Instant.message.getSuccessor(par);
              if (next) break;
              par = Instant.message.getParentMessage(par);
            }
            /* Moved all the way up -> main thread */
            if (! next) {
              Instant.input.jumpTo(root);
              return true;
            }
            /* Descend into the current message as far as possible */
            par = next;
            while (Instant.message.hasReplies(par)) {
              par = Instant.message.getReply(par);
            }
            /* Settle here */
            Instant.input.jumpTo(par);
            return true;
          case 'left':
            /* Switch to the parent of the current host (or to the root) */
            var par = Instant.message.getParentMessage(inputNode);
            if (! par) return false;
            Instant.input.jumpTo(
              Instant.message.getParentMessage(par) || root);
            return true;
          case 'right':
            /* Switch to the last child to the current host (if any) */
            var child = Instant.message.getLastReply(
              Instant.message.getParent(inputNode));
            if (child) {
              Instant.input.jumpTo(child);
              return true;
            }
            return false;
          case 'root':
            /* Just return to the root */
            Instant.input.jumpTo(root);
            return true;
          default:
            throw new Error('Invalid direction for input.navigate(): ' +
              direction);
        }
      },
      /* Convenience wrapper for Instant.pane.saveScrollState() */
      saveScrollState: function(focus) {
        return Instant.pane.saveScrollState(inputNode, 1, focus);
      },
      /* Update the message bar sizer */
      _updateMessage: function(event) {
        var sizerNick = $cls('input-nick-sizer', inputNode);
        var promptNick = $cls('input-nick-prompt', inputNode);
        var sizerMsg = $cls('input-message-sizer', inputNode);
        var inputMsg = $cls('input-message', inputNode);
        sizerMsg.value = inputMsg.value;
        /* Avoid devtools noise */
        if (promptNick.style.display != 'none')
          promptNick.style.display = 'none';
        /* Using a separate node for measurement drastically reduces
         * reflow load by having a single out-of-document-flow reflow
         * only in the best case.
         * The old approach of setting the height to 0 (to prevent
         * it from keeping the input box "inflated") and then to
         * the scrollHeight caused two whole-page reflows, which
         * affected performance rather badly. */
        var height = sizerMsg.scrollHeight;
        /* HACK: Depending on the font and other circumstances, there may be
         *       an off-by-one (rounding?) error, causing the input box to
         *       inflate to one pixel more than the rest of the bar.
         *       Needless to say, it is annoying. */
        if (height == sizerNick.offsetHeight - 1) height--;
        if (height + 'px' != inputMsg.style.height) {
          var restore = Instant.input.saveScrollState();
          inputMsg.style.height = height + 'px';
          restore();
        }
        Instant._fireListeners('input.update', {text: inputMsg.value,
          source: event});
      },
      /* Respond to key presses in the input box */
      _onkeydown: function(event) {
        function navigate(dir) {
          var ret = Instant.input.navigate(dir);
          if (ret) {
            Instant.pane.scrollIntoView(inputNode);
            event.preventDefault();
            Instant.input._updateMessage(event);
          }
          inputMsg.focus();
          return ret;
        }
        Instant._fireListeners('input.keydown', {source: event,
          _cancel: event.preventDefault.bind(event)});
        if (event.defaultPrevented) return;
        var inputMsg = $cls('input-message', inputNode);
        var text = inputMsg.value;
        if (event.keyCode == 13 && ! event.shiftKey) { // Return
          /* Send message! */
          /* Whether to clear the input bar */
          var clear = false;
          /* Allow event handlers to have a word */
          var evdata = {text: text, source: event,
            _cancel: event.preventDefault.bind(event)};
          Instant._fireListeners('input.send', evdata);
          if (event.defaultPrevented) return;
          text = evdata.text;
          /* Do not input line feeds in any case */
          event.preventDefault();
          /* Ignore empty sends */
          if (! text) {
            /* NOP */
          } else if (! Instant.connectionURL) {
            /* Save scroll state */
            var restore = Instant.input.saveScrollState();
            /* Fake messages if not connected */
            var msgid = 'local-' + leftpad(fakeSeq++, 8, '0');
            Instant.message.importMessage(
              {id: msgid, nick: Instant.identity.nick || '', text: text,
               parent: Instant.input.getParentID(),
               timestamp: Date.now()},
              Instant.message.getRoot(inputNode));
            /* Restore scroll state */
            restore();
            clear = true;
          } else if (Instant.connection.isConnected()) {
            /* Send actual message */
            Instant.connection.sendBroadcast({type: 'post',
              nick: Instant.identity.nick, text: text,
              parent: Instant.input.getParentID()});
            clear = true;
          }
          /* Clear input bar */
          if (clear) {
            inputMsg.value = '';
            Instant.input._updateMessage(event);
          }
        } else if (event.keyCode == 27) { // Escape
          if (navigate('root'))
            location.hash = '';
          inputMsg.focus();
        } else if (event.keyCode == 9 && ! event.shiftKey) { // Tab
          /* Extract text with selection removed and obtain the cursor
           * position */
          if (inputMsg.selectionStart != inputMsg.selectionEnd) {
            text = (text.substr(0, inputMsg.selectionStart) +
                    text.substr(inputMsg.selectionEnd));
          }
          var pos = inputMsg.selectionStart;
          /* Determine if we should complete at all */
          var m = MENTION_BEFORE.exec(text.substring(0, pos));
          if (! m) return;
          /* No tabbing beyond this point */
          event.preventDefault();
          /* Perform actual completion */
          var res = Instant.userList.complete(m[1] || '');
          /* No completion -- no action */
          if (res == null) return;
          /* Insert completed text */
          text = text.substring(0, pos) + res + text.substring(pos);
          var newpos = pos + res.length;
          /* Insert new text into entry */
          inputMsg.value = text;
          inputMsg.setSelectionRange(newpos, newpos);
          /* Update bar size */
          Instant.input._updateMessage(event);
        } else if (event.keyCode >= 37 && event.keyCode <= 40) {
          if (inputMsg.selectionStart != inputMsg.selectionEnd) return;
          var curs = inputMsg.selectionStart;
          if (event.keyCode == 37) { // Left
            if (! text || curs == 0)
              navigate('left');
          } else if (event.keyCode == 38) { // Up
            /* Special case: Get more logs */
            if (text.indexOf('\n') == -1 || curs == 0)
              if (! navigate('up')) Instant.logs.pull.more();
          } else if (event.keyCode == 39) { // Right
            if (! text || curs == inputMsg.value.length)
              navigate('right');
          } else if (event.keyCode == 40) { // Down
            if (text.indexOf('\n') == -1 || curs == inputMsg.value.length)
              navigate('down');
          }
        }
      },
      /* Insert text at the current editing position (if any) */
      insertText: function(text) {
        var inputMsg = $cls('input-message', inputNode);
        var from = inputMsg.selectionStart, to = inputMsg.selectionEnd;
        var oldText = inputMsg.value;
        inputMsg.value = (oldText.substring(0, from) + text +
                          oldText.substring(to));
        inputMsg.setSelectionRange(from + text.length, from + text.length);
        Instant.input._updateMessage();
      }
    };
  }();
  /* Miscellaneous pane utilities */
  Instant.pane = function() {
    /* The distance to keep nodes away from the screen edges */
    var OUTER_DIST = 50;
    return {
      /* Get the message-box containing this DOM node */
      getBox: function(node) {
        return $parentWithClass(node, 'message-box');
      },
      /* Get the message-pane containing this DOM node */
      getPane: function(node) {
        return $parentWithClass(node, 'message-pane');
      },
      /* Check if a message (or any part of its content if it's too large) is
       * visible with respect to its pane */
      isVisible: function(msg) {
        var line = $cls('line', msg);
        var pane = Instant.pane.getPane(msg);
        var lrect = line.getBoundingClientRect();
        var prect = pane.getBoundingClientRect();
        if (lrect.height > prect.height / 2) {
          /* Be less restrictive if message is too large to fit entirely */
          return (lrect.top < prect.bottom && lrect.bottom > prect.top);
        } else {
          /* Otherwise, message must fit entirely */
          return (lrect.top >= prect.top && lrect.bottom <= prect.bottom);
        }
      },
      /* Check if a message's content is visible in its entirety. */
      isFullyVisible: function(msg) {
        var line = $cls('line', msg);
        var pane = Instant.pane.getPane(msg);
        var lrect = line.getBoundingClientRect();
        var prect = pane.getBoundingClientRect();
        return (lrect.top >= prect.top && lrect.bottom <= prect.bottom);
      },
      /* Get all the messages whose lines are visible with respect to their
       * pane and that are within node */
      getVisible: function(node) {
        var pane = Instant.pane.getPane(node);
        var prect = pane.getBoundingClientRect();
        var ptop = prect.top, pbot = prect.bottom;
        return Instant.message.walk(node, function(msg) {
          /* Calculate rectangles */
          var mrect = msg.getBoundingClientRect();
          var lrect = $cls('line', msg).getBoundingClientRect();
          var mtop = mrect.top, mbot = mrect.bottom;
          var ret = 0;
          /* Determine whether line is visible */
          if (lrect.top < pbot && lrect.bottom > ptop) ret |= 1;
          /* Intersecting with viewport -- scan descendants */
          if (mtop < pbot && mbot > ptop) ret |= 2;
          /* Top within viewport -- scan up */
          if (mtop > ptop && mtop <= pbot) ret |= 4;
          /* Bottom within viewport -- scan down */
          if (mbot >= ptop && mbot < pbot) ret |= 8;
          /* Entirely below viewport -- bisect up */
          if (mtop > pbot) ret |= 16;
          /* Entirely above viewport -- bisect down */
          if (mbot < ptop) ret |= 32;
          return ret;
        });
      },
      /* Save the current (vertical) scrolling position of the pane
       * containing the given node for later restoration
       * If focus is true, the currently focused element is saved as well.
       * The exact line to be stored is determined by hf, which can be
       * (for example):
       * 0.0 -- Restore the top of the child
       * 0.5 -- Restore the middle of the child
       * 1.0 -- Restore the bottom of the child */
      saveScrollState: function(node, hf, focus) {
        var pane = Instant.pane.getPane(node);
        var focused = focus && document.activeElement;
        var nodeRect = node.getBoundingClientRect();
        var paneRect = pane.getBoundingClientRect();
        /* Cookie value stays invariant */
        var cookie = nodeRect.top - paneRect.top + nodeRect.height * hf -
          pane.scrollTop;
        return function() {
          /* Restore values */
          nodeRect = node.getBoundingClientRect();
          paneRect = pane.getBoundingClientRect();
          pane.scrollTop = nodeRect.top - paneRect.top +
            nodeRect.height * hf - cookie;
          if (focused) focused.focus();
        };
      },
      /* Scroll the pane containing node (vertically) such that the latter is
       * well in view
       * The dist parameter specifies which minimal distance to maintain from
       * the pane's boundaries */
      scrollIntoView: function(node, dist) {
        if (dist === null || dist === undefined) dist = OUTER_DIST;
        var pane = Instant.pane.getPane(node);
        Instant.pane.scrollIntoViewEx(node, pane, dist);
      },
      /* Scroll pane (vertically) such that node is no less than dist pixels
       * off a margin */
      scrollIntoViewEx: function(node, pane, dist) {
        var nodeRect = node.getBoundingClientRect();
        var paneRect = pane.getBoundingClientRect();
        if (nodeRect.top < paneRect.top + dist) {
          pane.scrollTop -= paneRect.top + dist - nodeRect.top;
        } else if (nodeRect.bottom > paneRect.bottom - dist) {
          pane.scrollTop -= paneRect.bottom - dist - nodeRect.bottom;
        }
      },
      /* Instant main pane utilities */
      main: function() {
        /* The pane */
        var pane = null;
        /* Original first and last child */
        var origFirst = null, origLast = null;
        return {
          /* Initialize submodule */
          init: function(node) {
            pane = node;
            origFirst = pane.firstChild;
            origLast = pane.lastChild;
          },
          /* Add a node before the main pane content */
          addBefore: function(node) {
            pane.insertBefore(node, origFirst);
          },
          /* Add a node after the main pane content */
          addAfter: function(node) {
            pane.appendChild(node);
          }
        };
      }()
    };
  }();
  /* Sidebar handling */
  Instant.sidebar = function() {
    /* The main sidebar node */
    var node = null;
    /* Already shown messages */
    var shownUIMessages = {};
    return {
      /* Initialize submodule */
      init: function(navNode) {
        function mutationInvalid(el) {
          return (el.target == wrapper);
        }
        Instant.userList.init();
        node = $makeNode('div', 'sidebar', [
          ['div', 'sidebar-content', [
            ['div', 'sidebar-top', [
              ['div', 'sidebar-top-line', [
                Instant.animation.spinner.init(), ' ',
                Instant.sidebar.roomName.init(), ' ',
                Instant.animation.onlineStatus.init(), ' ',
                Instant.settings.init()
              ]],
              ['div', 'ui-message-box']
            ]],
            ['div', 'sidebar-middle-wrapper', [
              ['div', 'sidebar-middle', [
                Instant.userList.getNode()
              ]]
            ]],
            ['div', 'sidebar-bottom', [
              Instant.userList.getCollapserNode()
            ]]
          ]]
        ]);
        var topLine = $cls('sidebar-top-line', node);
        var nameNode = $cls('room-name', node);
        if (navNode) {
          topLine.insertBefore(navNode, nameNode);
          topLine.insertBefore(document.createTextNode(' '), nameNode);
        }
        if (Instant.stagingLocation) {
          var stagingNode = $makeNode('span', 'staging',
            '(' + Instant.stagingLocation + ')');
          topLine.insertBefore(stagingNode, nameNode.nextSibling);
          topLine.insertBefore(document.createTextNode(' '),
                               nameNode.nextSibling);
        }
        var wrapper = $cls('sidebar-middle-wrapper', node);
        window.addEventListener('resize', Instant.sidebar.updateWidth);
        if (window.MutationObserver) {
          var obs = new MutationObserver(function(records, observer) {
            if (records.some(mutationInvalid)) return;
            Instant.sidebar.updateWidth();
          });
          obs.observe(wrapper, {childList: true, attributes: true,
            characterData: true, subtree: true,
            attributeFilter: ['class', 'style']});
        }
        return node;
      },
      /* Change the width of the content to avoid horizontal scrollbars */
      updateWidth: function() {
        /* Extract nodes */
        var wrapper = $cls('sidebar-middle-wrapper', node);
        var content = $cls('sidebar-middle', wrapper);
        /* Prevent faults during initialization */
        if (! wrapper) return;
        /* Make measurements accurate */
        wrapper.style.minWidth = '';
        /* HACK to check for the presence of (explicit) scrollbars */
        if (wrapper.clientWidth != wrapper.offsetWidth) {
          wrapper.classList.add('overflow');
          wrapper.style.minWidth = wrapper.offsetWidth +
            (wrapper.offsetWidth - wrapper.clientWidth) + 'px';
        } else {
          wrapper.classList.remove('overflow');
        }
      },
      /* Mount the given node into the sidebar top area */
      addTop: function(newNode) {
        var box = $cls('ui-message-box', node);
        box.parentNode.insertBefore(newNode, node);
        Instant.sidebar.updateWidth();
      },
      /* Mount the given node to the top of the middle of the sidebar */
      addMiddleTop: function(newNode) {
        var userList = $cls('user-list', node);
        userList.parentNode.insertBefore(newNode, userList);
        Instant.sidebar.updateWidth();
      },
      /* Mount the given node to the bottom of the middle of the sidebar */
      addMiddleBottom: function(newNode) {
        var userList = $cls('user-list', node);
        userList.parentNode.appendChild(newNode);
        Instant.sidebar.updateWidth();
      },
      /* Mount the given node to the bottom of the sidebar */
      addBottom: function(newNode) {
        var bottom = $cls('sidebar-bottom', node);
        bottom.appendChild(newNode);
        Instant.sidebar.updateWidth();
      },
      /* Scroll the sidebar such that the given node is fully visible */
      scrollIntoView: function(child) {
        Instant.pane.scrollIntoViewEx(child, $cls('sidebar-middle-wrapper',
                                                  node), 0);
      },
      /* Return the main DOM node */
      getNode: function() {
        return node;
      },
      /* Possibly show a UI message */
      _notify: function(notify) {
        var data = notify.data;
        if (! data.uiMessage) return;
        var msgnode = Instant.sidebar.makeMessage({
          content: data.uiMessageNode || notify.text,
          color: data.uiMessageColor || notify.color,
          onclick: notify.onclick});
        Instant.sidebar.showMessage(msgnode, data.uiMessage);
        if (notify.data.onuimessage)
          notify.data.onuimessage(msgnode);
      },
      /* Make a UI message */
      makeMessage: function(options) {
        function stopFlash(evt) {
          Instant.animation.unflash(evt.target);
        }
        var msgnode = document.createElement('div');
        msgnode.tabIndex = 0;
        if (typeof options.content == 'string') {
          msgnode.textContent = options.content;
        } else if (options.content) {
          msgnode.appendChild(options.content);
        }
        if (options.color) {
          msgnode.style.color = options.color;
        }
        if (options.flash) {
          Instant.animation.flash(msgnode);
        }
        msgnode.addEventListener('animationend', stopFlash);
        msgnode.addEventListener('click', stopFlash);
        if (options.onclick) {
          msgnode.classList.add('clickable');
          msgnode.addEventListener('click', options.onclick);
          msgnode.addEventListener('keydown', function(event) {
            // Return or Space
            if (event.keyCode == 13 || event.keyCode == 32) {
              options.onclick.call(this, event);
              event.preventDefault();
            }
          });
        }
        return msgnode;
      },
      /* Show a UI message */
      showMessage: function(msgnode, id) {
        var msgbox = $cls('ui-message-box', node);
        if (id) {
          if (shownUIMessages[id])
            msgbox.removeChild(shownUIMessages[id]);
          shownUIMessages[id] = msgnode;
          msgnode.setAttribute('data-msgid', id);
        }
        msgbox.appendChild(msgnode);
        Instant.sidebar.updateWidth();
      },
      /* Hide a UI message */
      hideMessage: function(msgnode) {
        var msgbox = $cls('ui-message-box', node);
        var msgid = msgnode.getAttribute('data-msgid');
        if (msgid) delete shownUIMessages[msgid];
        try {
          msgbox.removeChild(msgnode);
        } catch (e) {}
      },
      /* Room name widget */
      roomName: function() {
        /* The DOM node */
        var node = null;
        return {
          /* Initialize submodule */
          init: function() {
            node = $makeNode('a', 'room-name', {href: ''});
            /* Clickable UI messages are a bit too close to this one,
             * and one can still drag-and-drop it, or use the context menu.
             */
            node.addEventListener('click', function(event) {
              event.preventDefault();
            });
            if (Instant.roomName) {
              node.appendChild(document.createTextNode('&' +
                Instant.roomName));
            } else {
              node.appendChild($makeNode('i', null, 'local'));
            }
            return node;
          }
        };
      }()
    };
  }();
  /* User list handling */
  Instant.userList = function() {
    /* ID -> node */
    var nicks = {};
    /* The actual user list. Wrapper is retrieved automatically. */
    var node = null;
    /* The counter and collapser */
    var collapser = null;
    /* The "context menu" (re-anchored to the node currently in use) */
    var menu = null;
    /* Whether the list was previously collapsed */
    var lastCollapsed = false;
    return {
      /* Initialize state */
      init: function() {
        /* Helper */
        node = $makeNode('div', 'user-list');
        collapser = $makeNode('div', 'user-list-counter', [
          ['a', {href: '#'}, [
            ['img', {src: '/static/arrow-up.svg'}], ' ',
            ['span', ['...']]
          ]]
        ]);
        menu = $makeNode('div', 'user-list-menu', [
          ['h2', ['Actions:']],
          ['div', 'clear'],
          ['button', 'button action-ping', ['Insert ping']], ' ',
          ['button', 'button action-pm', ['PM']]
        ]);
        /* Maintain focus state of input bar */
        var inputWasFocused = false;
        collapser.addEventListener('mousedown', function(event) {
          inputWasFocused = Instant.input.isFocused();
        });
        collapser.addEventListener('keydown', function(event) {
          if (event.keyCode == 13) // Return
            inputWasFocused = true;
        });
        collapser.addEventListener('click', function(event) {
          Instant.userList.collapse(! Instant.userList.isCollapsed());
          if (inputWasFocused) {
            Instant.input.focus();
          } else {
            document.activeElement.blur();
          }
          event.preventDefault();
        });
        /* Collapse user list on small windows */
        window.addEventListener('resize', Instant.userList._updateCollapse);
        Instant.userList._updateCollapse();
        /* Flush the animation when the tab is shown (after having been
         * hidden) */
        window.addEventListener('visibilitychange', function() {
          if (document.visibilityState == 'visible')
            Instant.userList._updateDecay();
        });
        /* Context menu actions */
        $cls('action-ping', menu).addEventListener('click', function() {
          var parent = menu.parentNode;
          if (! parent) return;
          var nickNode = parent.firstElementChild;
          var nick = nickNode.getAttribute('data-nick');
          var ping = Instant.nick.makeMentionText(nick);
          Instant.input.insertText(ping + ' ');
          Instant.userList.showMenu(null);
          Instant.input.focus();
        });
        $cls('action-pm', menu).addEventListener('click', function() {
          var parent = menu.parentNode;
          if (! parent) return;
          var nickNode = parent.firstElementChild;
          var uid = nickNode.getAttribute('data-id');
          var nick = nickNode.getAttribute('data-nick');
          Instant.userList.showMenu(null);
          Instant.privmsg.write(uid, nick);
        });
      },
      /* Scan the list for a place where to insert */
      bisect: function(id, name) {
        /* No need to employ particularly fancy algorithms */
        if (! node) return null;
        var children = node.children;
        var b = 0, e = children.length;
        var ret;
        for (;;) {
          // Bounds met? Done.
          if (b == e) {
            ret = children[b];
            break;
          }
          // Middle index and text.
          var m = (b + e) >> 1;
          var t = children[m].firstElementChild.textContent;
          var i = children[m].firstElementChild.getAttribute('data-id');
          // Test which half to engage.
          if (name < t) {
            e = m;
          } else if (name > t) {
            if (b == m) m++;
            b = m;
          } else if (id && id < i) {
            e = m;
          } else if (id && id > i) {
            if (b == m) m++;
            b = m;
          } else {
            ret = children[m];
            break;
          }
        }
        return (ret) ? ret.firstElementChild : null;
      },
      /* Get the node corresponding to id or null */
      get: function(id) {
        return nicks[id] || null;
      },
      /* Add or update the entry for id */
      add: function(id, name, uuid) {
        function toggleMenu() {
          if (menu.parentNode == newWrapper) {
            Instant.userList.showMenu(null);
          } else {
            Instant.userList.showMenu(id);
          }
        }
        /* Create a new node if necessary */
        var newNode = nicks[id], newWrapper;
        if (newNode) {
          /* Do not disturb searching */
          newWrapper = newNode.parentNode;
          node.removeChild(newWrapper);
        } else {
          newNode = document.createElement('span');
          newNode.className = 'nick';
          newNode.setAttribute('data-id', id);
          newNode.id = 'user-' + id;
          newNode.tabIndex = 0;
          newWrapper = document.createElement('div');
          newWrapper.className = 'nick-box';
          newWrapper.appendChild(newNode);
          newNode.addEventListener('click', toggleMenu);
          newNode.addEventListener('keydown', function(event) {
            // Return or Space
            if (event.keyCode == 13 || event.keyCode == 32) {
              toggleMenu();
              event.preventDefault();
            }
          });
        }
        /* Apply new parameters to node */
        if (uuid) newNode.setAttribute('data-uuid', uuid);
        newNode.setAttribute('data-last-active', Date.now());
        newNode.setAttribute('data-nick', name);
        newNode.textContent = name;
        newNode.style.background = Instant.nick.nickColor(name);
        newWrapper.style.display = ((name) ? '' : 'none');
        /* Update animation */
        newNode.style.webkitAnimationDelay = '';
        newNode.style.animationDelay = '';
        /* Update data */
        nicks[id] = newNode;
        /* Abort if no node */
        if (! node) return null;
        /* Find insertion position */
        var insBefore = Instant.userList.bisect(id, name);
        if (insBefore) insBefore = insBefore.parentNode;
        /* Insert node into list */
        node.insertBefore(newWrapper, insBefore);
        /* Maintain consistency */
        Instant.userList.update();
        /* Return something sensible */
        return newNode;
      },
      /* Remove the given entry */
      remove: function(id) {
        if (! nicks[id]) return;
        try {
          node.removeChild(nicks[id].parentNode);
        } catch (e) {}
        delete nicks[id];
        Instant.userList.update();
      },
      /* Remove everything from list */
      clear: function() {
        nicks = {};
        if (node) while (node.firstChild) node.removeChild(node.firstChild);
        Instant.userList.update();
      },
      /* Obtain data for a certain (set of) users in a convenient format
       * The user list is filtered by entries that match all parameters of
       * the function which are not null (so, calling query() by itself will
       * dump the entire user list).
       * The return value is an (unordered) array of objects containing the
       * properties "id", "uuid", "nick" with corresponding contents, one
       * object for each user list entry that matched the filter. */
      query: function(nick, uuid, id) {
        var data = Array.prototype.map.call(node.children, function(el) {
          return el.firstElementChild;
        });
        if (nick != null)
          data = data.filter(function(el) {
            return (el.getAttribute('data-nick') == nick);
          });
        if (uuid != null)
          data = data.filter(function(el) {
            return (el.getAttribute('data-uuid') == uuid);
          });
        if (id != null)
          data = data.filter(function(el) {
            return (el.getAttribute('data-id') == id);
          });
        return data.map(function(el) {
          return {id: el.getAttribute('data-id'),
                  uuid: el.getAttribute('data-uuid'),
                  nick: el.getAttribute('data-nick')};
        });
      },
      /* Update the collapsing state */
      _updateCollapse: function() {
        var newState = (document.documentElement.offsetWidth <= 400 ||
                        ! node.children.length);
        if (newState == lastCollapsed) return;
        lastCollapsed = newState;
        Instant.userList.collapse(newState);
      },
      /* Forcefully update the nick color decay animation */
      _updateDecay: function() {
        var now = Date.now();
        Array.prototype.forEach.call(node.children, function(el) {
          var nick = el.firstElementChild;
          var time = nick.getAttribute('data-last-active') - now;
          /* Keep in sync with the CSS */
          if (time < -600000) time = -600000;
          nick.style.webkitAnimationDelay = time + 'ms';
          nick.style.animationDelay = time + 'ms';
        });
      },
      /* Update some CSS properties */
      update: function() {
        /* Update counter */
        if (node && collapser) {
          var c = $sel('span', collapser);
          var n = node.children.length;
          c.textContent = n + ' user' + ((n == 1) ? '' : 's');
        }
        /* Collapse as necessary */
        Instant.userList._updateCollapse();
        /* Now actually delegated to sidebar itself */
        Instant.sidebar.updateWidth();
        /* Fire event */
        Instant._fireListeners('userList.update');
      },
      /* Collapse or uncollapse the user list */
      collapse: function(invisible) {
        var parent = node.parentNode;
        if (! parent || ! parent.classList.contains('user-list-wrapper'))
          parent = null;
        if (invisible) {
          node.classList.add('collapsed');
          collapser.classList.add('collapsed');
          if (parent) parent.classList.add('collapsed');
        } else {
          node.classList.remove('collapsed');
          collapser.classList.remove('collapsed');
          if (parent) parent.classList.remove('collapsed');
          /* Update animations */
          Instant.userList._updateDecay();
        }
        Instant.userList.update();
      },
      /* Return whether the user list is currently collapsed */
      isCollapsed: function() {
        return (node.classList.contains('collapsed'));
      },
      /* List unique seminormalized nicks which match the semi-normalization
       * of the given prefix */
      listMatchingNicks: function(prefix) {
        prefix = Instant.nick.seminormalize(prefix);
        var nicks = Array.prototype.map.call(node.children, function(n) {
          return Instant.nick.seminormalize(
            n.firstElementChild.getAttribute('data-nick'));
        });
        var last = null;
        return nicks.filter(function(n) {
          if (last == null || n != last) {
            last = n;
          } else {
            return false;
          }
          return (n.substring(0, prefix.length) == prefix);
        });
      },
      /* Return a completion for the given nickname prefix, or null
       * if there is no nick matching the given prefix.
       * NOTE that the prefix must not contain whitespace since that
       *      would make the results ambiguous (and the implementation
       *      more convoluted than it already is). */
      complete: function(prefix) {
        /* List possible nicks */
        var nicks = Instant.userList.listMatchingNicks(prefix);
        /* Special cases */
        if (! nicks.length) {
          return null;
        } else if (nicks.length == 1) {
          return nicks[0].substring(prefix.length);
        }
        /* Strip prefix */
        for (var i = 0; i < nicks.length; i++)
          nicks[i] = nicks[i].substring(prefix.length);
        /* Determine common prefix of remaining nicks */
        var pref = nicks[0];
        for (var i = 1; i < nicks.length; i++) {
          var n = nicks[i];
          /* Truncate to common length */
          while (n.substring(0, pref.length) != pref)
            pref = pref.substring(0, pref.length - 1);
        }
        /* Return it */
        return pref;
      },
      /* Append the given node to the context menu */
      addMenuNode: function(node) {
        menu.appendChild(node);
      },
      /* Show the context menu on the given node, or hide it */
      showMenu: function(id) {
        var curParent = menu.parentNode;
        if (curParent) {
          var curChild = curParent.firstElementChild;
          if (curChild.getAttribute('data-id') == id) {
            return;
          } else {
            curParent.classList.remove('selected');
            curParent.removeChild(menu);
          }
        }
        var newChild = null;
        if (id) newChild = Instant.userList.get(id);
        if (! newChild) {
          Instant.userList.update();
          return;
        }
        var newParent = newChild.parentNode;
        newParent.classList.add('selected');
        newParent.appendChild(menu);
        Instant.userList.update();
        Instant.sidebar.scrollIntoView(newParent);
      },
      /* Return the ID of the currently selected user */
      getSelectedUser: function() {
        var curParent = menu.parentNode;
        if (! curParent) return null;
        return curParent.firstElementChild.getAttribute('data-id');
      },
      /* Get the main user list node */
      getNode: function() {
        return node;
      },
      /* Get the collapser node */
      getCollapserNode: function() {
        return collapser;
      }
    };
  }();
  /* Private messages */
  Instant.privmsg = function() {
    /* Color-coding */
    var COLORS = {
      U: '#808000', /* Unread - important */
      I: '#008000', /* Read - okay */
      D: '#0080ff', /* Draft - neutral */
      O: '#c00000'  /* Outbox - can be removed */
    };
    /* UI messages (unread/others) */
    var msgUnread = null, msgOthers = null;
    /* Popup storage */
    var popups = [];
    /* Message opening popup */
    var accessPopup = null;
    /* Reload-safe cache of popups */
    var storage = null;
    return {
      /* Export colors */
      COLORS: COLORS,
      /* Initialize submodule */
      init: function() {
        function sh(u, i, d, o) {
          return Instant.privmsg.show.bind(Instant.privmsg, u, i, d, o);
        }
        var su = sh(true, false, false, false);
        var sm = sh(false, true, true, true);
        msgUnread = Instant.sidebar.makeMessage({
          content: 'New private messages',
          color: Instant.notifications.COLORS.privmsg,
          onclick: su});
        msgOthers = Instant.sidebar.makeMessage({
          content: 'Private messages',
          color: Instant.notifications.COLORS.privmsg,
          onclick: sm});
        accessPopup = Instant.popups.make({
          title: 'Private messages',
          content: 'Click below to open your inbox / all drafts.',
          buttons: [
            {text: 'Unread', onclick: su, color: COLORS.U,
             className: 'show-unread'},
            {text: 'Inbox', onclick: sh(false, true, false, false),
             color: COLORS.I, className: 'show-inbox'},
            {text: 'Drafts', onclick: sh(false, false, true, false),
             color: COLORS.D, className: 'show-drafts'},
            {text: 'Outbox', onclick: sh(false, false, false, true),
             color: COLORS.O, className: 'show-outbox'}
          ],
          noClose: true,
          className: 'popup-weak'});
        storage = new Instant.storage.Storage('instant-pm-backup', true);
        storage.load();
        storage.keys().forEach(function(k) {
          var data = storage.get(k);
          if (data.type == 'pm-draft' || data.type == 'pm-afterview') {
            var popup = Instant.privmsg._write(data);
            if (data.type == 'pm-afterview')
              Instant.privmsg._transformAfterview(popup);
          } else {
            Instant.privmsg._read(data);
          }
        });
        Instant.privmsg._update();
      },
      /* Update notification state */
      _update: function() {
        /* Categorize popups into Unread, Inbox, Drafts, Outbox */
        var counts = {U: 0, I: 0, D: 0, O: 0};
        popups.forEach(function(popup) {
          counts[Instant.privmsg._getPopupClass(popup)]++;
        });
        /* Update signage
         * HACK: Pasting together HTML to levery Array.prototype.join(). */
        var text = ['I', 'D', 'O'].filter(function(l) {
          return counts[l];
        }).map(function(l) {
          return ('<span style="color: ' + COLORS[l] + '">' + counts[l] +
            l.toLowerCase() + '</span>');
        }).join('; ');
        if (counts.U) {
          var pls = (counts.U == 1) ? '' : 's';
          msgUnread.textContent = ('New private message' + pls + ' (' +
            counts.U + '!!)');
          Instant.sidebar.showMessage(msgUnread);
        } else {
          Instant.sidebar.hideMessage(msgUnread);
        }
        if (text) {
          var pls = (counts.I + counts.D + counts.O == 1) ? '' : 's';
          msgOthers.innerHTML = ('Private message' + pls + ' (' +
            text + ')');
          Instant.sidebar.showMessage(msgOthers);
        } else {
          Instant.sidebar.hideMessage(msgOthers);
        }
        /* Update control popup */
        if (counts.U + counts.I + counts.D + counts.O) {
          [['U', 'show-unread', 'Unread'], ['I', 'show-inbox', 'Inbox'],
           ['D', 'show-drafts', 'Drafts'], ['O', 'show-outbox', 'Outbox']
          ].forEach(function(el) {
            var btn = $cls(el[1], accessPopup);
            if (counts[el[0]]) {
              btn.textContent = el[2] + ' (' + counts[el[0]] + ')';
              btn.style.display = '';
            } else {
              btn.style.display = 'none';
            }
          });
          Instant.popups.add(accessPopup, true);
        } else {
          Instant.popups.del(accessPopup);
        }
        Instant.title.update();
      },
      /* Update the gray-out status of all popups */
      _updateNicks: function(popup) {
        if (popup) {
          var nick = $sel('.pm-header .nick', popup);
          if (! Instant.userList.get(nick.getAttribute('data-uid')) &&
              nick.style.backgroundColor)
            nick.style.backgroundColor = '';
          return;
        }
        popups.forEach(Instant.privmsg._updateNicks.bind(Instant.privmsg));
      },
      /* Show the requested popups */
      show: function(unread, inbox, drafts, outbox) {
        var flags = {U: unread, I: inbox, D: drafts, O: outbox};
        var update = false;
        popups.forEach(function(popup) {
          if (Instant.popups.isShown(popup)) return;
          var cls = Instant.privmsg._getPopupClass(popup);
          if (! flags[cls]) return;
          if (cls == 'U') {
            popup.classList.remove('pm-unread');
            Instant.privmsg._save(popup);
            update = true;
          }
          Instant.popups.add(popup);
        });
        Instant.privmsg._update();
        Instant.animation.unflash(msgUnread);
        Instant.animation.unflash(msgOthers);
      },
      /* Start writing a message to uid */
      write: function(uid, nick, text, parent) {
        var now = Date.now();
        Instant.privmsg._write({id: 'draft-' + now, parent: parent, to: uid,
          tonick: nick, text: text, timestamp: now, type: 'pm-draft'}, true);
      },
      /* Actually start writing a message with the given parameters */
      _write: function(data, isNew) {
        var popup = Instant.privmsg._makePopup(data);
        var editor = $cls('pm-editor', popup);
        editor.addEventListener('keydown', function(event) {
          if (event.keyCode == 13 && event.ctrlKey) { // Return
            Instant.privmsg._send(popup);
            event.preventDefault();
          } else if (event.keyCode == 27) { // Escape
            event.stopPropagation();
          }
        });
        editor.addEventListener('change', function(event) {
          Instant.privmsg._save(popup);
        });
        editor.setSelectionRange(editor.value.length, editor.value.length);
        popups.push(popup);
        if (isNew) {
          Instant.privmsg._save(popup);
          Instant.privmsg._updateNicks(popup);
          Instant.popups.add(popup);
          Instant.privmsg._update();
          editor.focus();
        }
        return popup;
      },
      /* Save the current state of the popup into storage */
      _save: function(popup) {
        var data = Instant.privmsg._extractPopupData(popup);
        storage.set(data.id, data);
      },
      /* Remove a PM draft or reader */
      _remove: function(popup) {
        storage.del(popup.getAttribute('data-id'));
        var idx = popups.indexOf(popup);
        if (idx != -1) popups.splice(idx, 1);
        Instant.popups.del(popup);
        Instant.privmsg._update();
      },
      /* Send a PM draft */
      _send: function(popup) {
        function callback(resp) {
          if (resp.type == 'error') {
            Instant.privmsg._showError(popup, resp.data.message);
          } else {
            Instant.privmsg._transformAfterview(popup, true);
            Instant.privmsg._update();
            Instant.popups.focus(popup);
          }
        }
        var data = {type: 'privmsg', nick: Instant.identity.nick,
          text: $cls('pm-editor', popup).value};
        var parentNode = $cls('pm-parent-id', popup);
        if (parentNode)
          data.parent = parentNode.textContent;
        var recipient = $cls('pm-to-id').textContent;
        Instant.connection.sendUnicast(recipient, data, callback);
      },
      /* Display the popup for an incoming message */
      _read: function(data, isNew) {
        var popup = Instant.privmsg._makePopup(data);
        popups.push(popup);
        if (isNew) {
          Instant.privmsg._save(popup);
          Instant.privmsg._update();
          Instant.animation.flash(msgUnread);
          Instant.notifications.submitNew({level: 'privmsg',
            text: 'You have a new private message.',
            btntext: 'View',
            onclick: function() {
              popup.classList.remove('pm-unread');
              Instant.popups.add(popup);
              Instant.privmsg._update();
              Instant.animation.unflash(msgUnread);
            }
          });
        }
        return popup;
      },
      /* Produce a DOM node corresponding to the given description
       * data has the following attributes:
       * draft     (A): Whether the PM to construct the UI for is a draft.
       * id        (A): The ID of the message.
       * parent    (A): The message this PM is a reply to.
       * from      (I): The user the PM originates from.
       * nick      (I): The nick-name of the user the PM originates from.
       * to        (O): The recipient of the PM.
       * tonick    (O): The nickname of the recipient of the PM.
       * text      (A): The content of the PM.
       * timestamp (A): The UNIX timestamp of the message in milliseconds.
       * (I -- applies to incoming messages; O -- applies to outgoing
       * messages; A -- applies to all messages.) */
      _makePopup: function(data) {
        /* Pre-computed variables */
        var draft = (data.type == 'pm-draft' || data.type == 'pm-afterview');
        var nick = (draft) ? data.tonick : data.nick;
        var nickNode = (nick == null) ? Instant.nick.makeAnonymous() :
          Instant.nick.makeNode(nick);
        nickNode.classList.add((draft) ? 'pm-to-nick' : 'pm-from-nick');
        /* Create structure skeleton */
        var body = $makeFrag(
          ! draft && data.id && ['div', 'pm-header', [
            ['strong', null, 'ID: '],
            ['span', [
              ['span', 'monospace pm-message-id'],
              data.parent && ' ',
              data.parent && ['i', [
                '(reply to ', ['span', 'monospace pm-parent-id'], ')'
              ]]
            ]]
          ]],
          draft && data.parent && ['div', 'pm-header', [
            ['strong', null, 'Reply-to: '],
            ['span', 'monospace pm-parent-id']
          ]],
          ! draft && ['div', 'pm-header', [
            ['strong', null, 'From: '],
            ['span', [
              nickNode, ' ',
              ['i', ['(user ID ', ['span', 'monospace pm-from-id'], ')']]
            ]]
          ]],
          draft && ['div', 'pm-header', [
            ['strong', null, 'To: '],
            ['span', [
              nickNode, ' ',
              ['i', ['(user ID ', ['span', 'monospace pm-to-id'], ')']]
            ]]
          ]],
          (data.timestamp != null) && ['div', 'pm-header', [
            ['strong', null, 'Date: '],
            ['span', 'pm-date', [formatDateNode(new Date(data.timestamp))]]
          ]],
          ['hr'],
          ['div', 'pm-body', [
            draft && ['textarea', 'pm-editor', {'data-hidden': ''}],
            ! draft && Instant.message.parseContent(data.text || '')
          ]]
        );
        /* Create buttons */
        var buttons = (draft) ? [
          {text: 'Preview', onclick: function() {
            var body = $cls('pm-body', popup);
            var editor = $cls('pm-editor', popup);
            if (body.lastElementChild != editor)
              body.removeChild(body.lastElementChild);
            if (editor.style.display == 'none') {
              editor.style.display = '';
              this.textContent = 'Preview';
            } else {
              body.appendChild(Instant.message.parseContent(editor.value));
              editor.style.display = 'none';
              this.textContent = 'Edit';
            }
          }, className: 'pm-preview'},
          null, // Spacer
          {text: 'Finish later', onclick: function() {
            Instant.popups.del(popup);
          }, className: 'first pm-finish-later'},
          {text: 'Delete', color: '#c00000', onclick: function() {
            Instant.privmsg._remove(popup);
          }},
          {text: 'Send', color: '#008000', onclick: function() {
            Instant.privmsg._send(popup);
          }, className: 'pm-send'}
        ] : [
          {text: 'Read later', onclick: function() {
            Instant.popups.del(popup);
          }, className: 'first'},
          {text: 'Delete', color: '#c00000', onclick: function() {
            Instant.privmsg._remove(popup);
          }},
          {text: 'Quote & Reply', color: '#008000', onclick: function() {
            var nick = data.nick;
            if (nick == undefined) nick = null;
            var text = data.text.replace(/^(.)?/mg, function(char) {
              return ((char) ? '> ' + char : '>');
            }) + '\n';
            Instant.privmsg.write(data.from, nick, text, data.id);
          }},
          {text: 'Reply', color: '#008000', onclick: function() {
            var nick = data.nick;
            if (nick == undefined) nick = null;
            Instant.privmsg.write(data.from, nick, null, data.id);
          }}
        ];
        /* Create actual popup */
        var popup = Instant.popups.make({
          title: 'Private message' + ((draft) ? ' editor' : ''),
          className: 'pm-popup ' + ((data.unread) ? 'pm-unread ' : '') +
            ((draft) ? 'pm-draft' : 'pm-viewer'),
          content: body,
          buttons: buttons,
          focusSel: (draft) ? '.pm-editor' : '.first'
        });
        /* Distribute remaining data into attributes */
        popup.setAttribute('data-id', data.id);
        if (data.parent)
          $cls('pm-parent-id', popup).textContent = data.parent;
        if (draft) {
          if (data.from != null)
            popup.setAttribute('data-from', data.from);
          if (data.nick != null)
            popup.setAttribute('data-from-nick', data.nick);
          $cls('pm-to-id', popup).textContent = data.to;
          $cls('pm-to-nick', popup).setAttribute('data-uid', data.to);
          $cls('pm-editor', popup).value = data.text || '';
        } else {
          $cls('pm-message-id', popup).textContent = data.id;
          $cls('pm-from-id', popup).textContent = data.from;
          $cls('pm-from-nick', popup).setAttribute('data-uid', data.from);
          if (data.to != null)
            popup.setAttribute('data-to', data.to);
          if (data.tonick != null)
            popup.setAttribute('data-to-nick', data.tonick);
        }
        /* Done */
        return popup;
      },
      /* Transform the given editor popup into an after-view */
      _transformAfterview: function(popup, isNew) {
        var title = $cls('popup-title', popup);
        var body = $cls('pm-body', popup);
        var editor = $cls('pm-editor', popup);
        var preview = $cls('pm-preview', popup);
        var finishLater = $cls('pm-finish-later', popup);
        var send = $cls('pm-send', popup);
        var newText = Instant.message.parseContent(editor.value);
        while (body.firstChild) body.removeChild(body.firstChild);
        body.appendChild(newText);
        preview.parentNode.removeChild(preview);
        finishLater.textContent = 'Dismiss';
        send.parentNode.removeChild(send);
        title.textContent = 'Private message (sent)';
        popup.classList.remove('pm-draft');
        popup.classList.add('pm-afterview');
        popup.setAttribute('data-focus', '.first');
        if (isNew)
          Instant.privmsg._save(popup);
      },
      /* Display an error message just above the button bar */
      _showError: function(popup, text) {
        var node = $cls('pm-error', popup);
        if (node == null) {
          node = $makeNode('div', 'pm-error', [
            ['b', null, 'Error: '],
            ['span', 'error-content']
          ]);
        } else {
          node.parentNode.removeChild(node);
        }
        if (text) {
          $cls('error-content', node).textContent = text;
          var bottom = $cls('popup-bottom', popup);
          bottom.parentNode.insertBefore(node, bottom);
        }
      },
      /* Determine which of the four classes the popup belongs to */
      _getPopupClass: function(popup) {
        if (popup.classList.contains('pm-draft')) {
          return 'D';
        } else if (popup.classList.contains('pm-afterview')) {
          return 'O';
        } else if (popup.classList.contains('pm-unread')) {
          return 'U';
        } else {
          return 'I';
        }
      },
      /* Reverse the operation performed by _makePopup */
      _extractPopupData: function(popup) {
        function extractText(cls, key) {
          var node = $cls(cls, popup);
          if (node) ret[key] = node.textContent;
        }
        var ret = {
          type: (popup.classList.contains('pm-draft')) ? 'pm-draft' :
                (popup.classList.contains('pm-afterview')) ? 'pm-afterview' :
                'privmsg',
          unread: popup.classList.contains('pm-unread'),
          id: popup.getAttribute('data-id')};
        var draft = (ret.type == 'pm-draft' || ret.type == 'pm-afterview');
        extractText('pm-parent-id', 'parent');
        var dateNode = $sel('.pm-date time', popup);
        if (dateNode)
          ret.timestamp = +dateNode.getAttribute('data-timestamp');
        if (draft) {
          ret.from = popup.getAttribute('data-from');
          ret.nick = popup.getAttribute('data-from-nick');
          extractText('pm-to-id', 'to');
          extractText('pm-to-nick', 'tonick');
          if (ret.type == 'pm-afterview') {
            ret.text = Instant.message.parser.extractText(
              $cls('message-text', popup));
          } else {
            ret.text = $cls('pm-editor', popup).value;
          }
        } else {
          extractText('pm-from-id', 'from');
          extractText('pm-from-nick', 'nick');
          ret.to = popup.getAttribute('data-to');
          ret.tonick = popup.getAttribute('data-to-nick');
          ret.text = Instant.message.parser.extractText(
            $cls('message-text', popup));
        }
        return ret;
      },
      /* Handle incoming remote messages */
      _onmessage: function(msg) {
        switch (msg.type) {
          case 'left':
            Instant.privmsg._updateNicks();
            break;
          // Dunno why someone should broadcast a PM, but, sure, why not.
          case 'unicast': case 'broadcast':
            var data = msg.data;
            switch (data.type) {
              case 'privmsg':
                Instant.privmsg._read({id: msg.id, parent: data.parent,
                  from: msg.from, nick: data.nick, text: data.text,
                  timestamp: msg.timestamp, type: 'privmsg', unread: true},
                  true);
                break;
          }
        }
      },
      /* Return the amount of unread private messages */
      countUnread: function() {
        var count = 0;
        popups.forEach(function(popup) {
          if (popup.classList.contains('pm-unread')) count++;
        });
        return count;
      }
    };
  }();
  /* Window title and notification manipulation */
  Instant.title = function() {
    /* Unread messages, replies (amongst the formers) to the current user,
     * @-mentions (amongst the formers) of the current user */
    var unreadMessages = 0, unreadReplies = 0, unreadMentions = 0;
    /* Whether an update is available */
    var updateAvailable = false;
    /* Whether the window is currently blurred */
    var blurred = false;
    return {
      /* Initialize the submodule */
      init: function() {
        window.addEventListener('blur', function() {
          blurred = true;
        });
        window.addEventListener('focus', function() {
          blurred = false;
          Instant.title.clearUnread();
          Instant.animation.offscreen.checkVisible();
        });
        Instant.title._update();
      },
      /* Read the current window title */
      _get: function() {
        return document.title;
      },
      /* Set the window title to str */
      _set: function(str) {
        document.title = str;
      },
      /* Update the window title to accord to the internal counters */
      _update: function() {
        var unreadPMs = Instant.privmsg.countUnread();
        var ext = Instant.titleExtension;
        if (unreadPMs) {
          ext = ' (' + unreadPMs + '!!!)';
        } else if (unreadMessages) {
          if (unreadMentions) {
            ext = ' (' + unreadMessages + '!!)';
          } else if (updateAvailable) {
            ext = ' (' + unreadMessages + ' !)';
          } else {
            ext = ' (' + unreadMessages + ')';
          }
        } else if (updateAvailable) {
          ext = ' (!)';
        }
        Instant.title._set(Instant.baseTitle + ext);
      },
      /* Process a notification object from Instant.notifications */
      _notify: function(notify) {
        Instant.title.addUnread(notify.data.unreadMessages || 0,
                                notify.data.unreadReplies || 0,
                                notify.data.unreadMentions || 0);
        if (notify.data.updateAvailable != null)
          Instant.title.setUpdateAvailable(notify.data.updateAvailable);
      },
      /* Update the window title and the favicon */
      update: function() {
        Instant.title._update();
        Instant.title.favicon._update();
      },
      /* Add the given amounts of messages, replies, and pings to the
       * internal counters and update the window title */
      addUnread: function(messages, replies, mentions) {
        if (! blurred) return;
        unreadMessages += messages;
        unreadReplies += replies;
        unreadMentions += mentions;
        Instant.title.update();
      },
      /* Clear the internal counters and update the window title to suit */
      clearUnread: function() {
        unreadMessages = 0;
        unreadReplies = 0;
        unreadMentions = 0;
        Instant.title.update();
      },
      /* Set the update available status */
      setUpdateAvailable: function(available) {
        updateAvailable = available;
        Instant.title.update();
      },
      /* Return the amount of unread messages */
      getUnreadMessages: function() {
        return unreadMessages;
      },
      /* Return the amount of unread replies (as included in the unread
       * message count) */
      getUnreadReplies: function() {
        return unreadReplies;
      },
      /* Return the amount of unread @-mentions (as included in the unread
       * message count) */
      getUnreadMentions: function() {
        return unreadMentions;
      },
      /* Return the value of the "update available" flag */
      getUpdateAvailable: function() {
        return updateAvailable;
      },
      /* Return whether the window is blurred */
      isBlurred: function() {
        return blurred;
      },
      /* Favicon management */
      favicon: function() {
        /* The currently displayed notification level */
        var curLevel = null;
        return {
          /* Update the favicon to match the current unread message
           * status */
          _update: function() {
            var unreadPMs = Instant.privmsg.countUnread();
            /* HACK: Avoid displaying the "disconnected" favicon on page
             *       load. */
            var connected = (Instant.connection.isConnected() ||
              ! Instant.connection.wasConnected());
            var level;
            if (unreadPMs) {
              level = 'privmsg';
            } else if (unreadMentions) {
              level = 'ping';
            } else if (updateAvailable) {
              level = 'update';
            } else if (unreadReplies) {
              level = 'reply';
            } else if (unreadMessages) {
              level = 'activity';
            } else if (! connected) {
              level = 'disconnect';
            } else {
              level = null;
            }
            /* Only update favicon when necessary */
            if (level == curLevel) return;
            curLevel = level;
            /* Push out new one */
            Instant.notifications.renderIcon(level).then(
              Instant.title.favicon._set);
          },
          /* Set the favicon to whatever the given URL (which may be
           * a data URI) points at */
          _set: function(url) {
            var link = $sel('link[rel~=icon]');
            if (! link) {
              link = document.createElement('link');
              link.rel = 'icon';
              document.head.appendChild(link);
            }
            link.href = url;
          }
        };
      }()
    };
  }();
  /* Special effects */
  Instant.animation = function() {
    /* The main message box */
    var messageBox = null;
    return {
      /* Initialize the submodule */
      init: function(msgNode) {
        function updateHash(event) {
          if (/^#?$/.test(location.hash)) {
            Instant.input.navigate('root');
            Instant.input.focus();
            Instant.pane.scrollIntoView(Instant.input.getNode());
          } else if (Instant.message.checkFragment(location.hash)) {
            Instant.animation.navigateToMessage(location.hash);
          }
        }
        messageBox = msgNode;
        var pane = Instant.pane.getPane(messageBox);
        /* Pull more logs when scrolled to top */
        pane.addEventListener('scroll', function(event) {
          if (pane.scrollTop == 0) Instant.logs.pull.more();
          Instant.animation.offscreen.checkVisible();
        });
        /* Navigate to linked messages */
        window.addEventListener('hashchange', updateHash);
        updateHash();
      },
      /* Flash something */
      flash: function(node) {
        if (node.classList.contains('flash-done')) {
          node.classList.remove('flash-done');
        } else if (! node.classList.contains('flash')) {
          node.classList.add('flash');
        } else {
          node.classList.remove('flash');
          /* HACK: Force a reflow. */
          void node.offsetWidth;
          node.classList.add('flash');
        }
      },
      /* Abort flashing something */
      unflash: function(node) {
        if (node.classList.contains('flash'))
          node.classList.add('flash-done');
      },
      /* Navigate the input to the given message */
      goToMessage: function(msg) {
        Instant.input.jumpTo(msg);
        Instant.input.focus();
        Instant.pane.scrollIntoView(msg);
      },
      /* Navigate to a message as identified by the given ID or fragment
       * identifier
       * If the message is not loaded (yet), will asynchronously pull
       * logs until the message is loaded (or surely absent) and finish
       * asynchronously. */
      navigateToMessage: function(target) {
        var msgid = Instant.message.parseFragment(target);
        if (! msgid) return;
        var msg = Instant.message.get(msgid);
        if (msg) {
          Instant.animation.goToMessage(msg);
        } else {
          Instant.logs.pull.upto(msgid);
        }
      },
      /* Check if more logs should be loaded */
      _updateLogs: function() {
        if (! messageBox) return;
        var tp = parseInt(getComputedStyle(messageBox).paddingTop);
        var pane = Instant.pane.getPane(messageBox);
        var msg = $cls('message', messageBox);
        if (msg && msg.offsetTop >= tp && pane.scrollTop == 0)
          Instant.logs.pull.more();
      },
      /* Greeting pane */
      greeter: function() {
        /* The actual node */
        var node = null;
        /* Whether the node is visible */
        var visible = true;
        /* Whether a particular state should be applied to the node */
        var pending = true;
        /* The timeout to hide it entirely */
        var hideTimeout = null;
        return {
          /* Initialize submodule */
          init: function(greeterNode) {
            node = greeterNode;
            if (pending) {
              Instant.animation.greeter.show();
            } else {
              Instant.animation.greeter.hide();
            }
            pending = null;
          },
          /* Show greeter */
          show: function() {
            pending = true;
            if (! node || visible) return;
            if (hideTimeout != null) clearTimeout(hideTimeout);
            node.style.display = 'inline-block';
            node.style.opacity = '1';
            visible = true;
          },
          /* Hide greeter */
          hide: function() {
            pending = false;
            if (! node || ! visible) return;
            if (hideTimeout != null) clearTimeout(hideTimeout);
            node.style.opacity = '0';
            hideTimeout = setTimeout(function() {
              node.style.display = 'none';
            }, 1000);
            visible = false;
          },
        };
      }(),
      /* Throbber indicating ongoing action */
      spinner: function() {
        /* Status
         * The spinner displays as long as at least one of the values in
         * here it true. */
        var status = {};
        /* The actual spinner element */
        var node = null;
        return {
          /* Initialize the submodule */
          init: function() {
            node = $makeNode('div', 'spinner', [
              ['img', {src: '/static/spinner.svg'}]
            ]);
            Instant.animation.spinner._update();
            return node;
          },
          /* Show the spinner, setting the given status variable */
          show: function(key) {
            status[key] = true;
            Instant.animation.spinner._update();
          },
          /* Possibly hide the spinner, but at least mark this task as
           * done */
          hide: function(key) {
            status[key] = false;
            Instant.animation.spinner._update();
            Instant.animation.greeter.hide();
          },
          /* Get the status value for the given key */
          get: function(key) {
            return status[key];
          },
          /* Update the node to accord to the status */
          _update: function() {
            if (! node) return;
            /* Scan for true values */
            var visible = false;
            for (var key in status) {
              if (! status.hasOwnProperty(key)) continue;
              if (status[key]) {
                visible = true;
                break;
              }
            }
            /* Update node accordingly */
            if (visible) {
              node.classList.add('visible');
            } else {
              node.classList.remove('visible');
            }
          }
        };
      }(),
      /* Connection status widget */
      onlineStatus: function() {
        /* The DOM node */
        var node = null;
        return {
          /* Initialize submodule */
          init: function() {
            node = $makeNode('span', 'online-status', {title: '...'});
            return node;
          },
          /* Update node */
          update: function() {
            if (Instant.connection.isConnected()) {
              node.classList.remove('broken');
              node.classList.remove('local');
              node.classList.add('connected');
              node.title = 'Connected';
            } else if (Instant.connection.wasConnected()) {
              node.classList.remove('connected');
              node.classList.remove('local');
              node.classList.add('broken');
              node.title = 'Broken';
            } else if (Instant.connectionURL == null) {
              node.classList.remove('conneced');
              node.classList.remove('broken');
              node.classList.add('local');
              node.title = 'Local';
            }
          },
          /* Return the DOM node */
          getNode: function() {
            return node;
          },
          /* Respond to a notification */
          _notify: function(notify) {
            Instant.animation.onlineStatus.update();
          }
        };
      }(),
      /* Offscreen/unread message (alert) management */
      offscreen: function() {
        /* The container nodes */
        var messageBox = null, containerNode = null;
        /* The (live) lists of offscreen messages/mentions */
        var unreadMessages = null, unreadMentions = null;
        /* Unread messages above/below */
        var unreadAbove = null, unreadBelow = null;
        /* Unread messages with @-mentions of self */
        var mentionAbove = null, mentionBelow = null;
        /* Scan the message tree in document order for a message for which
         * filter returns a true value; falsy messages (such as null) are
         * returned unconditionally. */
        function scanMessages(start, filter, step) {
          for (;;) {
            if (! start || filter(start)) return start;
            start = step(start);
          }
        }
        /* Is the given message offscreen? */
        function isUnread(msg) {
          return (msg.classList.contains('offscreen') &&
                  msg.classList.contains('new'));
        }
        /* Is the given message offscreen and a mention of the current
         * user? */
        function isUnreadMention(msg) {
          return (isUnread(msg) && msg.classList.contains('ping'));
        }
        return {
          /* Attach to a given DOM node */
          init: function(msgbox, container) {
            /* Handler for events */
            function handleEvent(event, node) {
              var msg = Instant.message.forFragment(node.hash);
              if (msg) {
                Instant.animation.goToMessage(msg);
                Instant.animation.offscreen.check(msg);
                event.preventDefault();
              }
              /* Allow scanning unread messages quickly by keeping
               * focus on the node. */
              if (event.type == 'keydown') node.focus();
            }
            /* Link interesting node (lists) */
            messageBox = msgbox;
            containerNode = container;
            unreadMessages = $clsAll('message new offscreen',
                                     msgbox);
            unreadMentions = $clsAll('message new offscreen ping',
                                     msgbox);
            /* Extract the alerts themself */
            var aboveNode = $cls('alert-above', container);
            var belowNode = $cls('alert-below', container);
            aboveNode.addEventListener('click', function(e) {
              handleEvent(e, aboveNode);
            });
            belowNode.addEventListener('click', function(e) {
              handleEvent(e, belowNode);
            });
            aboveNode.addEventListener('keydown', function(e) {
              if (e.keyCode != 13) return; // Yes, that's the Return key
              handleEvent(e, aboveNode);
            });
            belowNode.addEventListener('keydown', function(e) {
              if (e.keyCode != 13) return; // Return
              handleEvent(e, belowNode);
            });
          },
          /* Mark multiple messages as offscreen (or not) */
          checkMany: function(msgs) {
            if (Instant.title.isBlurred()) {
              Instant.animation.offscreen._updateOffscreen(msgs, null);
            } else {
              var add = [], rem = [];
              msgs.forEach(function(m) {
                if (Instant.pane.isVisible(m)) {
                  rem.push(m);
                } else {
                  add.push(m);
                }
              });
              Instant.animation.offscreen._updateOffscreen(add, rem);
            }
          },
          /* Check if a message is offscreen or not */
          check: function(msg) {
            if (Instant.title.isBlurred() ||
                ! Instant.pane.isVisible(msg)) {
              Instant.animation.offscreen.set(msg);
            } else {
              Instant.animation.offscreen.clear(msg);
            }
          },
          /* Clear the offscreen status for all visible messages */
          checkVisible: function(msg) {
            if (Instant.title.isBlurred()) return;
            if (! unreadAbove && ! unreadBelow) return;
            Instant.animation.offscreen._updateOffscreen(null,
              Instant.pane.getVisible(messageBox));
          },
          /* Mark the message as offscreen */
          set: function(msg) {
            Instant.animation.offscreen._updateOffscreen([msg], null);
          },
          /* Remove the offscreen mark */
          clear: function(msg) {
            Instant.animation.offscreen._updateOffscreen(null, [msg]);
          },
          /* Update the status of an alert */
          showAlert: function(name, visible, ping) {
            var node = $cls(name, containerNode);
            if (! node) return null;
            if (visible && ! node.classList.contains('visible') ||
                ping && ! node.classList.contains('ping'))
              Instant.animation.flash(node);
            if (visible) {
              node.classList.add('visible');
            } else {
              node.classList.remove('visible');
            }
            if (ping) {
              node.classList.add('ping');
            } else {
              node.classList.remove('ping');
            }
            return node;
          },
          /* Find the closest unread messages surrounding probe
           * If mention is true, they additionally have to be @-mentions.
           * If probe is one of the messages, the result is unspecified.
           * Returns an array with two elements, either of which can be null
           * to indicate lack of a matching message. */
          bisect: function(probe, mention) {
            var list = (mention) ? unreadMentions : unreadMessages;
            var docCmp = Instant.message.documentCmp.bind(Instant.message);
            /* Special case */
            if (! list.length) return [null, null];
            /* Actual bisection */
            var li = 0, ri = list.length;
            for (;;) {
              /* Stop if found */
              if (docCmp(probe, list[li]) <= 0) {
                return [list[li - 1] || null, list[li]];
              } else if (docCmp(list[ri - 1], probe) <= 0) {
                return [list[ri - 1], list[ri] || null];
              } else if (ri - li == 1) {
                return [list[li], list[ri]];
              }
              /* Select branch to choose */
              var m = (li + ri) >> 1;
              if (docCmp(probe, list[m]) < 0) {
                ri = m;
              } else {
                li = m;
              }
            }
          },
          /* Update the offscreen status of some messages */
          _updateOffscreen: function(add, remove) {
            var changed = false;
            if (add && add.length) {
              var docCmp = Instant.message.documentCmp.bind(Instant.message);
              var input = Instant.input.getNode();
              for (var i = 0; i < add.length; i++) {
                var n = add[i];
                if (! n.classList.contains('new') ||
                    n.classList.contains('offscreen'))
                  continue;
                n.classList.add('offscreen');
                var icmp = docCmp(n, input);
                if (icmp < 0) {
                  if (! unreadAbove || docCmp(unreadAbove, n) < 0) {
                    unreadAbove = n;
                    changed = true;
                  }
                  if (n.classList.contains('ping') && (! mentionAbove ||
                      docCmp(mentionAbove, n) < 0)) {
                    mentionAbove = n;
                    changed = true;
                  }
                } else if (icmp > 0) {
                  if (! unreadBelow || docCmp(unreadBelow, n) > 0) {
                    unreadBelow = n;
                    changed = true;
                  }
                  if (n.classList.contains('ping') && (! mentionBelow ||
                      docCmp(mentionBelow, n) > 0)) {
                    mentionBelow = n;
                    changed = true;
                  }
                }
              }
            }
            if (remove && remove.length) {
              var rescanUA = false, rescanUB = false;
              var rescanMA = false, rescanMB = false;
              for (var i = 0; i < remove.length; i++) {
                var n = remove[i];
                if (! n.classList.contains('offscreen')) continue;
                n.classList.remove('offscreen');
                if (n == unreadAbove) rescanUA = true;
                if (n == unreadBelow) rescanUB = true;
                if (n == mentionAbove) rescanMA = true;
                if (n == mentionBelow) rescanMB = true;
              }
              if (rescanUA || rescanUB || rescanMA || rescanMB) {
                var im = Instant.message;
                var prec = im.getDocumentPrecedessor.bind(im);
                var succ = im.getDocumentSuccessor.bind(im);
                if (rescanUA)
                  unreadAbove = scanMessages(unreadAbove, isUnread, prec);
                if (rescanUB)
                  unreadBelow = scanMessages(unreadBelow, isUnread, succ);
                if (rescanUA)
                  mentionAbove = scanMessages(mentionAbove, isUnreadMention,
                                              prec);
                if (rescanUB)
                  mentionBelow = scanMessages(mentionBelow, isUnreadMention,
                                              succ);
                changed = true;
              }
            }
            if (changed)
              Instant.animation.offscreen._updateArrows();
          },
          /* Re-point the notification arrows according to the location of
           * the input bar */
          _updateInput: function() {
            var docCmp = Instant.message.documentCmp.bind(Instant.message);
            var input = Instant.input.getNode();
            if ((! unreadAbove || docCmp(unreadAbove, input) < 0) &&
                (! unreadBelow || docCmp(unreadBelow, input) > 0))
              return;
            var newNeighbors = Instant.animation.offscreen.bisect(input);
            unreadAbove = newNeighbors[0];
            unreadBelow = newNeighbors[1];
            if (unreadAbove || unreadBelow) {
              newNeighbors = Instant.animation.offscreen.bisect(input, true);
              mentionAbove = newNeighbors[0];
              mentionBelow = newNeighbors[1];
            }
            Instant.animation.offscreen._updateArrows();
          },
          /* Update the attached nodes */
          _updateArrows: function() {
            var aboveNode = Instant.animation.offscreen.showAlert(
              'alert-above', unreadAbove, mentionAbove);
            var belowNode = Instant.animation.offscreen.showAlert(
              'alert-below', unreadBelow, mentionBelow);
            if (aboveNode)
              aboveNode.href = '#' + ((unreadAbove) ? unreadAbove.id : '');
            if (belowNode)
              belowNode.href = '#' + ((unreadBelow) ? unreadBelow.id : '');
          },
          /* Update the message nodes referenced if they could have been
           * replaced */
          _updateMessages: function() {
            if (unreadAbove != null) unreadAbove = $id(unreadAbove.id);
            if (unreadBelow != null) unreadBelow = $id(unreadBelow.id);
            if (mentionAbove != null) mentionAbove = $id(mentionAbove.id);
            if (mentionBelow != null) mentionBelow = $id(mentionBelow.id);
          },
          /* Reply to the input bar having moved */
          _inputMoved: function() {
            Instant.animation.offscreen._updateInput();
            var aboveNode = $cls('alert-above', containerNode);
            var belowNode = $cls('alert-below', containerNode);
            if (aboveNode) Instant.animation.unflash(aboveNode);
            if (belowNode) Instant.animation.unflash(belowNode);
          },
          /* Get the bottommost unread message above the screen, if any */
          getUnreadAbove: function() {
            return unreadAbove;
          },
          /* Get the topmost unread message below the screen, if any */
          getUnreadBelow: function() {
            return unreadBelow;
          },
          /* Get the bottommost @-mention of the current user above */
          getMentionAbove: function() {
            return mentionAbove;
          },
          /* Get the topmost @-mention of the current user below */
          getMentionBelow: function() {
            return mentionBelow;
          }
        };
      }()
    };
  }();
  /* Settings control */
  Instant.settings = function() {
    /* The outer node containing all the nice stuff */
    var wrapperNode = null;
    return {
      /* Initialize submodule */
      init: function() {
        function radio(group, name, desc, title, extra) {
          extra = extra || {};
          var node = $makeNode('input', {type: 'radio', name: group,
            value: name});
          if (extra.checked) node.checked = true;
          var xcls = (extra.className && ' ' + extra.className || '');
          return ['label', group + '-' + name + xcls, {title: title}, [
            node, ' ' + desc
          ]];
        }
        function checkbox(name, desc, title, extra) {
          extra = extra || {};
          var node = $makeNode('input', {type: 'checkbox', name: name});
          if (extra.checked) node.checked = true;
          return ['label', name, {title: title}, [
            node, ' ' + desc
          ]];
        }
        wrapperNode = $makeNode('div', 'settings-wrapper', [
          ['button', 'settings', [
            ['img', {src: '/static/gear.svg'}],
          ]],
          ['form', 'settings-content', [
            ['h2', ['Settings']],
            ['div', 'settings-theme', [
              ['h3', ['Theme:']],
              radio('theme', 'bright', 'Bright', 'Black-on-white theme ' +
                'for well-lit environments', {checked: true}),
              radio('theme', 'dark', 'Dark', 'Gray-on-black theme for ' +
                'those who like it'),
              radio('theme', 'verydark', 'Very dark', 'Dimmed version of ' +
                'Dark for very dark evironments')
            ]],
            ['hr'],
            ['div', 'settings-notifications', [
              ['h3', ['Notifications: ',
                ['a', 'more-link', {href: '#'}, '(more)']
              ]],
              radio('notifies', 'none', 'None', 'No notifications at all',
                {checked: true}),
              radio('notifies', 'privmsg', 'On private messages', 'Notify ' +
                'when you receive a private message',
                {className: 'more-content'}),
              radio('notifies', 'ping', 'When pinged', 'Notify when you ' +
                'are pinged (or any of the above)'),
              radio('notifies', 'update', 'On updates', 'Notify when ' +
                'there is a new update (or any of the above)',
                {className: 'more-content'}),
              radio('notifies', 'reply', 'When replied to', 'Notify when ' +
                'one of your messages is replied to (or any of the above)'),
              radio('notifies', 'activity', 'On activity', 'Notify when ' +
                'anyone posts a message (or any of the above)'),
              radio('notifies', 'disconnect', 'On disconnects', 'Notify ' +
                'when your connection is interrupted (or any of the above)',
                {className: 'more-content'})
            ]],
            ['hr'],
            ['div', 'settings-nodisturb', [
              checkbox('no-disturb', 'Do not disturb', 'Void ' +
                'notifications that are below your chosen level' )
            ]]
          ]]
        ]);
        var btn = $cls('settings', wrapperNode);
        var cnt = $cls('settings-content', wrapperNode);
        /* Toggle settings */
        btn.addEventListener('click', Instant.settings.toggle);
        /* Install event listeners */
        var apply = Instant.settings.apply.bind(Instant.settings);
        Array.prototype.forEach.call($selAll('input', cnt), function(el) {
          el.addEventListener('change', apply);
        });
        $cls('more-link', cnt).addEventListener('click', function(event) {
          var section = $cls('settings-notifications', cnt);
          if (section.classList.contains('show-more')) {
            section.classList.remove('show-more');
            $cls('more-link', cnt).textContent = '(more)';
          } else {
            section.classList.add('show-more');
            $cls('more-link', cnt).textContent = '(less)';
          }
          event.preventDefault();
        });
        return wrapperNode;
      },
      /* Outlined final part of initialization */
      load: function() {
        /* Restore settings from storage */
        Instant.settings.restore();
        Instant.settings.apply();
      },
      /* Actually apply the settings */
      apply: function(event) {
        var cnt = $cls('settings-content', wrapperNode);
        var theme = cnt.elements['theme'].value;
        if (theme == 'bright') {
          document.body.classList.remove('dark');
          document.body.classList.remove('very-dark');
        } else if (theme == 'dark') {
          document.body.classList.add('dark');
          document.body.classList.remove('very-dark');
        } else if (theme == 'verydark') {
          document.body.classList.add('dark');
          document.body.classList.add('very-dark');
        } else {
          console.warn('Unknown theme:', theme);
        }
        var level = cnt.elements['notifies'].value;
        Instant.notifications.level = level;
        if (level != 'none') Instant.notifications.desktop.request();
        var noDisturb = cnt.elements['no-disturb'].checked;
        Instant.notifications.noDisturb = noDisturb;
        Instant.storage.set('theme', theme);
        Instant.storage.set('notification-level', level);
        Instant.storage.set('no-disturb', noDisturb);
        Instant._fireListeners('settings.apply', {source: event});
      },
      /* Restore the settings from storage */
      restore: function() {
        var cnt = $cls('settings-content', wrapperNode);
        var theme = Instant.storage.get('theme');
        if (theme) cnt.elements['theme'].value = theme;
        var level = Instant.storage.get('notification-level');
        if (level) cnt.elements['notifies'].value = level;
        var noDisturb = Instant.storage.get('no-disturb');
        if (noDisturb) cnt.elements['no-disturb'].checked = noDisturb;
      },
      /* Add a node to the settings content */
      addSetting: function(newNode) {
        $cls('settings-content', wrapperNode).appendChild(newNode);
      },
      /* Set the setting popup visibility */
      _setVisible: function(vis, event) {
        var wasVisible = Instant.settings.isVisible();
        if (vis == null) {
          wrapperNode.classList.toggle('visible');
        } else if (vis) {
          wrapperNode.classList.add('visible');
        } else {
          wrapperNode.classList.remove('visible');
        }
        var visible = Instant.settings.isVisible();
        Instant._fireListeners('settings.visibility', {visible: visible,
          wasVisible: wasVisible, source: event});
      },
      /* Show the settings popup */
      show: function(event) {
        Instant.settings._setVisible(true, event);
      },
      /* Hide the settings popup */
      hide: function(event) {
        Instant.settings._setVisible(false, event);
      },
      /* Toggle the settings visibility */
      toggle: function(event) {
        Instant.settings._setVisible(null, event);
      },
      /* Obtain the current setttings node */
      getMainNode: function() {
        return $cls('settings-content', wrapperNode);
      },
      /* Returns whether the settings area is currently visible */
      isVisible: function() {
        return wrapperNode.classList.contains('visible');
      }
    };
  }();
  /* Desktop notifications */
  Instant.notifications = function() {
    /* Notification levels (from most to least severe) */
    var LEVELS = { none: 0, privmsg: 1, ping: 2, update: 3, reply: 4,
                   activity: 5, disconnect: 6, noise: 7 };
    /* The colors associated to the levels */
    var COLORS = {
      none: '#000000', /* No color in particular */
      privmsg: '#800080', /* Private messages are purple */
      ping: '#c0c000', /* @-mentions are yellow */
      update: '#008000', /* The update information is green */
      reply: '#0040ff', /* Replies are color-coded with blue */
      activity: '#c0c0c0', /* No color in particular, faint version */
      disconnect: '#c00000' /* Red... was not used */
    };
    /* The default icon to display */
    var ICON_PATH = '/static/logo-static_128x128.png';
    var ICON = ICON_PATH;
    /* A DOM Image holding the image for further use */
    var ICON_IMG = null;
    /* Canvas for icon rendering. Re-used. */
    var icon_canvas = null;
    /* Cache of icons for notification levels. */
    var level_icons = {};
    /* Notification object
     * The name is chosen to avoid clashes with the (desktop) Notification
     * object. */
    function Notify(options) {
      if (! options) options = {};
      /* Allow either style */
      if (! (this instanceof Notify))
        return new Notify(options);
      /* Initialize attributes */
      this.title = options.title || (Instant.baseTitle +
                                     Instant.titleExtension);
      this.text = options.text;
      this.level = options.level || 'noise';
      if (options.icon) {
        this.icon = options.icon;
      } else if (options.color) {
        this.color = options.color;
      } else if (options.level) {
        this.color = COLORS[options.level];
      } else {
        this.icon = null;
      }
      this.btntext = options.btntext || 'Activate';
      this.onclick = options.onclick || null;
      this.data = options.data || {};
      if (! this.icon && this.color && icon_canvas)
        this.icon = Instant.notifications._renderIcon(this.color);
    }
    return {
      /* Export levels and colors to outside */
      LEVELS: LEVELS,
      COLORS: COLORS,
      /* The current notification level (symbolic name) */
      level: null,
      /* Whether notifications below level should be swallowed */
      noDisturb: null,
      /* Initialize submodule */
      init: function() {
        /* Load icon */
        ICON_IMG = document.createElement('img');
        ICON_IMG.addEventListener('load', function() {
          /* Render to canvas */
          icon_canvas = document.createElement('canvas');
          icon_canvas.width = ICON_IMG.naturalWidth;
          icon_canvas.height = ICON_IMG.naturalHeight;
          var ctx = icon_canvas.getContext('2d');
          ctx.drawImage(ICON_IMG, 0, 0);
          ICON = icon_canvas.toDataURL('image/png');
        });
        ICON_IMG.src = ICON_PATH;
      },
      /* Get the notification level of the given message */
      getLevel: function(msg) {
        var mlvl = 'activity';
        if (msg.classList.contains('mine')) {
          /* The user presumably knows about their own messages */
          mlvl = 'noise';
        } else if (msg.classList.contains('ping')) {
          mlvl = 'ping';
        } else {
          var par = Instant.message.getCommentParent(msg);
          if (par && par.classList.contains('mine'))
            mlvl = 'reply';
        }
        return mlvl;
      },
      /* Return a Promise of a notification object */
      create: function(options) {
        var res = new Notify(options);
        if (res.color && ! res.icon) {
          return Instant.notifications.renderIcon(res.color).then(
            function(icon) {
              res.icon = icon;
              return res;
            });
        } else {
          return Promise.resolve(res);
        }
      },
      /* Process a notification object properly */
      submit: function(notify) {
        var data = {notify: notify, suppress: false};
        if (Instant.notifications.noDisturb) {
          var nl = LEVELS[notify.level];
          var ul = LEVELS[Instant.notifications.level];
          data.suppress = (nl > ul);
        }
        Instant._fireListeners('notifications.submit', data);
        Instant.sidebar._notify(notify);
        Instant.animation.onlineStatus._notify(notify);
        /* Externally visible means of notification can be swallowed */
        if (! data.suppress) {
          Instant.title._notify(notify);
          Instant.notifications.desktop._notify(notify);
        }
        Instant._fireListeners('notifications.submitLate', data);
        return notify;
      },
      /* Convenience function for creating and submiting a notify */
      submitNew: function(options) {
        return Instant.notifications.create(options).then(
          Instant.notifications.submit, function(error) {
            console.error('Failed to create internal notification object:',
                          error);
            return Promise.reject(error);
          });
      },
      /* Render the notification icon for the given color
       * A color of null results in the base image. May return null if the
       * base image is not loaded; see renderIconEx() and renderIcon(). */
      _renderIcon: function(color) {
        if (icon_canvas == null) return null;
        var stroke = icon_canvas.width / 32;
        var radius = icon_canvas.width / 4;
        var ctx = icon_canvas.getContext('2d');
        ctx.clearRect(0, 0, icon_canvas.width, icon_canvas.height);
        ctx.drawImage(ICON_IMG, 0, 0);
        if (color != null) {
          ctx.beginPath();
          ctx.arc(icon_canvas.width - radius, radius, radius - stroke / 2,
                  0, 2 * Math.PI, true);
          ctx.fillStyle = color;
          ctx.fill();
          ctx.lineWidth = stroke;
          ctx.stroke();
        }
        return icon_canvas.toDataURL('image/png');
      },
      /* Return a Promise of a notification icon with the given color
       * A color of null produces the base icon. */
      renderIconEx: function(color) {
        return new Promise(function(resolve, reject) {
          if (icon_canvas != null) {
            resolve(Instant.notifications._renderIcon(color));
          }
          ICON_IMG.addEventListener('load', function() {
            resolve(Instant.notifications._renderIcon(color));
          });
          ICON_IMG.addEventListener('error', function(event) {
            reject(event);
          });
        });
      },
      /* Return a Promise of a notification icon of the given level */
      renderIcon: function(level) {
        if (level_icons[level])
          return Promise.resolve(level_icons[level]);
        return Instant.notifications.renderIconEx(COLORS[level]).then(
          function(icon) {
            level_icons[level] = icon;
            return icon;
          });
      },
      desktop: function() {
        /* The currently displayed desktop notification */
        var current = null;
        return {
          /* Display a desktop notification for the given notification object
           * unconditionally
           * The appropriate checking mechanisms (i.e. submit()) should have
           * been used to determine whether to display it at all. */
          show: function(notify) {
            /* Do not show two notifications at once */
            var allNotifies = Instant.storage.get('all-notifies');
            if (current && ! allNotifies) return;
            /* Actual notification */
            Instant.notifications.desktop._show(notify.title, notify.text, {
              icon: notify.icon,
              oncreate: function(notify) {
                /* Do not clobber old notifications
                 * At least my browser does this... :S */
                if (current) current.close();
                /* Set current notification */
                current = notify;
                if (allNotifies) return;
                /* Since the close event is ambiguous and not supported
                 * anymore, we just let the notification stay for ten
                 * seconds, and forget about it thereafter. */
                setTimeout(function() {
                  current = null;
                  notify.close();
                }, 10000);
              },
              onclick: function(event) {
                current = null;
                if (notify.onclick) notify.onclick(event);
                event.target.close();
              }
            });
          },
          /* Request permission to display notifications */
          request: function(callback) {
            var res = Notification.requestPermission(callback);
            if (res && res.then) res.then(callback);
          },
          /* Process a notification object */
          _notify: function(notify) {
            var nl = LEVELS[notify.level];
            var ul = LEVELS[Instant.notifications.level];
            if (nl <= ul && Instant.title.isBlurred())
              Instant.notifications.desktop.show(notify);
          },
          /* Display an arbitrary notification */
          _show: function(title, body, options) {
            function run() {
              /* Parse options */
              var icon = options.icon || ICON;
              var oncreate = options.oncreate || null;
              var onclick = options.onclick || null;
              /* HACK: Firefox before release 49 would silently fail to
               *       display notifications with icons to varying rates
               *       (for me). */
              var m = /Firefox\/(\d+)(?=\D)/i.exec(navigator.userAgent);
              if (m && m[1] < 49) icon = null;
              /* Actually create notification */
              var opts = {body: body};
              if (icon != null) opts.icon = icon;
              var ret = new Notification(title, opts);
              /* Install event handler */
              ret.onclick = onclick;
              /* Allow user to modify notification after creation */
              if (oncreate) {
                oncreate(ret);
              }
            }
            /* Request permissions first */
            if (Notification.permission == 'granted') {
              run();
            } else if (Notification.permission == 'default') {
              Instant.notifications.desktop.request(run);
            }
          }
        };
      }()
    };
  }();
  /* Popup nodes */
  Instant.popups = function() {
    /* The main node wrapper and the main node itself */
    var wrapper = null, stack = null;
    /* URL-s for the icons
     * Replaced by data URI-s as soon as the images are preloaded. */
    var closeURL = '/static/close.svg';
    var collapseURL = '/static/collapse.svg';
    var expandURL = '/static/expand.svg';
    return {
      /* Initialize submodule */
      init: function() {
        function preloadImage(url) {
          if (! /\.svg$/.test(url)) return Promise.resolve(url);
          /* HACK: Using <object> elements to avoid devtools noise.
           *       Blame me for that later. */
          return new Promise(function(resolve, reject) {
            var obj = document.createElement('object');
            obj.addEventListener('load', function() {
              var doc = obj.contentDocument;
              var xml = new XMLSerializer().serializeToString(doc);
              /* Shush! */
              document.head.removeChild(obj);
              resolve('data:image/svg+xml;base64,' + btoa(xml));
            });
            obj.addEventListener('error', function(event) {
              reject(event);
            });
            obj.data = url;
            document.head.appendChild(obj);
          });
        }
        wrapper = $makeNode('div', 'popups-wrapper', [
          ['div', 'popups-content', [
            ['div', 'popups']
          ]],
          ['a', 'close-all', {href: '#'}, [
            ['img', {src: closeURL}]
          ]]
        ]);
        stack = $cls('popups', wrapper);
        $cls('close-all', wrapper).addEventListener('click',
          Instant.popups.delAll.bind(Instant.popups));
        /* Preload images */
        preloadImage(closeURL).then(function(res) {
          closeURL = res;
        });
        preloadImage(expandURL).then(function(res) {
          expandURL = res;
        });
        preloadImage(collapseURL).then(function(res) {
          collapseURL = res;
        });
        return stack;
      },
      /* Add a node to the popup stack */
      add: function(node, onTop) {
        if (onTop) {
          stack.insertBefore(node, stack.firstElementChild);
        } else {
          stack.appendChild(node);
        }
        if ($sel('.popup:not(.popup-weak)', stack)) {
          wrapper.style.display = 'block';
          Instant.popups._update();
          Instant.popups.focus(node);
        }
      },
      /* Remove a node from the popup stack */
      del: function(node) {
        var next = node.nextElementSibling || node.previousElementSibling;
        try {
          stack.removeChild(node);
        } catch (e) {
          return;
        }
        if (! $sel('.popup:not(.popup-weak)', stack)) {
          wrapper.style.display = '';
          Instant.input.focus();
        } else {
          Instant.popups._update();
          Instant.popups.focus(next);
        }
      },
      /* Collapse or expand a popup */
      collapse: function(node, force) {
        if (force == null) {
          force = (! node.classList.contains('collapsed'));
        }
        if (force) {
          node.classList.add('collapsed');
          $sel('.popup-collapse img', node).src = expandURL;
        } else {
          node.classList.remove('collapsed');
          $sel('.popup-collapse img', node).src = collapseURL;
        }
        if (node.parentNode == stack) {
          Instant.popups._update();
        }
      },
      /* Check whether a popup is already shown */
      isShown: function(node) {
        return stack.contains(node);
      },
      /* Remove all popups */
      delAll: function() {
        while (stack.firstChild) stack.removeChild(stack.firstChild);
        wrapper.style.display = '';
        Instant.input.focus();
      },
      /* Adjust the position of the "close all" button */
      _update: function() {
        Instant.util.adjustScrollbar($cls('close-all', wrapper),
                                     $cls('popups-content', wrapper));
      },
      /* Create a new popup */
      make: function(options) {
        function addContent(cls, cnt) {
          if (typeof cnt == 'string') {
            $sel(cls, ret).textContent = cnt;
          } else if (cnt) {
            $sel(cls, ret).appendChild(cnt);
          }
        }
        var co = (! options.noCollapse), cl = (! options.noClose);
        var ret = $makeNode('div', 'popup', [
          ['div', 'popup-header', [
            ['span', 'popup-title'],
            co && ['span', 'popup-title-sep'],
            co && ['button', 'button popup-button popup-collapse', [
              ['img', {src: collapseURL}]
            ]],
            cl && ['span', 'popup-title-sep'],
            cl && ['button', 'button popup-button popup-close', [
              ['img', {src: closeURL}]
            ]]
          ]],
          ['div', 'popup-content'],
          ['div', 'popup-bottom']
        ]);
        if (options.id) ret.id = options.id;
        if (options.className) ret.className += ' ' + options.className;
        addContent('.popup-title', options.title);
        addContent('.popup-content', options.content);
        addContent('.popup-bottom', options.bottom);
        if (options.buttons) {
          var bottom = $cls('popup-bottom', ret);
          options.buttons.forEach(function(el) {
            if (el == null) {
              bottom.appendChild($makeNode('span', 'spacer'));
              return;
            }
            var btn = $makeNode('button', 'button', [el.text]);
            if (el.color) btn.style.color = el.color;
            if (el.onclick) btn.addEventListener('click', el.onclick);
            if (el.className) btn.className += ' ' + el.className;
            if (bottom.childNodes.length)
              bottom.appendChild(document.createTextNode(' '));
            bottom.appendChild(btn);
          });
        }
        if (options.focusSel)
          ret.setAttribute('data-focus', options.focusSel);
        var collapser = $cls('popup-collapse', ret);
        var closer = $cls('popup-close', ret);
        if (collapser)
          collapser.addEventListener('click', function(event) {
            if (options.oncollapse) {
              options.oncollapse(event);
              if (event.defaultPrevented) return;
            }
            Instant.popups.collapse(ret);
            event.preventDefault();
          });
        if (closer)
          closer.addEventListener('click', function(event) {
            if (options.onclose) {
              options.onclose(event);
              if (event.defaultPrevented) return;
            }
            event.preventDefault();
            if (options._del) {
              options._del(ret);
            } else {
              Instant.popups.del(ret);
            }
          });
        return ret;
      },
      /* Create a new popup and show it */
      addNew: function(options) {
        var ret = Instant.popups.make(options);
        Instant.popups.add(ret);
        return ret;
      },
      /* Focus a concrete popup or anything */
      focus: function(node) {
        if (node == null) {
          $cls('close-all', wrapper).focus();
        } else if (node.classList.contains('collapsed')) {
          $cls('popup-collapse', node).focus();
        } else if (node.getAttribute('data-focus')) {
          $sel(node.getAttribute('data-focus'), node).focus();
        } else {
          var close = $cls('popup-close', node);
          if (close) {
            close.focus();
          } else {
            node.focus();
          }
        }
      },
      /* Returns the internal node containing the popups */
      getNode: function() {
        return wrapper;
      },
      /* Return the internal node holding the actual popups */
      getPopupNode: function() {
        return stack;
      },
      /* Nonmodal windows hovering over the chat */
      windows: function() {
        /* The main node */
        var winnode = null;
        return {
          /* Initialize submodule */
          init: function() {
            winnode = $makeNode('div', 'windows-wrapper', [
              ['div', 'windows']
            ]);
            return winnode;
          },
          /* Show the given window */
          add: function(wnd) {
            var cont = $cls('windows', winnode);
            cont.appendChild(wnd);
            winnode.style.display = 'block';
          },
          /* Hide the given window */
          del: function(wnd) {
            var cont = $cls('windows', winnode);;
            try {
              cont.removeChild(wnd);
            } catch (e) {}
            if (cont.children.length == 0)
              winnode.style.display = '';
          },
          /* Collapse (iconify) the given window */
          collapse: function(wnd, force) {
            Instant.popups.collapse(wnd, force);
          },
          /* Check whether the given window is visible as such */
          isShown: function(wnd) {
            return winnode.contains(wnd);
          },
          /* Create a new window with the given options */
          make: function(options) {
            var po = {};
            for (var key in options) {
              if (options.hasOwnProperty(key)) po[key] = options[key];
            }
            var self = Instant.popups.windows;
            po._del = self.del.bind(self);
            return Instant.popups.make(po);
          },
          /* Return the DOM node hosting the windows */
          getNode: function() {
            return winnode;
          }
        };
      }()
    };
  }();
  /* Query string parameters
   * The backend does not parse them (as of now), and even if it did,
   * some of those are only relevant to the frontend. */
  Instant.query = function() {
    /* The actual data */
    var data = null;
    return {
      /* Initialize submodule */
      init: function() {
        data = $query(location.search);
      },
      /* Get a parameter, or undefined if none */
      get: function(name) {
        if (! data.hasOwnProperty(name)) return undefined;
        return data[name];
      },
      /* Return the internal storage object */
      getData: function() {
        return data;
      }
    };
  }();
  /* Offline storage */
  Instant.storage = function() {
    /* Utility functions */
    function thaw(str) {
      if (! str) return null;
      var res = null;
      try {
        res = JSON.parse(str);
        if (typeof res != 'object') throw 'Malformed data';
      } catch (e) {
        console.warn('Could not deserialize storage:', e);
        return null;
      }
      return res;
    }
    function update(base, ext) {
      if (! ext) return;
      for (var k in ext) {
        if (! ext.hasOwnProperty(k)) continue;
        base[k] = ext[k];
      }
    }
    /* Storage class */
    function Storage(name, backupSession, backupLocal) {
      this.name = name;
      this.backupSession = backupSession;
      this.backupLocal = backupLocal;
      this._data = {};
    }
    Storage.prototype = {
      /* List all keys */
      keys: function() {
        var ret = [];
        for (var k in this._data) {
          if (this._data.hasOwnProperty(k)) ret.push(k);
        }
        return ret;
      },
      /* Get the value corresponding to key */
      get: function(key) {
        return this._data[key];
      },
      /* Assign value to key and return the old value */
      set: function(key, value) {
        var oldValue = this._data[key];
        this._data[key] = value;
        this.save();
        return oldValue;
      },
      /* Remove the key and return the old value */
      del: function(key) {
        var oldValue = this._data[key];
        delete this._data[key];
        this.save();
        return oldValue;
      },
      /* Reset all data */
      clear: function(key) {
        this._data = {};
        this.save();
      },
      /* Include an already-deserialized object into the internal data */
      _apply: function(merge, data) {
        if (! data) return;
        if (merge) {
          update(this._data, data);
        } else {
          this._data = data;
        }
      },
      /* Restore data from the configured locations
       * If merge is true, only individual keys are updated by their
       * counterparts from the storage (if present), otherwise, the entire
       * data set is replaced by its counterpart from the storage. Values
       * from session storage override values from local storage.
       * NOTE that merging is not recursive. */
      load: function(merge) {
        var apply = this._apply.bind(this, merge);
        if (this.backupLocal && window.localStorage)
          apply(thaw(localStorage.getItem(this.name)));
        if (this.backupSession && window.sessionStorage)
          apply(thaw(sessionStorage.getItem(this.name)));
      },
      /* Serialize all data to the configured locations */
      save: function() {
        var serData = JSON.stringify(this._data);
        if (this.backupSession && window.sessionStorage)
          sessionStorage.setItem(this.name, serData);
        if (this.backupLocal && window.localStorage)
          localStorage.setItem(this.name, serData);
        return serData;
      }
    };
    /* A specialized Storage that additionally backs up data inside
     * another stored object */
    function FallbackStorage(name, fallbackName, fallbackInstance) {
      Storage.call(this, name, true, true);
      this.fallbackName = fallbackName;
      this.fallbackInstance = fallbackInstance;
    }
    FallbackStorage.prototype = Object.create(Storage.prototype);
    /* Restore data from the configured locations */
    FallbackStorage.prototype.load = function(merge) {
      /* Duplicating because of relevant application order */
      var apply = this._apply.bind(this, merge);
      if (this.backupLocal && window.localStorage) {
        apply(thaw(localStorage.getItem(this.name)));
        if (this.fallbackName && this.fallbackInstance) {
          var d = localStorage.getItem(this.fallbackName);
          if (d && typeof d == 'object')
            apply(thaw(d[this.fallbackInstance]));
        }
      }
      if (this.backupSession && window.sessionStorage)
        apply(thaw(sessionStorage.getItem(this.name)));
    };
    /* Serialize all data to the configured locations */
    FallbackStorage.prototype.save = function() {
      Storage.prototype.save.call(this);
      if (this.backupLocal && window.localStorage && this.fallbackName &&
          this.fallbackInstance) {
        var rd = thaw(localStorage.getItem(this.fallbackName)) || {};
        rd[this.fallbackInstance] = data._data;
        localStorage.setItem(this.fallbackName, JSON.stringify(rd));
      }
    };
    /* Actual data object */
    var data = new FallbackStorage('instant-data', 'instant-data-rooms',
                                   Instant.roomName);
    return {
      /* Export classes */
      Storage: Storage,
      FallbackStorage: FallbackStorage,
      /* Initialize submodule */
      init: function() {
        Instant.storage.load();
      },
      /* Get the value associated with the given key, or undefined */
      get: function(key) {
        return data.get(key);
      },
      /* Assign the value to the key and save the results (asynchronously) */
      set: function(key, value) {
        var oldValue = data.set(key, value);
        Instant._fireListeners('storage.set', {key: key, value: value,
          oldValue: oldValue});
      },
      /* Remove the given key and save the results (asynchronously) */
      del: function(key) {
        var oldValue = data.del(key);
        Instant._fireListeners('storage.del', {key: key,
          oldValue: oldValue});
      },
      /* Remove all keys and save the results (still asynchronously) */
      clear: function() {
        data.clear();
        Instant._fireListeners('storage.clear');
      },
      /* Read the underlying storage backends and merge the results into the
       * data array. */
      load: function() {
        data.load(true);
        Instant._fireListeners('storage.load');
      },
      /* Serialize the current data to the backends */
      save: function() {
        Instant._fireListeners('storage.save');
        data.save();
      },
      /* Obtain a reference to the underlying storage object
       * NOTE that the reference might silently become invalid, and remember
       *      to save regularly. */
      getStorage: function() {
        return data;
      }
    };
  }();
  /* Miscellaneous utilities */
  Instant.util = function() {
    return {
      /* Left-pad a string */
      leftpad: leftpad,
      /* Format a date-time nicely */
      formatDate: formatDate,
      formatDateNode: formatDateNode,
      /* Run a function immediately, and then after a fixed interval */
      repeat: repeat,
      /* Adjust the right margin of an element to account for scrollbars */
      adjustScrollbar: function(target, measure) {
        var margin = (measure.offsetWidth - measure.clientWidth) + 'px';
        if (target.style.marginRight != margin)
          target.style.marginRight = margin;
      }
    };
  }();
  /* Plugin utilities */
  Instant.plugins = function() {
    /* Plugin registries */
    var plugins = {}, pendingPlugins = {};
    /* Plugin class */
    function Plugin(name, options) {
      this.name = name;
      this.options = options || {};
      this.data = undefined;
      this._styles = null;
      this._scripts = null;
      this._code = null;
      this._libs = null;
    }
    Plugin.prototype = {
      /* Commence loading the plugin in question, and return a Promise of the
       * loading result */
      _load: function() {
        function assignExpr(e, v) {
          if (e._scriptsIdx != null) {
            self._scripts[e._scriptsIdx] = v;
          } else if (e._libsIdx != null) {
            self._libs[e._libsIdx] = v;
          } else if (e._codeIdx != null) {
            self._code[e._codeIdx] = v;
          }
        }
        var self = this;
        /* Fetch stylesheets */
        var styles = this.options.styles || [];
        this._styles = styles.map(Instant.plugins.addStylesheet);
        /* Fetch dependencies */
        var deps = this.options.deps || [];
        var depsprom = Promise.all(deps.map(Instant.plugins.getPluginAsync));
        /* Resolve scripts */
        var descriptors = [];
        var scripts = this.options.scripts || [];
        var libs = this.options.libs || [];
        var code = this.options.code || [];
        this._scripts = [];
        this._libs = [];
        this._code = [];
        for (var i = 0; i < scripts.length; i++) {
          var el = scripts[i];
          descriptors.push({url: el.url, before: el.before, after: el.after,
            isolate: el.isolate, _scriptsIdx: i});
        }
        for (var i = 0; i < libs.length; i++)
          descriptors.push({url: libs[i], _libsIdx: i})
        for (var i = 0; i < code.length; i++)
          descriptors.push({url: code[i], isolate: true, _codeIdx: i});
        /* Fetch scripts */
        var pending = [depsprom];
        for (var i = 0; i < descriptors.length; i++) {
          var ent = descriptors[i];
          /* Isolated scripts gets their content XHR-ed in and eval()-ed
           * with the plugin as context. */
          if (ent.isolate) {
            var prom = Instant.plugins.loadFile(ent.url).then(function(req) {
              var code = req.response;
              var header = code.substring(0, 4096);
              /* Insert source reference. */
              if (! /\/\/#\s*source(Mapping)?URL\s*=/.test(header)) {
                code = '//# sourceURL=' + ent.url + '\n' + code;
              }
              /* Run code */
              return depsprom.then(function() {
                $evalIn.call(self, code, false);
              }).then(function(res) {
                return assignExpr(ent, res);
              });
            });
            pending.push(prom);
            continue;
          }
          /* Otherwise, using a regular script tag */
          var prefix = (ent.before) ? Promise.resolve() : depsprom;
          var prom = prefix.then(function() {
            var p;
            /* HACK: Creating promise "before" the src attribute is
             *       assigned. Might be useless. */
            assignExpr(ent, Instant.plugins.addScript(ent.url, null,
              function(node) {
                p = Instant.plugins._eventPromise(node);
              }
            ));
            return p;
          });
          if (! ent.after) pending.push(prom);
        }
        /* Run main function */
        if (this.options.main) this._main = this.options.main;
        return Promise.all(pending).then(function() {
          return self._main();
        }).then(function(data) {
          self.data = data;
          return self;
        });
      },
      /* Stub of the plugin initializer function */
      _main: function() {
        return null;
      }
    };
    return {
      /* Make constructor externally visible */
      Plugin: Plugin,
      /* Return a Promise of the result of an XMLHttpRequest GETting url
       * init is a callback that (if true) is called with the XMLHttpRequest
       * object as the only argument just before submitting the latter.
       * The promise resolves to the XHR object for the user's examination,
       * or rejects to an Error object that contains the XHR object as the
       * "request" property. */
      loadFile: function(url, init) {
        return new Promise(function(resolve, reject) {
          var req = new XMLHttpRequest();
          req.open('GET', url, true);
          req.onreadystatechange = function() {
            if (req.readyState != 4) return; // DONE
            if (req.status == 200) { // OK, and 304 Not Modified
              resolve(req);
            } else {
              var err = new Error('Downlad failed');
              err.request = req;
              reject(err);
            }
          };
          if (init) init(req);
          req.send();
        });
      },
      /* Return a Promise based on the load/error events of the given node
       * The Promise resolves if the load event fires, or rejects if the
       * error event fires, in both cases to the event. */
      _eventPromise: function(node) {
        return new Promise(function(resolve, reject) {
          node.onload = function(event) {
            resolve(event);
          };
          node.onerror = function(event) {
            reject(event);
          };
        });
      },
      /* Return a Promise of the contents of the file at url */
      loadContents: function(url, init) {
        return Instant.plugins.loadFile(url, init).then(function(x) {
          return x.response;
        });
      },
      /* Return a Promise of an <img> element with data from url
       * init is handled similarly to loadFile; also similarly to loadFile,
       * on error, the promise is rejected to an Error object containing the
       * Image the download was performed on as "image". Additionally,
       * the "event" property of the rejection value holds the error event
       * that led the promise to rejection. */
      loadImage: function(url, init) {
        var img = document.createElement('img');
        if (init) init(img);
        var ret = Instant.plugins._eventPromise(img);
        img.src = url;
        return ret;
      },
      /* Add a stylesheet <link> referencing the given URL to the <head>
       * If type is false, "text/css" is assigned to the link's "type"
       * property. */
      addStylesheet: function(url, type, init) {
        var style = document.createElement('link');
        style.rel = 'stylesheet';
        style.type = type || 'text/css';
        style.href = url;
        if (init) init(style);
        document.head.appendChild(style);
        return style;
      },
      /* Return a Promise of a <style> element with content from url
       * type is handled exactly like the corresponding parameter of
       * addStylesheet. */
      loadStylesheet: function(url, type, init) {
        return Instant.plugins.loadFile(url, init).then(function(req) {
          var el = document.createElement('style');
          el.type = type || 'text/css';
          el.innerHTML = req.response;
          return el;
        });
      },
      /* Add a <script> element to the <head>
       * type is handled analogously to addStylesheet, but the default is
       * application/javascript. */
      addScript: function(url, type, init) {
        var script = document.createElement('script');
        script.type = type || 'application/javascript';
        script.src = url;
        if (init) init(script);
        document.head.appendChild(script);
        return script;
      },
      /* Return a Promise of a <script> element with content from url */
      loadScript: function(url, type, init) {
        return Instant.plugins.loadFile(url, init).then(function(req) {
          var el = document.createElement('script');
          el.type = type || 'application/javascript';
          el.innerHTML = req.response;
          return el;
        });
      },
      /* Load the given plugin asynchronously and return a Promise of it
       * options contains the following properties (all optional):
       * deps       : An array of names of plugins this plugin is dependent
       *              on. All dependencies are loaded before this plugin is
       *              initialized.
       * styles     : An array of stylesheet URL-s to be fetched and to be
       *              added to the document (asynchronously).
       * scripts    : An array of objects describing code to be run in
       *              association with the plugin:
       *              url    : The URL of the script to be run. Required.
       *              before : Whether the script may be run before the
       *                       dependencies of the plugin are loaded.
       *              after  : Whether the script may finish after the plugin
       *                       has loaded, i.e., whether the plugin's
       *                       initialization should *not* depend on it.
       *              isolate: Whether the script should be run in an
       *                       isolated function scope. If so, false values
       *                       for before and after are implied, and the
       *                       this object for the script points to the
       *                       plugin object; references that have to
       *                       persist must be explicitly assigned to it.
       *              The non-required parameters (i.e. everything except
       *              url) are off as default.
       *              If isolate is true, the code of the plugin is
       *              eval()-uated; to preserve the file name for debugging,
       *              a sourceURL annotation is programmatically inserted at
       *              the beginning of the code if there is none (and no
       *              sourceMappingURL one) within the first 4096 bytes.
       * libs       : An array of JavaScript file URL-s for external
       *              libraries to load. They are equivalent to scripts (as
       *              above) with all additional parameters set to false.
       * code       : An array of JavaScript file URL-s for (additional) code
       *              for the plugin. Each entry is equivalent to a scripts
       *              entry with isolate set to true.
       * main       : Plugin initializer function. All synclibs have been
       *              run, and their results are available in the this
       *              object. The return value is assigned to the "data"
       *              property of the plugin object; if it is thenable, it is
       *              resolved first.
       * Execution order:
       * - All resources are fetched asynchronously in no particular order
       *   (and may have been cached). Execution of scripts is, except for
       *   the constraints noted, concurrent.
       * - styles are fully asynchronous and do not affect the loading of the
       *   plugin that requested them.
       * - Scripts with the before parameter set to false are not run until
       *   all dependencies of the plugin are loaded.
       * - The current plugin loads after (all dependencies have loaded and)
       *   all scripts with the after parameter set to false have run. */
      loadPlugin: function(name, options) {
        var pl = new Plugin(name, options);
        plugins[name] = null;
        var ret = pl._load().then(function(p) {
          plugins[name] = p;
          return p;
        });
        pendingPlugins[name] = ret;
        return ret;
      },
      /* Return the object corresponding to the named plugin */
      getPlugin: function(name) {
        var ret = plugins[name];
        if (ret === null)
          throw new Error('Plugin ' +  name + ' not loaded yet!');
        if (! ret)
          throw new Error('No such plugin: ' + name);
        return ret;
      },
      /* Return a Promise of the object of the named plugin */
      getPluginAsync: function(name) {
        var ret = pendingPlugins[name];
        if (! ret)
          throw new Error('No such plugin: ' + name);
        return ret;
      },
      /* Return a Promise of the data of the given plugin */
      getPluginDataAsync: function(name) {
        return getPluginAsync(name).then(function(plugin) {
          return plugin.data;
        });
      },
      /* Return the internal mapping of all plugins
       * Use with care. */
      getAllPlugins: function() {
        return plugins;
      }
    };
  }();
  /* Alias */
  Instant.loadPlugin = Instant.plugins.loadPlugin.bind(Instant.plugins);
  /* Event handling */
  var handlers = {};
  /* Event class */
  function InstantEvent(type, data) {
    if (! (this instanceof InstantEvent))
      return new InstantEvent(type, data);
    if (data == null) data = {};
    this.instant = Instant;
    this.type = type;
    this.cancelable = (!! data._cancel);
    this.canceled = false;
    for (var key in data) {
      if (! data.hasOwnProperty(key)) continue;
      this[key] = data[key];
    }
  }
  InstantEvent.prototype = {
    /* Cancel the event, if that did not already happen */
    cancel: function() {
      if (! this._cancel || this.canceled) return false;
      this.canceled = true;
      this._cancel();
      return true;
    }
  };
  Instant.InstantEvent = InstantEvent;
  /* Stop listening for an event
   * Returns where the listener had been installed at all. */
  Instant.stopListening = function(name, handler) {
    if (! handlers[name]) return;
    var idx = handlers[name].indexOf(handler);
    if (idx == -1) return false;
    handlers.splice(idx, 1);
    return true;
  };
  /* Listen for an event
   * handler is invoked upon the event with an object containing at least the
   * following properties:
   * instant: The Instant object.
   * type   : The type of the event.
   * Specific events may define more properties. */
  Instant.listen = function(name, handler) {
    Instant.stopListening(name, handler);
    if (! handlers[name]) handlers[name] = [];
    handlers[name].push(handler);
  };
  /* Invoke the listeners for a given event */
  Instant._fireListeners = function(type, data) {
    if (! handlers[type]) return;
    var event = new InstantEvent(type, data);
    runList(handlers[type], event);
  };
  /* Global initialization function */
  Instant.init = function(main, loadWrapper, navigation) {
    Instant._fireListeners('init.early');
    Instant.query.init();
    Instant.storage.init();
    Instant.message.init();
    Instant.input.init();
    Instant.message.getMessageBox().appendChild(Instant.input.getNode());
    Instant.sidebar.init(navigation);
    Instant.title.init();
    Instant.notifications.init();
    Instant.logs.pull.init(Instant.message.getMessageBox());
    Instant.animation.init(Instant.message.getMessageBox());
    Instant.animation.greeter.init(loadWrapper);
    Instant.animation.offscreen.init(Instant.message.getMessageBox(),
      $cls('alert-container', Instant.input.getNode()));
    Instant.popups.init();
    Instant.popups.windows.init();
    Instant.privmsg.init();
    main.appendChild(Instant.message.getMessagePane());
    main.appendChild(Instant.sidebar.getNode());
    main.appendChild(Instant.popups.windows.getNode());
    main.appendChild(Instant.popups.getNode());
    Instant.pane.main.init(main);
    Instant._fireListeners('init.late');
    Instant.settings.load();
    Instant.connection.init();
    repeat(function() {
      Instant.util.adjustScrollbar($cls('sidebar', main),
                                   $cls('message-pane', main));
      Instant.popups._update();
    }, 1000);
    Instant.notifications.submitNew({text: 'Ready.'});
    Instant._fireListeners('init.final');
  };
  /* To be assigned in window */
  return Instant;
}();

function init() {
  /* Obtain nodes */
  var wrapper = $id('load-wrapper');
  var main = $id('main');
  /* Apply some animations */
  wrapper.classList.add('busy');
  /* Display splash messages */
  var m = $id('splash-messages');
  Instant.message.addReply({id: 'loading-0-wait', nick: 'Loading',
    text: 'Please wait...'}, m);
  var isIE = /*@cc_on!@*/0;
  if (isIE) Instant.message.addReply({id: 'loading-1-ie', nick: 'Doom',
    text: '/me awaits IE users...'}, m);
  $onload(function() {
    /* Show main element
     * Deferred to avoid partial FOUC-s. */
    main.classList.add('ready');
    /* Hide greeter */
    if (! Instant.roomName) {
      /* Nothing is going to hide it, so we have to. */
      Instant.animation.greeter.hide();
    }
  }, true, true);
  /* Focus input bar if Escape pressed and not focused */
  document.documentElement.addEventListener('keydown', function(event) {
    if (event.keyCode == 27) { // Escape
      if (Instant.settings.isVisible())
        Instant.settings.hide();
      if (Instant.userList.getSelectedUser() != null)
        Instant.userList.showMenu(null);
      Instant.input.focus();
      event.preventDefault();
    }
  });
  /* Fire up Instant! */
  Instant.init(main, wrapper, $cls('breadcrumbs'));
  Instant.input.focus();
  /* Allow dismissing wrapper */
  var wrapperClose = $id('load-wrapper-close');
  wrapperClose.style.display = 'block';
  wrapperClose.addEventListener('click', function() {
    Instant.animation.greeter.hide();
    Instant.input.focus();
  });
  /* Avoid hasty reconnects on refreshes */
  window.addEventListener('beforeunload', function() {
    Instant.connection._dontConnect = true;
  });
}

/* It is quite conceivable this could be run after the document is ready.
 * Citation: Look at the thousands of lines above.
 * Second citation: The script is deferred. */
$onload(init);
