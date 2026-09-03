(function() {
  const codeInput = document.getElementById('codeInput');
  const playlistsContainer = document.getElementById('playlistsContainer');
  const btnAddPlaylist = document.getElementById('btnAddPlaylist');
  const setupForm = document.getElementById('setupForm');
  const submitBtn = document.getElementById('submitBtn');
  const spinner = document.getElementById('spinner');
  const btnText = document.getElementById('btnText');
  const alertBanner = document.getElementById('alertBanner');
  const successScreen = document.getElementById('successScreen');
  const successSummary = document.getElementById('successSummary');
  const resetBtn = document.getElementById('resetBtn');
  const deviceStatusBar = document.getElementById('deviceStatusBar');
  const statusDot = document.getElementById('statusDot');
  const deviceStatusText = document.getElementById('deviceStatusText');

  // State
  let playlistCounter = 0;
  const playlists = []; // Array of card controllers
  let sessionCheckTimeout = null;
  let lastCheckedCode = '';

  function showAlert(message, type = 'error') {
    alertBanner.textContent = message;
    alertBanner.className = 'alert ' + (type === 'error' ? 'alert-error' : 'alert-success');
    alertBanner.style.display = 'block';
    alertBanner.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
  }

  function hideAlert() {
    alertBanner.style.display = 'none';
  }

  function setLoading(isLoading) {
    submitBtn.disabled = isLoading;
    spinner.style.display = isLoading ? 'inline-block' : 'none';
    btnText.textContent = isLoading ? 'Updating TV...' : 'Update TV Playlists';
  }

  function setDeviceStatus(text, isOnline = false) {
    if (!deviceStatusBar) return;
    deviceStatusBar.style.display = 'flex';
    deviceStatusText.textContent = text;
    if (isOnline) {
      statusDot.className = 'status-dot online';
    } else {
      statusDot.className = 'status-dot';
    }
  }

  function hideDeviceStatus() {
    if (deviceStatusBar) deviceStatusBar.style.display = 'none';
  }

  // Check session on the server and pull device playlists if available
  async function checkSession(code) {
    if (!code || code.length !== 6 || code === lastCheckedCode) return;
    lastCheckedCode = code;

    setDeviceStatus(`Connecting to session ${code}...`, false);

    try {
      const res = await fetch(`/api/pair/session?code=${encodeURIComponent(code)}`);
      const data = await res.json();

      if (!res.ok) {
        setDeviceStatus(`Code ${code} not found or expired.`, false);
        return;
      }

      if (data.connected) {
        const sourcesCount = (data.sources && data.sources.length) || 0;
        if (sourcesCount > 0) {
          setDeviceStatus(`Connected to TV — ${sourcesCount} existing playlist(s) loaded for editing`, true);
          populateFromExistingSources(data.sources);
        } else {
          setDeviceStatus('Connected to TV Box (Ready to add playlists)', true);
        }
        btnText.textContent = 'Update TV Playlists';
      } else {
        setDeviceStatus(`Session ${code} found. Waiting for TV to connect...`, false);
      }
    } catch (_) {
      setDeviceStatus('Could not verify connection to TV.', false);
    }
  }

  function populateFromExistingSources(sources) {
    playlistsContainer.innerHTML = '';
    playlists.length = 0;
    playlistCounter = 0;

    sources.forEach(src => {
      createPlaylistCard(src.kind || 'm3u', src);
    });

    if (sources.length === 0) {
      createPlaylistCard('m3u');
    }
  }

  // Card Creator
  function createPlaylistCard(initialKind = 'm3u', preset = null) {
    playlistCounter++;
    const cardId = `card_${playlistCounter}`;

    const card = document.createElement('div');
    card.className = 'playlist-card';
    card.id = cardId;

    let currentKind = (preset && preset.kind) ? preset.kind.toLowerCase() : initialKind;
    if (currentKind !== 'xtream' && currentKind !== 'm3u') currentKind = 'm3u';
    let fetchedCategories = [];

    const existingId = preset ? (preset.id || null) : null;
    const initialName = preset ? (preset.name || '') : '';
    const initialM3uUrl = (preset && currentKind === 'm3u') ? (preset.url || preset.playlistUrl || '') : '';
    const initialXtreamUrl = (preset && currentKind === 'xtream') ? (preset.url || preset.serverUrl || '') : '';
    const initialUser = (preset && preset.username) || '';
    const initialPass = (preset && preset.password) || '';
    const initialEpg = (preset && preset.epgUrl) || '';

    card.innerHTML = `
      <div class="card-header">
        <div class="card-title-group">
          <span class="card-title">Playlist #${playlistCounter}</span>
          <span class="kind-badge ${currentKind === 'm3u' ? 'badge-m3u' : 'badge-xtream'}" id="${cardId}_badge">
            ${currentKind.toUpperCase()}
          </span>
          ${existingId ? '<span style="font-size:11px; color:var(--text-hint);">(Existing on TV)</span>' : ''}
        </div>
        <button type="button" class="btn-remove-card" id="${cardId}_btnRemove" title="Remove Playlist">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="currentColor">
            <path d="M6 19c0 1.1.9 2 2 2h8c1.1 0 2-.9 2-2V7H6v12zM19 4h-3.5l-1-1h-5l-1 1H5v2h14V4z"/>
          </svg>
          Remove
        </button>
      </div>

      <!-- Type Switcher -->
      <div class="type-switcher">
        <button type="button" class="type-btn ${currentKind === 'm3u' ? 'active' : ''}" id="${cardId}_typeM3u">
          M3U Playlist
        </button>
        <button type="button" class="type-btn ${currentKind === 'xtream' ? 'active' : ''}" id="${cardId}_typeXtream">
          Xtream Codes API
        </button>
      </div>

      <!-- Common Name -->
      <div class="form-group">
        <label for="${cardId}_name">Playlist / Provider Name (Optional)</label>
        <input 
          type="text" 
          id="${cardId}_name" 
          class="input-field" 
          value="${initialName}" 
          placeholder="e.g. Primary TV, Sports M3U" 
          inputmode="text" 
          autocapitalize="words" 
          autocorrect="off" 
          spellcheck="false" 
          enterkeyhint="next"
        >
      </div>

      <!-- M3U Section -->
      <div id="${cardId}_m3uSection" style="display: ${currentKind === 'm3u' ? 'block' : 'none'};">
        <div class="form-group">
          <label for="${cardId}_m3uUrl">M3U Playlist URL *</label>
          <div class="input-action-wrapper">
            <input 
              type="url" 
              id="${cardId}_m3uUrl" 
              class="input-field" 
              value="${initialM3uUrl}" 
              placeholder="https://example.com/playlist.m3u8" 
              inputmode="url" 
              autocapitalize="none" 
              autocorrect="off" 
              spellcheck="false" 
              enterkeyhint="next"
            >
            <button type="button" class="btn-input-action btn-paste" data-target="${cardId}_m3uUrl" title="Paste from clipboard">
              Paste
            </button>
          </div>
        </div>
        <div class="form-group">
          <label for="${cardId}_m3uEpg">XMLTV EPG URL (Optional)</label>
          <div class="input-action-wrapper">
            <input 
              type="url" 
              id="${cardId}_m3uEpg" 
              class="input-field" 
              value="${initialEpg}" 
              placeholder="https://example.com/epg.xml" 
              inputmode="url" 
              autocapitalize="none" 
              autocorrect="off" 
              spellcheck="false" 
              enterkeyhint="next"
            >
            <button type="button" class="btn-input-action btn-paste" data-target="${cardId}_m3uEpg" title="Paste from clipboard">
              Paste
            </button>
          </div>
        </div>
      </div>

      <!-- Xtream Section -->
      <div id="${cardId}_xtreamSection" style="display: ${currentKind === 'xtream' ? 'block' : 'none'};">
        <div class="form-group">
          <label for="${cardId}_xtreamHost">Server Address *</label>
          <div class="input-action-wrapper">
            <input 
              type="url" 
              id="${cardId}_xtreamHost" 
              class="input-field" 
              value="${initialXtreamUrl}" 
              placeholder="http://provider.tv:8080" 
              inputmode="url" 
              autocapitalize="none" 
              autocorrect="off" 
              spellcheck="false" 
              enterkeyhint="next"
            >
            <button type="button" class="btn-input-action btn-paste" data-target="${cardId}_xtreamHost" title="Paste from clipboard">
              Paste
            </button>
          </div>
        </div>
        <div class="form-group">
          <label for="${cardId}_xtreamUser">Username *</label>
          <input 
            type="text" 
            id="${cardId}_xtreamUser" 
            class="input-field" 
            value="${initialUser}" 
            placeholder="Username" 
            inputmode="text" 
            autocomplete="off" 
            autocapitalize="none" 
            autocorrect="off" 
            spellcheck="false" 
            enterkeyhint="next"
          >
        </div>
        <div class="form-group">
          <label for="${cardId}_xtreamPass">Password *</label>
          <div class="password-wrapper">
            <input 
              type="password" 
              id="${cardId}_xtreamPass" 
              class="input-field" 
              value="${initialPass}" 
              placeholder="Password" 
              autocomplete="new-password" 
              autocapitalize="none" 
              autocorrect="off" 
              spellcheck="false" 
              enterkeyhint="done"
            >
            <button type="button" id="${cardId}_togglePass" class="password-toggle">Show</button>
          </div>
        </div>

        <!-- Xtream Filter & Content Options -->
        <details class="filter-accordion">
          <summary>Channel &amp; Content Options</summary>
          <div class="filter-body">
            <!-- Content Types -->
            <label style="margin-bottom:8px;">Content Types to Include</label>
            <div class="content-toggles">
              <label class="toggle-label">
                <input type="checkbox" id="${cardId}_chkLive" checked> Live Channels
              </label>
              <label class="toggle-label">
                <input type="checkbox" id="${cardId}_chkVod"> Movies (VOD)
              </label>
              <label class="toggle-label">
                <input type="checkbox" id="${cardId}_chkSeries"> Series (Shows)
              </label>
            </div>

            <!-- Exclude Keywords -->
            <div class="form-group" style="margin-bottom: 14px;">
              <label for="${cardId}_excludeWords">Exclude Channels Containing</label>
              <input 
                type="text" 
                id="${cardId}_excludeWords" 
                class="input-field" 
                placeholder="e.g. Adult, XXX, 24/7, PPV" 
                inputmode="text" 
                autocapitalize="none" 
                autocorrect="off"
              >
              <div class="quick-tags">
                <button type="button" class="tag-btn" data-tag="Adult">+ Adult</button>
                <button type="button" class="tag-btn" data-tag="XXX">+ XXX</button>
                <button type="button" class="tag-btn" data-tag="24/7">+ 24/7</button>
                <button type="button" class="tag-btn" data-tag="PPV">+ PPV</button>
                <button type="button" class="tag-btn" data-tag="VIP">+ VIP</button>
              </div>
            </div>

            <!-- Include Keywords -->
            <div class="form-group" style="margin-bottom: 14px;">
              <label for="${cardId}_includeWords">Include ONLY Channels (Optional)</label>
              <input 
                type="text" 
                id="${cardId}_includeWords" 
                class="input-field" 
                placeholder="e.g. US, UK, Sports (blank = all)" 
                inputmode="text" 
                autocapitalize="none" 
                autocorrect="off"
              >
            </div>

            <!-- Fetch Categories from Server -->
            <button type="button" class="btn-fetch-cats" id="${cardId}_btnFetchCats">
              <svg width="15" height="15" viewBox="0 0 24 24" fill="currentColor">
                <path d="M12 4V1L8 5l4 4V6c3.31 0 6 2.69 6 6 0 1.01-.25 1.97-.7 2.8l1.46 1.46A7.93 7.93 0 0020 12c0-4.42-3.58-8-8-8zm0 14c-3.31 0-6-2.69-6-6 0-1.01.25-1.97.7-2.8L5.24 7.74A7.93 7.93 0 004 12c0 4.42 3.58 8 8 8v3l4-4-4-4v3z"/>
              </svg>
              <span>Load Categories from Server</span>
            </button>

            <!-- Category Picker UI -->
            <div class="cat-picker-container" id="${cardId}_catPicker">
              <div class="cat-picker-header">
                <span>Select Categories to Include:</span>
                <div>
                  <button type="button" class="tag-btn" id="${cardId}_btnSelectAllCats">All</button>
                  <button type="button" class="tag-btn" id="${cardId}_btnDeselectAllCats">None</button>
                </div>
              </div>
              <input 
                type="text" 
                id="${cardId}_catSearch" 
                class="input-field" 
                style="padding:8px 12px; font-size:14px; margin-bottom:10px; min-height:40px;" 
                placeholder="Search categories..."
                inputmode="search"
                autocapitalize="none"
              >
              <div id="${cardId}_catList"></div>
            </div>
          </div>
        </details>
      </div>
    `;

    playlistsContainer.appendChild(card);

    // Elements inside this card
    const badge = document.getElementById(`${cardId}_badge`);
    const btnRemove = document.getElementById(`${cardId}_btnRemove`);
    const typeM3uBtn = document.getElementById(`${cardId}_typeM3u`);
    const typeXtreamBtn = document.getElementById(`${cardId}_typeXtream`);
    const m3uSection = document.getElementById(`${cardId}_m3uSection`);
    const xtreamSection = document.getElementById(`${cardId}_xtreamSection`);
    const togglePassBtn = document.getElementById(`${cardId}_togglePass`);
    const xtreamPass = document.getElementById(`${cardId}_xtreamPass`);
    const excludeInput = document.getElementById(`${cardId}_excludeWords`);
    const btnFetchCats = document.getElementById(`${cardId}_btnFetchCats`);
    const catPicker = document.getElementById(`${cardId}_catPicker`);
    const catList = document.getElementById(`${cardId}_catList`);
    const catSearch = document.getElementById(`${cardId}_catSearch`);
    const btnSelectAllCats = document.getElementById(`${cardId}_btnSelectAllCats`);
    const btnDeselectAllCats = document.getElementById(`${cardId}_btnDeselectAllCats`);

    // Switch Kind
    typeM3uBtn.addEventListener('click', () => {
      if (currentKind === 'm3u') return;
      currentKind = 'm3u';
      typeM3uBtn.classList.add('active');
      typeXtreamBtn.classList.remove('active');
      badge.textContent = 'M3U';
      badge.className = 'kind-badge badge-m3u';
      m3uSection.style.display = 'block';
      xtreamSection.style.display = 'none';
      hideAlert();
    });

    typeXtreamBtn.addEventListener('click', () => {
      if (currentKind === 'xtream') return;
      currentKind = 'xtream';
      typeXtreamBtn.classList.add('active');
      typeM3uBtn.classList.remove('active');
      badge.textContent = 'XTREAM';
      badge.className = 'kind-badge badge-xtream';
      xtreamSection.style.display = 'block';
      m3uSection.style.display = 'none';
      hideAlert();
    });

    // Password Toggle
    togglePassBtn.addEventListener('click', () => {
      const isPassword = xtreamPass.type === 'password';
      xtreamPass.type = isPassword ? 'text' : 'password';
      togglePassBtn.textContent = isPassword ? 'Hide' : 'Show';
    });

    // Paste buttons for mobile convenience
    card.querySelectorAll('.btn-paste').forEach(btn => {
      btn.addEventListener('click', async () => {
        const targetId = btn.getAttribute('data-target');
        const input = document.getElementById(targetId);
        if (!input) return;

        try {
          if (navigator.clipboard && navigator.clipboard.readText) {
            const text = await navigator.clipboard.readText();
            if (text && text.trim()) {
              input.value = text.trim();
              const origText = btn.textContent;
              btn.textContent = 'Pasted!';
              btn.style.color = 'var(--success)';
              btn.style.borderColor = 'var(--success)';
              setTimeout(() => {
                btn.textContent = origText;
                btn.style.color = '';
                btn.style.borderColor = '';
              }, 1200);
              return;
            }
          }
        } catch (_) {}

        input.focus();
        input.select();
      });
    });

    // Quick tag buttons
    card.querySelectorAll('.tag-btn[data-tag]').forEach(btn => {
      btn.addEventListener('click', () => {
        const tag = btn.getAttribute('data-tag');
        let current = excludeInput.value.trim();
        const parts = current.split(',').map(s => s.trim()).filter(Boolean);
        if (!parts.includes(tag)) {
          parts.push(tag);
          excludeInput.value = parts.join(', ');
        }
      });
    });

    // Fetch Live Categories from Server
    btnFetchCats.addEventListener('click', async () => {
      const serverUrl = document.getElementById(`${cardId}_xtreamHost`).value.trim();
      const username = document.getElementById(`${cardId}_xtreamUser`).value.trim();
      const password = xtreamPass.value;

      if (!serverUrl || !username || !password) {
        showAlert('Please enter Server Address, Username, and Password first to load categories.');
        return;
      }

      btnFetchCats.disabled = true;
      btnFetchCats.querySelector('span').textContent = 'Loading categories...';

      try {
        const res = await fetch('/api/xtream/categories', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ serverUrl, username, password })
        });
        const data = await res.json();
        if (!res.ok) {
          throw new Error(data.error || 'Failed to load categories from provider');
        }

        fetchedCategories = data.categories || [];
        renderCategoryList();
        catPicker.style.display = 'block';
        btnFetchCats.querySelector('span').textContent = `Loaded ${fetchedCategories.length} Categories`;
      } catch (err) {
        showAlert(`Could not fetch categories: ${err.message}`);
        btnFetchCats.querySelector('span').textContent = 'Load Categories from Server';
      } finally {
        btnFetchCats.disabled = false;
      }
    });

    function renderCategoryList() {
      const q = (catSearch.value || '').toLowerCase().trim();
      catList.innerHTML = '';
      const filtered = fetchedCategories.filter(c => !q || c.name.toLowerCase().includes(q));

      if (filtered.length === 0) {
        catList.innerHTML = '<div style="color:var(--text-hint); font-size:13px; padding:6px;">No categories match search.</div>';
        return;
      }

      filtered.forEach(cat => {
        const item = document.createElement('label');
        item.className = 'cat-item';
        item.innerHTML = `
          <input type="checkbox" value="${cat.id}" class="cat-checkbox" ${cat.checked !== false ? 'checked' : ''}>
          <span>${cat.name}</span>
        `;
        item.querySelector('input').addEventListener('change', (e) => {
          cat.checked = e.target.checked;
        });
        catList.appendChild(item);
      });
    }

    catSearch.addEventListener('input', renderCategoryList);

    btnSelectAllCats.addEventListener('click', () => {
      fetchedCategories.forEach(c => c.checked = true);
      renderCategoryList();
    });

    btnDeselectAllCats.addEventListener('click', () => {
      fetchedCategories.forEach(c => c.checked = false);
      renderCategoryList();
    });

    // Remove Card
    btnRemove.addEventListener('click', () => {
      if (playlists.length <= 1) {
        showAlert('At least one playlist is required.');
        return;
      }
      card.remove();
      const idx = playlists.findIndex(p => p.cardId === cardId);
      if (idx !== -1) playlists.splice(idx, 1);
      updateCardNumbers();
    });

    const controller = {
      cardId,
      getData() {
        const name = document.getElementById(`${cardId}_name`).value.trim();

        if (currentKind === 'm3u') {
          const playlistUrl = document.getElementById(`${cardId}_m3uUrl`).value.trim();
          const epgUrl = document.getElementById(`${cardId}_m3uEpg`).value.trim();
          if (!playlistUrl) {
            throw new Error(`Playlist #${getCardNumber(cardId)}: Please enter an M3U URL.`);
          }
          return {
            id: existingId,
            name: name || 'M3U Playlist',
            kind: 'm3u',
            playlistUrl,
            epgUrl: epgUrl || null
          };
        } else {
          const serverUrl = document.getElementById(`${cardId}_xtreamHost`).value.trim();
          const username = document.getElementById(`${cardId}_xtreamUser`).value.trim();
          const password = xtreamPass.value;

          if (!serverUrl || !username || !password) {
            throw new Error(`Playlist #${getCardNumber(cardId)}: Fill in Server URL, Username, and Password.`);
          }

          const includeLive = document.getElementById(`${cardId}_chkLive`).checked;
          const includeVod = document.getElementById(`${cardId}_chkVod`).checked;
          const includeSeries = document.getElementById(`${cardId}_chkSeries`).checked;
          const excludeKeywords = document.getElementById(`${cardId}_excludeWords`).value.trim();
          const includeKeywords = document.getElementById(`${cardId}_includeWords`).value.trim();

          let excludeCategories = [];
          let includeCategories = [];
          if (fetchedCategories.length > 0) {
            fetchedCategories.forEach(c => {
              if (c.checked === false) excludeCategories.push(c.id);
              else includeCategories.push(c.id);
            });
          }

          return {
            id: existingId,
            name: name || 'Xtream Provider',
            kind: 'xtream',
            serverUrl,
            username,
            password,
            options: {
              includeLive,
              includeVod,
              includeSeries,
              excludeKeywords,
              includeKeywords,
              excludeCategories,
              includeCategories: (excludeCategories.length > 0 && includeCategories.length > 0) ? includeCategories : []
            }
          };
        }
      }
    };

    playlists.push(controller);
    updateCardNumbers();
    return controller;
  }

  function getCardNumber(cardId) {
    const el = document.getElementById(cardId);
    if (!el) return 1;
    const cards = Array.from(playlistsContainer.children);
    return cards.indexOf(el) + 1;
  }

  function updateCardNumbers() {
    const cards = Array.from(playlistsContainer.children);
    cards.forEach((c, idx) => {
      const title = c.querySelector('.card-title');
      if (title) title.textContent = `Playlist #${idx + 1}`;
      const removeBtn = c.querySelector('.btn-remove-card');
      if (removeBtn) {
        removeBtn.style.display = cards.length > 1 ? 'flex' : 'none';
      }
    });
  }

  // Initialize with first empty card
  createPlaylistCard('m3u');

  // Add playlist button with smooth auto-scroll on mobile
  btnAddPlaylist.addEventListener('click', () => {
    const newCard = createPlaylistCard('m3u');
    const el = document.getElementById(newCard.cardId);
    if (el) {
      setTimeout(() => {
        el.scrollIntoView({ behavior: 'smooth', block: 'center' });
      }, 50);
    }
  });

  // Read ?code= from URL query parameter
  const urlParams = new URLSearchParams(window.location.search);
  const initialCode = urlParams.get('code');
  if (initialCode) {
    const clean = initialCode.trim().toUpperCase().substring(0, 6);
    codeInput.value = clean;
    checkSession(clean);
  }

  // Auto-uppercase and detect complete 6-digit code
  codeInput.addEventListener('input', (e) => {
    const val = e.target.value.toUpperCase().replace(/[^A-Z0-9]/g, '').slice(0, 6);
    codeInput.value = val;
    hideAlert();

    if (sessionCheckTimeout) clearTimeout(sessionCheckTimeout);

    if (val.length === 6) {
      sessionCheckTimeout = setTimeout(() => {
        checkSession(val);
      }, 300);
    } else {
      hideDeviceStatus();
      lastCheckedCode = '';
    }
  });

  // Submit Handler
  setupForm.addEventListener('submit', async (e) => {
    e.preventDefault();
    hideAlert();

    const code = codeInput.value.trim().toUpperCase();
    if (!code || code.length !== 6) {
      showAlert('Please enter the 6-digit code from the TV screen.');
      codeInput.focus();
      return;
    }

    const payloadPlaylists = [];
    try {
      for (const p of playlists) {
        payloadPlaylists.push(p.getData());
      }
    } catch (err) {
      showAlert(err.message);
      return;
    }

    if (payloadPlaylists.length === 0) {
      showAlert('Please add at least one playlist.');
      return;
    }

    setLoading(true);

    const primary = payloadPlaylists[0] || {};
    const requestBody = {
      code,
      playlists: payloadPlaylists,
      name: primary.name || 'IPTV Playlist',
      playlistUrl: primary.kind === 'm3u' ? primary.playlistUrl : null,
      epgUrl: primary.epgUrl || null,
      xtreamData: primary.kind === 'xtream' ? {
        serverUrl: primary.serverUrl,
        username: primary.username,
        password: primary.password,
        options: primary.options
      } : null
    };

    try {
      const res = await fetch('/api/pair/push', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'Accept': 'application/json'
        },
        body: JSON.stringify(requestBody)
      });

      const data = await res.json();

      if (!res.ok) {
        throw new Error(data.error || 'Server error occurred during push.');
      }

      // Show success screen
      setupForm.style.display = 'none';
      btnAddPlaylist.style.display = 'none';
      successSummary.textContent = `${payloadPlaylists.length} playlist(s) updated on your TV. OpenTV is synchronizing and loading channels.`;
      successScreen.style.display = 'block';
      successScreen.scrollIntoView({ behavior: 'smooth', block: 'start' });
    } catch (err) {
      showAlert(err.message || 'Could not connect to provisioning service.');
    } finally {
      setLoading(false);
    }
  });

  // Reset button
  resetBtn.addEventListener('click', () => {
    setupForm.reset();
    codeInput.value = '';
    playlistsContainer.innerHTML = '';
    playlists.length = 0;
    playlistCounter = 0;
    createPlaylistCard('m3u');
    successScreen.style.display = 'none';
    setupForm.style.display = 'block';
    btnAddPlaylist.style.display = 'flex';
    hideDeviceStatus();
    hideAlert();
    codeInput.focus();
  });
})();
