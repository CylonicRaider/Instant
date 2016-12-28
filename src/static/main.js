
/* Strict mode FTW! */
'use strict';

/* Utilities */
function $hypot(dx, dy) {
  return Math.sqrt(dx * dx + dy * dy);
}

function $id(id, elem) {
  return (elem || document).getElementById(id);
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

/* Early preparation; define most of the functionality */
this.Instant = function() {
  /* Locale-agnostic abbreviated month name table */
  var MONTH_NAMES = { 1: 'Jan',  2: 'Feb',  3: 'Mar',  4: 'Apr',
                      5: 'May',  6: 'Jun',  7: 'Jul',  8: 'Aug',
                      9: 'Sep', 10: 'Oct', 11: 'Nov', 12: 'Dec'};
  /* Upcoming return value */
  var Instant = {};
  /* Prepare connection */
  var roomPaths = new RegExp('^(?:/([a-zA-Z0-9-]+))?(\/room\/' +
    '([a-zA-Z](?:[a-zA-Z0-9_-]*[a-zA-Z0-9])?))\/?');
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
  /* Logging handlers */
  var console = {
    /* Log a debugging message */
    log: function() {
      if (window.console) {
        window.console['log'].apply(window.console, arguments);
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
    /* Node to add the visible class to when there is an update */
    var updateNode = null;
    /* Node to show similarly to updateNode when the client is outdated */
    var refreshNode = null;
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
      /* Initialize the update notification and refresh request node */
      init: function(unode, rnode) {
        updateNode = unode;
        refreshNode = rnode;
      },
      /* Initialize the identity from the data part of a
       * server-side message */
      initFields: function(data) {
        if (Instant.connection.isURLOverridden()) {
          /* NOP */
        } else if ((Instant.identity.serverVersion != null &&
             Instant.identity.serverVersion != data.version ||
             Instant.identity.serverRevision != null &&
             Instant.identity.serverRevision != data.revision) &&
            updateNode) {
          updateNode.classList.add('visible');
          Instant.title.setUpdateAvailable(true);
        } else if (window._instantVersion_ &&
            (_instantVersion_.version != data.version ||
             _instantVersion_.revision != data.revision)) {
          refreshNode.classList.add('visible');
          Instant.title.setUpdateAvailable(true);
        }
        Instant.identity.id = data.id;
        Instant.identity.uuid = data.uuid;
        Instant.identity.serverVersion = data.version;
        Instant.identity.serverRevision = data.revision;
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
    /* The connection status widget */
    var connStatus = null;
    /* Sequence ID of outgoing messages */
    var seqid = null;
    /* The actual WebSocket */
    var ws = null;
    /* Whether the WebSocket is connected */
    var connected = false;
    /* Whether the default URL was overridden */
    var overridden = false;
    /* Send pings every thirty seconds */
    setInterval(function() {
      if (Instant && Instant.connection && Instant.connection.isConnected())
        Instant.connection.sendSeq({type: 'ping'});
    }, 30000);
    /* Debugging hook */
    if (window.logInstantMessages === undefined)
      window.logInstantMessages = false;
    return {
      /* Initialize the submodule, by installing the connection status
       * widget */
      init: function(statusNode) {
        connStatus = statusNode;
        /* Apply URL override */
        var override = Instant.query.get('connect');
        if (override) {
          Instant.connectionURL = override;
          overridden = true;
        }
        /* Connect */
        Instant.connection.reconnect();
        /* Force update of widget */
        if (ws && ws.readyState == WebSocket.OPEN) {
          Instant.connection._connected();
        }
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
        /* Update flag */
        connected = true;
        if (connStatus) {
          /* Update status widget */
          connStatus.classList.remove('broken');
          connStatus.classList.add('connected');
          connStatus.title = 'Connected';
        }
      },
      /* Handle a message */
      _message: function(event) {
        /* Implement debugging hook */
        if (window.logInstantMessages)
          console.debug('Received:', event, event.data);
        /* Raw message handler */
        if (Instant.connection.onRawMessage) {
          Instant.connection.onRawMessage(event);
          if (event.defaultPrevented) return;
        }
        /* Extract message data */
        var msg;
        try {
          msg = JSON.parse(event.data);
        } catch (e) {
          console.warn(e);
          return;
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
          case 'joined':
            /* New user joined (might be ourself) */
            Instant.userList.add(msg.data.id, '', msg.data.uuid);
            Instant.logs.addUUID(msg.data.id, msg.data.uuid);
            break;
          case 'left':
            /* User left */
            Instant.userList.remove(msg.data.id);
            Instant.logs.pull._onmessage(msg);
            break;
          case 'unicast': /* Someone sent a message directly to us */
          case 'broadcast': /* Someone sent a message to everyone */
            var data = msg.data || {};
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
              default:
                console.warn('Unknown client message:', data);
                break;
            }
            break;
          default:
            console.warn('Unknown server message:', msg);
            break;
        }
      },
      /* Handle a dead connection */
      _closed: function(event) {
        /* Update flag */
        connected = false;
        /* Update status widget */
        if (connStatus && ws != null) {
          /* Update status widget */
          connStatus.classList.remove('connected');
          connStatus.classList.add('broken');
          connStatus.title = 'Broken';
        }
        /* Inform logs */
        Instant.logs.pull._disconnected();
        /* Re-connect */
        if (event)
          Instant.connection.reconnect();
      },
      /* Handle an auxillary error */
      _error: function(event) {
        /* Update flag */
        connected = false;
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
      /* Check whether the client is currently connected */
      isConnected: function() {
        return connected;
      },
      /* Check whether the default connection URL was overridden */
      isURLOverridden: function() {
        return overridden;
      },
      /* Event handler for WebSocket messages */
      onRawMessage: null
    };
  }();
  /* Connect ASAP
   * Or not, since we're *much* quicker than Heim, and the user should be
   * entertained by the greeting animation for a few seconds. */
  /*if (Instant.connectionURL)
    Instant.connection.connect();*/
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
      /* Generate a DOM node carrying the nick */
      makeNode: function(name) {
        var node = document.createElement('span');
        var hue = Instant.nick.hueHash(name);
        node.className = 'nick';
        node.textContent = name;
        node.style.backgroundColor = 'hsl(' + hue + ', 75%, 80%)';
        node.setAttribute('data-nick', name);
        return node;
      },
      /* Generate a DOM node carrying a mention of the nick
       * name is the nickname with an @-sign. */
      makeMention: function(name) {
        if (name[0] != '@') throw new Error('Bad nick for makeMention()');
        var node = document.createElement('span');
        var hue = Instant.nick.hueHash(name.substr(1));
        node.className = 'mention';
        node.textContent = name;
        node.style.color = 'hsl(' + hue + ', 75%, 40%)';
        node.setAttribute('data-nick', name.substr(1));
        return node;
      }
    };
  }();
  /* Message handling */
  Instant.message = function() {
    /* Message ID -> DOM node */
    var messages = {};
    var fakeMessages = {};
    /* Pixel distance that differentiates a click from a drag */
    var DRAG_THRESHOLD = 4;
    return {
      /* Message parsing -- has an own namespace to avoid pollution */
      parser: function() {
        /* Smiley table */
        var SMILIES = {'+1': '#008000', '-1': '#c00000',
          '>:)': '#c00000', '>:]': '#c00000', '>:}': '#c00000',
          '>:D': '#c00000'};
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
              '[SD)\\\\/\\]}|{\\[(cCoO][:=]<?|\\^\\^|\\\\o/'),
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
          }
        ];
        return {
          /* Helper: Quickly create a DOM node */
          makeNode: makeNode,
          /* Helper: Quickly create a sigil node */
          makeSigil: makeSigil,
          /* Parse a message into a sequence of DOM nodes */
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
              /* Filter such that only user-made objects remain */
              if (typeof e != 'object' || e.nodeType !== undefined) continue;
              /* Add or remove emphasis, respectively */
              if (e.add) {
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
            for (var i = 0; i < stack.length; i++) {
              declassify(stack[i]);
            }
            /* Assign actual emphasis levels (italic -> bold -> small-caps,
             * with combinations in between) */
            stack = [makeNode(null, 'message-text')];
            status = {};
            for (var i = 0; i < out.length; i++) {
              var e = out[i], top = stack[stack.length - 1];
              /* Drain non-metadata parts into the current node */
              if (typeof e == 'string') {
                top.appendChild(document.createTextNode(e));
              } else if (e.nodeType !== undefined) {
                top.appendChild(e);
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
          }
        };
      }(),
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
        $sel('.line', msgNode).addEventListener('mousedown', function(evt) {
          if (evt.button != 0) return;
          clickPos = [evt.clientX, evt.clientY];
          inputWasFocused = Instant.input.isFocused();
        });
        /* Clicking to a messages moves to it */
        $sel('.line', msgNode).addEventListener('click', function(evt) {
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
        $sel('.permalink', msgNode).addEventListener('click', function(evt) {
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
        var msgNode = document.createElement('div');
        msgNode.id = 'message-' + id;
        msgNode.className = 'message message-fake';
        msgNode.setAttribute('data-id', id);
        /* Populate contents */
        msgNode.innerHTML = '<div class="line">' +
          '<time><a class="permalink">N/A</a></time>' +
          '<span class="nick-wrapper">' +
            '<span class="hidden" data-key="indent"></span>' +
            '<span class="nick">...</span>' +
          '</span></div>';
        /* Populate inner attributes */
        $sel('.line', msgNode).title = 'Message absent or not loaded (yet)';
        $sel('.permalink', msgNode).href = '#' + msgNode.id;
        /* Add event handlers */
        Instant.message._installEventHandlers(msgNode);
        /* Done */
        return msgNode;
      },
      /* Generate a DOM node for the specified message parameters */
      makeMessage: function(params) {
        /* Allocate return value; fill in basic values */
        var msgNode = document.createElement('div');
        msgNode.id = 'message-' + params.id;
        msgNode.setAttribute('data-id', params.id);
        if (typeof params.parent == 'string')
          msgNode.setAttribute('data-parent', params.parent);
        if (params.from)
          msgNode.setAttribute('data-from', params.from);
        /* Filter out emotes and whitespace */
        var emote = /^\/me/.test(params.text);
        var text = (emote) ? params.text.substr(3) : params.text;
        text = text.trim();
        /* Parse content */
        var content = Instant.message.parseContent(text);
        /* Assign classes */
        msgNode.className = 'message';
        if (emote)
          msgNode.className += ' emote';
        if (params.from && params.from == Instant.identity.id)
          msgNode.className += ' mine';
        if (Instant.identity.nick != null &&
            Instant.message.scanMentions(content, Instant.identity.nick))
          msgNode.className += ' ping';
        if (params.isNew)
          msgNode.className += ' new';
        /* Assign children */
        msgNode.innerHTML = '<div class="line">' +
          '<time><a class="permalink"></a></time>' +
          '<span class="nick-wrapper">' +
            '<span class="hidden" data-key="indent"></span>' +
            '<span class="hidden" data-key="before-nick">&lt;</span>' +
            '<span class="hidden" data-key="after-nick">&gt; </span>' +
          '</span>' +
          '<span class="content"></span></div>';
        /* Fill in timestamp */
        var timeNode = $sel('time', msgNode);
        var permalink = $sel('.permalink', msgNode);
        permalink.href = '#' + msgNode.id;
        if (typeof params.timestamp == 'number') {
          var date = new Date(params.timestamp);
          timeNode.setAttribute('datetime', date.toISOString());
          timeNode.title = formatDate(date);
          permalink.textContent = (leftpad(date.getHours(), 2, '0') + ':' +
            leftpad(date.getMinutes(), 2, '0'));
        } else {
          permalink.innerHTML = '<i>N/A</i>';
        }
        /* Embed nick */
        var afterNick = $sel('[data-key=after-nick]', msgNode);
        afterNick.parentNode.insertBefore(Instant.nick.makeNode(params.nick),
                                          afterNick);
        /* Add emote styles */
        if (emote) {
          var hue = Instant.nick.hueHash(params.nick);
          afterNick.textContent += '/me ';
          $sel('.content', msgNode).style.background = 'hsl(' + hue +
                                                       ', 75%, 90%)';
        }
        /* Insert text */
        $sel('.content', msgNode).appendChild(content);
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
      /* Same as getParent(), but fail if the current node is not a message */
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
      /* Get the node hosting the replies to the given message, or the message
       * itself if it's actually none at all */
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
          var nick = $sel('.nick', message);
          lc = document.createElement('div');
          lc.className = 'replies';
          if (nick.style.backgroundColor)
            lc.style.borderColor = nick.style.backgroundColor;
          message.appendChild(lc);
        }
        return lc;
      },
      /* Scan an array of messages where to insert
       * If a matching node is (already) found, it is removed. */
      bisect: function(array, id) {
        if (! array || ! array.length) return null;
        var f = 0, t = array.length - 1;
        var last = null;
        /* Exclude baloon */
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
          } else {
            /* Replace old node */
            var ret = array[c].nextElementSibling;
            array[c].parentNode.removeChild(array[c]);
            return ret;
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
        var before = Instant.message.bisect(parent.children, child.id);
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
      /* Create a Notification object for a given message */
      createNotification: function(msg) {
        var text;
        if (msg.classList.contains('emote')) {
          text = ('* ' + $sel('.nick', msg).textContent + ' ' +
          $sel('.content', msg).textContent);
        } else {
          /* HACK: Some notification systems seem to interpret the body as
           *       HTML, so the preferred "angled brackets" cannot be used
           *       (unless we want the name to be an HTML tag). */
          text = ('[' + $sel('.nick', msg).textContent + '] ' +
          $sel('.content', msg).textContent);
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
      }
    };
  }();
  /* Input bar management */
  Instant.input = function () {
    /* Match @-mentions with arbitrary text before
     * Keep in sync with mention matching in Instant.message. */
    var MENTION_BEFORE = new RegExp(
        ('(?:[^a-zA-Z0-9_]|^)\\B@(%MC%*(?:\\(%MC%*\\)%MC%*)*' +
         '(?:\\(%MC%*)?)$').replace(/%MC%/g, '[^.,:;!?()\\s]'));
    /* The DOM node containing the input bar */
    var inputNode = null;
    /* The sub-node currently focused */
    var focusedNode = null;
    return {
      /* Initialize input bar control with the given node */
      init: function(node) {
        /* Helpers for below */
        function updateNick() {
          var hue = Instant.nick.hueHash(inputNick.value);
          sizerNick.textContent = inputNick.value;
          sizerNick.style.background = 'hsl(' + hue + ', 75%, 80%)';
          if (inputNick.value) {
            sizerNick.style.minWidth = '';
          } else {
            sizerNick.style.minWidth = '1em';
          }
        }
        function refreshNick(force) {
          if (Instant.identity.nick == inputNick.value && ! force)
            return;
          Instant.identity.nick = inputNick.value;
          Instant.identity.sendNick();
        }
        function updateMessage() {
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
          if (sizerMsg.scrollHeight + 'px' == inputMsg.style.height)
            return;
          var restore = Instant.input.saveScrollState();
          inputMsg.style.height = sizerMsg.scrollHeight + 'px';
          restore();
        }
        function updateFocus(event) {
          focusedNode = event.target;
        }
        var fakeSeq = 0;
        /* Assign inputNode */
        inputNode = node;
        /* Install event handlers */
        var inputNick = $sel('.input-nick', inputNode);
        var sizerNick = $sel('.input-nick-sizer', inputNode);
        var promptNick = $sel('.input-nick-prompt', inputNode);
        var sizerMsg = $sel('.input-message-sizer', inputNode);
        var inputMsg = $sel('.input-message', inputNode);
        /* Update nick background */
        inputNick.addEventListener('input', updateNick);
        /* End nick editing on Return */
        inputNick.addEventListener('keydown', function(event) {
          if (event.keyCode == 13) { // Return
            inputMsg.focus();
            event.preventDefault();
            refreshNick();
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
        inputMsg.addEventListener('input', updateMessage);
        /* Remove nick setting prompt */
        inputMsg.addEventListener('focus', function() {
          promptNick.style.display = 'none';
        });
        /* Handle special keys */
        inputMsg.addEventListener('keydown', function(event) {
          var text = inputMsg.value;
          if (event.keyCode == 13 && ! event.shiftKey) { // Return
            /* Send message! */
            /* Retrieve input text */
            inputMsg.value = '';
            event.preventDefault();
            updateMessage();
            /* Ignore empty sends */
            if (! text) return;
            if (! Instant.connectionURL) {
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
            } else if (Instant.connection.isConnected()) {
              /* Send actual message */
              Instant.connection.sendBroadcast({type: 'post',
                nick: Instant.identity.nick, text: text,
                parent: Instant.input.getParentID()});
            } else {
              /* Roll back */
              inputMsg.value = text;
              updateMessage();
            }
          } else if (event.keyCode == 27) { // Escape
            if (Instant.input.navigate('root')) {
              location.hash = '';
              Instant.pane.scrollIntoView(inputNode);
              event.preventDefault();
            }
            inputMsg.focus();
          } else if (event.keyCode == 9 && ! event.shiftKey) { // Tab
            /* Extract text with selection removed and obtain the cursor
             * position */
            var text = inputMsg.value;
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
          }
          if (text.indexOf('\n') == -1) {
            if (event.keyCode == 38) { // Up
              if (Instant.input.navigate('up')) {
                Instant.pane.scrollIntoView(inputNode);
                event.preventDefault();
              } else {
                /* Special case: Get more logs */
                Instant.logs.pull.more();
              }
              inputMsg.focus();
            } else if (event.keyCode == 40) { // Down
              if (Instant.input.navigate('down')) {
                Instant.pane.scrollIntoView(inputNode);
                event.preventDefault();
              }
              inputMsg.focus();
            }
            if (! text) {
              if (event.keyCode == 37) { // Left
                if (Instant.input.navigate('left')) {
                  Instant.pane.scrollIntoView(inputNode);
                  event.preventDefault();
                }
                inputMsg.focus();
              } else if (event.keyCode == 39) { // Right
                if (Instant.input.navigate('right')) {
                  Instant.pane.scrollIntoView(inputNode);
                  event.preventDefault();
                }
                inputMsg.focus();
              }
            }
          }
        });
        /* Save the last focused node */
        inputNick.addEventListener('focus', updateFocus);
        inputMsg.addEventListener('focus', updateFocus);
        /* Scroll input into view when resized */
        window.addEventListener('resize', function(event) {
          Instant.pane.scrollIntoView(inputNode);
        });
        /* Read nickname from storage */
        var nick = Instant.storage.get('nickname');
        if (typeof nick == 'string') {
          inputNick.value = nick;
          refreshNick(true);
          inputMsg.focus();
        } else {
          inputNick.focus();
          inputNick.selectionStart = inputNick.value.length;
          inputNick.selectionEnd = inputNick.value.length;
        }
        updateNick();
      },
      /* Return the input bar */
      getNode: function() {
        return inputNode;
      },
      /* Transfer focus to the input bar */
      focus: function(forceInput) {
        var node = focusedNode;
        if (! node || forceInput) node = $sel('.input-message', inputNode);
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
        var line = $sel('.line', msg);
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
        var line = $sel('.line', msg);
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
          var lrect = $sel('.line', msg).getBoundingClientRect();
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
        var nodeRect = node.getBoundingClientRect();
        var paneRect = pane.getBoundingClientRect();
        if (nodeRect.top < paneRect.top + dist) {
          pane.scrollTop -= paneRect.top + dist - nodeRect.top;
        } else if (nodeRect.bottom > paneRect.bottom - dist) {
          pane.scrollTop -= paneRect.bottom - dist - nodeRect.bottom;
        }
      }
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
    return {
      /* Initialize state with the given node */
      init: function(listNode, collapseNode) {
        /* Helper */
        function updateCollapse() {
          var newState = (document.documentElement.offsetWidth <= 400);
          if (newState == lastState) return;
          lastState = newState;
          Instant.userList.collapse(newState);
        }
        node = listNode;
        collapser = collapseNode;
        /* Maintain focus state of input bar */
        var inputWasFocused = false;
        collapser.addEventListener('mousedown', function(event) {
          inputWasFocused = Instant.input.isFocused();
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
        var lastState = false;
        window.addEventListener('resize', updateCollapse);
        updateCollapse();
      },
      /* Scan the list for a place where to insert */
      bisect: function(id, name) {
        /* No need to employ particularly fancy algorithms */
        if (! node) return null;
        var children = node.children;
        var b = 0, e = children.length;
        for (;;) {
          // Bounds met? Done.
          if (b == e)
            return children[b] || null;
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
            return children[m] || null;
          }
        }
      },
      /* Get the node corresponding to id or null */
      get: function(id) {
        return nicks[id] || null;
      },
      /* Add or update the entry for id */
      add: function(id, name, uuid) {
        /* Create a new node if necessary */
        var newNode = nicks[id];
        if (newNode) {
          /* Do not disturb searching */
          node.removeChild(newNode);
        } else {
          newNode = document.createElement('span');
          newNode.className = 'nick';
          newNode.setAttribute('data-id', id);
          newNode.id = 'user-' + id;
        }
        /* Apply new parameters to node */
        var hue = Instant.nick.hueHash(name);
        if (uuid) newNode.setAttribute('data-uuid', uuid);
        newNode.setAttribute('data-last-active', Date.now());
        newNode.setAttribute('data-nick', name);
        newNode.textContent = name;
        newNode.style.background = 'hsl(' + hue + ', 75%, 80%)';
        /* Update animation */
        newNode.style.webkitAnimationDelay = '';
        newNode.style.animationDelay = '';
        /* Update data */
        nicks[id] = newNode;
        /* Abort if no node */
        if (! node) return null;
        /* Find insertion position */
        var insBefore = Instant.userList.bisect(id, name);
        /* Insert node into list */
        node.insertBefore(newNode, insBefore);
        /* Maintain consistency */
        Instant.userList.update();
        /* Return something sensible */
        return newNode;
      },
      /* Remove the given entry */
      remove: function(id) {
        if (! nicks[id]) return;
        try {
          node.removeChild(nicks[id]);
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
      /* Update some CSS properties */
      update: function() {
        /* Update counter */
        if (node && collapser) {
          var c = $sel('span', collapser);
          var n = node.children.length;
          c.textContent = n + ' user' + ((n == 1) ? '' : 's');
        }
        /* Now delegated to sidebar itself */
        Instant.animation.updateSidebar();
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
            var time = el.getAttribute('data-last-active') - now;
            if (time < -300000) time = -300000;
            el.style.webkitAnimationDelay = time + 'ms';
            el.style.animationDelay = time + 'ms';
          });
        }
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
          return Instant.nick.seminormalize(n.getAttribute('data-nick'));
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
        /* Interval to wait before choosing peer */
        var WAIT_TIME = 1000;
        /* When to poll whether to choose new peer */
        var POLL_TIME = 100;
        /* The pane to add parent-less messages to */
        var pane = null;
        /* Waiting for replies to arrive */
        var timer = null;
        /* Last time we got a log-info */
        var lastUpdate = null;
        /* Peers for oldest and newest messages */
        var oldestPeer = null, newestPeer = null;
        /* Which logs exactly are pulled */
        var pullType = {before: null, after: null};
        /* Message we want to fetch */
        var goal = null;
        return {
          /* Initialize the pane node */
          init: function(paneNode) {
            pane = paneNode;
          },
          /* Actually start pulling logs */
          _start: function() {
            if (timer == null) {
              Instant.connection.sendBroadcast({type: 'log-query'});
              lastUpdate = Date.now();
              timer = setInterval(Instant.logs.pull._check, POLL_TIME);
            }
            if (pullType.before)
              Instant.animation.throbber.show('logs-before');
            if (pullType.after)
              Instant.animation.throbber.show('logs-after');
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
            /* Abort if new data arrived while waiting; stop waiting
             * otherwise */
            if (Date.now() - lastUpdate < WAIT_TIME) return;
            clearInterval(timer);
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
              }
            } else {
              if (oldestPeer && pullType.before) {
                Instant.connection.sendUnicast(oldestPeer.id,
                  {type: 'log-request', to: oldestLog, key: 'before'});
                sentBefore = true;
              }
              if (newestPeer && ! logsLive && pullType.after) {
                Instant.connection.sendUnicast(newestPeer.id,
                  {type: 'log-request', from: newestLog, key: 'after'});
                sentAfter = true;
              }
            }
            /* Clear throbber */
            Instant.logs.pull._done(! sentBefore, ! sentAfter);
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
              Instant.animation.throbber.hide('logs-before');
            }
            if (after) {
              pullType.after = false;
              Instant.animation.throbber.hide('logs-after');
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
        var ext = Instant.titleExtension;
        if (unreadMessages) {
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
      /* Add the given amounts of messages, replies, and pings to the
       * internal counters and update the window title */
      addUnread: function(messages, replies, mentions) {
        if (! blurred) return;
        unreadMessages += messages;
        unreadReplies += replies;
        unreadMentions += mentions;
        Instant.title._update();
        Instant.title.favicon._update();
      },
      /* Clear the internal counters and update the window title to suit */
      clearUnread: function() {
        unreadMessages = 0;
        unreadReplies = 0;
        unreadMentions = 0;
        Instant.title._update();
        Instant.title.favicon._update();
      },
      /* Set the update available status */
      setUpdateAvailable: function(available) {
        updateAvailable = available;
        Instant.title._update();
        Instant.title.favicon._update();
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
            var level;
            if (unreadMentions) {
              level = 'ping';
            } else if (updateAvailable) {
              level = 'update';
            } else if (unreadReplies) {
              level = 'reply';
            } else if (! Instant.connection.isConnected()) {
              level = 'disconnect';
            } else if (unreadMessages) {
              level = 'any';
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
    /* The sidebar */
    var sideBar = null;
    return {
      /* Initialize the submodule */
      init: function(msgNode, barNode) {
        function updateHash(event) {
          if (/^#?$/.test(location.hash)) {
            Instant.input.navigate('root');
            Instant.input.focus();
            Instant.pane.scrollIntoView(Instant.input.getNode());
          } else if (Instant.message.checkFragment(location.hash)) {
            Instant.animation.navigateToMessage(location.hash);
          }
        }
        function mutationInvalid(el) {
          return (el.target == wrapper);
        }
        messageBox = msgNode;
        sideBar = barNode;
        var pane = Instant.pane.getPane(messageBox);
        /* Pull more logs when scrolled to top */
        pane.addEventListener('scroll', function(event) {
          if (pane.scrollTop == 0) Instant.logs.pull.more();
          Instant.animation.offscreen.checkAll();
        });
        window.addEventListener('hashchange', updateHash);
        updateHash();
        /* Sidebar handling */
        var wrapper = $sel('.sidebar-middle-wrapper', sideBar);
        window.addEventListener('resize', Instant.animation.updateSidebar);
        if (window.MutationObserver) {
          var obs = new MutationObserver(function(records, observer) {
            if (records.some(mutationInvalid)) return;
            Instant.animation.updateSidebar();
          });
          obs.observe(wrapper, {childList: true, attributes: true,
            characterData: true, subtree: true,
            attributeFilter: ['class', 'style']});
        }
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
        var msg = $sel('.message', messageBox);
        if (msg && msg.offsetTop >= tp && pane.scrollTop == 0)
          Instant.logs.pull.more();
      },
      /* Update sidebar styles */
      updateSidebar: function() {
        /* Extract nodes */
        var wrapper = $sel('.sidebar-middle-wrapper', sideBar);
        var content = $sel('.sidebar-middle', wrapper);
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
      throbber: function() {
        /* Status
         * The throbber displays as long as at least one of the values in
         * here it true. */
        var status = {};
        /* The actual throbber element */
        var node = null;
        return {
          /* Initialize the submodule with the given node */
          init: function(throbberNode) {
            node = throbberNode;
            Instant.animation.throbber._update();
          },
          /* Show the throbber, setting the given status variable */
          show: function(key) {
            status[key] = true;
            Instant.animation.throbber._update();
          },
          /* Possibly hide the throbber, but at least mark this task as
           * done */
          hide: function(key) {
            status[key] = false;
            Instant.animation.throbber._update();
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
            aboveNode = $sel('.alert-above', container);
            belowNode = $sel('.alert-below', container);
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
      init: function(node) {
        wrapperNode = node;
        var btn = $sel('.settings', wrapperNode);
        var cnt = $sel('.settings-content', wrapperNode);
        /* Toggle settings */
        btn.addEventListener('click', function(event) {
          Instant.settings.toggle();
        });
        /* Install event listener */
        var apply = Instant.settings.apply.bind(Instant.settings);
        Array.prototype.forEach.call($selAll('input', cnt), function(el) {
          el.addEventListener('change', apply);
        });
        /* Restore settings from storage */
        Instant.settings.restore();
        Instant.settings.apply();
      },
      /* Actually apply the settings */
      apply: function() {
        var cnt = $sel('.settings-content', wrapperNode);
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
        Instant.storage.set('theme', theme);
        Instant.storage.set('notification-level', level);
      },
      /* Restore the settings from storage */
      restore: function() {
        var cnt = $sel('.settings-content', wrapperNode);
        var theme = Instant.storage.get('theme');
        cnt.elements['theme'].value = theme;
        var level = Instant.storage.get('notification-level');
        cnt.elements['notifies'].value = level;
      },
      /* Show the settings popup */
      show: function() {
        wrapperNode.classList.add('visible');
      },
      /* Hide the settings popup */
      hide: function() {
        wrapperNode.classList.remove('visible');
      },
      /* Toggle the settings visibility */
      toggle: function() {
        wrapperNode.classList.toggle('visible');
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
    var LEVELS = { none: 0, ping: 1, update: 2, reply: 3, disconnect: 4,
                   any: 5 };
    /* The colors associated to the levels */
    var COLORS = {
      none: '#000000', /* No color in particular */
      ping: '#c0c000', /* @-mentions are yellow */
      update: '#008000', /* The update information is green */
      reply: '#0040ff', /* Replies are color-coded with blue */
      disconnect: '#c00000', /* Red... was not used */
      any: '#c0c0c0' /* No color in particular, faint version */
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
      this.title = options.title || 'Instant';
      this.text = options.text;
      this.level = options.level || 'any';
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
        var mlvl = 'any';
        if (msg.classList.contains('ping')) {
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
        Instant.title._notify(notify);
        Instant.notifications.desktop._notify(notify);
        return notify;
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
            if (current) return;
            Instant.notifications.desktop._show(notify.title, notify.text, {
              icon: notify.icon,
              oncreate: function(notify) {
                /* Set current notification */
                current = notify;
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
              /* HACK: Firefox before release 49 would silently fail to display
               *       notifications with icons to varying rates (for me). */
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
        data[key] = value;
        Instant.storage.save();
      },
      /* Remove the given key and save the results (asynchronously) */
      del: function(key) {
        delete data[key];
        Instant.storage.save();
      },
      /* Remove all keys and save the results (still asynchronously) */
      clear: function() {
        data = {};
        Instant.storage.save();
      },
      /* Reset the internal storage *without* saving automatically */
      _clear: function() {
        data = {};
      },
      /* Read the underlying storage backends and merge the results into the
       * data array. */
      load: function() {
        function thaw(str) {
          if (! str) return;
          var res = null;
          try {
            res = JSON.parse(str);
            if (typeof res != 'object') throw 'Malformed data';
          } catch (e) {
            console.warn('Could not deserialize storage:', e);
            return;
          }
          for (var key in res) {
            if (! res.hasOwnProperty(key)) continue;
            data[key] = res[key];
          }
        }
        if (window.localStorage)
          thaw(localStorage.getItem('instant-data'));
        if (window.sessionStorage)
          thaw(sessionStorage.getItem('instant-data'));
      },
      /* Serialize the current data to the backends */
      save: function() {
        var encoded = JSON.stringify(data);
        if (window.sessionStorage)
          sessionStorage.setItem('instant-data', encoded);
        if (window.localStorage)
          localStorage.setItem('instant-data', encoded);
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
        var ch = measure.firstElementChild;
        if (! ch) return;
        var margin = (measure.offsetWidth - ch.offsetWidth) + 'px';
        if (target.style.marginRight != margin)
          target.style.marginRight = margin;
      }
    };
  }();
  /* Global initialization function */
  Instant.init = function(main, loadWrapper) {
    Instant.query.init();
    Instant.storage.init();
    Instant.identity.init($sel('.update-message', main),
                          $sel('.refresh-message', main));
    Instant.input.init($sel('.input-bar', main));
    Instant.userList.init($sel('.user-list', main),
                          $sel('.user-list-counter', main));
    Instant.title.init();
    Instant.notifications.init();
    Instant.logs.pull.init($sel('.message-box', main));
    Instant.animation.init($sel('.message-box', main));
    Instant.animation.greeter.init(loadWrapper);
    Instant.animation.throbber.init($sel('.throbber', main));
    Instant.animation.offscreen.init($sel('.alert-container', main));
    Instant.settings.init($sel('.settings-wrapper', main));
    Instant.connection.init($sel('.online-status', main));
    repeat(function() {
      Instant.util.adjustScrollbar($sel('.sidebar', main),
                                   $sel('.message-pane', main));
    }, 1000);
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
  /* "No connection" messages retained for historical significance */
  /*var c = Instant.message.addReply({id: 'loading-2-conn',
      nick: 'Connection', text: '/me is missing.'}, m);
    Instant.message.addReply({id: 'loading-3-comment', nick: 'Loading',
      text: 'Prepare for a long wait...', parent: 'loading-2-conn'});
    Instant.message.addReply({id: 'loading-4-comment', nick: 'Loading',
      text: 'Or, try solving the issue somehow.', parent: 'loading-2-conn'});
  */
  /* Show room name, or none in local mode */
  var nameNode = $sel('.room-name');
  if (Instant.roomName) {
    nameNode.textContent = '&' + Instant.roomName;
  } else {
    nameNode.innerHTML = '<i>local</i>';
    $sel('.online-status').style.background = '#c0c0c0';
    $sel('.online-status').title = 'Local';
    $sel('.user-list').style.display = 'none';
    $sel('.user-list-counter').style.display = 'none';
    /* Nothing is going to hide it, so we have to. */
    Instant.animation.greeter.hide();
  }
  if (Instant.stagingLocation) {
    var stagingNode = document.createElement('span');
    stagingNode.className = 'staging';
    stagingNode.textContent = ' (' + Instant.stagingLocation + ')';
    nameNode.parentNode.insertBefore(stagingNode, nameNode.nextSibling);
  }
  /* Show main element */
  main.style.opacity = '1';
  /* Focus input bar if Escape pressed and not focused */
  document.documentElement.addEventListener('keydown', function(event) {
    if (event.keyCode == 27) { // Escape
      if (Instant.settings.isVisible())
        Instant.settings.hide();
      Instant.input.focus();
      event.preventDefault();
    }
  });
  /* Fire up Instant! */
  Instant.init(main, wrapper);
}
