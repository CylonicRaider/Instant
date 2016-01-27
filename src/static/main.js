
window.Instant = (window.Instant || {});

(function() {
  /* Connection preparation. */
  var roomPaths = /^(\/room\/([a-z](?:[a-zA-Z0-9_-]*[a-zA-Z0-9])?))\/?/;
  var roomMatch = roomPaths.exec(document.location.pathname);
  if (roomMatch) {
    var wsURL = 'ws://' + document.location.host + roomMatch[1] + '/ws';
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
  /* Groupings: 1: Room name matched
   *            2: Full URL matched
   *               3: Scheme (with colon and double slash)
   *               4: Username with "@"
   *               5: Hostname
   *               6: Port
   *               7: Path
   *            8: Nick-name @-mentioned.
   */
  var INTERESTING = '\\B&([a-zA-Z](?:[a-zA-Z0-9_-]*[a-zA-Z0-9])?)\\b|' +
    '<(((?!javascript:)[a-zA-Z]+://)?([a-zA-Z0-9._~-]+@)?([a-zA-Z0-9.-]+)' +
    '(:[0-9]+)?(/[^>]*)?)>|\\B@([^.,:;:!?\\s]+)\\b';
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
    var hue = hutHash(text);
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
    if (data.mine)
      msgNode.setAttribute('data-mine', 'true');
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
      $sel('[data-key=after-nick]', msgNode).textContent += ' /me';
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
    // Highlight quick replies.
    var color = null;
    if (/^\+1$/.test(msgo.text)) {
      color = '#008000';
    } else if (/^-1$/.test(msgo.text)) {
      color = '#c00000';
    } else if (/^(:[D)|(CSP]|[SD)|(C]:|\^\^)$/.test(msgo.text)) {
      color = '#c0c000';
    }
    if (color) {
      var node = makeSpan(msgo.text, color);
      node.style.fontWeight = 'bold';
      msgo.text = [node];
      return;
    }
    // Otherwise, find room references, and highlight them.
    var out = [], regex = new RegExp(INTERESTING, 'g');
    var s, l = 0;
    for (;;) {
      var m = regex.exec(msgo.text);
      if (m == null) {
        s = msgo.text.substring(l);
        if (s) out.push(s);
        break;
      }
      s = msgo.text.substring(l, m.index);
      if (s) out.push(s);
      l = m.index + m[0].length;
      if (m[1]) {
        var node = document.createElement('a');
        node.href = '/room/' + m[1] + '/';
        node.target = '_blank';
        node.textContent = m[0];
        out.push(node);
      } else if (m[2]) {
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
      } else if (m[0]) {
        out.push(m[0]);
      }
    }
    for (var i = 0; i < out.length; i++) {
      if (typeof out[i] == 'string')
        out[i] = document.createTextNode(out[i]);
    }
    msgo.text = out;
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
    if (! replies) {
      replies = document.createElement('div');
      replies.className = 'replies';
      var clr = $sel('.nick', msg).style.backgroundColor
      replies.style.borderColor = clr;
      msg.appendChild(replies);
    }
    return replies;
  }
  function addComment(parent, msg) {
    if (parent == null) {
      parent = $sel('.message-pane', Instant.mainPane);
    } else if (typeof parent == 'string') {
      parent = messages[parent];
      if (! parent) parent = $sel('.message-pane', Instant.mainPane);
    }
    if (! parent) throw 'Trying to add comment without parent!';
    if (typeof msg == 'object' && msg.nodeType === undefined) {
      Instant.preprocessMessage(msg);
      msg = Instant.makeMessage(msg);
    }
    if (parent.classList.contains('message')) {
      var replies = makeReplies(parent);
      var pi = parent.getAttribute('data-indent');
      var mi = ((pi) ? pi : '') + '| ';
      msg.setAttribute('data-indent', mi);
      $sel('[data-key=indent]', msg).textContent = mi;
      replies.appendChild(msg);
    } else {
      parent.appendChild(msg);
    }
    if (Instant.blurred)
      Instant.updateUnread(Instant.unreadMessages + 1);
    return msg;
  }
  function sortComments(parent) {
    if (parent == null) {
      parent = $sel('.message-pane', Instant.mainPane);
    } else if (typeof parent == 'string') {
      parent = messages[parent];
      if (! parent) parent = $sel('.message-pane', Instant.mainPane);
    }
    if (! parent) throw 'Trying to sort nonexisting message!';
    var replies = $sel('.replies', parent);
    if (! replies) return;
    var children = [], och = replies.children;
    for (var i = 0; i < och.length; i++) {
      children.push(och[i]);
    }
    children.sort(function(a, b) {
      if (! a.classList.contains('message') ||
          ! b.classList.contains('message'))
        return (a < b) ? -1 : (a > b) ? 1 : 0;
      var ida = a.getAttribute('data-message-id');
      var idb = b.getAttribute('data-message-id');
      return (ida < idb) ? -1 : (ida > idb) ? 1 : 0;
    });
    for (var i = 0; i < children.length; i++) {
      replies.appendChild(children[i]);
    }
  }
  /* Input bar management */
  function _findPane(el) {
    while (el && ! (el.classList && el.classList.contains('message-pane'))) {
      el = el.parentNode;
    }
    return el;
  }
  function _findWrapper(el) {
    while (el && ! (el.classList &&
        el.classList.contains('message-pane-wrapper')))
      el = el.parentNode;
    return el;
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
      if (wr)
        wr.scrollTop = pane.offsetTop + bar.offsetTop + bar.offsetHeight -
          cookie;
    }
  }
  function _updateInput(parent, focus, scrollTo) {
    var bar = $sel('.input-bar', Instant.mainPane);
    var el = document.activeElement;
    if (! parent) {
      parent = bar.parentNode;
    } else if (parent.classList.contains('message')) {
      parent = makeReplies(parent);
    }
    parent.appendChild(bar);
    if (focus == 'bar') {
      $sel('.input-message', bar).focus();
    } else if (focus == 'last-active') {
      el = $sel('[data-was-active=true]');
      if (el) el.focus();
    } else if (focus == 'active') {
      if (el) el.focus();
    } else if (typeof focus != 'string' && focus) {
      focus.focus();
    }
    _applyScroll(bar, scrollTo);
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
      if (prev && prev.getAttribute('data-mine') == 'true')
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
        t = '<' + msg.nick + '> ' + msg.rawText;
      }
      // Seems like I'm supposed just to construct it...
      var n = Instant.pendingNotification = new Notification(
        '&' + Instant.roomName, {'body': t});
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
      console.log(n);
    }
  }
  function updateUnread(amount) {
    Instant.unreadMessages = amount;
    var title = $sel('title');
    if (amount) {
      title.innerHTML = '&amp;' + $esc(Instant.roomName) +
        ' (' + amount + ')';
    } else {
      title.innerHTML = Instant.baseTitle;
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
    /* Exports */
    Instant.userList.content = content;
    Instant.userList.update = update;
    Instant.userList.add = add;
    Instant.userList.remove = remove;
    Instant.userList.clear = clear;
  })();
  /* Offline storage */
  (function() {
    Instant.storage = Instant.storage || {};
    var data = {};
    /* Extract state from storage */
    function load() {
      var frozen = localStorage.getItem('instant-data');
      var thawed;
      try {
        thawed = JSON.parse(frozen);
      } catch (e) {
        console.error(e);
        return;
      }
      for (key in thawed) {
        if (! thawed.hasOwnProperty(key)) continue;
        data[key] = thawed[key];
      }
    }
    /* Poke state into storage */
    function save() {
      localStorage.setItem('instant-data', JSON.stringify(data));
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
          delete keys[i];
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
        updateTimeout = null;
        if (farthestAvailable != null) {
          var payload = {type: 'log-request',
            from: farthestAvailable.from};
          if (keys) payload.to = keys[0];
          Instant.connection.sendUnicast(farthestAvailable.peer,
                                         payload);
        }
      }, 1000);
    }
    /* Exports */
    Instant.logs.keys = keys;
    Instant.logs.messages = messages;
    Instant.logs.add = add;
    Instant.logs.merge = merge;
    Instant.logs.get = get;
    Instant.logs.clear = clear;
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
          if (msgt == 'post') {
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
              text: text};
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
          } else if (msgt == 'nick') {
            /* Message format: {type: 'nick', nick: <nick>} */
            // Sanitize input
            var nick = msgd.nick;
            if (typeof nick != 'string') nick = '';
            Instant.userList.add(msg.from, nick);
            Instant.userList.update();
          } else if (msgt == 'who') {
            /* Unicast a nick message back */
            Instant.connection.sendUnicast(msg.from, {type: 'nick',
              nick: Instant.nickname});
          } else if (msgt == 'log-query') {
            /* Return available messages */
            var logs = Instant.logs;
            if (logs.keys) {
              Instant.connection.sendUnicast(msg.from, {type: 'log-info',
                from: logs.keys[0], to: logs.keys[logs.keys.length - 1],
                length: logs.keys.length});
            }
          } else if (msgt == 'log-info') {
            /* Let the logs object take care of that. */
            Instant.logs._handleLogInfo(msg);
          } else if (msgt == 'log-request') {
            /* Return a subset of the logs */
            var reply = {type: 'log'};
            if (msgd.from != null) reply.from = msgd.from;
            if (msgd.to != null) reply.to = msgd.to;
            if (msgd.amount != null) reply.amount = msgd.amount;
            reply.data = Instant.logs.get(reply.from, reply.to,
                                          reply.amount);
            Instant.connection.sendUnicast(msg.from, reply);
          } else if (msgt == 'log') {
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
            upd();
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
      inmsg.placeholder = '';
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
    inmsg.addEventListener('keyup', function() {
      inmsg.placeholder = '';
    });
    innick.focus();
    window.addEventListener('blur', function() {
      Instant.blurred = true;
    });
    window.addEventListener('focus', function() {
      Instant.blurred = false;
      Instant.updateUnread(0);
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
  Instant.sortComments = sortComments;
  Instant.prepareInputUpdate = prepareInputUpdate;
  Instant.updateInput = updateInput;
  Instant.navigateInput = navigateInput;
  Instant.toggleSettings = toggleSettings;
  Instant.setNotificationStatus = setNotificationStatus;
  Instant.checkNotifications = checkNotifications;
  Instant.updateUnread = updateUnread;
  Instant.init = init;
  Instant.mainPane = null;
  Instant.identity = null;
  Instant.nickname = '';
  Instant.blurred = false;
  Instant.notifies = 'none';
  Instant.pendingNotification = null;
  Instant.unreadMessages = 0;
})();

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
    wrapper.style.marginTop = '-30px';
    wrapper.style.opacity = '0';
    $sel('.room-name').textContent = '&' + Instant.roomName;
    main.style.opacity = '1';
    setTimeout(function() { wrapper.style.display = 'none'; }, 1000);
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
  if ($id('dark-theme-toggle').checked) {
    link.rel = 'stylesheet';
    link.title = '';
  } else {
    link.rel = 'alternate stylesheet';
    link.title = link.getAttribute('data-orig-title');
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
