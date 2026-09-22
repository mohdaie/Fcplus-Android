/* EA DOM adapter: unsupported or ambiguous markup stops execution, never guesses. */
(function () {
  'use strict';
  if (window.__fcplus040) return;
  window.__fcplus040 = true;
  const native = payload => browser.runtime.sendNativeMessage('fcplus_native', payload);
  const clean = s => String(s || '').replace(/\s+/g, ' ').trim();
  const coin = s => { const v = clean(s); return /^\d[\d, ]*$/.test(v) ? Number(v.replace(/[, ]/g, '')) : 0; };
  const visible = e => e && e.getBoundingClientRect().width > 0 && e.getBoundingClientRect().height > 0 && getComputedStyle(e).visibility !== 'hidden' && getComputedStyle(e).display !== 'none';
  const all = (s, root = document) => Array.from(root.querySelectorAll(s)).filter(visible);
  const text = (root, s) => clean(root.querySelector(s)?.textContent);
  const attr = (e, names) => { for (const n of names) { const v = e.getAttribute(n) || e.querySelector('[' + n + ']')?.getAttribute(n); if (v) return clean(v); } return ''; };
  let busy = false, lastSnap, lastError = '';
  function only(list, description) { if (list.length !== 1) throw new Error('Cannot uniquely identify ' + description + ' (' + list.length + ')'); return list[0]; }
  function button(label, root = document) { return only(all('button,[role="button"]', root).filter(e => clean(e.textContent).toLowerCase() === label.toLowerCase() && !e.disabled), label); }
  function field(label, root = document) {
    const direct = all('input', root).filter(e => clean(e.getAttribute('aria-label') || e.placeholder).toLowerCase() === label.toLowerCase());
    if (direct.length === 1) return direct[0];
    const labels = all('label', root).filter(e => clean(e.textContent).replace(/:$/, '').toLowerCase() === label.toLowerCase());
    const inputs = labels.map(l => l.control || l.parentElement.querySelector('input')).filter(Boolean);
    return only(inputs, label + ' input');
  }
  function setValue(input, value) {
    const setter = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value').set;
    setter.call(input, String(value)); input.dispatchEvent(new Event('input', { bubbles: true })); input.dispatchEvent(new Event('change', { bubbles: true }));
    if (input.value !== String(value)) throw new Error('EA rejected input value');
  }
  async function waitFor(fn, ms = 5000) {
    const end = Date.now() + ms;
    while (Date.now() < end) { try { const x = fn(); if (x) return x; } catch (_) {} await new Promise(r => setTimeout(r, 200)); }
    throw new Error('EA screen did not reach the expected state');
  }
  function security() {
    const t = clean(document.body?.innerText).toLowerCase();
    return ['captcha','too many actions','temporarily unavailable','try again later','access has been restricted','verify your identity','security verification','soft ban'].find(x => t.includes(x)) || '';
  }
  function status(e) {
    const declared = attr(e, ['data-trade-status', 'data-auction-state']);
    if (['market','highest','outbid','won','lost','listed','sold','expired'].includes(declared)) return declared;
    const classes = [e, ...e.querySelectorAll('[class]')].map(x => String(x.className)).join(' ').toLowerCase();
    if (/\b(sold)\b/.test(classes)) return 'sold';
    if (/\b(outbid)\b/.test(classes)) return 'outbid';
    if (/\b(highest-bid|highestbid|winning)\b/.test(classes)) return 'highest';
    if (/\b(won)\b/.test(classes)) return 'won';
    if (/\b(lost)\b/.test(classes)) return 'lost';
    if (/\b(expired)\b/.test(classes)) return 'expired';
    if (/\b(listed|selling)\b/.test(classes)) return 'listed';
    return page() === 'results' ? 'market' : 'unknown';
  }
  function page() {
    const heads = all('h1,h2,h3,.ut-navigation-bar-view .title,.ut-navigation-bar-view .text').map(e => clean(e.textContent).toLowerCase());
    if (heads.includes('transfer targets')) return 'targets';
    if (heads.includes('transfer list')) return 'list';
    if (heads.includes('search results')) return 'results';
    if (heads.includes('transfer market')) return 'market';
    if (heads.includes('home')) return 'home';
    return 'other';
  }
  function numeric(e, selector, regex) {
    const v = text(e, selector); if (v) return coin(v);
    const m = clean(e.innerText).match(regex); return m ? coin(m[1]) : 0;
  }
  function parseRow(e) {
    const name = text(e, '.name,.player-name,[data-player-name]') || attr(e, ['data-player-name']);
    const resource = attr(e, ['data-resource-id','data-definition-id']);
    const chem = attr(e, ['data-chemistry-style']) || text(e, '.chemistryStyle,.chem-style');
    const rating = coin(text(e, '.rating,[data-rating]') || attr(e, ['data-rating']));
    const t = clean(e.innerText), time = t.match(/(?:Time(?: Remaining)?\s*:?\s*)(<?\d+)\s*(seconds?|minutes?|hours?)/i);
    return {
      auctionId: attr(e, ['data-trade-id','data-auction-id']), itemId: attr(e, ['data-item-id']),
      identity: resource && name && rating && chem ? resource + ':' + chem : '', name, rating, chem: chem || 'Basic',
      startPrice: numeric(e, '.startPrice .value,[data-start-price-value]', /Start Price\s*:?\s*([\d,]+)/i),
      currentBid: numeric(e, '.currentBid .value,[data-current-bid-value]', /(?:Current )?Bid\s*:?\s*([\d,]+)/i),
      buyNow: numeric(e, '.buyNowPrice .value,[data-buy-now-value]', /Buy Now(?: Price)?\s*:?\s*([\d,]+)/i),
      paid: numeric(e, '[data-paid-value],.purchasePrice .value', /(?:Purchased For|Bought For|Winning Bid)\s*:?\s*([\d,]+)/i),
      salePrice: numeric(e, '[data-sale-price-value],.soldPrice .value', /(?:Sold For|Sold Price)\s*:?\s*([\d,]+)/i),
      timeSeconds: time ? Number(time[1].replace('<','')) * (/hour/i.test(time[2]) ? 3600 : /minute/i.test(time[2]) ? 60 : 1) : 999999,
      status: status(e)
    };
  }
  function rowElements() {
    const nodes = all('.listFUTItem,li[data-trade-id],li[data-auction-id],[data-fcplus-auction]');
    return nodes.filter(e => !nodes.some(other => other !== e && other.contains(e)));
  }
  function snapshot() {
    const rows = rowElements().map(parseRow);
    const searchName = rows.length && rows.every(r => r.name === rows[0].name) ? rows[0].name : '';
    const missing = rows.length ? rows.filter(r => !r.identity || !r.auctionId).length : 0;
    return { page: page(), searchName, rows, capturedAt: Date.now(), coins: coin(text(document, '.view-navbar-currency-coins .value,.ut-coins .value,[data-coins-value]')),
      security: security(), loggedOut: all('button').some(e => /^(log in|sign in)$/i.test(clean(e.textContent))),
      diagnostics: missing ? missing + ' rows lack verified auction/card IDs; live orders blocked.' : rows.length ? rows.length + ' identified EA rows' : 'No supported auction rows on this screen',
      url: location.origin + location.pathname };
  }
  function findRow(action) {
    return only(rowElements().filter(e => { const r = parseRow(e); return r.identity === action.identity && (action.itemId ? r.itemId === action.itemId : r.auctionId === action.auctionId); }), 'exact auction');
  }
  async function permit(action) {
    if (security()) throw new Error('EA verification required');
    const response = await native({ type: 'permit', id: action.id, runId: action.id.split(':')[0] });
    if (!response?.allowed) throw new Error('Session stopped or permission expired');
  }
  async function navigate(destination) {
    if (page() === destination) return;
    const labels = { market: 'Transfer Market', targets: 'Transfer Targets', list: 'Transfer List' };
    try { button(labels[destination]).click(); } catch (_) {
      // One explicit navigation step per tick; do not click generic back/confirmation buttons.
      button('Transfers').click();
      (await waitFor(() => button(labels[destination]))).click();
    }
    await waitFor(() => page() === destination);
  }
  async function search(target) {
    if (page() === 'results') button('Back').click();
    if (page() !== 'market') await navigate('market');
    // Clear prior filters so old price/quality filters cannot bias a valuation.
    button('Reset').click();
    const input = field('Player Name'); setValue(input, target.name);
    const choice = await waitFor(() => {
      const choices = all('.ut-player-search-control li,.playerSearchResults li,[role="option"]')
        .filter(e => text(e, '.name,.player-name') === target.name && (!target.rating || coin(text(e, '.rating')) === target.rating));
      return choices.length === 1 ? choices[0] : null;
    });
    choice.click();
    if (target.chem && target.chem !== 'Basic') {
      button('Chemistry Style').click();
      button(target.chem).click();
    }
    button('Search').click();
    await waitFor(() => page() === 'results');
  }
  async function execute(action) {
    let submitted = false;
    try {
      await permit(action);
      if (action.type === 'navigate') { await navigate(action.page); return; }
      if (action.type === 'search') { await search(action.target); return; }
      const e = findRow(action), before = parseRow(e);
      if (action.type === 'buy' && (before.status !== 'market' || before.buyNow !== action.amount)) throw new Error('Buy price/status changed');
      if (action.type === 'bid' && (!['market','outbid'].includes(before.status) || FCTrader.nextBid(before) !== action.amount)) throw new Error('Bid price/status changed');
      if (action.type === 'list' && !['won','expired'].includes(before.status)) throw new Error('Item is not available to list');
      e.click();
      if (action.type === 'list') {
        (await waitFor(() => button('List on Transfer Market'))).click();
        const dialog = await waitFor(() => only(all('.ut-sell-item-view,[role="dialog"]'), 'listing form'));
        setValue(field('Start Price', dialog), action.startPrice);
        setValue(field('Buy Now Price', dialog), action.amount);
        // Require an explicit 1 hour selection; never reuse a stale duration.
        const duration = all('select', dialog).find(x => Array.from(x.options).some(o => /1 Hour/i.test(o.text)));
        if (!duration) throw new Error('Cannot verify listing duration');
        const option = Array.from(duration.options).find(o => /^1 Hour$/i.test(clean(o.text)));
        if (!option) throw new Error('1 hour duration unavailable');
        duration.value = option.value; duration.dispatchEvent(new Event('change', { bubbles: true }));
        await permit(action); submitted = true; button('List on Transfer Market', dialog).click();
      } else {
        const label = action.type === 'buy' ? 'Buy Now' : 'Make Bid';
        if (action.type === 'bid') setValue(await waitFor(() => field('Bid')), action.amount);
        const orderButton = await waitFor(() => button(label));
        // Recheck identity and price immediately before sending an order.
        const latest = parseRow(findRow(action));
        if ((action.type === 'buy' ? latest.buyNow : FCTrader.nextBid(latest)) !== action.amount) throw new Error('Auction changed before submit');
        await permit(action); submitted = true; orderButton.click();
        // A dialog is optional. Confirm only an explicit buy/bid dialog containing the exact amount.
        await new Promise(r => setTimeout(r, 350));
        const dialogs = all('[role="dialog"],.ut-dialog-view');
        if (dialogs.length) {
          const dialog = only(dialogs, 'order confirmation'), t = clean(dialog.innerText);
          if (!/buy|purchase|bid/i.test(t) || !new RegExp('(?:^|[^0-9])' + String(action.amount).split('').join(',?') + '(?:[^0-9]|$)').test(t)) throw new Error('Unrecognized order confirmation');
          await permit(action); button('Yes', dialog).click();
        }
      }
      await native({ type: 'action_result', id: action.id, submitted: true, error: '' });
    } catch (error) {
      const message = clean(error.message).slice(0, 180); lastError = message;
      await native({ type: 'action_result', id: action.id, submitted, error: message });
    }
  }
  async function tick() {
    if (busy) return; busy = true;
    try {
      lastSnap = snapshot();
      const reply = await native({ type: 'tick', snapshot: lastSnap });
      if (reply?.reconcile && reply.state) {
        const cfg = Object.assign({}, reply.config, { runId: reply.state.runId, dryRun: true, targets: [] });
        const observed = FCTrader.decide(reply.state, lastSnap, cfg, Date.now());
        await native({ type: 'reconcile', state: observed.state });
      }
      if (!reply?.running) return;
      const decision = FCTrader.decide(reply.state, lastSnap, reply.config, Date.now());
      const ack = await native({ type: 'checkpoint', state: decision.state, runId: reply.config.runId, halt: decision.halt, command: decision.command });
      if (ack?.allowed && decision.command) await execute(decision.command);
    } catch (error) {
      lastError = clean(error.message).slice(0,180);
      try { await native({ type: 'adapter_error', error: lastError }); } catch (_) {}
    } finally { busy = false; }
  }
  setInterval(tick, 2500);
  tick();
})();
