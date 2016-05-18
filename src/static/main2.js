
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
          hueHashCache[name] = hash;
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
    var nodes = {};
    /* Locale-agnostic abbreviated month name table */
    var MONTH_NAMES = { 1: 'Jan',  2: 'Feb',  3: 'Mar',  4: 'Apr',
                        5: 'May',  6: 'Jun',  7: 'Jul',  8: 'Aug',
                        9: 'Sep', 10: 'Oct', 11: 'Nov', 12: 'Dec'};
    /* Interesting substring regex.
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
    var mc = /[^.,:;!?()\s]/;
    var ALLOW_AROUND = /[^a-zA-Z0-9_]|^|$/;
    var INTERESTING = (
      '\\B&([a-zA-Z](?:[a-zA-Z0-9_-]*[a-zA-Z0-9])?)\\b|' +
      '<(((?!javascript:)[a-zA-Z]+://)?([a-zA-Z0-9._~-]+@)?' +
        '([a-zA-Z0-9.-]+)(:[0-9]+)?(/[^>]*)?)>|' +
      '\\B(@%%MC%%+(?:\\(%MC%*\\)%MC%*)*)|' +
      '((?:[+-]1|:[D)|/(CSP\\\\oO]|[SD)/|(C\\\\oO]:|\\^\\^|;\\))(?=%AA%))|' +
      '((?:\\*+([^*\\s]+)\\*+|\\*+([^*\\s]+)|([^*\\s]+)\\*+)(?=%AA%))'
      ).replace(/%MC%/g, mc).replace(/%AA%/g, ALLOW_AROUND);
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
        var out = [], l = 0;
        /* Extract the individual goodies from the input */
        for (;;) {
          /* Match regex */
          var m = re.exec(text);
          if (m == null) {
            /* Append ending */
            s = msgo.text.substring(l);
            if (s) out.push(s);
            break;
          }
          /* Calculate beginning and end */
          var start = m.index, end = m.index + m[0].length;
          /* Insert text between matches */
          var s = msgo.text.substring(l, start);
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
          if (typeof e != 'object' || e.nodeType != undefined) continue;
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
          var top = stack[stack.length - 1];
          /* Drain non-metadata parts into the current node */
          if (typeof e == 'string') {
            top.appendChild(document.createTextNode(e));
          } else if (e.nodeType != undefined) {
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
            stack.push(node);
          } else if (e.emphRem) {
            stack.pop();
          }
        }
        /* Done! */
        return stack[0];
      }
    };
  }();
  /* To be assigned in window */
  return Instant;
}();
