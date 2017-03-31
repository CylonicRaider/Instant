
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
function $esc(text) {
  return text.replace(/&/g, '&amp;').replace(/</g, '&lt;');
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

function $query(str, ret) {
  if (! ret) ret = {};
  var regex = /[#?&]?([^&=]+)=([^&]*)(?=&|$)|($)/g;
  for (;;) {
    var m = regex.exec(str);
    if (! m) return null;
    if (m[3] != null) break;
    var n = decodeURIComponent(m[1]);
    var v = decodeURIComponent(m[2]);
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
  /* Format a date sensibly */
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
  /* Logging handlers */
  var console = {
    /* Log a debugging message */
    log: function() {
      if (window.console) {
        window.console['log'].apply(window.console, arguments);
      }
    },
    /* Log an information message */
    info: function() {
      if (window.console) {
        window.console.info.apply(window.console, arguments);
      }
    },
    /* Log a warning */
    warn: function() {
      if (window.console) {
        window.console.warn.apply(window.console, arguments);
      }
    },
    /* Log an error */
    error: function() {
      if (window.console) {
        window.console.error.apply(window.console, arguments);
      } else {
        alert('ERROR: ' + arguments.join(' '));
      }
    }
  };
  console['debug'] = console['log'];
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
        Instant._fireListeners('identity.estabilished');
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
    /* Send pings every thirty seconds */
    setInterval(function() {
      if (Instant && Instant.connection && Instant.connection.isConnected())
        Instant.connection.sendSeq({type: 'ping'});
    }, 30000);
    return {
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
        if (! Instant.connectionURL) {
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
        Instant._fireListeners('connection.connected', {source: event});
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
        Instant._fireListeners('connection.message', {source: event});
        if (event.defaultPrevented) return;
        /* Extract message data */
        var msg;
        try {
          msg = JSON.parse(event.data);
        } catch (e) {
          console.warn('Cannot parse message:', e);
          return;
        }
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
          case 'pong': /* Server replied to a ping */
          case 'reply': /* Reply to a message sent */
            /* Nothing to do */
            break;
          case 'joined': /* New user joined (might be ourself) */
            Instant.userList.add(msg.data.id, '', msg.data.uuid);
            Instant.logs.addUUID(msg.data.id, msg.data.uuid);
            break;
          case 'left': /* User left */
            Instant.userList.remove(msg.data.id);
            Instant.logs.pull._onmessage(msg);
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
        var wasConnected = connected;
        /* Update flag */
        connected = false;
        /* Inform logs */
        Instant.logs.pull._disconnected();
        /* Send event */
        Instant._fireListeners('connection.closed', {source: event});
        /* Send a notification */
        if (wasConnected)
          Instant.notifications.submitNew({text: 'Disconnected!',
            level: 'disconnect'});
        /* Re-connect */
        if (event)
          Instant.connection.reconnect();
      },
      /* Handle an auxillary error */
      _error: function(event) {
        /* Update flag */
        connected = false;
        /* Send event */
        Instant._fireListeners('connection.error', {source: event});
        /* Cannnot really do anything */
        if (event)
          console.warn('WebSocket error:', event);
        /* Re-connect */
        if (event)
          Instant.connection.reconnect();
      },
      /* Most basic sending */
      sendRaw: function(data) {
        return ws.send(data);
      },
      /* Send an object whilst adding a sequence ID (in-place); return the
       * sequence ID */
      sendSeq: function(data) {
        data.seq = seqid++;
        Instant.connection.sendRaw(JSON.stringify(data));
        return data.seq;
      },
      /* Send a ping with the given payload (or none at all) to the server */
      sendPing: function(data) {
        var msg = {type: 'ping'};
        if (data !== undefined) msg.data = data;
        return Instant.connection.sendSeq(msg);
      },
      /* Send an unicast message to the given participant with the given
       * payload */
      sendUnicast: function(to, data) {
        return Instant.connection.sendSeq({type: 'unicast', to: to,
                                           data: data});
      },
      /* Send a broadcast message with the given payload */
      sendBroadcast: function(data) {
        return Instant.connection.sendSeq({type: 'broadcast',
                                           data: data});
      },
      /* Send either a unicast or a broadcast */
      send: function(to, data) {
        if (to) {
          return Instant.connection.sendUnicast(to, data);
        } else {
          return Instant.connection.sendBroadcast(data);
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
        var hue = Instant.nick.hueHash(name);
        var node = $makeNode('span', 'nick mdl-chip', [
          ['span', 'mdl-chip__text', name]
        ]);
        node.style.backgroundColor = Instant.nick.nickColor(name);
        node.setAttribute('data-nick', name);
        return node;
      },
      /* Generate a DOM node carrying a mention of the nick
       * name is the nickname with an @-sign. */
      makeMention: function(name) {
        if (name[0] != '@') throw new Error('Bad nick for makeMention()');
        var node = $makeNode('span', 'mention mdl-chip' [
          ['span', 'mdl-chip__text', name]
        ]);
        var realName = name.substr(1);
        var hue = Instant.nick.hueHash(realName);
        node.style.color = Instant.nick.pingColor(realName);
        node.setAttribute('data-nick', realName);
        return node;
      },
      /* Make a nickname node for an anonymous user */
      makeAnonymous: function() {
        return $makeNode('span', 'nick anonymous mdl-chip' [
          ['span', 'mdl-chip__text', 'Anonymous']
        ]);
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
        return msgPane;
      },
      /* Detect links, emphasis, and smileys out of a flat string and render
       * those into a DOM node */
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
          Instant.input.moveTo(msgNode);
          if (inputWasFocused) {
            Instant.input.focus();
          } else {
            document.activeElement.blur();
          }
          Instant.pane.scrollIntoView(msgNode);
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
        var emote = /^\/me/.test(params.text);
        var text = (emote) ? params.text.substr(3) : params.text;
        text = text.trim();
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
      /* Get the message this is a comment to, i.e. the precedessor, or the
       * parent if none, or null if none at all */
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
      /* Get the node hosting the replies to the given message, or the
       * message itself if it's actually none at all */
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
      /* Scan an array of messages where to insert
       * If a matching node is (already) found and remove is true, it is
       * removed. */
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
           * have problems more grave than that. */
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
            throw new Error('Adding message with nonexistant parent');
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
      /* Integrate a message into a hierarchy */
      importMessage: function(message, root) {
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
      /* Update the indent string of the given message and all of its
       * children (if any; recursively) */
      updateIndents: function(message, indent) {
        if (! indent) {
          var par = Instant.message.getParentMessage(message);
          if (par) {
            indent = $sel('[data-key=indent]', par).textContent + '| ';
          } else {
            indent = '';
          }
        }
        $sel('[data-key=indent]', message).textContent = indent;
        indent += '| ';
        var children = Instant.message.getReplies(message);
        for (var i = 0; i < children.length; i++) {
          Instant.message.updateIndents(children[i], indent);
        }
      },
      /* Traverse a message tree and return the nodes that match the given
       * predicate.
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
         * toIdx is inclusive). */
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
      /* Check if the given fragment idenfitier is a valid message
       * identifier */
      checkFragment: function(url) {
        return (/^#message-.+$/.test(url));
      },
      /* Extract a message out of a fragment identifier or return it
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
      /* Return the message identified by this is, or undefined if none */
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
        /* For window title et al. */
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
        /* Smiley table */
        var SMILIES = {
          '+1' : '#008000', '-1' : '#c00000', '>:)': '#c00000',
          '>:]': '#c00000', '>:}': '#c00000', '>:D': '#c00000'
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
            re: new RegExp('<(((?!javascript:)[a-zA-Z]+://)?' +
              '([a-zA-Z0-9._~-]+@)?([a-zA-Z0-9.-]+)(:[0-9]+)?(/[^>]*)?)>'),
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
            re: /@[^.,:;!?()\s]+(?:\([^.,:;!?()\s]*\)[^.,:;!?()\s]*)*/,
            bef: /\W|^$/, aft: /\W|^$/,
            cb: function(m, out) {
              out.push(Instant.nick.makeMention(m[0]));
            }
          },
          { /* Smileys */
            name: 'smiley',
            re: new RegExp('[+-]1|>?[:;=][D)\\]}|{\\[/\\\\(cCSPoO3]|' +
              '[SD)\\\\/\\]}|{\\[(cCoO][:=]<?|\\^\\^|([\\^oO])[._]\\1|' +
              '>[._-]?<|;[_-];|\\._\\.|\\\\o/'),
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
              var style = node.style;
              style.fontStyle = (level & 1) ? 'italic' : 'normal';
              style.fontWeight = (level & 2) ? 'bold' : 'normal';
              style.fontVariant = (level & 4) ? 'small-caps' : 'normal';
              return node;
            },
            rem: function(stack, status) {
              stack.pop();
              status.emphLevel--;
            }
          },
          { /* Block monospace sigils */
            name: 'monoBlock',
            re: /(\n)?```(\n)?/,
            cb: function(m, out, status) {
              /* Block-level monospace marker */
              if (m[2] != null && status.grabbing == null) {
                /* Sigil introducing block */
                var st = (m[1] || '') + '```';
                var node = makeSigil(st, 'mono-block-before');
                var nl = makeNode('\n', 'hidden');
                out.push(node);
                out.push(nl);
                out.push({add: 'monoBlock', nodes: [node, nl]});
                status.grabbing = status.id;
              } else if (m[1] != null && status.grabbing != null) {
                /* Sigil terminating block */
                var st = '```' + (m[2] || '');
                var node = makeSigil(st, 'mono-block-after');
                var nl = makeNode('\n', 'hidden');
                out.push({rem: 'monoBlock', nodes: [node, nl]});
                out.push(nl);
                out.push(node);
                status.grabbing = null;
              } else {
                out.push(m[0]);
              }
            },
            add: function() {
              return makeNode(null, 'monospace monospace-block');
            }
          },
          { /* Quoted lines */
            name: 'quote',
            re: /^(>\s*)+/m,
            cb: function(m, out, status) {
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
          }
        ];
        return {
          /* Helper: Quickly create a DOM node */
          makeNode: makeNode,
          /* Helper: Quickly create a sigil node */
          makeSigil: makeSigil,
          /* Parse a message into a DOM node */
          parse: function(text) {
            /* Intermediate result; current index; text length; array of
             * matches; length of matchers; status object */
            var out = [], idx = 0, len = text.length, matches = [];
            var mlen = matchers.length, status = {grabbing: null};
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
              var minIdx = null, ib, ie;
              /* Which indices to iterate over */
              if (status.grabbing == null) {
                ib = 0;
                ie = mlen;
              } else {
                ib = status.grabbing;
                ie = ib + 1;
              }
              for (var i = ib; i < ie; i++) {
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
              matchers[minIdx].cb(matches[minIdx], out, status);
              idx = matches[minIdx].index + matches[minIdx][0].length;
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
            /* Assign actual emphasis levels (italic -> bold -> small-caps,
             * with combinations in between) */
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
    /* Match @-mentions with arbitrary text before
     * Keep in sync with mention matching in Instant.message. */
    var MENTION_BEFORE = new RegExp(
        ('(?:\\W|^)\\B@(%MC%*(?:\\(%MC%*\\)%MC%*)*' +
         '(?:\\(%MC%*)?)$').replace(/%MC%/g, '[^.,:;!?()\\s]'));
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
          /*if (name) {
            sizerNick.style.minWidth = '';
          } else {
            sizerNick.style.minWidth = '1em';
          }*/
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
        inputNode = $makeNode('div', 'input-bar mdl-shadow--2dp', [
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
        /* Remove marker class from old parent */
        var oldParent = Instant.message.getParentMessage(inputNode);
        if (oldParent) oldParent.classList.remove('input-host');
        /* Handle message parents */
        if (Instant.message.isMessage(parent)) {
          /* Add marker class to current parent */
          parent.classList.add('input-host');
          parent = Instant.message.makeReplies(parent);
        }
        /* Actually relocate the input */
        parent.appendChild(inputNode);
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
            Instant.input.jumpTo(Instant.message.getParent(message));
            return;
          }
        }
        Instant.input.jumpTo(message);
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
        Instant._fireListeners('input.edit', {text: inputMsg.value,
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
        Instant._fireListeners('input.keydown', {source: event});
        if (event.defaultPrevented) return;
        var inputMsg = $cls('input-message', inputNode);
        var text = inputMsg.value;
        if (event.keyCode == 13 && ! event.shiftKey) { // Return
          /* Send message! */
          /* Whether to clear the input bar */
          var clear = false;
          /* Allow event handlers to have a word */
          var evdata = {text: text, source: event};
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
          var res = Instant.userList.complete(m[1]);
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
        }
        if (text.indexOf('\n') == -1) {
          if (event.keyCode == 38) { // Up
            /* Special case: Get more logs */
            if (! navigate('up'))
              Instant.logs.pull.more();
          } else if (event.keyCode == 40) { // Down
            navigate('down');
          }
          if (! text) {
            if (event.keyCode == 37) { // Left
              navigate('left');
            } else if (event.keyCode == 39) { // Right
              navigate('right');
            }
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
  /* Sidebar handling
   * Reinterpreted into app bar (and right-hand-side drawer). */
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
        node = $makeNode('div',
            'appbar mdl-layout mdl-js-layout mdl-layout--fixed-header', [
          ['header', 'mdl-layout__header', [
            ['div', 'mdl-layout__header-row', [
              ['div', 'mdl-layout-title', [
                Instant.sidebar.roomName.init()
              ]],
              ['div', 'mdl-layout-spacer'],
              ['div', 'appbar-icon', [
                Instant.animation.spinner.init()
              ]],
              ['div', 'appbar-icon', [
                Instant.animation.onlineStatus.init()
              ]],
            ]]
          ]],
          ['div', 'mdl-layout__drawer', [
            ['div', 'ui-message-box'],
            Instant.settings.init(),
            Instant.userList.getNode()
          ]],
          ['main', 'mdl-layout__content']
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
        /*if (window.MutationObserver) {
          var obs = new MutationObserver(function(records, observer) {
            if (records.some(mutationInvalid)) return;
            Instant.sidebar.updateWidth();
          });
          obs.observe(wrapper, {childList: true, attributes: true,
            characterData: true, subtree: true,
            attributeFilter: ['class', 'style']});
        }*/
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
        Instant.pane.scrollIntoViewEx(child, $cls('mdl-layout__drawer',
                                                  node), 0);
      },
      /* Return the main DOM node */
      getNode: function() {
        return node;
      },
      /* Return the node for layout content */
      getContentNode: function() {
        return $cls('mdl-layout__content', node);
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
        menu = $makeNode('div', 'user-list-menu mdl-card mdl-shadow--2dp', [
          ['div', 'mdl-card__title'],
          ['div', 'mdl-card__actions', [
            ['button', 'action-ping mdl-button mdl-js-button ' +
              'mdl-js-ripple-effect', ['Insert ping']], ' ',
            ['button', 'action-pm mdl-button mdl-js-button ' +
              'mdl-js-ripple-effect', ['PM']]
          ]],
          ['div', 'mdl-card__menu', [
            ['button', 'action-close mdl-button mdl-button--icon ' +
                'mdl-js-button mdl-js-ripple-effect', [
              ['i', 'material-icons', 'close']
            ]]
          ]]
        ]);
        componentHandler.upgradeElement(menu);
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
        $cls('action-close', menu).addEventListener('click', function() {
          Instant.userList.showMenu(null);
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
          var t = children[m].textContent;
          var i = children[m].getAttribute('data-id');
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
          newNode = $makeNode('button', 'nick mdl-chip ', {id: 'user-' + id,
              'data-id': id}, [
            ['span', 'mdl-chip__text']
          ]);
          newWrapper = $makeNode('div', 'nick-box', [newNode]);
          newNode.addEventListener('click', toggleMenu);
          newNode.addEventListener('keydown', function(event) {
            // Return or Space
            if (event.keyCode == 13 || event.keyCode == 32) {
              toggleMenu();
              event.preventDefault();
            }
          });
          componentHandler.upgradeElement(newNode);
        }
        /* Apply new parameters to node */
        if (uuid) newNode.setAttribute('data-uuid', uuid);
        newNode.setAttribute('data-last-active', Date.now());
        newNode.setAttribute('data-nick', name);
        $sel('span', newNode).textContent = name;
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
      /* Update the collapsing state */
      _updateCollapse: function() {
        var newState = (document.documentElement.offsetWidth <= 400 ||
                        ! node.children.length);
        if (newState == lastCollapsed) return;
        lastCollapsed = newState;
        Instant.userList.collapse(newState);
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
          var now = Date.now();
          Array.prototype.forEach.call(node.children, function(el) {
            var nick = el.firstElementChild;
            var time = nick.getAttribute('data-last-active') - now;
            if (time < -300000) time = -300000;
            nick.style.webkitAnimationDelay = time + 'ms';
            nick.style.animationDelay = time + 'ms';
          });
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
        /* Waiting for replies to arrive */
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
            if (window.logInstantLogPulling === undefined)
              window.logInstantLogPulling = (!! Instant.query.get('verbose'));
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
            var reply = null, replyTo = msg.from;
            /* Check message */
            var data = msg.data;
            switch (data.type) {
              case 'log-query': /* Someone asks about our logs */
                /* FIXME: Does include "live" messages as logs. */
                reply = Instant.logs.bounds();
                reply.type = 'log-info';
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
                  console.debug("[LogPull]", "Got advertisement", data);
                break;
              case 'log-request': /* Someone requests logs from us */
                reply = {type: 'log'};
                if (data.from != null) reply.from = data.from;
                if (data.to != null) reply.to = data.to;
                if (data.length != null) reply.length = data.length;
                if (data.key != null) reply.key = data.key;
                reply.data = Instant.logs.get(data.from, data.to,
                                              data.length);
                reply.uuids = Instant.logs.queryUUID(
                  reply.data.map(function(e) {
                    return e.from;
                  }));
                break;
              case 'log': /* Someone delivers logs to us */
                var before = null, after = null;
                if (data.data) {
                  /* Actually merge logs */
                  var added = Instant.logs.merge(data.data, true);
                  /* Merge UUID-s */
                  Instant.logs.mergeUUID(data.uuids);
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
                  /* Check for offscreen messages */
                  Instant.animation.offscreen.update(added.map(
                    function(key) {
                      return Instant.message.get(key);
                    }
                  ));
                  /* Avoid stale node references */
                  Instant.animation.offscreen._updateMessages();
                  /* Detect earliest and latest message */
                  data.data.forEach(function(el) {
                    if (! before || el.id < before) before = el.id;
                    if (! after || el.id > after) after = el.id;
                  });
                }
                /* Call finishing handler */
                var key = data.key;
                before = (key == 'initial' || key == 'before') ?
                  before || true : null;
                after = (key == 'initial' || key == 'after') ?
                  after || true : null;
                Instant.logs.pull._done(before, after);
                Instant._fireListeners('logs.new', {message: msg,
                  data: data});
                break;
              case 'delete':
                /* Nuh! */
                break;
              default:
                throw new Error('Bad message supplied to _onmessage().');
            }
            /* Send reply */
            if (reply != null) Instant.connection.send(replyTo, reply);
          },
          /* Done with loading logs for whatever reasons */
          _done: function(before, after) {
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
  /* Private messages */
  Instant.privmsg = function() {
    /* UI messages (reading/writing) */
    var msgRead = null, msgEdit = null;
    /* Popup arrays (reading/writing) */
    var popupsRead = [], popupsEdit = [];
    return {
      /* Initialize submodule */
      init: function() {
        msgRead = Instant.sidebar.makeMessage({
          content: 'Private messages',
          color: Instant.notifications.COLORS.privmsg,
          onclick: Instant.privmsg.showRead});
        msgEdit = Instant.sidebar.makeMessage({
          content: 'Private message drafts',
          color: Instant.notifications.COLORS.privmsg,
          onclick: Instant.privmsg.showWrite});
      },
      /* Update notification state */
      _update: function() {
        if (! popupsRead.length) {
          Instant.sidebar.hideMessage(msgRead);
        } else {
          var count = popupsRead.length, unread = 0;
          for (var i = 0; i < count; i++) {
            if (popupsRead[i].getAttribute('data-new')) unread++;
          }
          var text;
          if (unread == 0) {
            text = 'Private messages (' + count + ')';
          } else if (count == unread) {
            text = 'Private messages (' + unread + '!!)';
          } else {
            text = ('Private messages (' + (count - unread) + ' + ' +
              unread + '!!)');
          }
          msgRead.textContent = text;
          Instant.sidebar.showMessage(msgRead);
        }
        if (! popupsEdit.length) {
          Instant.sidebar.hideMessage(msgEdit);
        } else {
          var text;
          if (popupsEdit.length == 1) {
            text = 'Private message draft';
          } else {
            text = 'Private message drafts (' + popupsEdit.length + ')';
          }
          msgEdit.textContent = text;
          Instant.sidebar.showMessage(msgEdit);
        }
        Instant.title.update();
      },
      /* Show the reading popups */
      showRead: function() {
        var update = false;
        popupsRead.forEach(function(popup) {
          if (! Instant.popups.isShown(popup)) {
            popup.removeAttribute('data-new');
            Instant.popups.add(popup);
            update = true;
          }
        });
        if (focus) Instant.privmsg._update();
      },
      /* Show the writing popups */
      showWrite: function() {
        popupsEdit.forEach(function(popup) {
          if (! Instant.popups.isShown(popup))
            Instant.popups.add(popup);
        });
      },
      /* Start writing a message to uid */
      write: function(uid, nick, text) {
        var nickNode;
        if (nick === undefined) {
          nickNode = Instant.userList.get(uid);
          if (nickNode)
            nick = nickNode.getAttribute('data-nick');
        }
        if (nick == null) {
          nickNode = Instant.nick.makeAnonymous();
        } else {
          nickNode = Instant.nick.makeNode(nick);
        }
        var popup = Instant.popups.make({title: 'Private message editor',
          className: 'pm-popup',
          content: $makeFrag(['div', 'pm-header', [
            ['strong', null, 'To: '],
            ['span', [nickNode, ' ', ['i', ['(user ID ',
              ['span', 'monospace', uid], ')']]]],
          ]], ['hr'], ['textarea', 'pm-editor']),
          buttons: [
            {text: 'Finish later', onclick: function() {
              Instant.popups.del(popup);
            }, className: 'first'},
            {text: 'Delete', color: '#c00000', onclick: function() {
              Instant.privmsg._remove(popup);
            }},
            {text: 'Send', color: '#008000', onclick: function() {
              Instant.privmsg._send(popup);
            }}
          ],
          focusSel: '.pm-editor'});
        popup.setAttribute('data-recipient', uid);
        var editor = $cls('pm-editor', popup);
        if (text) editor.value = text;
        popupsEdit.push(popup);
        Instant.popups.add(popup);
        Instant.privmsg._update();
        $cls('pm-editor', popup).focus();
        editor.setSelectionRange(editor.value.length, editor.value.length);
      },
      /* Remove a PM draft or reader */
      _remove: function(popup) {
        var idx = popupsRead.indexOf(popup);
        if (idx != -1) popupsRead.splice(idx, 1);
        idx = popupsEdit.indexOf(popup);
        if (idx != -1) popupsEdit.splice(idx, 1);
        Instant.popups.del(popup);
        Instant.privmsg._update();
      },
      /* Send a PM draft */
      _send: function(popup) {
        var recipient = popup.getAttribute('data-recipient');
        var text = $cls('pm-editor', popup).value;
        Instant.connection.sendUnicast(recipient, {type: 'privmsg',
          nick: Instant.identity.nick, text: text});
        Instant.privmsg._remove(popup);
      },
      /* Incoming remote message */
      _onmessage: function(msg) {
        var data = msg.data;
        if (data.type != 'privmsg') return;
        var nickNode;
        if (data.nick == null) {
          nickNode = Instant.nick.makeAnonymous();
        } else {
          nickNode = Instant.nick.makeNode(data.nick);
        }
        var msgNode = Instant.message.parseContent(data.text);
        var popup = Instant.popups.make({title: 'Private message',
          className: 'pm-popup',
          content: $makeFrag(['div', 'pm-header', [
            ['strong', null, 'From: '], ['span', [
              nickNode, ' ',
              ['i', ['(user ID ', ['span', 'monospace', msg.from], ')']]
            ]]
          ]], ['div', 'pm-header', [
            ['strong', null, 'Date: '], ['span', [
              formatDate(new Date(msg.timestamp))
            ]]
          ]], ['hr'], msgNode),
          buttons: [
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
              Instant.privmsg.write(msg.from, nick, text);
            }},
            {text: 'Reply', color: '#008000', onclick: function() {
              var nick = data.nick;
              if (nick == undefined) nick = null;
              Instant.privmsg.write(msg.from, nick);
            }}
          ],
          focusSel: '.first'});
        popup.setAttribute('data-new', 'yes');
        popupsRead.push(popup);
        Instant.privmsg._update();
        Instant.notifications.submitNew({level: 'privmsg',
          text: 'You have a new private message.',
          onclick: function() {
            popup.removeAttribute('data-new');
            Instant.popups.add(popup);
            Instant.privmsg._update();
          }
        });
      },
      /* Return the amount of unread private messages */
      countUnread: function() {
        var count = 0;
        popupsRead.forEach(function(popup) {
          if (popup.getAttribute('data-new')) count++;
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
          Instant.animation.offscreen.checkAll();
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
          Instant.animation.offscreen.checkAll();
        });
        window.addEventListener('hashchange', updateHash);
        updateHash();
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
      /* Offscreen message (alert) management */
      offscreen: function() {
        /* Unread messages above/below */
        var unreadAbove = null, unreadBelow = null;
        /* Unread messages with @-mentions of self */
        var mentionAbove = null, mentionBelow = null;
        /* The nodes containing the alerts */
        var aboveNode = null, belowNode = null;
        /* Scan the message tree in document order for a message for which
         * filter returns a true value; falsy messages are returned
         * unconditionally. */
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
          init: function(container) {
            /* Handler for events */
            function handleEvent(event, node) {
              var msg = Instant.message.forFragment(node.hash);
              if (msg) {
                Instant.animation.goToMessage(msg);
                event.preventDefault();
              }
              /* Allow scanning unread messages quickly by keeping
               * focus on the node. */
              if (event.type == 'keydown') node.focus();
            }
            /* Extract the alerts themself */
            aboveNode = $cls('alert-above', container);
            belowNode = $cls('alert-below', container);
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
            Instant.animation.offscreen._update();
          },
          /* Mark multiple messages as offscreen (or not) */
          update: function(msgs) {
            msgs.forEach(function(m) {
              Instant.animation.offscreen.check(m);
            });
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
          checkAll: function(msg) {
            if (Instant.title.isBlurred()) return;
            if (! unreadAbove && ! unreadBelow) return;
            Instant.pane.getVisible(messageBox).forEach(function(msg) {
              Instant.animation.offscreen.clear(msg);
            });
          },
          /* Mark the message as offscreen */
          set: function(msg) {
            msg.classList.add('offscreen');
            if (isUnread(msg)) {
              var docCmp = Instant.message.documentCmp.bind(Instant.message);
              var icmp = docCmp(msg, Instant.input.getNode());
              if (icmp < 0 && (! unreadAbove ||
                  docCmp(msg, unreadAbove) > 0))
                unreadAbove = msg;
              if (icmp > 0 && (! unreadBelow ||
                  docCmp(msg, unreadBelow) < 0))
                unreadBelow = msg;
              if (msg.classList.contains('ping')) {
                if (icmp < 0 && (! mentionAbove ||
                    docCmp(msg, mentionAbove) > 0))
                  mentionAbove = msg;
                if (icmp > 0 && (! mentionBelow ||
                    docCmp(msg, mentionBelow) < 0))
                  mentionBelow = msg;
              }
              Instant.animation.offscreen._update();
            }
          },
          /* Remove the offscreen mark */
          clear: function(msg) {
            msg.classList.remove('offscreen');
            if (msg == unreadAbove || msg == unreadBelow ||
                msg == mentionAbove || msg == mentionBelow) {
              var im = Instant.message;
              var prec = im.getDocumentPrecedessor.bind(im);
              var succ = im.getDocumentSuccessor.bind(im);
              if (msg == unreadAbove)
                unreadAbove = scanMessages(msg, isUnread, prec);
              if (msg == unreadBelow)
                unreadBelow = scanMessages(msg, isUnread, succ);
              if (msg == mentionAbove)
                mentionAbove = scanMessages(msg, isUnreadMention, prec);
              if (msg == mentionBelow)
                mentionBelow = scanMessages(msg, isUnreadMention, succ);
              Instant.animation.offscreen._update();
            }
          },
          /* Update the attached nodes */
          _update: function() {
            if (aboveNode) {
              if (mentionAbove) {
                aboveNode.classList.add('ping');
                aboveNode.classList.add('visible');
              } else if (unreadAbove) {
                aboveNode.classList.remove('ping');
                aboveNode.classList.add('visible');
              } else {
                aboveNode.classList.remove('ping');
                aboveNode.classList.remove('visible');
              }
              aboveNode.href = '#' + ((unreadAbove) ? unreadAbove.id : '');
            }
            if (belowNode) {
              if (mentionBelow) {
                belowNode.classList.add('ping');
                belowNode.classList.add('visible');
              } else if (unreadBelow) {
                belowNode.classList.remove('ping');
                belowNode.classList.add('visible');
              } else {
                belowNode.classList.remove('ping');
                belowNode.classList.remove('visible');
              }
              belowNode.href = '#' + ((unreadBelow) ? unreadBelow.id : '');
            }
          },
          /* Update the message nodes referenced if they could have been
           * replaced */
          _updateMessages: function() {
            if (unreadAbove != null) unreadAbove = $id(unreadAbove.id);
            if (unreadBelow != null) unreadBelow = $id(unreadBelow.id);
            if (mentionAbove != null) mentionAbove = $id(mentionAbove.id);
            if (mentionBelow != null) mentionBelow = $id(mentionBelow.id);
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
        function xradio(name, value, desc, ext, checked) {
          var input = $makeNode('input', 'mdl-radio__button', {type: 'radio',
            name: name, value: value, id: name + '-' + value + '-input'});
          if (checked) input.checked = true;
          var ret = $makeNode('label', 'mdl-radio mdl-js-radio ' +
              'mdl-js-ripple-effect', {for: input.id}, [
            input,
            ['span', 'mdl-radio__label ' + name + '-' + value, desc]
          ]);
          if (ext) ret.classList.add('more-content');
          return ret;
        }
        function xcheckbox(name, desc, checked, title) {
          var input = $makeNode('input', 'mdl-switch__input',
            {type: 'checkbox', name: name, id: name});
          if (checked) input.checked = true;
          return $makeNode('label', 'mdl-switch mdl-js-switch ' +
              'mdl-js-ripple-effect', {for: input.id}, [
            input,
            ['span', 'mdl-switch__label', desc]
          ]);
        }
        wrapperNode = $makeNode('div', 'settings-wrapper mdl-card', [
          ['div', 'mdl-card__title mdl-card--border', [
            ['h2', 'settings mdl-button mdl-js-button ' +
                'mdl-js-ripple-effect', [
              'Settings',
              ['i', 'icon material-icons', 'expand_more']
            ]]
          ]],
          ['form', 'settings-content mdl-card__supporting-text ' +
              'mdl-card--border', [
            ['div', 'settings-theme', [
              ['h3', ['Theme:']],
              xradio('theme', 'bright', 'Bright', false, true),
              xradio('theme', 'dark', 'Dark'),
              xradio('theme', 'verydark', 'Very dark')
            ]],
            ['hr'],
            ['div', 'settings-notifications', [
              ['h3', ['Notifications: ',
                ['a', 'more-link', {href: '#'}, '(more)']
              ]],
              xradio('notifies', 'none', 'None', false, true),
              xradio('notifies', 'privmsg', 'On private messages', true),
              xradio('notifies', 'ping', 'When pinged'),
              xradio('notifies', 'update', 'On updates', true),
              xradio('notifies', 'reply', 'When replied to'),
              xradio('notifies', 'activity', 'On activity'),
              xradio('notifies', 'disconnect', 'On disconnects', true)
            ]],
            ['hr'],
            ['div', 'settings-nodisturb', [
              xcheckbox('no-disturb', 'Do not disturb', false, 'Void ' +
                'notifications that are below your chosen level')
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
        var icon = $sel('.settings .icon', wrapperNode);
        if (vis == null) {
          wrapperNode.classList.toggle('visible');
        } else if (vis) {
          wrapperNode.classList.add('visible');
        } else {
          wrapperNode.classList.remove('visible');
        }
        var visible = Instant.settings.isVisible();
        if (visible) {
          icon.textContent = 'expand_less';
        } else {
          icon.textContent = 'expand_more';
        }
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
        /* The currently pending desktop notification */
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
              var ret = new Notification(title, {body: body,
                icon: icon});
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
      add: function(node) {
        stack.appendChild(node);
        wrapper.style.display = 'block';
        Instant.util.adjustScrollbar($cls('close-all', wrapper),
                                     $cls('popups-content', wrapper));
        Instant.popups.focus(node);
      },
      /* Remove a node from the popup stack */
      del: function(node) {
        var next = node.nextElementSibling || node.previousElementSibling;
        stack.removeChild(node);
        if (! stack.children.length) {
          wrapper.style.display = '';
          Instant.input.focus();
        } else {
          Instant.util.adjustScrollbar($cls('close-all', wrapper),
                                       $cls('popups-content', wrapper));
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
            co && ['a', 'popup-button popup-collapse', {href: '#'}, [
              ['img', {src: collapseURL}]
            ]],
            cl && ['span', 'popup-title-sep'],
            cl && ['a', 'popup-button popup-close', {href: '#'}, [
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
            Instant.popups.del(ret);
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
          $cls('popup-close', node).focus();
        }
      },
      /* Returnt the internal node containing the popups */
      getNode: function() {
        return wrapper;
      },
      /* Return the internal node holding the actual popups */
      getPopupNode: function() {
        return stack;
      }
    };
  }();
  /* Query string parameters
   * The backend does not parse them (as of now), and even if it did not,
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
    /* Utility function */
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
    /* Actual data */
    var data = {};
    return {
      /* Initialize submodule */
      init: function() {
        Instant.storage.load();
      },
      /* Get the value associated with the given key, or undefined */
      get: function(key) {
        return data[key];
      },
      /* Assign the value to the key and save the results (asynchronously) */
      set: function(key, value) {
        var oldValue = data[key];
        data[key] = value;
        Instant.storage._save();
        Instant._fireListeners('storage.set', {key: key, value: value,
          oldValue: oldValue});
      },
      /* Remove the given key and save the results (asynchronously) */
      del: function(key) {
        var oldValue = data[key];
        delete data[key];
        Instant.storage._save();
        Instant._fireListeners('storage.del', {key: key,
          oldValue: oldValue});
      },
      /* Remove all keys and save the results (still asynchronously) */
      clear: function() {
        Instant.storage._clear(),
        Instant.storage._save();
        Instant._fireListeners('storage.clear');
      },
      /* Reset the internal storage *without* saving automatically */
      _clear: function() {
        data = {};
      },
      /* Read the underlying storage backends and merge the results into the
       * data array. */
      load: function() {
        function embed(res) {
          if (! res) return;
          for (var key in res) {
            if (! res.hasOwnProperty(key)) continue;
            data[key] = res[key];
          }
        }
        if (window.localStorage) {
          embed(thaw(localStorage.getItem('instant-data')));
          if (Instant.roomName) {
            var d = thaw(localStorage.getItem('instant-data-rooms'));
            if (d) embed(d[Instant.roomName]);
          }
        }
        if (window.sessionStorage) {
          embed(thaw(sessionStorage.getItem('instant-data')));
        }
        Instant._fireListeners('storage.load');
      },
      /* Serialize the current data to the backends
       * This version does not run event handlers. */
      _save: function() {
        var encoded = JSON.stringify(data);
        if (window.sessionStorage) {
          sessionStorage.setItem('instant-data', encoded);
        }
        if (window.localStorage) {
          localStorage.setItem('instant-data', encoded);
          if (Instant.roomName) {
            var rd = thaw(localStorage.getItem('instant-data-rooms')) || {};
            rd[Instant.roomName] = data;
            localStorage.setItem('instant-data-rooms', JSON.stringify(rd));
          }
        }
      },
      /* Serialize the current data to the backends */
      save: function() {
        Instant._fireListeners('storage.save');
        Instant.storage._save();
      },
      /* Obtain a reference to the raw data storage object
       * NOTE that the reference might silently become invalid, and remember
       *      to save regularly. */
      getData: function() {
        return data;
      },
    };
  }();
  /* Miscellaneous utilities */
  Instant.util = function() {
    return {
      /* Left-pad a string */
      leftpad: leftpad,
      /* Format a date-time nicely */
      formatDate: formatDate,
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
      this._libs = null;
      this._synclibs = null;
    }
    Plugin.prototype = {
      /* Commence loading the plugin in question, and return a Promise of the
       * loading result */
      _load: function() {
        var self = this;
        var styles = this.options.styles || [];
        this._styles = styles.map(Instant.plugins.addStylesheet);
        var scripts = this.options.scripts || [];
        this._scripts = scripts.map(Instant.plugins.addScript);
        var deps = this.options.deps || [];
        var depsprom = Promise.all(deps.map(Instant.plugins.getPluginAsync));
        var libs = this.options.libs || [];
        this._libs = [];
        var libsprom = Promise.all(libs.map(function(url, idx) {
          return new Promise(function(resolve, reject) {
            var node = document.createElement('script');
            node.type = 'application/javascript';
            node.onload = function() {
              resolve(node);
            };
            node.onerror = function(event) {
              reject(event);
            };
            node.src = url;
            self._libs[idx] = node;
            document.head.appendChild(node);
          });
        }));
        var preprom = Promise.all([depsprom, libsprom]);
        var synclibs = this.options.synclibs || [];
        this._synclibs = [];
        var pending = synclibs.map(function(url, idx) {
          return Instant.plugins.loadFile(url).then(function(req) {
            return preprom.then(function() {
              return $evalIn.call(self, req.response, false);
            }).then(function(res) {
              self._synclibs[idx] = res;
            });
          });
        });
        pending.push(preprom);
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
      /* Return a Promise of an <img> element with data from url
       * init is handled similarly to loadFile; also similarly to loadFile,
       * on error, the promise is rejected to an Error object containing the
       * Image the download was performed on as "image". Additionally,
       * the "event" property of the rejection value holds the error event
       * that led the promise to rejection. */
      loadImage: function(url, init) {
        return new Promise(function(resolve, reject) {
          var img = document.createElement('img');
          img.onload = function() {
            resolve(img);
          }
          img.onerror = function(event) {
            var err = new Error('Image download failed');
            err.image = img;
            err.event = event;
            reject(err);
          }
          if (init) init(img);
          img.src = url;
        });
      },
      /* Return a Promise of the contents of the file at url */
      loadContents: function(url) {
        return Instant.plugins.loadFile(url).then(function(x) {
          return x.response;
        });
      },
      /* Add a stylesheet <link> referencing the given URL to the <head>
       * If type is false, "text/css" is assigned to the link's "type"
       * property. */
      addStylesheet: function(url, type) {
        var style = document.createElement('link');
        style.rel = 'stylesheet';
        style.type = type || 'text/css';
        style.href = url;
        document.head.appendChild(style);
        return style;
      },
      /* Return a Promise of a <style> element with content from url
       * type is handled exactly like the corresponding parameter of
       * addStylesheet. */
      loadStylesheet: function(url, type) {
        return Instant.plugins.loadFile(url).then(function(req) {
          var el = document.createElement('style');
          el.type = type || 'text/css';
          el.innerHTML = req.response;
          return el;
        });
      },
      /* Add a <script> element to the <head>
       * type is handled analogously to addStylesheet, but the default is
       * application/javascript. */
      addScript: function(url, type) {
        var script = document.createElement('script');
        script.type = type || 'application/javascript';
        script.src = url;
        document.head.appendChild(script);
        return script;
      },
      /* Return a Promise of a <script> element with content from url */
      loadScript: function(url, type) {
        return Instant.plugins.loadFile(url).then(function(req) {
          var el = document.createElement('script');
          el.type = type || 'application/javascript';
          el.innerHTML = req.response;
          return el;
        });
      },
      /* Load the given plugin asynchronously and return a Promise of it
       * options contains the following properties (all optional):
       * deps    : An array of names of plugins this plugin is dependent on.
       *           All dependencies must be loaded before this plugin is
       *           initialized.
       * styles  : An array of stylesheet URL-s to be fetched and to added
       *           to the document (asynchronously).
       * scripts : An array of JavaScript file URL-s to be loaded and run
       *           (asynchronously). The scripts are run in the global
       *           context.
       * libs    : An array of JavaScript file URL-s to be loaded and run
       *           (in the global scope) before the plugin is initialized.
       * synclibs: An array of JavaScript file URL-s to be loaded and
       *           executed (in isolated scopes, with the this object
       *           pointing to the plugin) before the plugin is initialized.
       *           References to objects that should persist must be assigned
       *           to the this object explicitly.
       *           Dependencies are already initialized when these are run.
       * main    : Plugin initializer function. All synclibs have been run,
       *           and their results are available in the this object. The
       *           return value is assigned to the "data" property of the
       *           plugin object; if it is thenable, it is resolved first.
       * Execution order:
       * - All resources are fetched asynchronously in no particular order
       *   (and may have been cached).
       * - styles and scripts are fully asynchronous and do not affect the
       *   loading of the plugin that requested them.
       * - synclibs are not run before all deps and libs are initialized and
       *   loaded, respectively.
       * - The plugin is not initialized until the preconditions for synclibs
       *   are met, and they (if any) have been run. */
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
        if (ret == null) throw new Error('Plugin ' +  name + ' not loaded yet!');
        if (! ret) throw new Error('No such plugin: ' + name);
        return ret;
      },
      /* Return a Promise of the object of the named plugin */
      getPluginAsync: function(name) {
        var ret = pendingPlugins[name];
        if (! ret) throw new Error('No such plugin: ' + name);
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
    this.instant = Instant;
    this.type = type;
    for (var key in data) {
      if (! data.hasOwnProperty(key)) continue;
      this[key] = data[key];
    }
  }
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
    Instant.animation.offscreen.init(
      $cls('alert-container', Instant.input.getNode()));
    Instant.popups.init();
    Instant.privmsg.init();
    Instant.sidebar.getContentNode().appendChild(
      Instant.message.getMessagePane());
    main.appendChild(Instant.sidebar.getNode());
    main.appendChild(Instant.popups.getNode());
    Instant.pane.main.init(main);
    Instant._fireListeners('init.late');
    Instant.settings.load();
    Instant.connection.init();
    /*repeat(function() {
      Instant.util.adjustScrollbar($cls('sidebar', main),
                                   $cls('message-pane', main));
    }, 1000);*/
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
  wrapper.style.boxShadow = '0 0 30px #808080';
  $id('loading-message').style.display = 'none';
  /* Display splash messages */
  var m = $id('splash-messages');
  Instant.message.addReply({id: 'loading-0-wait', nick: 'Loading',
    text: 'Please wait...'}, m);
  var isIE = /*@cc_on!@*/0;
  if (isIE) Instant.message.addReply({id: 'loading-1-ie', nick: 'Doom',
    text: '/me awaits IE users...'}, m);
  /* Hide greeter */
  if (! Instant.roomName) {
    /* Nothing is going to hide it, so we have to. */
    Instant.animation.greeter.hide();
  }
  /* Show main element */
  main.style.opacity = '1';
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
  /* HACK: My browser scrolls down randomly at page load if the viewport is
   *       too small. */
  setTimeout(function() {
    document.documentElement.scrollTop = 0;
  }, 0);
  /* Fire up Instant! */
  Instant.init(main, wrapper, null);
  Instant.input.focus();
}

/* It is quite conceivable this could be run after the document is ready.
 * Citation: Look at the 5000 lines above.
 * Second citation: The script is deferred. */
$onload(init);
