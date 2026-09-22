(function () {
  if (window.__fcplusMarketBridge030) return;
  window.__fcplusMarketBridge030 = true;

  var NATIVE_APP = 'fcplus_native';
  var lastSecurityReason = '';

  function clean(value) {
    return String(value || '').replace(/\s+/g, ' ').trim();
  }

  function lower(value) {
    return clean(value).toLowerCase();
  }

  function coin(value) {
    return Number(String(value || '').replace(/[^\d]/g, '')) || 0;
  }

  function visible(el) {
    if (!el || !el.getBoundingClientRect) return false;
    var r = el.getBoundingClientRect();
    var s = getComputedStyle(el);
    return r.width > 4 && r.height > 4 &&
      s.display !== 'none' && s.visibility !== 'hidden' && s.opacity !== '0';
  }

  function pageType() {
    var t = lower(document.body ? document.body.innerText : '');
    if (t.indexOf('list on transfer market') >= 0 &&
        t.indexOf('start price') >= 0 &&
        t.indexOf('buy now price') >= 0) return 'sell';
    if (t.indexOf("congratulations, you've won this item for") >= 0) return 'won';
    if (t.indexOf('search results') >= 0) return 'results';
    if (t.indexOf('item details') >= 0) return 'details';
    if (t.indexOf('player details') >= 0) return 'playerdetails';
    if (t.indexOf('transfer market') >= 0) return 'market';
    if (t.indexOf('home') >= 0) return 'home';
    return 'other';
  }

  function timeSeconds(raw) {
    var s = lower(raw), m;
    m = s.match(/(\d+)\s*seconds?/); if (m) return Number(m[1]);
    m = s.match(/(\d+)\s*minutes?/); if (m) return Number(m[1]) * 60;
    m = s.match(/(\d+)\s*hours?/); if (m) return Number(m[1]) * 3600;
    if (s.indexOf('<5 seconds') >= 0) return 4;
    return 999999;
  }

  function parseListing(raw) {
    var s = clean(raw);
    var start = s.match(/Start Price:\s*([\d,]+)/i);
    var bid = s.match(/Bid\s*([\d,]+|---)/i);
    var bin = s.match(/Buy Now:\s*([\d,]+)/i);
    var tm = s.match(/Time\s*([^]+)$/i);
    var name = s.split(/Start Price:/i)[0].trim()
      .replace(/^\d+\s*[A-Z]{1,5}\s*/i, '').trim() || 'Item';

    return {
      name: name,
      startPrice: start ? coin(start[1]) : 0,
      currentBid: bid && bid[1] !== '---' ? coin(bid[1]) : 0,
      buyNow: bin ? coin(bin[1]) : 0,
      timeSeconds: tm ? timeSeconds(tm[1]) : 999999
    };
  }

  function listingCards() {
    var found = [], seen = {};
    var nodes = document.querySelectorAll('li,article,section,div');

    for (var i = 0; i < nodes.length; i++) {
      var el = nodes[i];
      if (!visible(el)) continue;
      var t = clean(el.innerText || '');
      if (!/Start Price:/i.test(t) || !/Buy Now:/i.test(t) || !/\bTime\b/i.test(t)) continue;

      var r = el.getBoundingClientRect();
      if (r.width < 180 || r.height < 60 || r.height > 300) continue;

      var p = parseListing(t);
      if (!p.buyNow) continue;

      var key = Math.round(r.top / 10) + ':' + p.buyNow + ':' + p.startPrice + ':' + p.currentBid;
      if (seen[key]) continue;
      seen[key] = true;

      p.top = r.top;
      p.area = r.width * r.height;
      found.push(p);
    }

    found.sort(function (a, b) { return a.area - b.area; });
    var out = [];

    found.forEach(function (item) {
      var duplicate = out.some(function (x) {
        return Math.abs(x.top - item.top) < 30 &&
          x.buyNow === item.buyNow &&
          x.startPrice === item.startPrice;
      });
      if (!duplicate) {
        delete item.top;
        delete item.area;
        out.push(item);
      }
    });

    return out.slice(0, 30);
  }

  function detectPlayerName() {
    var type = pageType();
    if (type !== 'results' && type !== 'details' && type !== 'playerdetails') return '';

    var headings = Array.from(document.querySelectorAll('h1,h2,h3,.name,.player-name')).filter(visible);
    for (var i = 0; i < headings.length; i++) {
      var t = clean(headings[i].textContent || '');
      if (!t) continue;
      if (/Search Results|Item Details|Player Details|Transfer Market/i.test(t)) continue;
      if (t.length >= 2 && t.length <= 45) return t;
    }

    var rows = listingCards();
    return rows.length ? rows[0].name : '';
  }

  function detectChemStyle() {
    var t = clean(document.body ? document.body.innerText : '');
    var styles = [
      'Hunter','Shadow','Hawk','Anchor','Engine','Catalyst','Finisher',
      'Deadeye','Marksman','Sniper','Artist','Architect','Powerhouse',
      'Maestro','Sentinel','Guardian','Gladiator','Backbone'
    ];

    for (var i = 0; i < styles.length; i++) {
      var rx = new RegExp('(?:Chemistry Style|Chem Style)\\s*:?\\s*' + styles[i], 'i');
      if (rx.test(t)) return styles[i];
    }
    return '';
  }

  function securityReason() {
    var t = lower(document.body ? document.body.innerText : '');
    var flags = [
      'captcha',
      'too many actions',
      'temporarily unavailable',
      'try again later',
      'access has been restricted',
      'verify your identity',
      'security verification'
    ];

    for (var i = 0; i < flags.length; i++) {
      if (t.indexOf(flags[i]) >= 0) return flags[i];
    }
    return '';
  }

  function send(payload) {
    try {
      var result = browser.runtime.sendNativeMessage(NATIVE_APP, payload);
      if (result && typeof result.catch === 'function') result.catch(function () {});
    } catch (_) {}
  }

  function scan() {
    var reason = securityReason();
    if (reason && reason !== lastSecurityReason) {
      lastSecurityReason = reason;
      send({ type: 'security_stop', reason: reason });
      return;
    }

    send({
      type: 'snapshot',
      pageType: pageType(),
      playerName: detectPlayerName(),
      chemStyle: detectChemStyle(),
      listings: listingCards(),
      capturedAt: Date.now(),
      url: location.href
    });
  }

  var timer;
  function schedule() {
    clearTimeout(timer);
    timer = setTimeout(scan, 350);
  }

  var observer = new MutationObserver(schedule);
  observer.observe(document.documentElement, {
    subtree: true,
    childList: true,
    characterData: true
  });

  scan();
  setInterval(scan, 2500);
})();