const test = require('node:test');
const assert = require('node:assert/strict');
const E = require('../app/src/main/assets/fcplus_ext/trader-core.js');
const T = 1800576000000;
const target = {name:'Example Player',rating:80,chem:'Basic'};
const cfg = {runId:'test',dryRun:false,targets:[target],maxBuy:5000,budget:20000,minProfit:300,minRoi:8,maxOpen:3,durationMinutes:20,intervalSeconds:15,maxActions:60,dailyCap:100000};
const row = (id,bin=2000) => ({auctionId:id,itemId:'',identity:'123:Basic',name:target.name,rating:80,chem:'Basic',startPrice:1500,currentBid:0,buyNow:bin,status:'market',timeSeconds:60,paid:0,salePrice:0});
const snap = (rows=[row('a'),row('b'),row('c'),row('d')],page='results',now=T) => ({rows,page,searchName:target.name,coins:50000,capturedAt:now,security:'',loggedOut:false});
const initial = () => E.fresh(T);
const decide = (state=initial(),s=snap(),c=cfg,now=T) => E.decide(state,s,c,now);

test('legal increments at every price boundary',()=>{
  assert.equal(E.nextBid({...row('a'),currentBid:950}),1000);
  assert.equal(E.nextBid({...row('a'),currentBid:1000}),1100);
  assert.equal(E.nextBid({...row('a'),currentBid:10000}),10250);
  assert.equal(E.nextBid({...row('a'),currentBid:50000}),50500);
  assert.equal(E.nextBid({...row('a'),currentBid:100000}),101000);
  assert.equal(E.down(149),0); assert.equal(E.down(999),950);
});
test('bid intent persisted before execution',()=>{
  const d=decide(); assert.equal(d.command.type,'bid'); assert.equal(d.command.amount,1500);
  assert.equal(d.state.pending.id,d.command.id); assert.equal(d.state.ledger.length,0);
});
test('Dry Run never emits a financial command or books fake profit',()=>{
  const d=decide(initial(),snap(),{...cfg,dryRun:true}); assert.equal(d.command,null); assert.equal(d.state.pending,null);
  assert.equal(d.state.dayProfit,0); assert.match(d.state.message,/DRY RUN/);
});
test('duplicate tick waits rather than repeating order',()=>{
  const d=decide(); const next=decide(d.state,snap(undefined,'results',T+16000),cfg,T+16000);
  assert.equal(next.command,null); assert.equal(next.state.pending.id,d.command.id);
});
test('lost response stops after timeout, retaining unresolved intent',()=>{
  const d=decide(); const next=decide(d.state,snap(undefined,'results',T+46000),cfg,T+46000);
  assert.equal(next.halt,true); assert.ok(next.state.pending); assert.equal(next.command,null);
});
test('restart refuses outstanding pending intent',()=>{
  const d=decide(); const next=decide(d.state,snap(),{...cfg,runId:'new'});
  assert.equal(next.halt,true); assert.ok(next.state.pending);
});
test('confirmed highest bid reserves coins; unconfirmed snapshot does not',()=>{
  const d=decide(); const r={...row('a'),status:'highest',currentBid:1500};
  const n=decide(d.state,snap([r],'targets',T+1000),cfg,T+1000);
  assert.equal(n.state.pending,null); assert.equal(n.state.ledger[0].reserved,1500); assert.equal(n.state.dayProfit,0);
});
test('rebid uses next legal increment and respects ceiling',()=>{
  const d=decide(); let n=decide(d.state,snap([{...row('a'),status:'highest',currentBid:1500}],'targets',T+1000),cfg,T+1000);
  n=decide(n.state,snap([{...row('a'),status:'outbid',currentBid:1500}],'targets',T+16000),cfg,T+16000);
  assert.equal(n.command.type,'bid'); assert.equal(n.command.amount,1600);
  const over=structuredClone(n.state); over.pending=null;
  const x=decide(over,snap([{...row('a'),status:'outbid',currentBid:1600}],'targets',T+32000),cfg,T+32000);
  assert.notEqual(x.command?.type,'bid');
});
test('buy now win → list → sale is accounted once, after tax',()=>{
  const rows=[row('a',1500),row('b'),row('c'),row('d')];
  let d=decide(initial(),snap(rows)); assert.equal(d.command.type,'buy');
  const won={...rows[0],status:'won',itemId:'item1',paid:1500};
  d=decide(d.state,snap([won],'targets',T+16000),cfg,T+16000);
  assert.equal(d.state.ledger[0].state,'won'); assert.equal(d.state.sessionSpent,1500); assert.equal(d.command.type,'list');
  const listed={...won,status:'listed',buyNow:2000};
  d=decide(d.state,snap([listed],'list',T+32000),cfg,T+32000); assert.equal(d.state.ledger[0].state,'listed');
  const sold={...listed,status:'sold',salePrice:2000};
  d=decide(d.state,snap([sold],'list',T+48000),cfg,T+48000); assert.equal(d.state.dayProfit,400);
  d=decide(d.state,snap([sold],'list',T+49000),cfg,T+49000); assert.equal(d.state.dayProfit,400);
});
test('missing or wrong item sale cannot create profit',()=>{
  const s=initial();s.runId='test';s.startedAt=T;s.ledger=[{auctionId:'a',itemId:'item1',identity:'123:Basic',name:target.name,state:'listed',cost:1500,sellPrice:2000}];
  const d=decide(s,snap([{...row('z'),itemId:'item2',status:'sold',salePrice:2000}],'list'));
  assert.equal(d.state.dayProfit,0);assert.equal(d.state.ledger[0].state,'listed');
});
test('daily realized profit cap halts; UTC rollover resets computed total',()=>{
  const s=initial();s.runId='test';s.startedAt=T;s.ledger=[{state:'sold',soldAt:T,profit:100000}];
  assert.equal(decide(s).halt,true);
  const n=decide(s,snap(undefined,'results',T+86400000),{...cfg,runId:'next'},T+86400000);
  assert.equal(n.state.dayProfit,0);assert.equal(n.halt,false);
});
test('minimum data, exact variant and stale data prevent orders',()=>{
  assert.notEqual(decide(initial(),snap([row('a')])).command?.type,'bid');
  const rows=[row('a'),row('b'),row('c'),{...row('d'),identity:'other:Basic'}];
  assert.equal(decide(initial(),snap(rows)).command,null);
  assert.equal(decide(initial(),snap(),cfg,T+11000).command,null);
});
test('no orders with insufficient coins/budget, time limit or CAPTCHA',()=>{
  const poor=decide(initial(),{...snap(),coins:100}); assert.notEqual(poor.command?.type,'bid');
  const budget=decide(initial(),snap(),{...cfg,budget:1000}); assert.notEqual(budget.command?.type,'bid');
  const s=initial();s.runId='test';s.startedAt=T-1200001;assert.equal(decide(s).halt,true);
  assert.equal(decide(initial(),{...snap(),security:'captcha'}).halt,true);
});
test('unrelated auctions cannot confirm a pending order',()=>{
  const d=decide();const n=decide(d.state,snap([{...row('z'),status:'highest',currentBid:1500}],'targets',T+1000),cfg,T+1000);
  assert.ok(n.state.pending);assert.equal(n.state.ledger.length,0);
});
test('multiple open positions do not bounce forever between targets/list',()=>{
  const s=initial();s.runId='test';s.startedAt=T;s.ledger=[{state:'bid',auctionId:'x',identity:'1',reserved:1000},{state:'listed',itemId:'y',identity:'2',cost:1000,sellPrice:2000}];
  let d=decide(s,snap([],'targets'));assert.equal(d.command.page,'list');
  d=decide(d.state,snap([],'list',T+16000),cfg,T+16000);assert.equal(d.command.type,'search');
});
test('reservations survive restart and reduce available budget',()=>{
  const s=initial();s.ledger=[{state:'bid',auctionId:'x',identity:'other',reserved:19500}];
  const d=decide(s);assert.notEqual(d.command?.type,'bid');
});

test('EA player matching ignores case and accents',()=>{
  assert.equal(E.sameName('victor munoz','Víctor Muñoz'),true);
  assert.equal(E.sameName('Víctor Muñoz','VICTOR   MUNOZ'),true);
  assert.equal(E.sameName('Victor Munoz','Different Player'),false);
});
test('Dry Run can evaluate exact source auctions even when identical UI rows cannot be uniquely bound',()=>{
  const accentedTarget={name:'victor munoz',rating:80,chem:'Basic'};
  const rows=[row('a'),row('b'),row('c'),row('d')].map(r=>({...r,name:'Víctor Muñoz',uiBound:false}));
  const sourceSnap={...snap(rows),searchName:'Víctor Muñoz'};
  const dry=decide(initial(),sourceSnap,{...cfg,dryRun:true,targets:[accentedTarget]});
  assert.equal(dry.command,null);
  assert.equal(dry.state.pending,null);
  assert.match(dry.state.message,/DRY RUN: would bid/);
  const live=decide(initial(),sourceSnap,{...cfg,dryRun:false,targets:[accentedTarget]});
  assert.equal(live.command.type,'search');
  assert.equal(live.state.pending,null);
  assert.match(live.state.message,/waiting for a uniquely bound EA row/);
});
