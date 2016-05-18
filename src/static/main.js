
window.Instant = (window.Instant || {});

(function() {
  /* Connection preparation. */
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
})();

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

/* Change window title */
if (Instant.roomName) {
  Instant.baseTitle = '&amp;' + $esc(Instant.roomName) + ' &mdash; Instant';
  $sel('title').innerHTML = Instant.baseTitle;
}

(function() {
  var MONTH_NAMES = { 1: 'Jan',  2: 'Feb',  3: 'Mar',  4: 'Apr',
                      5: 'May',  6: 'Jun',  7: 'Jul',  8: 'Aug',
                      9: 'Sep', 10: 'Oct', 11: 'Nov', 12: 'Dec'};
  var NOTIFY_LEVELS = {'none': 0, 'ping': 1, 'reply': 2, 'any': 3};
  var INACTIVITY_TIMEOUT = 600000;
  /* Groupings:  1: Room name matched
   *             2: Full URL matched
   *                3: Scheme (with colon and double slash)
   *                4: Username with "@"
   *                5: Hostname
   *                6: Port
   *                7: Path
   *             8: Nick-name @-mentioned
   *             9: Smiley (space before must be ensured)
   *            10: Text with highlighting marker(s) (check space before)
   *                11: Markers before and after (excluded)
   *                12: Marker before (excluded)
   *                13: Marker after (excluded)
   */
  var mc = '[^.,:;!?()\\s]';
  var INTERESTING = ('\\B&([a-zA-Z](?:[a-zA-Z0-9_-]*[a-zA-Z0-9])?)\\b|' +
    '<(((?!javascript:)[a-zA-Z]+://)?([a-zA-Z0-9._~-]+@)?([a-zA-Z0-9.-]+)' +
    '(:[0-9]+)?(/[^>]*)?)>|\\B@(%%MC%%+(?:\\(%%MC%%*\\)%%MC%%*)*)|' +
    '((?:[+-]1|:[D)|/(CSP\\\\oO]|[SD)/|(C\\\\oO]:|\\^\\^|;\\))(?=\\W|$))|' +
    '((?:\\*+([^*\\s]+)\\*+|\\*+([^*\\s]+)|([^*\\s]+)\\*+)(?=\\W|$))'
    ).replace(/%%MC%%/g, mc);
  var ALLOW_BEFORE = /[\s()[\]{}]/;
  var ALLOW_MENTION = /^@[^.,:;!?\s]+$/;
  var SMILEYS = {'+1': '#008000', '-1': '#c00000',
    ':D': '#c0c000', ':)': '#c0c000', ':|': '#c0c000', ':/': '#c0c000',
    ':(': '#c0c000', ':C': '#c0c000', ':S': '#c0c000', ':P': '#c0c000',
    'S:': '#c0c000', 'D:': '#c0c000', '):': '#c0c000', '/:': '#c0c000',
    '|:': '#c0c000', '(:': '#c0c000', 'C:': '#c0c000',
    ':O': '#c0c000', ':o': '#c0c000', 'o:': '#c0c000', 'O:': '#c0c000',
    ';)': '#c0c000', '^^': '#c0c000',
    ':\\': '#c0c000', '\\:': '#c0c000'
  };
  /* Hint for developer tools users */
  window.logInstantMessages = false;
  /* Nick-names */
  var hueHashCache = {};
  function normalizeNick(name) {
    return name.replace(/\s+/g, '').toLowerCase();
  }
  function hueHashRaw(name) {
    var hash = 0;
    for (var i = 0; i < name.length; i++) {
      hash += name.charCodeAt(i) * 359 + i * 271 + 89;
      hash = (hash * hash * hash) % 360;
    }
    return hash;
  }
  function hueHash(name) {
    name = normalizeNick(name);
    var hash = hueHashCache[name];
    if (! hash) {
      hash = hueHashRaw(name);
      hueHashCache[name] = hash;
    }
    return hash;
  }
  function makeNick(text) {
    var node = document.createElement('span');
    var hue = hueHash(text);
    node.class = 'nick';
    node.textContent = text;
    node.style.background = 'hsl(' + hue + ', 75%, 80%)';
    return node;
  }
  /* Message construction */
  var messages = {};
  function makeMessage(data) {
    function zpad(n, l) {
      var ret = n.toString();
      while (ret.length < l) ret = '0' + ret;
      return ret;
    }
    var id = data['id'], nick = data['nick'], text = data['text'];
    var timestamp = data['timestamp'], emote = data['emote'];
    var date = new Date(timestamp);
    var msgNode = document.createElement('div');
    msgNode.id = 'message-' + id;
    msgNode.setAttribute('data-id', id);
    if (emote) {
      msgNode.className = 'message emote';
    } else {
      msgNode.className = 'message';
    }
    if (data.ping)
      msgNode.className += ' ping';
    if (data.isNew)
      msgNode.className += ' new';
    if (data.mine)
      msgNode.className += ' mine';
    msgNode.innerHTML = '<div class="line"><span class="time-wrapper">' +
      '<time></time></span><span class="nick-wrapper"><span ' +
      'class="hidden" data-key="indent"></span><span class="hidden">&lt;' +
      '</span><span class="nick"></span><span data-key="after-nick" ' +
      'class="hidden">&gt; </span></span><span class="content"><span ' +
      'class="text"></span></span></div>';
    var hue = Instant.hueHash(nick);
    msgNode.setAttribute('data-hue', hue);
    $sel('.nick', msgNode).style.background = 'hsl(' + hue + ', 75%, 80%)';
    $sel('.nick', msgNode).textContent = nick;
    if (typeof text == 'string') {
      $sel('.text', msgNode).textContent = text;
    } else {
      var textNode = $sel('.text', msgNode);
      for (var i = 0; i < text.length; i++)
        textNode.appendChild(text[i]);
    }
    if (emote) {
      $sel('[data-key=after-nick]', msgNode).textContent += '/me ';
      $sel('.content', msgNode).style.background = 'hsl(' + hue +
        ', 75%, 90%)';
    }
    var time = $sel('time', msgNode);
    time.setAttribute('datetime', date.toISOString());
    time.title = (zpad(date.getFullYear(), 4) + '-' +
      MONTH_NAMES[date.getMonth() + 1] + '-' + zpad(date.getDate(), 2) + ' ' +
      zpad(date.getHours(), 2) + ':' + zpad(date.getMinutes(), 2) + ':' +
      zpad(date.getSeconds(), 2));
    time.textContent = (zpad(date.getHours(), 2) + ':' +
      zpad(date.getMinutes(), 2));
    msgNode.addEventListener('mousedown', function(event) {
      var el = $sel('[data-was-active=true]');
      if (el) el.removeAttribute('data-was-active');
      if (document.activeElement)
        document.activeElement.setAttribute('data-was-active', true);
      event.stopPropagation();
    });
    msgNode.addEventListener('click', function(event) {
      var bar = $sel('.input-bar', Instant.mainPane);
      var sparent = getParent(msgNode);
      var bparent = getParent(bar);
      if (! $sel('.input-bar', sparent) || bparent == msgNode) {
        updateInput(getParent(msgNode) ||
          $sel('.message-pane', Instant.mainPane), 'last-active');
      } else {
        updateInput(msgNode, 'last-active');
      }
      event.stopPropagation();
    });
    messages[id] = msgNode;
    return msgNode;
  }
  function preprocessMessage(msgo) {
    function makeSpan(text, color) {
      var node = document.createElement('span');
      node.textContent = text;
      if (color != undefined)
        node.style.color = color;
      return node;
    }
    function addch(node, el) {
      if (node.push) {
        node.push(el);
      } else {
        node.appendChild(el);
      }
    }
    function fmtdis(f) {
      f.disabled = true;
      if (f.node) f.node.style.color = '';
      if (f.node2) f.node2.style.color = '';
    }
    // Strip emote prefix / whitespace.
    if (/^\/me/.test(msgo.text)) {
      msgo.text = msgo.text.substring(3);
      msgo.emote = true;
    } else {
      msgo.emote = false;
    }
    msgo.ping = false;
    msgo.mine = (Instant.identity && msgo.from &&
        msgo.from == Instant.identity);
    msgo.text = msgo.text.trim();
    msgo.rawText = msgo.text;
    // Find room references, other stuff, and highlight all of that.
    var out = [], regex = new RegExp(INTERESTING, 'g');
    var s, l = 0;
    for (;;) {
      var m = regex.exec(msgo.text);
      if (m == null) {
        s = msgo.text.substring(l);
        if (s) out.push(s);
        break;
      }
      var start = m.index, end = m.index + m[0].length;
      s = msgo.text.substring(l, start);
      if (s) out.push(s);
      l = end;
      if (m[1]) {
        var node = document.createElement('a');
        node.href = '/room/' + m[1] + '/';
        node.target = '_blank';
        node.textContent = m[0];
        out.push(node);
      } else if (m[2] && /[^\w-_]/.test(m[2])) {
        out.push(makeSpan('<', '#808080'));
        var node = document.createElement('a');
        var url = m[2];
        if (! m[3]) url = 'http://' + url;
        node.href = url;
        node.target = '_blank';
        node.textContent = m[2];
        out.push(node);
        out.push(makeSpan('>', '#808080'));
      } else if (m[8]) {
        out.push(makeSpan(m[0], 'hsl(' + Instant.hueHash(m[8]) +
          ', 75%, 40%)'));
        if (normalizeNick(m[8]) == normalizeNick(Instant.nickname)) {
          msgo.ping = true;
        }
      } else if (m[9]) {
        var before = msgo.text.substr(start - 1, 1);
        if (start > 0 && ! ALLOW_BEFORE.test(before)) {
          out.push(m[0]);
        } else {
          var node = makeSpan(m[9], SMILEYS[m[9]]);
          node.style.fontWeight = 'bold';
          out.push(node);
        }
      } else if (m[10]) {
        var pref = $prefLength(m[10], '*');
        var suff = $suffLength(m[10], '*');
        var prefStr = m[10].substring(0, pref);
        var suffStr = m[10].substring(m[10].length - suff);
        var before = msgo.text.substr(start - 1, 1);
        if (start > 0 && ! ALLOW_BEFORE.test(before)) {
          out.push(m[0]);
        } else if (m[11]) {
          var ast = makeSpan(prefStr, '#808080'), fmt;
          out.push(ast);
          if (pref == suff) {
            if (pref == 1) {
              fmt = {italic: true, node: ast};
            } else if (pref == 2) {
              fmt = {bold: true, node: ast};
            } else if (pref >= 3) {
              fmt = {italic: true, bold: true, node: ast};
            }
            out.push(fmt);
            out.push(m[11]);
            ast = makeSpan(suffStr, '#808080');
            if (fmt) {
              var nfmt = {node: ast};
              if (fmt.italic) nfmt.italic = false;
              if (fmt.bold) nfmt.bold = false;
              out.push(nfmt);
            }
            out.push(ast);
          } else {
            out.push(m[10]);
          }
        } else if (m[12]) {
          var ast = makeSpan(prefStr, '#808080');
          out.push(ast);
          if (pref == 1) {
            out.push({italic: true, node: ast});
          } else if (pref == 2) {
            out.push({bold: true, node: ast});
          } else if (pref >= 3) {
            out.push({italic: true, bold: true, node: ast});
          }
          out.push(m[12]);
        } else if (m[13]) {
          out.push(m[13]);
          var ast = makeSpan(suffStr, '#808080');
          if (suff == 1) {
            out.push({italic: false, node: ast});
          } else if (suff == 2) {
            out.push({bold: false, node: ast});
          } else if (suff >= 3) {
            out.push({italic: false, bold: false, node: ast});
          }
          out.push(ast);
        }
      } else if (m[0]) {
        out.push(m[0]);
      }
    }
    var tstack = [];
    for (var i = 0; i < out.length; i++) {
      var e = out[i];
      if (typeof e != 'object' || e.nodeType !== undefined) continue;
      if (e.italic === true || e.bold === true) {
        tstack.push(e);
      } else if (e.italic === false || e.bold === false) {
        var l = tstack[tstack.length - 1];
        if (! l) {
          fmtdis(e);
          continue;
        }
        var il = {};
        if (l.italic) il.italic = false;
        if (l.bold) il.bold = false;
        if (l && e.italic === il.italic && e.bold === il.bold) {
          tstack.pop();
        } else {
          fmtdis(e);
        }
      }
    }
    for (var i = 0; i < tstack.length; i++) fmtdis(tstack[i]);
    tstack = [[]];
    for (var i = 0; i < out.length; i++) {
      if (typeof out[i] == 'object' && out[i].nodeType === undefined) {
        if (out[i].disabled) continue;
        if (out[i].italic === false || out[i].bold === false) {
          tstack.pop();
        } else {
          var node = document.createElement('span');
          addch(tstack[tstack.length - 1], node);
          if (out[i].italic) node.style.fontStyle = 'italic';
          if (out[i].bold) node.style.fontWeight = 'bold';
          tstack.push(node);
        }
      } else if (typeof out[i] == 'string') {
        addch(tstack[tstack.length - 1], document.createTextNode(out[i]));
      } else {
        addch(tstack[tstack.length - 1], out[i]);
      }
    }
    msgo.text = tstack[0];
  }
  function createMessage(id, nick, text, emote, process) {
    var msgo = {id: id, nick: nick, text: text, emote: emote,
                timestamp: Date.now()};
    if (process) preprocessMessage(msgo);
    return makeMessage(msgo);
  }
  function getParent(msg) {
    do {
      msg = msg.parentNode;
    } while (msg && ! (msg.classList && msg.classList.contains('message')));
    return msg;
  }
  function getPreceding(msg) {
    if (! msg) return null;
    var par = getParent(msg);
    if (! (par && par.classList.contains('message'))) return null;
    var prev = msg.previousElementSibling;
    if (prev && prev.classList.contains('message')) {
      return prev;
    } else {
      return par;
    }
  }
  function getMessageID(msg) {
    while (msg && ! (msg.classList && msg.classList.contains('message')))
      msg = msg.parentNode;
    if (msg) return msg.getAttribute('data-id');
    return null;
  }
  function makeReplies(msg) {
    var replies = $sel('.replies', msg);
    if (! replies || replies.parentNode != msg) {
      replies = document.createElement('div');
      replies.className = 'replies';
      var clr = $sel('.nick', msg).style.backgroundColor
      replies.style.borderColor = clr;
      msg.appendChild(replies);
    }
    return replies;
  }
  function addComment(parent, msg, _nested) {
    var pendingParent = null;
    if (parent == null) {
      parent = $sel('.message-pane', Instant.mainPane);
    } else if (typeof parent == 'string') {
      var parStr = parent;
      parent = messages[parent];
      if (! parent) {
        if (! Instant.logs.pending[parStr])
          Instant.logs.pending[parStr] = [];
        var msgid = (msg.getAttribute) ? msg.getAttribute('data-id') :
          msg.id;
        Instant.logs.pending[parStr].push(msgid);
        pendingParent = parStr;
        parent = $sel('.message-pane', Instant.mainPane);
      }
    }
    if (! parent) throw 'Trying to add comment without parent!';
    if (typeof msg == 'object' && msg.nodeType === undefined) {
      Instant.preprocessMessage(msg);
      msg = Instant.makeMessage(msg);
    }
    if (pendingParent)
      msg.setAttribute('data-pending-parent', pendingParent);
    if (parent.classList.contains('message')) {
      var replies = makeReplies(parent);
      if (! _nested) {
        var pi = parent.getAttribute('data-indent');
        var mi = (pi || '') + '| ';
        if (mi) msg.setAttribute('data-indent', mi);
        $sel('[data-key=indent]', msg).textContent = mi;
      }
      replies.appendChild(msg);
    } else {
      parent.appendChild(msg);
    }
    var msgid = msg.getAttribute('data-id');
    if (Instant.logs.pending.hasOwnProperty(msgid)) {
      var pending = Instant.logs.pending[msgid];
      delete Instant.logs.pending[msgid];
      for (var i = 0; i < pending.length; i++) {
        messages[pending[i]].removeAttribute('data-pending-parent');
        Instant.addComment(msg, messages[pending[i]], true);
      }
      Instant.sortComments(msg);
      Instant.updateIndents(msg);
    }
    if (Instant.blurred) {
      Instant.updateUnread(Instant.unreadMessages + 1,
        Instant.unreadPings || msg.classList.contains('ping'));
    }
    updateOffscreenSingle(msg);
    return msg;
  }
  function updateIndents(msg, _nested) {
    var parent = getParent(msg);
    var mi;
    if (! parent || ! parent.getAttribute) {
      mi = '';
    } else {
      mi = (parent.getAttribute('data-indent') || '') + '| ';
    }
    if (mi) msg.setAttribute('data-indent', mi);
    $sel('[data-key=indent]', msg).textContent = mi;
    if (_nested) return;
    var replies = makeReplies(msg);
    if (replies) {
      var children = $selAll('.message', replies);
      for (var i = 0; i < children.length; i++) {
        updateIndents(children[i], true);
      }
    }
  }
  function sortComments(parent) {
    if (parent == null) {
      parent = $sel('.message-pane', Instant.mainPane);
    } else if (typeof parent == 'string') {
      parent = messages[parent];
      if (! parent) {
        parent = $sel('.message-pane', Instant.mainPane);
      } else {
        parent = makeReplies(parent);
        if (! parent) return;
      }
    } else if (parent.classList.contains('message')) {
      parent = makeReplies(parent);
      if (! parent) return;
    }
    if (! parent) throw 'Trying to sort nonexisting message!';
    var children = [], och = parent.children;
    for (var i = 0; i < och.length; i++) {
      children.push(och[i]);
    }
    children.sort(function(a, b) {
      if (! a.classList.contains('message')) {
        if (! b.classList.contains('message')) {
          return (a < b) ? -1 : (a > b) ? 1 : 0;
        } else {
          return 1;
        }
      } else if (! b.classList.contains('message')) {
        return -1;
      }
      var ida = a.getAttribute('data-id');
      var idb = b.getAttribute('data-id');
      var ppa = a.getAttribute('data-pending-parent');
      var ppb = b.getAttribute('data-pending-parent');
      if (ppa && ppb) {
        if (ppa < ppb) return -1;
        if (ppa > ppb) return 1;
      } else if (ppa) {
        return (ppa < idb) ? -1 : (ppa > idb) ? 1 : 0;
      } else if (ppb) {
        return (ida < ppb) ? -1 : (ida > ppb) ? 1 : 0;
      }
      return (ida < idb) ? -1 : (ida > idb) ? 1 : 0;
    });
    for (var i = 0; i < children.length; i++) {
      parent.appendChild(children[i]);
    }
  }
  /* Input bar management */
  function _findPane(el) {
    return $parentWithClass(el, 'message-pane');
  }
  function _findWrapper(el) {
    return $parentWithClass(el, 'message-pane-wrapper');
  }
  function _prepareScroll(bar) {
    var pane = _findPane(bar);
    var wr = _findWrapper(pane);
    if (wr) {
      return pane.offsetTop + bar.offsetTop + bar.offsetHeight -
        wr.scrollTop;
    } else {
      return null;
    }
  }
  function _applyScroll(bar, cookie) {
    if (cookie != null) {
      var pane = _findPane(bar);
      var wr = _findWrapper(pane);
      if (! wr) return;
      wr.scrollTop = pane.offsetTop + bar.offsetTop + bar.offsetHeight -
        cookie;
    }
  }
  function _boundScroll(bar) {
    if (! bar) return;
    var wr = _findWrapper(bar);
    var brect = bar.getBoundingClientRect();
    var wrect = wr.getBoundingClientRect();
    if ((wrect.bottom - wrect.top) - (brect.bottom - brect.top) > 100) {
      if (brect.bottom > wrect.bottom - 50) {
        wr.scrollTop += brect.bottom - (wrect.bottom - 50);
      } else if (brect.top < wrect.top + 50) {
        wr.scrollTop -= (wrect.top + 50) - brect.top;
      }
    }
  }
  function _updateInput(parent, focus, scrollTo) {
    function chfocus(el) {
      if (el && el != document.activeElement) el.focus();
    }
    var bar = $sel('.input-bar', Instant.mainPane);
    var el = document.activeElement;
    if (! parent) {
      parent = bar.parentNode;
    } else if (typeof parent == 'string') {
      if (! messages[parent]) parent = bar.parentNode;
      parent = makeReplies(messages[parent]);
    } else if (parent.classList.contains('message')) {
      parent = makeReplies(parent);
    }
    if (parent != bar.parentNode || bar.nextElementSibling) {
      parent.appendChild(bar);
    }
    if (focus == 'bar') {
      chfocus($sel('.input-message', bar));
    } else if (focus == 'last-active') {
      chfocus($sel('[data-was-active=true]'));
    } else if (focus == 'active') {
      chfocus(el);
    } else if (typeof focus != 'string' && focus) {
      chfocus(focus);
    }
    _applyScroll(bar, scrollTo);
    if (scrollTo) {
      var wr = _findWrapper(bar);
      var st = wr.scrollTop;
      setTimeout(function() { wr.scrollTop = st; }, 0);
      setTimeout(function() { wr.scrollTop = st; }, 100);
    }
  }
  function prepareInputUpdate() {
    var focus = document.activeElement;
    var bar = $sel('.input-bar', Instant.mainPane);
    var scrollTo = _prepareScroll(bar);
    return function(parent) {
      _updateInput(parent, focus, scrollTo);
    };
  }
  function updateInput(parent, focus) {
    _updateInput(parent, focus);
  }
  function navigateInput(dir) {
    var bar = $sel('.input-bar', Instant.mainPane);
    var prev = bar.previousElementSibling;
    var parent = null;
    var main = $sel('.message-pane', Instant.mainPane);
    if (dir == 'root') {
      // Return into the main thread.
      parent = main;
    } else if (dir == 'up') {
      if (prev && ! $sel('.message', prev)) {
        // Sibling of message without children? Comment.
        parent = prev;
      } else {
        var allowNest = false;
        if (! prev) {
          // Try to find a parent with a previous sibling.
          for (prev = bar; prev; prev = getParent(prev)) {
            if (! prev.classList.contains('message'))
              continue;
            if (prev.previousElementSibling &&
                prev.previousElementSibling.classList.contains('message')) {
              prev = prev.previousElementSibling;
              break;
            }
          }
          allowNest = ($sel('.message', prev) == null);
        }
        if (prev) {
          // Ascend until just after most nested child.
          while (prev && (allowNest || $sel('.message', prev))) {
            parent = prev;
            var replies = $sel('.replies', prev);
            prev = null;
            if (replies) {
              var ch = replies.children;
              for (var i = 0; i < ch.length; i++) {
                if (ch[i].classList.contains('message')) prev = ch[i];
              }
            }
          }
        }
      }
    } else if (dir == 'down') {
      var p = getParent(bar);
      if (p) {
        if (! prev && ! p.nextElementSibling) {
          // Commenting on post without successors? Direct reply.
          parent = getParent(p) || main;
        } else {
          parent = p;
          // Search parent with successor.
          while (parent && ! parent.nextElementSibling)
            parent = getParent(parent);
          if (parent) {
            parent = parent.nextElementSibling;
            // Descend as deeply as possible.
            for (;;) {
              var c = $sel('.message', parent);
              if (! c) break;
              parent = c;
            }
          } else {
            parent = main;
          }
        }
      }
    } else if (dir == 'left') {
      // Select parent of current message.
      parent = getParent(bar);
      if (parent) parent = getParent(parent);
      parent = parent || main;
    } else if (dir == 'right') {
      // Comment to previous sibling.
      if (prev) {
        parent = prev;
      }
    }
    Instant.updateInput(parent, 'active');
    _boundScroll(bar);
  }
  /* Miscellaneous */
  function toggleSettings() {
    var btn = $sel('.settings', Instant.mainPane);
    var cnt = $sel('.settings-content', Instant.mainpane);
    var vis = (btn.getAttribute('data-visible') != 'yes');
    btn.setAttribute('data-visible', (vis) ? 'yes' : 'no');
    if (vis) {
      btn.style.borderRadius = '0';
      btn.style.borderBottomWidth = '0';
      btn.style.paddingBottom = '1px';
      btn.style.boxShadow = 'none';
      cnt.style.display = 'block';
    } else {
      btn.style.borderRadius = '';
      btn.style.borderBottomWidth = '';
      btn.style.paddingBottom = '';
      btn.style.boxShadow = '';
      cnt.style.display = '';
    }
  }
  function setNotificationStatus(notifies) {
    if (notifies == 'none') {
      Instant.notifies = 'none';
      return;
    }
    if (Notification.permission == 'default') {
      // Work around partial support.
      var ret = Notification.requestPermission(function() {
        setNotificationStatus(notifies);
      });
      if (ret && ret.then) ret.then(function() {
        setNotificationStatus(notifies);
      });
      return;
    }
    if (Notification.permission == 'denied') {
      Instant.notifies = 'none';
      $id('notifies-none').checked = true;
      return;
    }
    Instant.notifies = notifies;
  }
  function checkNotifications(msg) {
    var notifyLevel = 'any';
    if (msg.ping) {
      notifyLevel = 'ping';
    } else {
      var prev = getPreceding(messages[msg.id]);
      if (prev && prev.classList.contains('mine'))
        notifyLevel = 'reply';
    }
    if (NOTIFY_LEVELS[notifyLevel] <= NOTIFY_LEVELS[Instant.notifies]) {
      if (Notification.permission != 'granted' ||
          Instant.pendingNotification || ! Instant.blurred)
        return;
      var t;
      if (msg.emote) {
        t = '* ' + msg.nick + ' ' + msg.rawText;
      } else {
        t = '[' + msg.nick + '] ' + msg.rawText;
      }
      // Seems like I'm supposed just to construct it...
      // Icons just didn't want to work. :(
      var n = Instant.pendingNotification = new Notification(
        '&' + Instant.roomName, {body: t});
      n.onshow = function() {
        Instant.pendingNotification = n;
      }
      n.onclose = function() {
        Instant.pendingNotification = null;
      };
      n.onclick = function() {
        Instant.updateInput(messages[msg.id], 'bar');
        window.focus();
        n.close();
      };
      console.log("Notification:", n);
    }
  }
  function updateUnread(amount, pings) {
    Instant.unreadMessages = amount;
    Instant.unreadPings = pings;
    var title = $sel('title');
    if (amount) {
      title.innerHTML = '&amp;' + $esc(Instant.roomName) +
        ' (' + amount + ((pings) ? '!!' : '') + ')';
    } else {
      title.innerHTML = Instant.baseTitle;
    }
  }
  function updateOffscreenSingle(msg, noAlerts) {
    var wr = _findWrapper(msg);
    if (! wr) {
      msg.classList.remove('offscreen');
      return;
    }
    var mrect = msg.getBoundingClientRect();
    var wrect = wr.getBoundingClientRect();
    if (Instant.blurred ||
        mrect.bottom < wrect.top || mrect.top >= wrect.bottom ||
        mrect.right < wrect.left || mrect.left >= wrect.right) {
      msg.classList.add('offscreen');
    } else {
      msg.classList.remove('offscreen');
    }
    if (! noAlerts) updateOffscreenAlerts();
    return mrect;
  }
  function updateOffscreenAlerts() {
    var msgs = $selAll('.message.new.offscreen', Instant.mainPane);
    var input = $sel('.input-bar', Instant.mainPane);
    var irect = input.getBoundingClientRect();
    var above = null, below = null;
    for (var i = 0; i < msgs.length; i++) {
      var m = msgs[i];
      var mrect = m.getBoundingClientRect();
      if (mrect.top >= irect.bottom) {
        if (! below) below = m.getAttribute('data-id');
      }
      if (mrect.bottom <= irect.top) above = m.getAttribute('data-id');
    }
    var alertAbove = $sel('.above-alert', input);
    var alertBelow = $sel('.below-alert', input);
    alertAbove.style.visibility = (above) ? 'visible' : 'hidden';
    alertBelow.style.visibility = (below) ? 'visible' : 'hidden';
    alertAbove.setAttribute('data-scroll-message', above || '');
    alertBelow.setAttribute('data-scroll-message', below || '');
  }
  function updateOffscreen() {
    var msgs = $selAll('.message.offscreen', Instant.mainPane);
    for (var i = 0; i < msgs.length; i++) {
      updateOffscreenSingle(msgs[i], true);
    }
    updateOffscreenAlerts();
  }
  function updateScroll() {
    updateOffscreen();
    /* Possibly fetch more logs */
    if (Instant.logs.keys) {
      var first = messages[Instant.logs.keys[0]];
      if (! first) return;
      var wr = _findWrapper(first);
      if (! wr) return;
      var frect = first.getBoundingClientRect();
      var wrect = wr.getBoundingClientRect();
      if (frect.top >= wrect.top)
        Instant.logs.requestMore();
    }
  }
  /* User list */
  (function() {
    Instant.userList = Instant.userList || {};
    // Mapping of ID-s to DOM nodes.
    var content = {};
    /* Update the given node to display the users in order */
    function update(node) {
      function updateTimeout(ch, now) {
        var lastActive = ch.getAttribute('data-last-active') || now;
        var inactive = now - lastActive;
        var timeout = ch.getAttribute('data-timeout-id');
        if (timeout != null) clearTimeout(timeout);
        if (inactive < INACTIVITY_TIMEOUT) {
          ch.classList.add('active');
          ch.setAttribute('data-timeout-id', setTimeout(function() {
            ch.classList.remove('active');
          }, INACTIVITY_TIMEOUT - inactive));
        } else {
          ch.classList.remove('active');
        }
      }
      if (! node) node = $sel('.user-list', Instant.mainPane);
      var listed = {}, children = [];
      for (var i = 0; i < node.children.length; i++) {
        var child = node.children[i];
        var uid = child.getAttribute('data-id');
        if (content[uid] && content[uid].textContent) {
          listed[uid] = true;
          children.push(child);
        } else {
          node.removeChild(child);
          i--;
        }
      }
      for (var uid in content) {
        if (! content.hasOwnProperty(uid)) continue;
        if (listed[uid] || ! content[uid].textContent) continue;
        node.appendChild(content[uid]);
        children.push(content[uid]);
        content[uid].setAttribute('data-id', uid);
      }
      children.sort(function(a, b) {
        var ta = $sel('.nick', a).textContent.toUpperCase();
        var tb = $sel('.nick', b).textContent.toUpperCase();
        return (ta == tb) ? 0 : (ta > tb) ? 1 : -1;
      });
      var now = Date.now();
      for (var i = 0; i < children.length; i++) {
        var ch = children[i];
        updateTimeout(ch, now);
        node.appendChild(ch);
      }
      /* Ugly sizing hacks */
      var innerWrapper = $parentWithClass(node,
        'user-list-inner-wrapper');
      var outerWrapper = $parentWithClass(innerWrapper,
        'user-list-wrapper');
      outerWrapper.style.width = (innerWrapper.offsetWidth + 20) + 'px';
    }
    /* Add user to list, or update existing entry */
    function add(uid, nick) {
      var node = content[uid];
      if (! node) {
        node = document.createElement('div');
        node.className = 'nick-wrapper';
        node.appendChild(document.createElement('span'));
        node.lastChild.className = 'nick';
        content[uid] = node;
      }
      var n = $sel('.nick', node);
      n.textContent = nick;
      n.style.background = 'hsl(' + Instant.hueHash(nick) +
        ', 75%, 80%)';
      node.setAttribute('data-last-active', Date.now());
    }
    /* Remove user from list */
    function remove(uid) {
      delete content[uid];
    }
    /* Clear the list */
    function clear() {
      for (var key in content) {
        if (! content.hasOwnProperty(key)) continue;
        delete content[key];
      }
    }
    /* Try to tab-complete a nick */
    function complete(nick) {
      var candidates = [];
      for (key in content) {
        if (! content.hasOwnProperty(key)) continue;
        var n = $sel('.nick', content[key]).textContent;
        n = n.replace(/\s+/g, "");
        if (n.substr(0, nick.length) != nick) continue;
        candidates.push(n.substr(nick.length));
      }
      if (! candidates) return '';
      var prefix = candidates[0];
      for (var i = 1; i < candidates.length; i++) {
        var c = candidates[i];
        while (c.substr(0, prefix.length) != prefix)
          prefix = prefix.substr(0, prefix.length - 1);
        if (! prefix) break;
      }
      return prefix;
    }
    /* Exports */
    Instant.userList.content = content;
    Instant.userList.update = update;
    Instant.userList.add = add;
    Instant.userList.remove = remove;
    Instant.userList.complete = complete;
    Instant.userList.clear = clear;
  })();
  /* Offline storage */
  (function() {
    Instant.storage = Instant.storage || {};
    var data = {};
    /* Extract state from storage */
    function load() {
      var frozen = localStorage.getItem('instant-data');
      var thawed = null;
      try {
        thawed = JSON.parse(frozen);
      } catch (e) {
        console.error(e);
      }
      if (thawed) {
        for (key in thawed) {
          if (! thawed.hasOwnProperty(key)) continue;
          data[key] = thawed[key];
        }
      }
      frozen = sessionStorage.getItem('instant-data');
      thawed = null;
      try {
        thawed = JSON.parse(frozen);
      } catch (e) {
        console.error(e);
      }
      if (thawed) {
        for (key in thawed) {
          if (! thawed.hasOwnProperty(key)) continue;
          data[key] = thawed[key];
        }
      }
    }
    /* Poke state into storage */
    function save() {
      var str = JSON.stringify(data);
      sessionStorage.setItem('instant-data', str);
      localStorage.setItem('instant-data', str);
    }
    /* Exports */
    Instant.storage.load = load;
    Instant.storage.save = save;
    Instant.storage.data = data;
  })();
  /* Logs management */
  (function() {
    Instant.logs = Instant.logs || {};
    var keys = [], messages = {};
    var pending = {};
    var farthestAvailable = null;
    var updateTimeout = null;
    /* Add a single new message */
    function add(msg) {
      keys.push(msg.id);
      messages[msg.id] = msg;
    }
    /* Merge the given messages in */
    function merge(msgs) {
      var seen = {}, ret = [];
      for (var i = 0; i < msgs.length; i++) {
        var m = msgs[i];
        if (! messages[m.id]) ret.push(m.id);
        messages[m.id] = m;
        keys.push(m.id);
      }
      keys.sort();
      for (var i = 0; i < keys.length; i++) {
        if (seen[keys[i]]) {
          keys.splice(i, 1);
          i--;
          continue;
        }
        seen[keys[i]] = true;
      }
      return ret;
    }
    /* Fetch some portion of the logs */
    function get(from, to, amnt) {
      function extract(f, t) {
        return keys.slice(Math.max(f, 0), Math.min(t, keys.length));
      }
      var fromidx = (from) ? keys.indexOf(from) : null;
      var toidx = (to) ? keys.indexOf(to) : null;
      var retkeys;
      if (fromidx == -1) fromidx = null;
      if (toidx == -1) toidx = null;
      if (fromidx != null && toidx != null) {
        retkeys = extract(fromidx, toidx + 1);
      } else if (fromidx != null) {
        if (amnt == null) amnt = keys.length;
        retkeys = extract(fromidx, fromidx + keys.length);
      } else if (toidx != null) {
        if (amnt == null) amnt = keys.length;
        retkeys = extract(toidx - amnt + 1, toidx + 1);
      } else if (amnt != null) {
        retkeys = extract(keys.length - amnt, amnt);
      } else {
        retkeys = keys;
      }
      var ret = [];
      for (var i = 0; i < retkeys.length; i++)
        ret.push(messages[retkeys[i]]);
      return ret;
    }
    /* Clear the logs */
    function clear() {
      for (var i = 0; i < keys.length; i++) {
        delete messages[keys[i]];
      }
      keys.length = 0;
    }
    /* Update the local logs if necessary. */
    function _handleLogInfo(msg) {
      var from = msg.data.from;
      if (from != null && (farthestAvailable == null ||
          from < farthestAvailable.from))
        farthestAvailable = {from: from, peer: msg.from};
      if (updateTimeout != null)
        clearTimeout(updateTimeout);
      updateTimeout = setTimeout(function() {
        hideGreeter();
        updateTimeout = null;
        if (farthestAvailable != null) {
          var payload = {type: 'log-request'};
          if (keys) payload.to = keys[0];
          Instant.connection.sendUnicast(farthestAvailable.peer,
                                         payload);
        }
      }, 1000);
    }
    function requestMore() {
      if (updateTimeout != null) return;
      if (farthestAvailable && keys && farthestAvailable.from &&
          keys[0] == farthestAvailable.from)
        return;
      Instant.connection.sendBroadcast({type: 'log-query'});
    }
    /* Exports */
    Instant.logs.keys = keys;
    Instant.logs.messages = messages;
    Instant.logs.pending = pending;
    Instant.logs.add = add;
    Instant.logs.merge = merge;
    Instant.logs.get = get;
    Instant.logs.clear = clear;
    Instant.logs.requestMore = requestMore;
    Instant.logs._handleLogInfo = _handleLogInfo;
  })();
  /* Initialization */
  function initConnection(repeated) {
    function reconnect(event) {
      event.target.onopen = null;
      event.target.onclose = null;
      event.target.onerror = null;
      event.target.onmessage = null;
      initConnection(true);
    }
    var connStatus = $sel('.online-status', Instant.mainPane);
    Instant.connection = new WebSocket(Instant.connectionURL);
    Instant.connection.seqid = 0;
    Instant.connection.sendBroadcast = function(data) {
      var id = this.seqid++;
      this.send(JSON.stringify({type: 'broadcast', seq: id,
        data: data}));
      return id;
    };
    Instant.connection.sendUnicast = function(to, data) {
      var id = this.seqid++;
      this.send(JSON.stringify({type: 'unicast', seq: id,
        to: to, data: data}));
      return id;
    };
    Instant.connection.sendPing = function(data) {
      var id = this.seqid++;
      var msg = {type: 'ping', seq: id};
      if (data !== undefined) msg.data = data;
      this.send(JSON.stringify(msg));
    }
    Instant.connection.onopen = function() {
      connStatus.classList.add('connected');
      connStatus.classList.remove('broken');
      connStatus.title = 'Connected.';
      if (repeated) {
        Instant.userList.clear();
        Instant.connection.sendBroadcast({type: 'nick',
          nick: Instant.nickname});
      }
      Instant.logs.clear();
      Instant.connection.sendBroadcast({type: 'who'});
      Instant.connection.sendBroadcast({type: 'log-query'});
      var conn = Instant.connection;
      var iid = setInterval(function() {
        if (conn.readyState != WebSocket.OPEN) {
          clearInterval(iid);
          return;
        }
        conn.sendPing();
      }, 30000);
    };
    if (Instant.connection.readyState == WebSocket.OPEN) {
      Instant.connection.onopen();
    }
    Instant.connection.onclose = reconnect;
    Instant.connection.onerror = reconnect;
    Instant.connection.onmessage = function(event) {
      if (window.logInstantMessages)
        console.log('received', event, event.data);
      // Let exceptions propagate.
      var msg = JSON.parse(event.data);
      // Process message.
      switch (msg.type) {
        case 'error':
          console.error('Error message:', msg);
          break;
        case 'identity':
          Instant.identity = msg.data.id;
          break;
        case 'reply':
          /* NOP */
          break;
        case 'joined':
          Instant.userList.add(msg.data.id, '');
          Instant.userList.update();
          break;
        case 'left':
          Instant.userList.remove(msg.data.id);
          Instant.userList.update();
          break;
        case 'broadcast':
        case 'unicast':
          var msgd = msg.data;
          var msgt = msg.data.type;
          switch (msgt) {
            case 'post':
              /* Message format: {type: 'post', nick: <nick>, text: <text>,
              * parent: <id>}. ID and timestamp supplied by backend. */
              // Sanitize input.
              var nick = msgd.nick;
              var text = msgd.text;
              if (typeof nick != 'string') nick = '';
              if (typeof text != 'string')
                // HACK: Serialize text to something remotely senseful.
                text = JSON.stringify(text);
              // Data for the various message processing functions.
              var ent = {id: msg.id, parent: msgd.parent || null,
                timestamp: msg.timestamp, from: msg.from, nick: nick,
                text: text, isNew: true};
              // Post message.
              var upd = Instant.prepareInputUpdate();
              Instant.addComment(ent.parent, ent);
              Instant.checkNotifications(ent);
              upd();
              // Scrape for nick changes
              Instant.userList.add(msg.from, nick);
              Instant.userList.update();
              // Update logs (stores raw values).
              var logent = {id: msg.id, parent: msgd.parent,
                timestamp: msg.timestamp, from: msg.from, nick: msgd.nick,
                text: msgd.text};
              Instant.logs.add(logent);
              break;
            case 'nick':
              /* Message format: {type: 'nick', nick: <nick>} */
              // Sanitize input
              var nick = msgd.nick;
              if (typeof nick != 'string') nick = '';
              Instant.userList.add(msg.from, nick);
              Instant.userList.update();
              break;
            case 'who':
              /* Unicast a nick message back */
              Instant.connection.sendUnicast(msg.from, {type: 'nick',
                nick: Instant.nickname});
              break;
            case 'log-query':
              /* Return available messages */
              var logs = Instant.logs;
              if (logs.keys) {
                Instant.connection.sendUnicast(msg.from, {type: 'log-info',
                  from: logs.keys[0], to: logs.keys[logs.keys.length - 1],
                  length: logs.keys.length});
              }
              break;
            case 'log-info':
              /* Let the logs object take care of that. */
              Instant.logs._handleLogInfo(msg);
              break;
            case 'log-request':
              /* Return a subset of the logs */
              var reply = {type: 'log'};
              if (msgd.from != null) reply.from = msgd.from;
              if (msgd.to != null) reply.to = msgd.to;
              if (msgd.amount != null) reply.amount = msgd.amount;
              reply.data = Instant.logs.get(reply.from, reply.to,
                                            reply.amount);
              Instant.connection.sendUnicast(msg.from, reply);
              break;
            case 'log':
              /* Incorporate given logs into data */
              var upd = Instant.prepareInputUpdate();
              var sort = {};
              var keys = Instant.logs.merge(msgd.data);
              for (var i = 0; i < keys.length; i++) {
                var ent = Instant.logs.messages[keys[i]];
                if (messages[ent.id]) continue;
                var e = {id: ent.id, timestamp: ent.timestamp,
                  from: ent.from, nick: ent.nick, text: ent.text};
                // Sanitize values.
                if (typeof e.nick != 'string') e.nick = '';
                if (typeof e.text != 'string') e.text = JSON.stringify(e.text);
                Instant.addComment(ent.parent, e);
                Instant.checkNotifications(e);
                sort[ent.parent] = true;
              }
              for (var k in sort) {
                if (! sort.hasOwnProperty(k)) continue;
                Instant.sortComments(k);
              }
              /* Possibly fetch even more logs */
              Instant.updateScroll();
              upd();
              break;
            case 'log-inquiry':
            case 'log-done':
              /* Both for log scraper interaction, not interesting to us */
              break;
          }
          break;
        case 'pong':
          /* NOP */
          break;
        default:
          console.error('Unknown message type:', msg);
          break;
      }
    };
    $sel('.settings', Instant.mainpane).addEventListener('click',
                                                         toggleSettings);
    if (repeated) {
      connStatus.classList.remove('connected');
      connStatus.classList.add('broken');
      connStatus.title = 'Re-connecting...';
    } else {
      connStatus.title = 'Connecting...';
    }
  }
  function init(pane) {
    function installChange(elem, func) {
      elem.addEventListener('keydown', func);
      elem.addEventListener('keyup', func);
      elem.addEventListener('keypress', func);
      elem.addEventListener('paste', function(e) {
        setTimeout(function() { func(e); }, 0);
      });
      elem.addEventListener('change', func);
      elem.addEventListener('blur', func);
      func();
    }
    function broadcastNick(name) {
      Instant.nickname = name;
      Instant.storage.data.nickname = name;
      Instant.storage.save();
      Instant.connection.sendBroadcast({type: 'nick', nick: name});
    }
    Instant.mainPane = pane;
    Instant.storage.load();
    var bar = $sel('.input-bar', pane);
    var innick = $sel('.input-nick', bar);
    var sizenick = $sel('.input-nick-sizer', bar);
    var inmsg = $sel('.input-message', bar);
    Instant.nickname = Instant.storage.data.nickname || Instant.nickname;
    innick.value = Instant.nickname || '';
    innick.selectionStart = innick.selectionEnd = innick.value.length;
    inmsg.value = '';
    bar.addEventListener('click', function(event) {
      event.stopPropagation();
    });
    installChange(innick, function() {
      sizenick.textContent = innick.value;
      sizenick.style.background = 'hsl(' + Instant.hueHash(innick.value) +
        ', 75%, 80%)';
      if (innick.value) {
        sizenick.style.minWidth = '';
      } else {
        sizenick.style.minWidth = '1em';
      }
    });
    installChange(inmsg, function() {
      var c = _prepareScroll(bar);
      inmsg.style.height = '';
      inmsg.style.height = inmsg.scrollHeight + 'px';
      _applyScroll(bar, c);
      _boundScroll(bar);
    });
    innick.addEventListener('keydown', function(event) {
      if (event.keyCode == 13) {
        broadcastNick(innick.value);
        inmsg.focus();
        event.preventDefault();
      }
    });
    innick.addEventListener('change', function(event) {
      broadcastNick(innick.value);
    });
    inmsg.addEventListener('focus', function(event) {
      $sel('.input-nick-prompt', Instant.mainPane).style.display = 'none';
      event.preventDefault();
    });
    inmsg.addEventListener('focusin', function(event) {
      event.preventDefault();
    });
    inmsg.addEventListener('keydown', function(event) {
      var code = event.keyCode;
      if (code == 13 && ! event.shiftKey) { // Return
        var text = inmsg.value;
        inmsg.value = '';
        if (text) {
          if (! Instant.connection) {
            var upd = Instant.prepareInputUpdate();
            var msgid = 'local-' + (Instant.connection.seqid++);
            Instant.addComment(bar.parentNode, Instant.createMessage(msgid,
              innick.value, inmsg.value, false, true));
            upd();
          } else {
            var bar = $sel('.input-bar', Instant.mainPane);
            var parent = Instant.getMessageID(bar);
            Instant.connection.sendBroadcast({type: 'post',
              nick: Instant.nickname, text: text, parent: parent});
          }
        }
        event.preventDefault();
      } else if (code == 27) { // Escape
        Instant.navigateInput('root');
      } else if (code == 9 && ! event.shiftKey) { // Tab
        event.preventDefault();
        // Obtain text and cursor position; "replace" selection.
        var text = inmsg.value;
        if (inmsg.selectionStart != inmsg.selectionEnd) {
          text = text.substr(0, inmsg.selectionStart) +
            text.substr(inmsg.selectionEnd);
        }
        var pos = inmsg.selectionStart;
        // Scan for '@' sign
        var atpos = pos;
        while (atpos >= 0 && text[atpos] != '@') atpos--;
        if (atpos < 0) return;
        // Check if valid so far.
        if (! ALLOW_MENTION.test(text.substring(atpos, pos)))
          return;
        // Find continuation
        var cont = Instant.userList.complete(text.substring(atpos + 1, pos));
        var newpos = pos + cont.length;
        // Insert it.
        text = text.substr(0, pos) + cont + text.substr(pos);
        // Apply text; move cursor.
        inmsg.value = text;
        inmsg.setSelectionRange(newpos, newpos);
      } else if (inmsg.value.indexOf('\n') == -1) {
        if (code == 38) { // Up
          Instant.navigateInput('up');
          event.preventDefault();
        } else if (code == 40) { // Down
          Instant.navigateInput('down');
          event.preventDefault();
        }
        if (! inmsg.value) {
          if (code == 37) { // Left
            Instant.navigateInput('left');
          } else if (code == 39) { // Right
            Instant.navigateInput('right');
          }
        }
      }
    });
    inmsg.addEventListener('keyup', function(event) {
      inmsg.placeholder = '';
    });
    innick.focus();
    window.addEventListener('blur', function(event) {
      Instant.blurred = true;
    });
    window.addEventListener('focus', function(event) {
      Instant.blurred = false;
      Instant.updateUnread(0, false);
      Instant.updateOffscreen();
    });
    var msgwrp = $sel('.message-pane-wrapper', pane);
    msgwrp.addEventListener('scroll', function(event) {
      Instant.updateScroll();
    });
    msgwrp.addEventListener('resize', function(event) {
      Instant.updateOffscreen();
    });
    var alertAbove = $sel('.above-alert', bar);
    var alertBelow = $sel('.below-alert', bar);
    alertAbove.addEventListener('click', function(event) {
      var above = alertAbove.getAttribute('data-scroll-message');
      alertAbove.removeAttribute('data-scroll-message');
      if (! above) return;
      Instant.updateInput(above, 'bar');
      _boundScroll(bar);
    });
    alertBelow.addEventListener('click', function(event) {
      var below = alertBelow.getAttribute('data-scroll-message');
      alertBelow.removeAttribute('data-scroll-message');
      if (! below) return;
      Instant.updateInput(below, 'bar');
      _boundScroll(bar);
    });
    // Can finish connection preparation now.
    if (Instant.connectionURL) {
      initConnection();
    }
  }
  /* Exports */
  Instant.hueHash = hueHash;
  Instant.makeNick = makeNick;
  Instant.messages = messages;
  Instant.makeMessage = makeMessage;
  Instant.preprocessMessage = preprocessMessage;
  Instant.createMessage = createMessage;
  Instant.getMessageID = getMessageID;
  Instant.addComment = addComment;
  Instant.updateIndents = updateIndents;
  Instant.sortComments = sortComments;
  Instant.prepareInputUpdate = prepareInputUpdate;
  Instant.updateInput = updateInput;
  Instant.navigateInput = navigateInput;
  Instant.toggleSettings = toggleSettings;
  Instant.setNotificationStatus = setNotificationStatus;
  Instant.checkNotifications = checkNotifications;
  Instant.updateUnread = updateUnread;
  Instant.updateOffscreen = updateOffscreen;
  Instant.updateScroll = updateScroll;
  Instant.init = init;
  Instant.mainPane = null;
  Instant.identity = null;
  Instant.nickname = '';
  Instant.blurred = false;
  Instant.notifies = 'none';
  Instant.pendingNotification = null;
  Instant.unreadMessages = 0;
  Instant.unreadPings = false;
})();

function hideGreeter() {
  var wrapper = $id('load-wrapper');
  wrapper.style.marginTop = '-30px';
  wrapper.style.opacity = '0';
  setTimeout(function() { wrapper.style.display = 'none'; }, 1000);
}

function init() {
  var wrapper = $id('load-wrapper');
  var main = $id('main');
  wrapper.style.boxShadow = '0 0 30px #808080';
  var m = $id('splash-messages');
  $id('loading-message').style.display = 'none';
  Instant.addComment(m, Instant.createMessage('loading-wait', 'Loading',
                                              'Please wait...'));
  var isIE = /*@cc_on!@*/0;
  if (isIE) Instant.addComment(m, Instant.createMessage('loading-ie',
    'Doom', 'awaits IE users...', true));
  if (! Instant.connectionURL) {
    Instant.addComment(m, Instant.createMessage('loading-connection',
      'Connection', 'is missing.', true));
    Instant.addComment('loading-connection', Instant.createMessage(
      'loading-connection-comment-1', 'Loading',
      'Prepare for a long wait...'));
    Instant.addComment('loading-connection', Instant.createMessage(
      'loading-connection-comment-2', 'Loading',
      'Or, try solving the issue somehow.'));
  } else {
    setTimeout(function() { hideGreeter(); }, 10000);
    $sel('.room-name').textContent = '&' + Instant.roomName;
    $sel('.input-nick-prompt').addEventListener('click', function(event) {
      $sel('.input-nick').focus();
    });
    main.style.opacity = '1';
    document.documentElement.addEventListener('keydown', function(event) {
      if (event.keyCode == 27) {
        $sel('.input-message', Instant.mainPane).focus();
        event.preventDefault();
      }
    });
    Instant.init($id('main'));
  }
  toggleTheme();
  updateNotifications();
}

function toggleTheme() {
  var link = $id('dark-style');
  if (! link.getAttribute('data-orig-title')) {
    link.setAttribute('data-orig-title', link.title);
  }
  if ($id('theme-dark').checked || $id('theme-verydark').checked) {
    link.rel = 'stylesheet';
    link.title = '';
  } else {
    link.rel = 'alternate stylesheet';
    link.title = link.getAttribute('data-orig-title');
  }
  if ($id('theme-verydark').checked) {
    $id('main').classList.add('very-dark');
  } else {
    $id('main').classList.remove('very-dark');
  }
}

function updateNotifications() {
  var notifies = null;
  if ($id('notifies-none').checked) {
    notifies = 'none';
  } else if ($id('notifies-ping').checked) {
    notifies = 'ping';
  } else if ($id('notifies-reply').checked) {
    notifies = 'reply';
  } else if ($id('notifies-any').checked) {
    notifies = 'any';
  }
  Instant.setNotificationStatus(notifies);
}
