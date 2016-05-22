
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
      /* Return whether a message has direct replies (and therefore replies
       * at all) */
      hasReplies: function(message) {
        var children = Instant.message._getReplyNode(message);
        if (! children) return false;
        for (var i = 0; i < children.length; i++) {
          if (children[i].classList.contains('message'))
            return true;
        }
        return false;
      },
      /* Get all the (direct) replies to a message */
      getReplies: function(message) {
        var children = Instant.message._getReplyNode(message), ret = [];
        if (! children) return ret;
        for (var i = 0; i < children.length; i++) {
          if (children[i].classList.contains('message'))
            ret.push(children[i]);
        }
        return ret;
      },
      /* Get the nth reply to the message, counting from the beginning */
      getReply: function(message, n) {
        var replies = Instant.message._getReplyNode(message);
        if (! replies) return null;
        if (! n) n = 0;
        for (var i = 0, j = 0; i < replies.length; i++) {
          if (! replies[i].classList.contains('message')) continue;
          if (j++ == n) return reples[i];
        }
        return null;
      },
      /* Get the nth reply to the message, counting from the end */
      getLastReply: function(message, n) {
        var replies = Instant.message._getReplyNode(message);
        if (! replies) return null;
        if (! n) n = 0;
        for (var i = replies.length - 1, j = 0; i >= 0; i--) {
          if (! replies[i].classList.contains('message')) continue;
          if (j++ == n) return reples[i];
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
        /* Exclude input bar */
        while (t >= 0 && ! array[t].classList.contains('message'))
          last = array[t--];
        if (t < 0) return last;
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
          sizerNick.textContent = inputNick.value;
          sizerNick.style.background = 'hsl(' +
          Instant.nick.hueHash(inputNick.value) + ', 75%, 80%)';
          if (inputNick.value) {
            sizerNick.style.minWidth = '';
          } else {
            sizerNick.style.minWidth = '1em';
          }
        }
        function updateMessage() {
          inputMsg.style.height = '0';
          inputMsg.style.height = inputMsg.scrollHeight + 'px';
          promptNick.style.display = 'none';
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
          }
        });
        /* Update status when nick changes */
        inputNick.addEventListener('change', function() {
          Instant.identity.nick = inputNick.value;
          console.log('nick', Instant.identity.nick);
        });
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
          if (event.keyCode == 13 && ! event.shiftKey) { // Return
            /* Send message! */
            /* Retrieve input text */
            var text = inputMsg.value;
            inputMsg.value = '';
            event.preventDefault();
            updateMessage();
            /* Ignore empty sends */
            if (! text) return;
            /* Currently only fake messages */
            var msgid = 'local-' + leftpad(fakeSeq++, 8, '0');
            Instant.message.importMessage(
              {id: msgid, nick: Instant.identity.nick || '', text: text,
                parent: Instant.input.getParentID(), timestamp: Date.now()},
              Instant.message.getRoot(inputNode));
          }
        });
        /* Focus the nick input */
        inputNick.focus();
        Instant.identity.nick = inputNick.value;
        inputNick.selectionStart = inputNick.value.length;
        inputNick.selectionEnd = inputNick.value.length;
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
            inputNode.parentNode == Instant.getReplies(message)) {
          Instant.input.jumpTo(Instant.message.getParent(message));
        } else {
          Instant.input.jumpTo(message);
        }
      },
      /* Move the input bar relative to its current position */
      navigateInput: function(direction) {
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
            if (! prev) return;
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
            break;
          case 'down':
            /* Special case: message without replies or successor */
            var par = Instant.message.getParentMessage(inputNode);
            if (! Instant.message.hasReplies(par) &&
                ! Instant.message.getSuccessor(par)) {
              Instant.input.jumpTo(Instant.message.getParent(par));
              return;
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
              return;
            }
            /* Descend into the current message as far as possible */
            par = next;
            while (Instant.message.hasReplies(par)) {
              par = Instant.message.getReply(par);
            }
            /* Settle here */
            Instant.input.jumpTo(par);
            break;
          case 'left':
            /* Switch to the parent of the current host (or to the root) */
            Instant.input.jumpTo(
              Instant.message.getParentMessage(inputNode) || root);
            break;
          case 'right':
            /* Switch to the last child to the current host (if any) */
            var child = Instant.message.getLastReply(
              Instant.message.getParent(inputNode));
            if (child) Instant.input.jumpTo(child);
            break;
          case 'root':
            /* Just return to the root */
            Instant.input.jumpTo(root);
            break;
          default:
            throw new Error('Invalid direction for navigateInput: ' +
              direction);
        }
      }
    };
  }();
  /* To be assigned in window */
  return Instant;
}();

function init() {
  function hideGreeter() {
    var wrapper = $id('load-wrapper');
    wrapper.style.marginTop = '-30px';
    wrapper.style.opacity = '0';
    setTimeout(function() { wrapper.style.display = 'none'; }, 1000);
  }
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
  if (! Instant.connectionURL && false) {
    var c = Instant.message.addReply({id: 'loading-2-conn',
      nick: 'Connection', text: '/me is missing.'}, m);
    Instant.message.addReply({id: 'loading-3-comment', nick: 'Loading',
      text: 'Prepare for a long wait...', parent: 'loading-2-conn'});
    Instant.message.addReply({id: 'loading-4-comment', nick: 'Loading',
      text: 'Or, try solving the issue somehow.', parent: 'loading-2-conn'});
  } else {
    /* Testing modifications */
    $sel('.room-name').innerHTML = '<i>local</i>';
    $sel('.online-status').style.background = '#808080';
    $sel('.settings').style.display = 'none';
    main.style.opacity = '1';
    document.documentElement.addEventListener('keydown', function(event) {
      if (event.keyCode == 27) {
        $sel('.input-message', main).focus();
        event.preventDefault();
      }
    });
    Instant.input.init($sel('.input-bar', main));
    hideGreeter();
  }
}
