/* Pure decision engine. No DOM, credentials, network or clicks. */
(function (root) {
  'use strict';
  const DAY = 86400000;
  const ACTIVE = ['bid', 'won', 'listed'];
  function step(p) { return p < 1000 ? 50 : p < 10000 ? 100 : p < 50000 ? 250 : p < 100000 ? 500 : 1000; }
  function down(p) {
    if (p < 150) return 0;
    const s = p <= 1000 ? 50 : p <= 10000 ? 100 : p <= 50000 ? 250 : p <= 100000 ? 500 : 1000;
    return Math.floor(p / s) * s;
  }
  function nextBid(row) { return row.currentBid > 0 ? row.currentBid + step(row.currentBid) : row.startPrice; }
  function net(p) { return Math.floor(p * 95 / 100); }
  function normalizedName(value) {
    return String(value || '').normalize('NFKD').replace(/[\u0300-\u036f]/g, '')
      .toLowerCase().replace(/[’']/g, '').replace(/[^a-z0-9]+/g, ' ').trim();
  }
  function sameName(a, b) { return normalizedName(a) === normalizedName(b); }
  function utcDay(now) { return Math.floor(now / DAY); }
  function fresh(now) { return { schema: 1, ledger: [], pending: null, targetIndex: 0, nextAt: 0, seq: 0, runId: '', startedAt: 0, day: utcDay(now), dayProfit: 0, sessionSpent: 0, sessionActions: 0, logs: [] }; }
  function log(s, text, now) { s.logs = [{ at: now, text }, ...(s.logs || [])].slice(0, 80); s.message = text; }
  function ceiling(sell, c) { return down(Math.min(c.maxBuy, net(sell) - c.minProfit, Math.floor(net(sell) / (1 + c.minRoi / 100)))); }
  function quote(rows, target, c) {
    const eligible = rows.filter(r => r.identity && r.identity === target.identity && r.buyNow >= 150 && r.status === 'market');
    // Distinct auctions only; identical cards/prices are legitimate separate observations.
    const unique = [...new Map(eligible.filter(r => r.auctionId).map(r => [r.auctionId, r])).values()];
    if (unique.length < 4) return null;
    const prices = unique.map(r => r.buyNow).sort((a, b) => a - b);
    // Require at least three comparable cheap listings; never value mixed player variants together.
    const sell = prices[1];
    if (prices[3] > sell * 1.15) return null;
    return { sell: down(sell), max: ceiling(sell, c), sample: unique.length };
  }
  function reconcile(s, snap, now) {
    for (const t of s.ledger) {
      const r = snap.rows.find(r => (t.itemId && r.itemId === t.itemId) || r.auctionId === t.auctionId);
      if (!r || r.identity !== t.identity) continue;
      if (t.state === 'bid' && r.status === 'lost') { t.state = 'lost'; t.reserved = 0; }
      if (t.state === 'bid' && r.status === 'won' && r.itemId && r.paid > 0 && r.paid <= t.maxBid) {
        t.state = 'won'; t.itemId = r.itemId; t.cost = r.paid; t.reserved = 0; s.sessionSpent += r.paid;
        log(s, 'Won ' + t.name + ' for ' + r.paid, now);
      }
      if (t.state === 'listed' && r.status === 'sold' && r.salePrice > 0 && r.salePrice <= t.sellPrice && r.itemId === t.itemId) {
        t.state = 'sold'; t.soldAt = now; t.salePrice = r.salePrice; t.profit = net(r.salePrice) - t.cost;
        t.reserved = 0; log(s, 'Sold ' + t.name + ': ' + (t.profit >= 0 ? '+' : '') + t.profit + ' after tax', now);
      }
    }
    s.day = utcDay(now);
    s.dayProfit = s.ledger.filter(t => t.state === 'sold' && utcDay(t.soldAt) === s.day).reduce((a, t) => a + t.profit, 0);
  }
  function decide(saved, snap, cfg, now) {
    const s = JSON.parse(JSON.stringify(saved || fresh(now)));
    const c = Object.assign({ dryRun: true, minProfit: 300, minRoi: 8, maxBuy: 5000, budget: 20000, maxOpen: 3, durationMinutes: 20, intervalSeconds: 15, maxActions: 60, dailyCap: 100000, targets: [], runId: '' }, cfg);
    const done = (message, halt) => { if (message) s.message = message; return { state: s, command: null, halt: !!halt }; };
    if (![c.minProfit,c.minRoi,c.maxBuy,c.budget,c.maxOpen,c.durationMinutes,c.intervalSeconds,c.maxActions,c.dailyCap].every(Number.isFinite) || c.minProfit < 0 || c.minRoi < 0 || c.maxBuy < 150 || c.budget < 150 || c.maxOpen < 1 || c.durationMinutes < 1 || c.durationMinutes > 60 || c.maxActions < 1 || c.intervalSeconds < 10) return done('Invalid trading limits', true);
    if (s.schema !== 1) return done('Unsupported saved ledger. Trading stopped.', true);
    if (s.runId !== c.runId) {
      if (s.pending) return done('Unconfirmed action from previous session. Reconcile it before restarting.', true);
      s.runId = c.runId; s.startedAt = now; s.sessionSpent = 0; s.sessionActions = 0; s.nextAt = 0; s.lastTargetsAt = 0; s.lastListAt = 0;
    }
    if (!snap.capturedAt || now - snap.capturedAt > 10000 || snap.capturedAt > now + 5000) return done('Waiting for fresh EA data');
    reconcile(s, snap, now);
    if (snap.security) return done('EA security stop: ' + snap.security, true);
    if (snap.loggedOut) return done('EA login required. Open EA Login once to reconnect.', true);
    if (s.pending) {
      const p = s.pending;
      const r = snap.rows.find(r => (p.itemId && r.itemId === p.itemId) || r.auctionId === p.auctionId);
      if (r && r.identity === p.identity) {
        if ((p.type === 'bid' || p.type === 'buy') && ((r.status === 'highest' && r.currentBid === p.amount) || (r.status === 'won' && r.paid === p.amount && r.itemId))) {
          let t = s.ledger.find(t => t.auctionId === p.auctionId);
          if (!t) { t = { auctionId: p.auctionId, identity: p.identity, name: p.name, sellPrice: p.sellPrice, maxBid: p.maxBid, quoteAt: p.at, state: 'bid', cost: 0 }; s.ledger.push(t); }
          t.reserved = p.amount; t.lastBid = p.amount;
          s.pending = null; log(s, p.type + ' confirmed: ' + p.name + ' at ' + p.amount, now);
          reconcile(s, snap, now);
        } else if (p.type === 'list' && r.status === 'listed' && r.itemId === p.itemId && r.buyNow === p.amount) {
          const t = s.ledger.find(t => t.itemId === p.itemId);
          if (t) { t.state = 'listed'; t.sellPrice = p.amount; }
          s.pending = null; log(s, 'Listing confirmed: ' + p.name, now);
        } else if ((p.type === 'bid' || p.type === 'buy') && r.status === 'lost') {
          s.pending = null; log(s, 'Auction ended without a confirmed win', now);
        }
      }
      if (s.pending) {
        if (now - s.pending.at > 45000) return done('Action outcome unconfirmed. Stopped to prevent duplicate transactions; inspect EA.', true);
        return done('Waiting for EA to confirm ' + s.pending.type);
      }
    }
    if (s.dayProfit >= Math.min(100000, c.dailyCap)) return done('Daily realized-profit goal reached. Trading stopped.', true);
    if (now - s.startedAt >= c.durationMinutes * 60000) return done('Session time limit reached.', true);
    if (s.sessionActions >= c.maxActions) return done('Session action limit reached.', true);
    if (!snap.capturedAt || now - snap.capturedAt > 10000 || snap.capturedAt > now + 5000) return done('Waiting for fresh EA data');
    if (now < s.nextAt) return done();
    function command(type, data, financial) {
      const action = Object.assign({ type, id: c.runId + ':' + (++s.seq), at: now }, data || {});
      s.nextAt = now + Math.max(10, c.intervalSeconds) * 1000;
      if (financial && c.dryRun) { log(s, 'DRY RUN: would ' + type + ' ' + action.name + ' at ' + action.amount + ' (no order sent)', now); return done(); }
      if (financial) s.pending = action;
      s.sessionActions++;
      log(s, action.note || ((financial ? 'Submitting ' : '') + type + (action.name ? ' · ' + action.name : '')), now);
      return { state: s, command: action, halt: false };
    }
    // Service existing positions before seeking new ones.
    const open = s.ledger.filter(t => ACTIVE.includes(t.state));
    if (snap.page === 'targets') s.lastTargetsAt = now;
    if (snap.page === 'list') s.lastListAt = now;
    for (const t of open) {
      const r = snap.rows.find(r => (t.itemId && r.itemId === t.itemId) || r.auctionId === t.auctionId);
      if (!r) continue; // Absence is not evidence of sale or loss.
      if (r.identity !== t.identity) return done('Tracked item identity changed. Inspect EA before continuing.', true);
      if (t.state === 'won' || (t.state === 'listed' && r.status === 'expired')) {
        if (!t.itemId || !t.cost) return done('Waiting for verified winning price and item identity');
        return command('list', { itemId: t.itemId, auctionId: r.auctionId, identity: t.identity, name: t.name, amount: t.sellPrice, startPrice: Math.max(150, t.sellPrice - step(t.sellPrice - 1)) }, true);
      }
      if (t.state === 'bid' && r.status === 'outbid' && now - (t.quoteAt || 0) <= 300000) {
        const amount = nextBid(r), reserved = open.reduce((v, x) => v + (x.reserved || 0), 0);
        if (amount <= t.maxBid && amount <= c.maxBuy && amount > t.lastBid && s.sessionSpent + reserved - (t.reserved || 0) + amount <= c.budget && snap.coins >= amount) {
          return command('bid', { auctionId: t.auctionId, identity: t.identity, name: t.name, amount, maxBid: t.maxBid, sellPrice: t.sellPrice }, true);
        }
      }
    }
    if (open.some(t => t.state !== 'listed') && now - (s.lastTargetsAt || 0) >= 30000) return command('navigate', { page: 'targets' });
    if (open.some(t => t.state === 'listed') && now - (s.lastListAt || 0) >= 30000) return command('navigate', { page: 'list' });
    if (open.length >= c.maxOpen) return done('Monitoring open trades; new entries paused');
    if (!c.targets.length) return done('Add a target or use AI Scout before starting');
    const target = c.targets[s.targetIndex % c.targets.length];
    if (snap.page !== 'results' || !sameName(snap.searchName, target.name) || (target.identity && !snap.rows.some(r => r.identity === target.identity))) return command('search', { target });
    // A target may acquire its exact EA identity only when all four comparison rows agree.
    const ids = [...new Set(snap.rows.filter(r => sameName(r.name, target.name) && (!target.rating || r.rating === target.rating) && (!target.chem || r.chem === target.chem)).map(r => r.identity).filter(Boolean))];
    if (ids.length !== 1) { s.targetIndex++; s.nextAt = now + Math.max(10, c.intervalSeconds) * 1000; return done('Exact card variant is ambiguous; skipping ' + target.name); }
    const exactTarget = Object.assign({}, target, { identity: target.identity || ids[0] });
    if (target.identity && target.identity !== ids[0]) return done('EA variant does not match configured target', true);
    const q = quote(snap.rows, exactTarget, c);
    if (!q) { s.targetIndex++; return command('search', { target: c.targets[s.targetIndex % c.targets.length] }); }
    const reserved = open.reduce((a, t) => a + (t.reserved || 0), 0);
    const available = Math.min(c.budget - s.sessionSpent - reserved, snap.coins || 0);
    const candidates = snap.rows.filter(r => r.identity === exactTarget.identity && r.status === 'market' && r.auctionId && (c.dryRun || r.uiBound !== false) && !s.ledger.some(t => t.auctionId === r.auctionId))
      .map(r => ({ row: r, type: r.buyNow > 0 && r.buyNow <= q.max ? 'buy' : 'bid', amount: r.buyNow > 0 && r.buyNow <= q.max ? r.buyNow : nextBid(r) }))
      .filter(x => x.amount >= 150 && x.amount <= q.max && x.amount <= available && (x.type === 'buy' || (x.row.timeSeconds > 5 && x.row.timeSeconds <= 120)))
      .sort((a, b) => a.amount - b.amount);
    if (candidates.length) {
      const x = candidates[0];
      return command(x.type, { auctionId: x.row.auctionId, identity: x.row.identity, name: x.row.name, amount: x.amount, maxBid: q.max, sellPrice: q.sell }, true);
    }
    const reason = 'No qualifying entry · ' + exactTarget.name + ': market ' + q.sell + ' · max entry ' + q.max +
      (c.dryRun ? ' · Dry Run' : ' · waiting for a uniquely bound EA row');
    s.targetIndex++;
    return command('search', { target: c.targets[s.targetIndex % c.targets.length], note: reason });
  }
  const api = { step, down, nextBid, net, normalizedName, sameName, fresh, ceiling, quote, decide };
  if (typeof module !== 'undefined' && module.exports) module.exports = api;
  root.FCTrader = api;
})(typeof globalThis !== 'undefined' ? globalThis : this);
