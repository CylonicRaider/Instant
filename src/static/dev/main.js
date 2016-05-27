
/* Utilities */
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

/* Early preparation; define most of the functionality */
window.Instant = function() {
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
    Instant.baseTitle = '&amp;' + $esc(Instant.roomName) + ' &mdash; Instant';
    $sel('title').innerHTML = Instant.baseTitle;
  } else {
    Instant.baseTitle = 'Instant';
  }
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
  /* Own identity */
  Instant.identity = {
    /* The session ID */
    id: null,
    /* The (current) nickname */
    nick: null,
    /* Server version */
    serverVersion: null,
    /* Fine-grained server version */
    serverRevision: null,
    /* The nickname as known to the outside */
    _sentNick: null,
    /* Broadcast or send the current nickname */
    sendNick: function(to) {
      if (Instant.connection.isConnected() &&
          Instant.identity.nick != Instant.identity._sentNick) {
        Instant.connection.send(to, {type: 'nick',
          nick: Instant.identity.nick});
        Instant.identity._sentNick = Instant.identity.nick;
      }
    }
  };
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
    /* Debugging hook */
    if (window.logInstantMessages === undefined)
      window.logInstantMessages = false;
    return {
      /* Initialize the submodule, by installing the connection status
       * widget */
      init: function(statusNode) {
        connStatus = statusNode;
        /* Force update of widget */
        if (ws && ws.readyState == WebSocket.OPEN) {
          console.log('Forcing status update');
          Instant.connection._connected();
        }
      },
      /* Actually connect */
      connect: function() {
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
            console.error(e);
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
          console.log('Received:', event, event.data);
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
          console.error(e);
          return;
        }
        /* Switch on the message type */
        switch (msg.type) {
          case 'error': /* Error */
            console.error('Error message:', msg);
            break;
          case 'identity': /* Own (and server's) identity */
            Instant.identity.id = msg.data.id;
            Instant.identity.serverVersion = msg.data.version;
            Instant.identity.serverRevision = msg.data.revision;
            break;
          case 'pong': /* Server replied to a ping */
          case 'reply': /* Reply to a message sent */
            /* Nothing to do */
            break;
          case 'joined':
            /* New user joined (might be ourself) -- NYI */
            break;
          case 'left':
            /* User left -- NYI */
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
                /* Prepare message object */
                var ent = {id: msg.id, parent: data.parent || null,
                  timestamp: msg.timestamp, from: msg.from, nick: nick,
                  text: text, isNew: true};
                /* Only display message when initialized */
                var inp = Instant.input.getNode();
                if (inp) {
                  /* Prepare for scrolling */
                  var restore = Instant.pane.saveScrollState(inp, 1);
                  /* Post message */
                  var box = Instant.pane.getBox(inp);
                  Instant.message.importMessage(ent, box);
                  /* Restore scroll state */
                  restore();
                }
                break;
              case 'nick': /* Someone informs us about their nick */
                /* NYI */
                break;
              case 'who': /* Someone asks about others' nicks */
                Instant.identity.sendNick(msg.from);
                break;
              case 'log-query': /* Someone asks about our logs */
              case 'log-info': /* Someone informs us about their logs */
              case 'log-request': /* Someone requests logs from us */
              case 'log': /* Someone delivers logs to us */
                /* NYI */
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
            console.error('Unknown server message:', msg);
            break;
        }
      },
      /* Handle a dead connection */
      _closed: function(event) {
        /* Update flag */
        connected = false;
        /* Update status widget */
        if (connStatus) {
          /* Update status widget */
          connStatus.classList.remove('connected');
          connStatus.classList.add('broken');
          connStatus.title = 'Broken';
        }
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
          console.error('WebSocket error:', event);
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
      /* Event handler for WebSocket messages */
      onRawMessage: null
    };
  }();
  /* Connect ASAP */
  if (Instant.connectionURL)
    Instant.connection.connect();
  /* Nick-name handling */
  Instant.nick = function() {
    /* Nick -> Hue hash */
    var cache = {};
    return {
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
        node.style.background = 'hsl(' + hue + ', 75%, 80%)';
        node.setAttribute('data-nick', name);
        return node;
      },
      /* Generate a DOM node carrying a mention of the nick
       * name is the nickname with an @-sign. */
      makeMention: function(name) {
        var node = document.createElement('span');
        var hue = Instant.nick.hueHash(name);
        node.className = 'mention';
        node.textContent = name;
        node.style.color = 'hsl(' + hue + ', 75%, 40%)';
        if (name[0] != '@') throw new Error('Bad nick for makeMention()');
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
    /* Interesting substring regex
     * Groupings:  1: Room name matched
     *             2: Full URL matched (without surrounding marks)
     *                 3: Scheme (with colon and double slash)
     *                 4: Username with "@"
     *                 5: Hostname
     *                 6: Port
     *                 7: Path
     *             8: Nick-name @-mentioned (with the @ sign)
     *             9: Smiley (space before must be ensured)
     *            10: Text with emphasis marker(s) (check space before)
     *                11: Markers before and after (not included themself)
     *                12: Marker before (same)
     *                13: Marker after (same as well)
     *            14: Text with monospace marker(s) (check space before)
     *                15: Markers before and after
     *                16: Marker before
     *                17: Marker after
     *            18: Block-level monospace marker (check whitespace before)
     */
    var mc = '[^.,:;!?()\\s]';
    var aa = '[^a-zA-Z0-9_]|^|$';
    var INTERESTING = (
      '\\B&([a-zA-Z](?:[a-zA-Z0-9_-]*[a-zA-Z0-9])?)\\b|' +
      '<(((?!javascript:)[a-zA-Z]+://)?([a-zA-Z0-9._~-]+@)?' +
        '([a-zA-Z0-9.-]+)(:[0-9]+)?(/[^>]*)?)>|' +
      '\\B(@%MC%+(?:\\(%MC%*\\)%MC%*)*)|' +
      '((?:[+-]1|:[D)|/(CSP\\\\oO]|[SD)/|(C\\\\oO]:|\\^\\^|;\\))(?=%AA%))|' +
      '((?:\\*+([^*\\s]+)\\*+|\\*+([^*\\s]+)|([^*\\s]+)\\*+)(?=%AA%))|' +
      '((?:`([^`\\s]+)`|`([^`\\s]+)|([^`\\s]+)`)(?=%AA%))|' +
      '(```(?=%AA%))').replace(/%MC%/g, mc).replace(/%AA%/g, aa);
    var ALLOW_AROUND = new RegExp(aa);
    /* Smiley table
     * Keep in sync with the regex above */
    var SMILEYS = {'+1': '#008000', '-1': '#c00000',
      ':D': '#c0c000', ':)': '#c0c000', ':|': '#c0c000', ':/': '#c0c000',
      ':(': '#c0c000', ':C': '#c0c000', ':S': '#c0c000', ':P': '#c0c000',
      'S:': '#c0c000', 'D:': '#c0c000', '):': '#c0c000', '/:': '#c0c000',
      '|:': '#c0c000', '(:': '#c0c000', 'C:': '#c0c000',
      ':O': '#c0c000', ':o': '#c0c000', 'o:': '#c0c000', 'O:': '#c0c000',
      ';)': '#c0c000', '^^': '#c0c000',
      ':\\': '#c0c000', '\\:': '#c0c000'
    };
    /* Color for mark-up sigils */
    var GRAY = '#808080';
    return {
      /* Detect links, emphasis, and smileys out of a flat string and render
       * those into a DOM node */
      parseContent: function(text) {
        /* Quickly prepare DOM nodes */
        function makeNode(text, className, color, bold, tag) {
          var node = document.createElement(tag || 'span');
          if (className) node.className = className;
          if (color) node.style.color = color;
          if (bold) node.style.fontWeight = 'bold';
          if (text) node.textContent = text;
          return node;
        }
        function makeSigil(text, className) {
          return makeNode(text, 'sigil ' + className, GRAY);
        }
        /* Disable a wrongly-assumed emphasis mark */
        function declassify(elem) {
          elem.disabled = true;
          elem.node.className = elem.node.className.replace(/before/g,
            'before-false').replace(/after/g, 'after-false');
          elem.node.style.color = '';
          elem.node.style.fontWeight = '';
        }
        /* Regular expression instance */
        var re = new RegExp(INTERESTING, 'g');
        /* Intermediate output; last character of input processed */
        var out = [], l = 0, s, mono = false;
        /* Extract the individual goodies from the input */
        for (;;) {
          /* Match regex */
          var m = re.exec(text);
          if (m == null) {
            /* Append ending */
            s = text.substring(l);
            if (s) out.push(s);
            break;
          }
          /* Calculate beginning and end */
          var start = m.index, end = m.index + m[0].length;
          /* Insert text between matches */
          s = text.substring(l, start);
          if (s) out.push(s);
          /* Character immediately before match */
          var before = (start == 0) ? '' : text.substr(start - 1, 1);
          /* Update last character */
          l = end;
          /* Switch on match */
          if (m[1]) {
            /* Room link */
            var node = makeNode(m[0], 'room-link', null, false, 'a');
            node.href = '/room/' + m[1] + '/';
            node.target = '_blank';
            out.push(node);
          } else if (m[2] && /[^\w_-]/.test(m[2])) {
            /* Hyperlink (must contain non-word character) */
            out.push(makeSigil('<', 'link-before'));
            /* Insert http:// if necessary */
            var url = m[2];
            if (! m[3]) url = 'http://' + url;
            var node = makeNode(m[2], 'link', null, false, 'a');
            node.href = url;
            node.target = '_blank';
            out.push(node);
            out.push(makeSigil('>', 'link-after'));
          } else if (m[8]) {
            /* @-mention */
            out.push(Instant.nick.makeMention(m[8]));
          } else if (m[9] && ALLOW_AROUND.test(before)) {
            /* Smiley (allowed characters after are already checked) */
            out.push(makeNode(m[9], 'smiley', SMILEYS[m[9]], true));
          } else if (m[10] && ALLOW_AROUND.test(before) && ! mono) {
            /* Emphasized text (again, only before has to be tested) */
            var pref = $prefLength(m[10], '*');
            var suff = $suffLength(m[10], '*');
            /* Sigils are in individual nodes so they can be selectively
             * disabled */
            for (var i = 0; i < pref; i++) {
              var node = makeSigil('*', 'emph-before');
              out.push(node);
              out.push({emphAdd: true, node: node});
            }
            /* Add actual text; which one does not matter */
            out.push(m[11] || m[12] || m[13]);
            /* Same as above for trailing sigil */
            for (var i = 0; i < suff; i++) {
              var node = makeSigil('*', 'emph-after');
              out.push({emphRem: true, node: node});
              out.push(node);
            }
          } else if (m[14] && ALLOW_AROUND.test(before) && ! mono) {
            /* Inline monospace */
            /* Leading sigil */
            if (m[15] != null || m[16] != null) {
              var node = makeSigil('`', 'mono-before');
              out.push(node);
              out.push({monoAdd: true, node: node});
            }
            /* Embed actual text */
            out.push(m[15] || m[16] || m[17]);
            /* Trailing sigil */
            if (m[15] != null || m[17] != null) {
              var node = makeSigil('`', 'mono-after');
              out.push({monoRem: true, node: node});
              out.push(node);
            }
          } else if (m[18] && ALLOW_AROUND.test(before)) {
            /* Block-level monospace marker */
            if (! mono) {
              var node = makeSigil(m[18], 'mono-block-before');
              out.push(node);
              out.push({monoAdd: true, node: node});
              mono = true;
            } else {
              var node = makeSigil(m[18], 'mono-block-after');
              out.push({monoRem: true, node: node});
              out.push(node);
              mono = false;
            }
          } else if (m[0]) {
            out.push(m[0]);
          }
        }
        /* Disable stray emphasis marks
         * Those nested highlights actually form a context-free grammar. */
        var stack = [];
        for (var i = 0; i < out.length; i++) {
          var e = out[i];
          /* Filter such that only user-made objects remain */
          if (typeof e != 'object' || e.nodeType !== undefined) continue;
          /* Add or remove emphasis, respectively */
          if (e.emphAdd || e.monoAdd) {
            stack.push(e);
          } else if (e.emphRem || e.monoRem) {
            if (stack.length) {
              /* Check if it actually matches */
              var top = stack[stack.length - 1];
              if (e.emphRem && top.emphAdd || e.monoRem && top.monoAdd) {
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
        /* Assign actual emphasis levels (italic -> bold -> small-caps, with
         * combinations in between) */
        var level = 0;
        stack = [makeNode(null, 'message-text')];
        for (var i = 0; i < out.length; i++) {
          var e = out[i], top = stack[stack.length - 1];
          /* Drain non-metadata parts into the current node */
          if (typeof e == 'string') {
            top.appendChild(document.createTextNode(e));
          } else if (e.nodeType !== undefined) {
            top.appendChild(e);
          }
          /* Process emphasis nodes */
          if (e.disabled) {
            /* NOP */
          } else if (e.emphAdd) {
            level++;
            var node = makeNode(null, 'emph');
            node.style.fontStyle = (level & 1) ? 'italic' : 'normal';
            node.style.fontWeight = (level & 2) ? 'bold' : 'normal';
            node.style.fontVariant = (level & 4) ? 'small-caps' : 'normal';
            top.appendChild(node);
            stack.push(node);
          } else if (e.monoAdd) {
            var node = makeNode(null, 'monospace');
            top.appendChild(node);
            stack.push(node);
          } else if (e.emphRem) {
            level--;
            stack.pop();
          } else if (e.monoRem) {
            stack.pop();
          }
        }
        /* Done! */
        return stack[0];
      },
      /* Scan for @-mentions of a given nickname in a message */
      scanMentions: function(content, nick) {
        var ret = 0, children = content.children;
        for (var i = 0; i < children.length; i++) {
          if (children[i].classList.contains('mention')) {
            /* If nick is correct, one instance found */
            if (children[i].getAttribute('data-nick') == nick)
              ret++;
          } else {
            /* Scan recursively */
            ret += Instant.message.scanMentions(children[i], nick);
          }
        }
        return ret;
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
        if (Instant.message.scanMentions(content, Instant.identity.nick))
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
        /* Add event handler */
        $sel('.line', msgNode).addEventListener('click', function(event) {
          Instant.input.moveTo(msgNode);
          $sel('.input-message', Instant.input.getNode()).focus();
          Instant.pane.scrollIntoView(msgNode);
          event.stopPropagation();
        });
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
      /* Get the message immediately preceding the current one */
      getPrecedessor: function(message) {
        var prev = message.previousElementSibling;
        if (! prev || ! Instant.message.isMessage(prev))
          return null;
        return prev;
      },
      /* Get the message immediately following the current one */
      getSuccessor: function(message) {
        var next = message.nextElementSibling;
        if (! next || ! Instant.message.isMessage(next))
          return null;
        return next;
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
      /* Scan an array of messages where to insert */
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
          /* |0 to cast to integer */
          var c = (f + t) / 2 | 0;
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
            parent.removeChild(array[c]);
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
        /* Resolve fake messages */
        var fake = fakeMessages[message.getAttribute('data-id')];
        if (fake) {
          $moveCh(Instant.message.getReplies(fake),
                  Instant.message.makeReplies(message));
          fake.parentNode.removeChild(fake);
          delete fakeMessages[message.getAttribute('data-id')];
        }
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
      /* Return the message identified by this is, or undefined if none */
      get: function(id) {
        return messages[id];
      },
      /* Clear the internal message registry */
      clear: function() {
        mesages = {};
        fakeMessages = {};
      }
    };
  }();
  /* Input bar management */
  Instant.input = function () {
    /* The DOM node containing the input bar */
    var inputNode = null;
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
        function refreshNick() {
          Instant.identity.nick = inputNick.value;
          Instant.identity.sendNick();
        }
        function updateMessage() {
          var restore = Instant.pane.saveScrollState(inputNode, 1);
          inputMsg.style.height = '0';
          inputMsg.style.height = inputMsg.scrollHeight + 'px';
          promptNick.style.display = 'none';
          restore();
        }
        var fakeSeq = 0;
        /* Assign inputNode */
        inputNode = node;
        /* Install event handlers */
        var inputNick = $sel('.input-nick', inputNode);
        var sizerNick = $sel('.input-nick-sizer', inputNode);
        var promptNick = $sel('.input-nick-prompt', inputNode);
        var inputMsg = $sel('.input-message', inputNode);
        /* Update nick background */
        inputNick.addEventListener('input', updateNick);
        updateNick();
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
              var restore = Instant.pane.saveScrollState(inputNode, 1);
              /* Fake messages if not connected */
              var msgid = 'local-' + leftpad(fakeSeq++, 8, '0');
              Instant.message.importMessage(
                {id: msgid, nick: Instant.identity.nick || '', text: text,
                  parent: Instant.input.getParentID(), timestamp: Date.now()},
                Instant.message.getRoot(inputNode));
              /* Restore scroll state */
              restore();
            } else if (Instant.connection.isConnected()) {
              /* Send actual message */
              Instant.connection.sendBroadcast({type: 'post',
                nick: Instant.identity.nick, text: text,
                parent: Instant.input.getParentID()});
            }
          } else if (event.keyCode == 27) { // Escape
            if (Instant.input.navigate('root')) {
              Instant.pane.scrollIntoView(inputNode);
              event.preventDefault();
            }
            inputMsg.focus();
          }
          if (text.indexOf('\n') == -1) {
            if (event.keyCode == 38) { // Up
              if (Instant.input.navigate('up')) {
                Instant.pane.scrollIntoView(inputNode);
                event.preventDefault();
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
        /* Focus the nick input */
        inputNick.focus();
        Instant.identity.nick = inputNick.value;
        inputNick.selectionStart = inputNick.value.length;
        inputNick.selectionEnd = inputNick.value.length;
      },
      /* Return the input bar */
      getNode: function() {
        return inputNode;
      },
      /* Get the message ID of the parent of the input bar */
      getParentID: function() {
        var parent = Instant.message.getParentMessage(inputNode);
        if (! parent) return null;
        return parent.getAttribute('data-id');
      },
      /* Move the input bar into the given message/container */
      jumpTo: function(parent) {
        /* Handle message parents */
        if (Instant.message.isMessage(parent))
          parent = Instant.message.makeReplies(parent);
        /* Actually relocate the input */
        parent.appendChild(inputNode);
      },
      /* Move the input bar to the given message, or to its parent if the
       * bar is already there. */
      moveTo: function(message) {
        if (Instant.message.isMessage(message) &&
            inputNode.parentNode == Instant.message._getReplyNode(message)) {
          Instant.input.jumpTo(Instant.message.getParent(message));
        } else {
          Instant.input.jumpTo(message);
        }
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
      }
    };
  }();
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
  /* To be assigned in window */
  return Instant;
}();

function init() {
  /* Callback for below */
  function hideGreeter() {
    wrapper.style.marginTop = '-30px';
    wrapper.style.opacity = '0';
    setTimeout(function() { wrapper.style.display = 'none'; }, 1000);
  }
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
  /* Display "no connection" messages */
  if (! Instant.connectionURL && false) {
    var c = Instant.message.addReply({id: 'loading-2-conn',
      nick: 'Connection', text: '/me is missing.'}, m);
    Instant.message.addReply({id: 'loading-3-comment', nick: 'Loading',
      text: 'Prepare for a long wait...', parent: 'loading-2-conn'});
    Instant.message.addReply({id: 'loading-4-comment', nick: 'Loading',
      text: 'Or, try solving the issue somehow.', parent: 'loading-2-conn'});
  } else {
    /* Show room name, or none in local mode */
    var nameNode = $sel('.room-name');
    if (Instant.roomName) {
      nameNode.textContent = '&' + Instant.roomName;
    } else {
      nameNode.innerHTML = '<i>local</i>';
      $sel('.online-status').style.background = '#c0c0c0';
      $sel('.online-status').title = 'Local';
    }
    if (Instant.stagingLocation) {
      var stagingNode = document.createElement('span');
      stagingNode.className = 'staging';
      stagingNode.textContent = ' (' + Instant.stagingLocation + ')';
      nameNode.parentNode.insertBefore(stagingNode, nameNode.nextSibling);
    }
    /* Currently NYI */
    $sel('.settings').style.display = 'none';
    /* Show main element */
    main.style.opacity = '1';
    /* Focus input bar if Escape pressed and not focused */
    document.documentElement.addEventListener('keydown', function(event) {
      if (event.keyCode == 27) {
        $sel('.input-message', main).focus();
        event.preventDefault();
      }
    });
    /* Initialize submodules */
    Instant.connection.init($sel('.online-status', main));
    Instant.input.init($sel('.input-bar', main));
    /* Hide greeter manually since Instant does not (for now) */
    hideGreeter();
  }
}
