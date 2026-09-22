/* Read-only bridge to EA's own Ultimate Team item services.
 * It observes search/watchlist/transfer-list responses so FC+ can attach exact
 * auction/card identities to the visible rows. It does not read auth tokens
 * and it does not submit bids, purchases, listings, or other writes.
 */
(function () {
  'use strict';
  if (window.__fcplusEaObserver050) return;
  window.__fcplusEaObserver050 = true;

  const EVENT = 'FCPLUS_EA_OBSERVATION';
  const STRATEGY_REQUEST = 'FCPLUS_STRATEGY_REQUEST';
  const STRATEGY_RESPONSE = 'FCPLUS_STRATEGY_RESPONSE';
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
    return chemistry[number(raw)] || 'Basic';
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
  function observeOnce(result, timeoutMs = 12000) {
    return new Promise((resolve, reject) => {
      if (!result) return reject(new Error('EA market service returned no result'));
      const timer = setTimeout(() => reject(new Error('EA market search timed out')), timeoutMs);
      const done = (error, value) => {
        clearTimeout(timer);
        if (error) reject(error); else resolve(value);
      };
      try {
        if (typeof result.observe === 'function') {
          result.observe({}, (_sender, response) => done(null, response));
        } else if (typeof result.then === 'function') {
          result.then(value => done(null, value)).catch(error => done(error));
        } else {
          done(null, result);
        }
      } catch (error) { done(error); }
    });
  }
  function blankCriteria() {
    const defaults = {
      type: 'player', category: 'any', position: 'any', zone: -1, nationality: -1,
      league: -1, club: -1, playStyle: -1, playStylePlus: -1, minBid: 0,
      maxBid: 0, minBuy: 0, maxBuy: 0, level: 'any', maskedDefId: 0,
      defId: [], rarities: [], types: [], playStyles: [], roles: [],
      playerRoles: [], traits: [], chemistryStyles: []
    };
    try {
      const Ctor = window.UTSearchCriteriaDTO || window.UTItemSearchCriteriaDTO || window.UTMarketSearchCriteriaDTO;
      const dto = typeof Ctor === 'function' ? new Ctor() : {};
      Object.entries(defaults).forEach(([key, value]) => { try { dto[key] = value; } catch (_) {} });
      return dto;
    } catch (_) { return Object.assign({}, defaults); }
  }
  async function marketSearch(criteria, page) {
    const itemService = window.services && window.services.Item;
    if (!itemService || typeof itemService.searchTransferMarket !== 'function') throw new Error('EA market service unavailable');
    if (typeof itemService.clearTransferMarketCache === 'function') {
      try { itemService.clearTransferMarketCache(); } catch (_) {}
    }
    return observeOnce(itemService.searchTransferMarket(criteria, page || 1));
  }
  function down(price) {
    if (price < 150) return 0;
    const step = price <= 1000 ? 50 : price <= 10000 ? 100 : price <= 50000 ? 250 : price <= 100000 ? 500 : 1000;
    return Math.floor(price / step) * step;
  }
  function net(price) { return Math.floor(price * 95 / 100); }
  function maxEntry(sell, minProfit, minRoi, hardCap) {
    return down(Math.min(hardCap, net(sell) - minProfit, Math.floor(net(sell) / (1 + minRoi / 100))));
  }
  async function silverQuickFlip(params) {
    const bankroll = Math.max(0, coins());
    const hardCap = Math.max(1500, Math.min(number(params.maxBuy) || 5000, bankroll > 0 ? Math.floor(bankroll * 0.05) : 5000));
    const minProfit = Math.max(150, number(params.minProfit) || 200);
    const minRoi = Math.max(3, number(params.minRoi) || 8);
    const broad = blankCriteria();
    broad.type = 'player';
    broad.level = 'silver';
    const candidateMap = new Map();
    for (let page = 1; page <= 3; page++) {
      const response = await marketSearch(broad, page);
      for (const item of itemsFrom(response)) {
        const row = normalize(item, 'market');
        const definitionId = number(item && (item.definitionId || item.resourceId || item.assetId));
        if (!definitionId || !row.name || !row.rating || row.buyNow < 150) continue;
        const key = String(definitionId);
        const current = candidateMap.get(key) || { definitionId, name: row.name, rating: row.rating, sightings: 0, cheapest: Infinity };
        current.sightings++;
        current.cheapest = Math.min(current.cheapest, row.buyNow);
        candidateMap.set(key, current);
      }
    }
    const seeds = Array.from(candidateMap.values())
      .sort((a, b) => (b.sightings - a.sightings) || (a.cheapest - b.cheapest))
      .slice(0, 10);
    let best = null;
    for (const seed of seeds) {
      const exact = blankCriteria();
      exact.type = 'player';
      exact.level = 'silver';
      exact.maskedDefId = seed.definitionId;
      const response = await marketSearch(exact, 1);
      const rows = itemsFrom(response).map(item => normalize(item, 'market'))
        .filter(row => row.buyNow >= 150 && row.chem === 'Basic' && row.name === seed.name && row.rating === seed.rating)
        .sort((a, b) => a.buyNow - b.buyNow);
      const unique = [...new Map(rows.filter(row => row.auctionId).map(row => [row.auctionId, row])).values()];
      if (unique.length < 4) continue;
      const sell = down(unique[1].buyNow);
      if (!sell || unique[3].buyNow > sell * 1.15) continue;
      const ceiling = maxEntry(sell, minProfit, minRoi, hardCap);
      const entry = unique.find(row => {
        const bid = row.currentBid > 0 ? row.currentBid : row.startPrice;
        return (row.buyNow > 0 && row.buyNow <= ceiling) || (bid >= 150 && bid <= ceiling && row.timeSeconds <= 120);
      });
      const score = (entry ? Math.max(0, net(sell) - Math.min(entry.buyNow || Infinity, entry.currentBid || entry.startPrice || Infinity)) : 0) +
        Math.min(unique.length, 21) * 10 + seed.sightings * 20;
      const candidate = {
        target: { name: seed.name, rating: seed.rating, chem: 'Basic', identity: String(seed.definitionId) + ':Basic' },
        marketSell: sell,
        maxEntry: ceiling,
        sample: unique.length,
        sightings: seed.sightings,
        score,
        hasEntry: !!entry
      };
      if (!best || candidate.hasEntry && !best.hasEntry || candidate.hasEntry === best.hasEntry && candidate.score > best.score) best = candidate;
    }
    if (!best) throw new Error('No liquid silver candidate passed the current market checks');
    return Object.assign(best, {
      bankroll,
      parameters: { maxBuy: hardCap, minProfit, minRoi, scanPages: 3, candidateChecks: seeds.length }
    });
  }

  window.addEventListener('message', async event => {
    if (event.source !== window || event.data?.type !== STRATEGY_REQUEST) return;
    const requestId = clean(event.data.requestId);
    if (!requestId) return;
    try {
      let result;
      if (event.data.strategy === 'silver_quick_flip') result = await silverQuickFlip(event.data.params || {});
      else throw new Error('Unknown trading method');
      window.postMessage({ type: STRATEGY_RESPONSE, requestId, result }, '*');
    } catch (error) {
      window.postMessage({ type: STRATEGY_RESPONSE, requestId, error: clean(error && error.message).slice(0, 220) }, '*');
    }
  });

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