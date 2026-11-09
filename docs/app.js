function copyText(t) {
      navigator.clipboard.writeText(t).then(() => {
        document.querySelectorAll('.copy-btn').forEach(b => {
          if (b.innerText === 'Copy') { b.innerText = 'Copied'; setTimeout(() => b.innerText = 'Copy', 1500); }
        });
      });
    }

    const pageTitles = {
      'index': 'Overview - FindTheBlock Docs',
      'getting-started': 'Quick Start - FindTheBlock Docs',
      'world-factory': 'World Gen & Nether - FindTheBlock Docs',
      'lifecycle': 'Match Flow - FindTheBlock Docs',
      'reconnect': 'Crash Reconnect - FindTheBlock Docs',
      'buffs': '20-Min Buffs - FindTheBlock Docs',
      'void-lobby': 'Void Lobby - FindTheBlock Docs',
      'custom-lobby': 'Change Lobby World - FindTheBlock Docs',
      'difficulty': 'Difficulty & Recipes - FindTheBlock Docs',
      'config': 'Full Config - FindTheBlock Docs',
      'commands': 'Commands & Permissions - FindTheBlock Docs',
      'placeholders': 'PlaceholderAPI - FindTheBlock Docs',
      'faq': 'FAQ - FindTheBlock Docs'
    };

    function showPage(pageId) {
      if (!pageId || pageId === '') pageId = 'index';
      const target = document.getElementById('page-' + pageId);
      if (!target) return;

      document.querySelectorAll('.page-view').forEach(el => el.classList.remove('active'));
      target.classList.add('active');

      document.querySelectorAll('.nav-item').forEach(el => {
        if (el.getAttribute('data-page') === pageId) {
          el.classList.add('active');
        } else {
          el.classList.remove('active');
        }
      });

      if (pageTitles[pageId]) {
        document.title = pageTitles[pageId];
      }

      window.scrollTo({ top: 0, behavior: 'instant' });
    }

    // Intercept clicks on any page link
    document.addEventListener('click', (e) => {
      const link = e.target.closest('a');
      if (!link) return;
      const targetPage = link.getAttribute('data-page');
      const href = link.getAttribute('href');

      if (link.target === '_blank' || (href && (href.startsWith('http://') || href.startsWith('https://')))) {
        return;
      }

      let pageId = targetPage;
      if (!pageId && href) {
        if (href.endsWith('.html')) pageId = href.replace('.html', '');
        else if (href.startsWith('#')) pageId = href.replace('#', '');
      }

      if (pageId && document.getElementById('page-' + pageId)) {
        e.preventDefault();
        history.pushState({ pageId }, '', '#' + pageId);
        showPage(pageId);
      }
    });

    window.addEventListener('popstate', (e) => {
      const pageId = (e.state && e.state.pageId) || location.hash.replace('#', '') || 'index';
      showPage(pageId);
    });

    // Search system
    const searchIndex = [
      { title: 'Overview', page: 'index', section: 'Getting Started' },
      { title: 'Quick Start', page: 'getting-started', section: 'Getting Started' },
      { title: 'World Gen & Nether', page: 'world-factory', section: 'Core Mechanics' },
      { title: 'Match Flow', page: 'lifecycle', section: 'Core Mechanics' },
      { title: 'Crash Reconnect', page: 'reconnect', section: 'Core Mechanics' },
      { title: '20-Min Buffs', page: 'buffs', section: 'Core Mechanics' },
      { title: 'Void Lobby', page: 'void-lobby', section: 'Setup & Config' },
      { title: 'Change Lobby World', page: 'custom-lobby', section: 'Setup & Config' },
      { title: 'Difficulty & Recipes', page: 'difficulty', section: 'Setup & Config' },
      { title: 'Full Config', page: 'config', section: 'Setup & Config' },
      { title: 'Commands & Permissions', page: 'commands', section: 'Reference' },
      { title: 'PlaceholderAPI', page: 'placeholders', section: 'Reference' },
      { title: 'FAQ', page: 'faq', section: 'Reference' }
    ];

    const searchModal = document.getElementById('searchModal');
    const openSearchBtn = document.getElementById('openSearch');
    const searchInput = document.getElementById('searchInput');
    const searchResults = document.getElementById('searchResults');

    function renderResults(query) {
      searchResults.innerHTML = '';
      const q = query.toLowerCase().trim();
      const filtered = q ? searchIndex.filter(i => i.title.toLowerCase().includes(q) || i.section.toLowerCase().includes(q)) : searchIndex;
      filtered.forEach(item => {
        const a = document.createElement('a');
        a.className = 'search-result-item';
        a.href = '#' + item.page;
        a.setAttribute('data-page', item.page);
        a.innerHTML = `<div class="search-result-title">${item.title}</div><div class="search-result-snippet">${item.section}</div>`;
        a.onclick = (e) => {
          e.preventDefault();
          searchModal.classList.remove('open');
          history.pushState({ pageId: item.page }, '', '#' + item.page);
          showPage(item.page);
        };
        searchResults.appendChild(a);
      });
    }

    openSearchBtn.onclick = () => { searchModal.classList.add('open'); searchInput.value = ''; renderResults(''); searchInput.focus(); };
    searchModal.onclick = (e) => { if (e.target === searchModal) searchModal.classList.remove('open'); };
    window.onkeydown = (e) => {
      if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 'k') { e.preventDefault(); searchModal.classList.toggle('open'); searchInput.focus(); renderResults(''); }
      if (e.key === 'Escape') searchModal.classList.remove('open');
    };
    searchInput.oninput = (e) => renderResults(e.target.value);

    // Initial load
    const initialHash = location.hash.replace('#', '');
    if (initialHash && document.getElementById('page-' + initialHash)) {
      showPage(initialHash);
    } else {
      showPage('index');
    }