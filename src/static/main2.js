
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
        node.class = 'nick';
        node.textContent = name;
        node.style.background = 'hsl(' + hue + ', 75%, 80%)';
        return node;
      }
    };
  }();
  /* To be assigned in window */
  return Instant;
}();
