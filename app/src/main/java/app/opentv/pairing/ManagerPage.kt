/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv.pairing

/**
 * The page a phone or laptop sees when managing channels.
 *
 * Like [PairingPage], one self-contained document with no external requests of any kind — no
 * fonts, no frameworks, no CDN. The browser is talking to a television on the local network, which
 * may have no route to the internet at all, so a page that needed a CDN to render would just fail.
 *
 * Unlike the pairing page this is opened on a phone OR a laptop, so it is responsive and
 * touch-friendly rather than TV-styled. All channel data arrives as JSON and is rendered with
 * `textContent`/DOM APIs (never string-built HTML), so a channel name full of `<`, `"`, `|` or
 * emoji can never break out into markup.
 */
object ManagerPage {

    fun page(token: String): String = """
        <!DOCTYPE html>
        <html lang="en">
        <head>
        <meta charset="utf-8">
        <meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover">
        <meta name="robots" content="noindex, nofollow">
        <title>OpenTV — manage channels</title>
        <style>
          :root { --bg:#07080c; --surface:#12141d; --surface2:#181b26; --line:#2a2f3d;
                  --text:#e8eaf0; --muted:#a8aec0; --accent:#7c93ff; --accent2:#3a447a;
                  --gold:#ffd166; --err:#ff6b6b; --ok:#6ee7a0; }
          * { box-sizing:border-box; -webkit-tap-highlight-color:transparent; }
          body { margin:0; background:var(--bg); color:var(--text);
                 font:16px/1.5 -apple-system,BlinkMacSystemFont,"Segoe UI",Roboto,sans-serif; }
          .wrap { max-width:760px; margin:0 auto; padding:20px 16px 80px; }
          header { position:sticky; top:0; z-index:5; background:linear-gradient(var(--bg),var(--bg) 78%,rgba(7,8,12,0));
                   padding-top:8px; }
          h1 { font-size:1.35rem; margin:0; letter-spacing:-.02em; }
          .hint { color:var(--muted); font-size:.85rem; margin:4px 0 14px; }
          .search { width:100%; padding:13px 14px; font-size:17px; margin-bottom:14px;
                    background:var(--surface); color:var(--text); border:1px solid var(--line); border-radius:11px; }
          .search:focus { outline:none; border-color:var(--accent); }
          .chips { display:flex; flex-wrap:wrap; gap:8px; margin-bottom:14px; }
          .chip { padding:9px 14px; font-size:.9rem; border-radius:999px; cursor:pointer;
                  background:var(--surface); border:1px solid var(--line); color:var(--muted); }
          .chip.on { background:var(--accent2); border-color:var(--accent); color:var(--text); }
          .list { display:flex; flex-direction:column; gap:8px; }
          .cat { display:flex; align-items:center; gap:12px; padding:15px 16px; cursor:pointer;
                 background:var(--surface); border:1px solid var(--line); border-radius:12px; }
          .cat:active { background:var(--surface2); }
          .cat .label { flex:1; font-weight:600; overflow:hidden; text-overflow:ellipsis; white-space:nowrap; }
          .cat .count { color:var(--muted); font-size:.85rem; font-variant-numeric:tabular-nums; }
          .cat .chev { color:var(--muted); }
          .row { display:flex; align-items:center; gap:11px; padding:10px 12px;
                 background:var(--surface); border:1px solid var(--line); border-radius:12px; }
          .row.hidden { opacity:.55; }
          .logo { width:44px; height:44px; border-radius:8px; background:#000; object-fit:contain; flex:none; }
          .rowmeta { flex:1; min-width:0; }
          .name { font-weight:600; cursor:text; display:inline-block; max-width:100%;
                  overflow:hidden; text-overflow:ellipsis; white-space:nowrap; vertical-align:bottom; }
          .name:hover { text-decoration:underline dotted; }
          .rename { width:100%; padding:6px 8px; font-size:16px; font-weight:600;
                    background:var(--bg); color:var(--text); border:1px solid var(--accent); border-radius:8px; }
          .sub { color:var(--muted); font-size:.78rem; margin-top:2px; }
          .sub .badge { color:var(--err); font-weight:600; }
          .ctrls { display:flex; align-items:center; gap:6px; flex:none; }
          .btn { width:40px; height:40px; display:flex; align-items:center; justify-content:center;
                 font-size:18px; border-radius:9px; cursor:pointer; user-select:none;
                 background:var(--surface2); border:1px solid var(--line); color:var(--muted); }
          .btn:active { transform:scale(.92); }
          .btn.star.on { color:var(--gold); border-color:var(--gold); }
          .btn.eye.on { color:var(--accent); border-color:var(--accent); }
          .btn.mv { font-size:14px; }
          .btn[disabled] { opacity:.3; pointer-events:none; }
          .back { display:inline-flex; align-items:center; gap:6px; padding:9px 14px; margin-bottom:12px;
                  background:var(--surface); border:1px solid var(--line); border-radius:10px;
                  color:var(--text); font-weight:600; cursor:pointer; }
          .chanhead { font-size:1.1rem; font-weight:700; margin:0 0 4px; }
          .empty { color:var(--muted); text-align:center; padding:40px 10px; }
          #status { position:fixed; left:50%; bottom:18px; transform:translateX(-50%) translateY(120%);
                    max-width:90%; padding:11px 18px; border-radius:11px; font-size:.9rem; font-weight:600;
                    background:var(--surface2); border:1px solid var(--line); color:var(--text);
                    transition:transform .25s ease; z-index:20; box-shadow:0 8px 24px rgba(0,0,0,.4); }
          #status.show { transform:translateX(-50%) translateY(0); }
          #status.ok { border-color:var(--ok); color:var(--ok); }
          #status.err { border-color:var(--err); color:#ffc9c9; }
          .privacy { color:var(--muted); font-size:.8rem; margin-top:28px; line-height:1.5; }
        </style>
        </head>
        <body>
          <div class="wrap">
            <header>
              <h1>OpenTV — manage channels</h1>
              <p class="hint">Changes apply to your TV the moment you make them.</p>
            </header>

            <section id="catView">
              <div id="srcBar" class="chips" hidden></div>
              <input id="catSearch" class="search" type="text" placeholder="Filter categories…"
                     autocapitalize="off" autocorrect="off" spellcheck="false">
              <div id="catList" class="list"></div>
              <div id="catEmpty" class="empty" hidden></div>
            </section>

            <section id="chanView" hidden>
              <div id="back" class="back">&#8249;&nbsp;Categories</div>
              <h2 id="chanHead" class="chanhead"></h2>
              <p class="hint" id="chanHint">Tap a name to rename. Star to favourite, eye to hide, arrows to reorder.</p>
              <div id="chanList" class="list"></div>
              <div id="chanEmpty" class="empty" hidden></div>
            </section>

            <p class="privacy">
              This page is served by your television, over your own network — there is no OpenTV
              account and no OpenTV server. Nothing you change here leaves your home.
            </p>
          </div>

          <div id="status"></div>

        <script>
          var EMBEDDED_TOKEN = "$token";
          var token = new URLSearchParams(location.search).get('t') || EMBEDDED_TOKEN;

          var meta = { sources: [], categories: [] };
          var currentSource = null;   // selected source filter, null = all
          var currentCat = null;      // the category object being viewed
          var channels = [];          // rows currently shown, in display order

          var el = function(id){ return document.getElementById(id); };
          var statusTimer = null;
          function setStatus(msg, kind){
            var s = el('status');
            s.textContent = msg;
            s.className = 'show ' + (kind || '');
            if (statusTimer) clearTimeout(statusTimer);
            statusTimer = setTimeout(function(){ s.className = kind || ''; }, 2600);
          }

          function withToken(path){ return path + (path.indexOf('?') >= 0 ? '&' : '?') + 't=' + encodeURIComponent(token); }
          async function getJson(path){
            var r = await fetch(withToken(path));
            if (!r.ok) throw new Error('HTTP ' + r.status);
            return r.json();
          }
          async function post(path, bodyObj){
            var r = await fetch(withToken(path), {
              method: 'POST', headers: { 'Content-Type': 'application/json' },
              body: JSON.stringify(bodyObj)
            });
            if (!r.ok) throw new Error('HTTP ' + r.status);
            return r.json();
          }
          var UNREACH = "Couldn't reach the TV — still on the same wifi?";

          // ---- Categories --------------------------------------------------------------------
          async function loadMeta(){
            try {
              meta = await getJson('/meta');
              renderSources();
              renderCategories();
            } catch (e) {
              el('catList').innerHTML = '';
              var d = el('catEmpty'); d.hidden = false; d.textContent = UNREACH;
            }
          }

          function renderSources(){
            var bar = el('srcBar');
            if (!meta.sources || meta.sources.length < 2) { bar.hidden = true; return; }
            bar.hidden = false;
            bar.innerHTML = '';
            var mk = function(label, id){
              var c = document.createElement('div');
              c.className = 'chip' + (currentSource === id ? ' on' : '');
              c.textContent = label;
              c.onclick = function(){ currentSource = id; renderSources(); renderCategories(); };
              bar.appendChild(c);
            };
            mk('All sources', null);
            meta.sources.forEach(function(s){ mk(s.name, s.id); });
          }

          function renderCategories(){
            var q = el('catSearch').value.trim().toLowerCase();
            var list = el('catList');
            list.innerHTML = '';
            var cats = (meta.categories || []).filter(function(c){
              if (currentSource !== null && c.sourceId !== currentSource) return false;
              if (q && c.label.toLowerCase().indexOf(q) < 0) return false;
              return true;
            });
            var empty = el('catEmpty');
            if (cats.length === 0) {
              empty.hidden = false;
              empty.textContent = (meta.categories && meta.categories.length) ? 'No categories match that.' : 'No channels yet. Add a provider on the TV first.';
              return;
            }
            empty.hidden = true;
            cats.forEach(function(c){
              var row = document.createElement('div');
              row.className = 'cat';
              var label = document.createElement('span'); label.className = 'label'; label.textContent = c.label;
              var count = document.createElement('span'); count.className = 'count'; count.textContent = c.count;
              var chev = document.createElement('span'); chev.className = 'chev'; chev.innerHTML = '&#8250;';
              row.appendChild(label); row.appendChild(count); row.appendChild(chev);
              row.onclick = function(){ openCategory(c); };
              list.appendChild(row);
            });
          }
          el('catSearch').addEventListener('input', renderCategories);

          // ---- Channels ----------------------------------------------------------------------
          async function openCategory(cat){
            currentCat = cat;
            el('catView').hidden = true;
            el('chanView').hidden = false;
            el('chanHead').textContent = cat.label;
            window.scrollTo(0, 0);
            var listEl = el('chanList'); listEl.innerHTML = '';
            var empty = el('chanEmpty'); empty.hidden = true;
            try {
              channels = await getJson('/channels?cat=' + encodeURIComponent(cat.id) + '&source=' + cat.sourceId);
              renderChannels();
            } catch (e) {
              empty.hidden = false; empty.textContent = UNREACH;
            }
          }

          el('back').onclick = function(){
            el('chanView').hidden = true;
            el('catView').hidden = false;
            channels = [];
          };

          function renderChannels(){
            var listEl = el('chanList');
            listEl.innerHTML = '';
            var empty = el('chanEmpty');
            if (!channels.length) { empty.hidden = false; empty.textContent = 'No channels in this category.'; return; }
            empty.hidden = true;
            channels.forEach(function(c, i){ listEl.appendChild(buildRow(c, i)); });
          }

          function buildRow(c, index){
            var row = document.createElement('div');
            row.className = 'row' + (c.hidden ? ' hidden' : '');

            var logo = document.createElement('img');
            logo.className = 'logo'; logo.alt = '';
            if (c.logo) { logo.src = c.logo; } else { logo.style.visibility = 'hidden'; }
            logo.onerror = function(){ logo.style.visibility = 'hidden'; };

            var mid = document.createElement('div'); mid.className = 'rowmeta';
            var name = document.createElement('span'); name.className = 'name'; name.textContent = c.name;
            name.onclick = function(){ startRename(name, c); };
            var sub = document.createElement('div'); sub.className = 'sub';
            mid.appendChild(name); mid.appendChild(sub);
            function refreshSub(){
              sub.innerHTML = '';
              if (c.number !== null && c.number !== undefined) {
                sub.appendChild(document.createTextNode('No. ' + c.number));
              }
              if (c.hidden) {
                if (sub.childNodes.length) sub.appendChild(document.createTextNode('  ·  '));
                var b = document.createElement('span'); b.className = 'badge'; b.textContent = 'Hidden';
                sub.appendChild(b);
              }
              if (!sub.childNodes.length) sub.textContent = c.original !== c.name ? c.original : ' ';
            }
            refreshSub();

            var ctrls = document.createElement('div'); ctrls.className = 'ctrls';

            var star = document.createElement('div');
            star.className = 'btn star' + (c.favourite ? ' on' : '');
            star.innerHTML = c.favourite ? '&#9733;' : '&#9734;';
            star.title = 'Favourite';
            star.onclick = function(){
              var target = !c.favourite;
              post('/channel', { id: c.id, favourite: target })
                .then(function(){ c.favourite = target; star.className = 'btn star' + (target ? ' on' : ''); star.innerHTML = target ? '&#9733;' : '&#9734;'; setStatus(target ? 'Favourited' : 'Unfavourited', 'ok'); })
                .catch(function(){ setStatus(UNREACH, 'err'); });
            };

            var eye = document.createElement('div');
            eye.className = 'btn eye' + (c.hidden ? '' : ' on');
            eye.innerHTML = c.hidden ? '&#128584;' : '&#128065;';
            eye.title = c.hidden ? 'Hidden — tap to show' : 'Showing — tap to hide';
            eye.onclick = function(){
              var target = !c.hidden;
              post('/channel', { id: c.id, hidden: target })
                .then(function(){
                  c.hidden = target;
                  row.className = 'row' + (target ? ' hidden' : '');
                  eye.className = 'btn eye' + (target ? '' : ' on');
                  eye.innerHTML = target ? '&#128584;' : '&#128065;';
                  eye.title = target ? 'Hidden — tap to show' : 'Showing — tap to hide';
                  refreshSub();
                  setStatus(target ? 'Hidden from the guide' : 'Showing in the guide', 'ok');
                })
                .catch(function(){ setStatus(UNREACH, 'err'); });
            };

            var up = document.createElement('div');
            up.className = 'btn mv'; up.innerHTML = '&#9650;'; up.title = 'Move up';
            if (index === 0) up.setAttribute('disabled', '');
            up.onclick = function(){ move(index, index - 1); };

            var down = document.createElement('div');
            down.className = 'btn mv'; down.innerHTML = '&#9660;'; down.title = 'Move down';
            if (index === channels.length - 1) down.setAttribute('disabled', '');
            down.onclick = function(){ move(index, index + 1); };

            ctrls.appendChild(star); ctrls.appendChild(eye); ctrls.appendChild(up); ctrls.appendChild(down);
            row.appendChild(logo); row.appendChild(mid); row.appendChild(ctrls);
            return row;
          }

          function startRename(span, c){
            var input = document.createElement('input');
            input.type = 'text'; input.className = 'rename'; input.value = c.name;
            span.replaceWith(input); input.focus(); input.select();
            var closed = false;
            function finish(save){
              if (closed) return; closed = true;
              var val = input.value.trim();
              if (save) {
                c.name = val || c.original;
                post('/channel', { id: c.id, name: val })
                  .then(function(){ setStatus('Renamed', 'ok'); })
                  .catch(function(){ setStatus("Couldn't save the name.", 'err'); });
              }
              var fresh = document.createElement('span');
              fresh.className = 'name'; fresh.textContent = c.name;
              fresh.onclick = function(){ startRename(fresh, c); };
              input.replaceWith(fresh);
            }
            input.addEventListener('keydown', function(e){
              if (e.key === 'Enter') { e.preventDefault(); finish(true); }
              else if (e.key === 'Escape') { finish(false); }
            });
            input.addEventListener('blur', function(){ finish(true); });
          }

          function move(from, to){
            if (to < 0 || to >= channels.length) return;
            var item = channels.splice(from, 1)[0];
            channels.splice(to, 0, item);
            renderChannels();
            post('/reorder', { ids: channels.map(function(c){ return c.id; }) })
              .then(function(){ setStatus('Order saved', 'ok'); })
              .catch(function(){ setStatus(UNREACH, 'err'); });
          }

          loadMeta();
        </script>
        </body>
        </html>
    """.trimIndent()
}
