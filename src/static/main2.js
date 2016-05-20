
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
  var Instant = {};
  /* Prepare connection */
  var roomPaths = /^(\/room\/([a-zA-Z](?:[a-zA-Z0-9_-]*[a-zA-Z0-9])?))\/?/;
  var roomMatch = roomPaths.exec(document.location.pathname);
  if (roomMatch) {
    var scheme = (document.location.protocol == 'https:') ? 'wss' : 'ws';
    var wsURL = scheme + '://' + document.location.host +
      roomMatch[1] + '/ws';
    Instant.connectionURL = wsURL;
    Instant.roomName = roomMatch[2];
  } else {
    Instant.connectionURL = null;
    Instant.roomName = null;
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
      MONTH_NAMES[date.getMonth() + 1] + '-' + zpad(date.getDate(), 2) + ' ' +
      zpad(date.getHours(), 2) + ':' + zpad(date.getMinutes(), 2) + ':' +
      zpad(date.getSeconds(), 2));
  }
  /* Own identity */
  Instant.identity = {
    /* The session ID */
    id: null,
    /* The (current) nickname */
    nick: null
  };
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
     */
    var mc = '[^.,:;!?()\\s]';
    var aa = '[^a-zA-Z0-9_]|^|$';
    var INTERESTING = (
      '\\B&([a-zA-Z](?:[a-zA-Z0-9_-]*[a-zA-Z0-9])?)\\b|' +
      '<(((?!javascript:)[a-zA-Z]+://)?([a-zA-Z0-9._~-]+@)?' +
        '([a-zA-Z0-9.-]+)(:[0-9]+)?(/[^>]*)?)>|' +
      '\\B(@%MC%+(?:\\(%MC%*\\)%MC%*)*)|' +
      '((?:[+-]1|:[D)|/(CSP\\\\oO]|[SD)/|(C\\\\oO]:|\\^\\^|;\\))(?=%AA%))|' +
      '((?:\\*+([^*\\s]+)\\*+|\\*+([^*\\s]+)|([^*\\s]+)\\*+)(?=%AA%))'
      ).replace(/%MC%/g, mc).replace(/%AA%/g, aa);
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
          elem.className = elem.className.replace(/before/g,
            'before-false').replace(/after/g, 'after-false');
          elem.node.style.color = '';
          elem.node.style.fontWeight = '';
        }
        /* Regular expression instance */
        var re = new RegExp(INTERESTING, 'g');
        /* Intermediate output; last character of input processed */
        var out = [], l = 0, s;
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
          } else if (m[10] && ALLOW_AROUND.test(before)) {
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
          if (e.emphAdd) {
            stack.push(e);
          } else if (e.emphRem) {
            if (stack) {
              stack.pop();
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
            var emphLevel = stack.length;
            var node = makeNode(null, 'emph');
            if (emphLevel & 1) node.style.fontStyle = 'italic';
            if (emphLevel & 2) node.style.fontWeight = 'bold';
            if (emphLevel & 4) node.style.fontVariant = 'small-caps';
            top.appendChild(node);
            stack.push(node);
          } else if (e.emphRem) {
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
        /* Done */
        return msgNode;
      },
      /* Get the parent of the message */
      getParent: function(message) {
        if (! message.parentNode ||
            ! message.parentNode.classList.contains('replies') ||
            ! message.parentNode.parentNode ||
            ! message.parentNode.parentNode.classList.contains('message')) {
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
            ! message.parentNode.parentNode.classList.contains('message')) {
          return null;
        } else {
          return message.parentNode.parentNode;
        }
      },
      /* Get all the (direct) replies to a message */
      getReplies: function(message) {
        var children;
        if (message.classList.contains('message')) {
          var lc = message.lastElementChild;
          if (! lc || ! lc.classList.contains('replies'))
            return [];
          children = lc;
        } else {
          children = message;
        }
        var ret = [];
        for (var i = 0; i < children.length; i++) {
          if (children[i].classList.contains('message'))
            ret.push(children[i]);
        }
        return ret;
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
        if (parent.classList.contains('message')) {
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
          var par = Instant.message.parentMessage(message);
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
  /* To be assigned in window */
  return Instant;
}();

function init() {
  var wrapper = $id('load-wrapper');
  var main = $id('main');
  wrapper.style.boxShadow = '0 0 30px #808080';
  $id('loading-message').style.display = 'none';
  var m = $id('splash-messages');
  Instant.message.addReply({id: 'loading-0-wait', nick: 'Loading',
    text: 'Please wait...'}, m);
  var isIE = /*@cc_on!@*/0;
  if (isIE) Instant.message.addReply({id: 'loading-1-ie', nick: 'Doom',
    text: '/me awaits IE users...'}, m);
  if (! Instant.connectionURL) {
    var c = Instant.message.addReply({id: 'loading-2-conn',
      nick: 'Connection', text: '/me is missing.'}, m);
    Instant.message.addReply({id: 'loading-3-comment', nick: 'Loading',
      text: 'Prepare for a long wait...', parent: 'loading-2-conn'});
    Instant.message.addReply({id: 'loading-4-comment', nick: 'Loading',
      text: 'Or, try solving the issue somehow.', parent: 'loading-2-conn'});
  }
}
