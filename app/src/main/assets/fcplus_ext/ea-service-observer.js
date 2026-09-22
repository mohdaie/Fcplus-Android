/* Read-only bridge to EA's own Ultimate Team item services.
 * It observes search/watchlist/transfer-list responses so FC+ can attach exact
 * auction/card identities to the visible rows. It does not read auth tokens
 * and it does not submit bids, purchases, listings, or other writes.
 */
(function () {
  'use strict';
  if (window.__fcplusEaObserver041) return;
  window.__fcplusEaObserver041 = true;

  const EVENT = 'FCPLUS_EA_OBSERVATION';
  const chemistry = {
    250: 'Basic', 251: 'Sniper', 252: 'Finisher', 253: 'Deadeye',
    254: 'Marksman', 255: 'Hawk', 256: 'Artist', 257: 'Architect',
    258: 'Powerhouse', 259: 'Maestro', 260: 'Engine', 261: 'Sentinel',
    262: 'Guardian', 263: 'Gladiator', 264: 'Backbone', 265: 'Anchor',
    266: 'Hunter', 267: 'Catalyst', 268: 'Shadow'
  };

  const clean = value => String(value == null ? '' : value).replace(/\s+/g, ' ').trim();
  const number = value => {
    const n = Number(value);
    return Number.isFinite(n) ? n : 0;
  };
  function auctionData(item) {
    try { return typeof item.getAuctionData === 'function' ? item.getAuctionData() : null; } catch (_) { return null; }
  }
  function boolCall(obj, name) {
    try { return !!(obj && typeof obj[name] === 'function' && obj[name]()); } catch (_) { return false; }
  }
  function chemName(item) {
    const raw = item && (item.playStyle != null ? item.playStyle :
      item.playStyleId != null ? item.playStyleId : item.chemistryStyleId);
    if (typeof raw === 'string') {
      const found = Object.values(chemistry).find(value => value.toLowerCase() === clean(raw).toLowerCase());
      return found || '';
    }
    return chemistry[number(raw)] || '';
  }
  function state(item, kind) {
    const auction = item && item._auction || {};
    const data = auctionData(item);
    const trade = clean(auction._tradeState || auction.tradeState).toLowerCase();
    const bid = clean(auction._bidState || auction.bidState).toLowerCase();
    if (kind === 'market') return 'market';
    if (kind === 'watchlist') {
      if (boolCall(data, 'isWon')) return 'won';
      if (boolCall(data, 'isExpired') || (boolCall(data, 'isClosedTrade') && !boolCall(data, 'isWon'))) return 'lost';
      if (/highest|winning/.test(bid)) return 'highest';
      if (/outbid/.test(bid)) return 'outbid';
      if (trade === 'active') return 'unknown';
      return 'unknown';
    }
    if (kind === 'transferlist') {
      if (boolCall(data, 'isSold')) return 'sold';
      if (boolCall(data, 'isExpired')) return 'expired';
      if (boolCall(data, 'isSelling')) return 'listed';
      return 'unknown';
    }
    return 'unknown';
  }
  function normalize(item, kind) {
    const auction = item && item._auction || {};
    const stat = item && item._staticData || {};
    const definitionId = number(item && (item.definitionId || item.resourceId || item.assetId));
    const name = clean(stat.name || item && (item.name || item.commonName));
    const rating = number(item && item.rating || stat.rating);
    const chem = chemName(item);
    const status = state(item, kind);
    const currentBid = number(auction.currentBid);
    const buyNow = number(auction.buyNowPrice);
    return {
      auctionId: clean(auction.tradeId),
      itemId: clean(item && (item.id || item.idStr)),
      identity: definitionId && name && rating && chem ? String(definitionId) + ':' + chem : '',
      name,
      rating,
      chem,
      startPrice: number(auction.startingBid),
      currentBid,
      buyNow,
      paid: status === 'won' ? (currentBid || buyNow) : 0,
      salePrice: status === 'sold' ? (currentBid || buyNow) : 0,
      timeSeconds: number(auction.expires) || 999999,
      status
    };
  }
  function itemsFrom(response) {
    const body = response && (response.response || response.data || response);
    if (!body) return [];
    if (Array.isArray(body.items)) return body.items;
    if (Array.isArray(body.itemData)) return body.itemData;
    return [];
  }
  function coins() {
    try {
      const user = window.services && window.services.User && window.services.User.getUser && window.services.User.getUser();
      return number(user && user.coins && user.coins.amount);
    } catch (_) { return 0; }
  }
  function publish(kind, response) {
    const rows = itemsFrom(response).map(item => normalize(item, kind));
    window.postMessage({
      type: EVENT,
      kind,
      rows,
      coins: coins(),
      capturedAt: Date.now()
    }, '*');
  }
  function tap(result, kind) {
    if (!result) return;
    try {
      if (typeof result.observe === 'function') {
        result.observe({}, function (_sender, response) { publish(kind, response); });
      } else if (typeof result.then === 'function') {
        result.then(response => publish(kind, response)).catch(() => {});
      }
    } catch (_) {}
  }
  function patch(itemService, method, kind) {
    const original = itemService && itemService[method];
    if (typeof original !== 'function' || original.__fcplusObserved) return;
    function wrapped() {
      const result = original.apply(this, arguments);
      tap(result, kind);
      return result;
    }
    wrapped.__fcplusObserved = true;
    wrapped.__fcplusOriginal = original;
    itemService[method] = wrapped;
  }
  function install() {
    try {
      const itemService = window.services && window.services.Item;
      if (!itemService) return false;
      patch(itemService, 'searchTransferMarket', 'market');
      patch(itemService, 'requestWatchedItems', 'watchlist');
      patch(itemService, 'requestTransferItems', 'transferlist');
      window.postMessage({ type: EVENT, kind: 'ready', rows: [], coins: coins(), capturedAt: Date.now() }, '*');
      return true;
    } catch (_) {
      return false;
    }
  }

  if (!install()) {
    const timer = setInterval(() => { if (install()) clearInterval(timer); }, 500);
    setTimeout(() => clearInterval(timer), 30000);
  }
})();