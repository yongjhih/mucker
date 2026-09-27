// Impeccable Web Experience: Theme toggle, Copy code, Mobile Nav & Mermaid.js support
(function () {
  const themeToggle = document.getElementById('themeToggle');
  const mobileMenuBtn = document.getElementById('mobileMenuBtn');
  const siteNav = document.querySelector('.site-nav');
  const savedTheme = localStorage.getItem('mucker-theme') || 'dark';
  document.documentElement.setAttribute('data-theme', savedTheme);

  // Mobile menu toggle
  if (mobileMenuBtn && siteNav) {
    mobileMenuBtn.addEventListener('click', () => {
      const isVisible = siteNav.style.display === 'flex';
      siteNav.style.display = isVisible ? 'none' : 'flex';
      if (!isVisible) {
        siteNav.style.flexDirection = 'column';
        siteNav.style.position = 'absolute';
        siteNav.style.top = '64px';
        siteNav.style.left = '0';
        siteNav.style.right = '0';
        siteNav.style.background = 'var(--bg-surface)';
        siteNav.style.padding = '16px 24px';
        siteNav.style.borderBottom = '1px solid var(--border)';
        siteNav.style.boxShadow = 'var(--shadow-md)';
      }
    });
  }

  // Theme switcher
  if (themeToggle) {
    themeToggle.addEventListener('click', () => {
      const current = document.documentElement.getAttribute('data-theme') || 'dark';
      const next = current === 'dark' ? 'light' : 'dark';
      document.documentElement.setAttribute('data-theme', next);
      localStorage.setItem('mucker-theme', next);
      // Re-render mermaid diagrams if present
      renderMermaidDiagrams();
    });
  }

  // Impeccable Code Blocks with Header, Language Badge, and Copy
  document.querySelectorAll('.markdown-body pre').forEach((pre) => {
    if (pre.querySelector('code.language-mermaid') || pre.classList.contains('mermaid') || pre.closest('.mermaid-chart')) {
      return;
    }
    if (pre.parentElement.classList.contains('code-block-wrapper')) {
      return;
    }

    // Determine language
    const code = pre.querySelector('code');
    let lang = 'CODE';
    const classes = (code?.className || '') + ' ' + (pre.className || '') + ' ' + (pre.closest('[class*="language-"]')?.className || '');
    const langMatch = classes.match(/language-([a-zA-Z0-9_\-]+)/);
    if (langMatch && langMatch[1] && langMatch[1] !== 'plaintext') {
      lang = langMatch[1].toUpperCase();
    } else if (classes.includes('highlighter-rouge')) {
      const rougeMatch = classes.match(/language-([a-zA-Z0-9_\-]+)/);
      if (rougeMatch) lang = rougeMatch[1].toUpperCase();
    }

    // Wrapper container
    const wrapper = document.createElement('div');
    wrapper.className = 'code-block-wrapper';

    // Header bar
    const header = document.createElement('div');
    header.className = 'code-block-header';

    const langSpan = document.createElement('span');
    langSpan.className = 'code-block-lang';
    langSpan.innerText = lang;

    const copyBtn = document.createElement('button');
    copyBtn.className = 'code-copy-btn';
    copyBtn.setAttribute('aria-label', `Copy ${lang} code`);
    copyBtn.innerHTML = `
      <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="9" y="9" width="13" height="13" rx="2" ry="2"/><path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"/></svg>
      <span>Copy</span>
    `;

    copyBtn.addEventListener('click', async () => {
      const text = code?.innerText || pre.innerText;
      try {
        await navigator.clipboard.writeText(text);
        copyBtn.classList.add('copied');
        copyBtn.innerHTML = `
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="20 6 9 17 4 12"/></svg>
          <span>Copied!</span>
        `;
        setTimeout(() => {
          copyBtn.classList.remove('copied');
          copyBtn.innerHTML = `
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="9" y="9" width="13" height="13" rx="2" ry="2"/><path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"/></svg>
            <span>Copy</span>
          `;
        }, 2000);
      } catch (err) {
        console.warn('Clipboard write failed:', err);
      }
    });

    header.appendChild(langSpan);
    header.appendChild(copyBtn);

    pre.parentNode.insertBefore(wrapper, pre);
    wrapper.appendChild(header);
    wrapper.appendChild(pre);
  });

  // Dynamic Syntax Highlighting
  async function applySyntaxHighlighting() {
    const codeBlocks = document.querySelectorAll('.markdown-body pre code');
    if (codeBlocks.length === 0) return;

    const needsHighlight = Array.from(codeBlocks).some(c => !c.querySelector('span') && !c.classList.contains('language-mermaid'));
    if (!needsHighlight && window.hljs) return;

    if (!window.hljs) {
      try {
        await new Promise((resolve, reject) => {
          const script = document.createElement('script');
          script.src = 'https://cdnjs.cloudflare.com/ajax/libs/highlight.js/11.9.0/highlight.min.js';
          script.onload = resolve;
          script.onerror = reject;
          document.head.appendChild(script);
        });
      } catch (e) {
        console.warn('Highlight.js failed to load, fallback to built-in tokens:', e);
      }
    }

    if (window.hljs) {
      codeBlocks.forEach((code) => {
        if (code.classList.contains('language-mermaid') || code.closest('.mermaid') || code.closest('.mermaid-chart')) {
          return;
        }
        if (!code.classList.contains('hljs') && !code.querySelector('span.k, span.s, span.c')) {
          window.hljs.highlightElement(code);
        }
      });
    }
  }

  // Dynamic Mermaid rendering
  let mermaidModule = null;

  async function renderMermaidDiagrams() {
    let targets = Array.from(document.querySelectorAll('pre code.language-mermaid, pre.mermaid, div.mermaid, .mermaid-chart'));

    // Fallback scan: Detect any blocks where Kramdown left raw mermaid text
    document.querySelectorAll('pre, code, div, p').forEach(el => {
      if (targets.includes(el)) return;
      if (el.children.length === 0) {
        const text = (el.textContent || '').trim();
        if (text.startsWith('```mermaid') || text.startsWith('flowchart') || text.startsWith('sequenceDiagram') || text.startsWith('graph')) {
          targets.push(el);
        }
      }
    });

    if (targets.length === 0) return;

    if (!mermaidModule) {
      try {
        const { default: mermaid } = await import('https://cdn.jsdelivr.net/npm/mermaid@10/dist/mermaid.esm.min.mjs');
        mermaidModule = mermaid;
      } catch (e) {
        console.warn('Failed to load Mermaid.js:', e);
        return;
      }
    }

    const isDark = document.documentElement.getAttribute('data-theme') !== 'light';
    mermaidModule.initialize({
      startOnLoad: false,
      theme: isDark ? 'dark' : 'default',
      themeVariables: {
        darkMode: isDark,
        primaryColor: isDark ? '#6366f1' : '#4f46e5',
        primaryTextColor: isDark ? '#f8fafc' : '#0f172a',
        primaryBorderColor: '#6366f1',
        lineColor: '#38bdf8',
        secondaryColor: isDark ? '#1e293b' : '#f1f5f9',
        tertiaryColor: isDark ? '#0f172a' : '#ffffff',
        fontFamily: 'Inter, sans-serif'
      }
    });

    let chartIdx = 0;
    for (const el of targets) {
      chartIdx++;
      const isAlreadyContainer = el.classList.contains('mermaid-chart');
      let rawSource = isAlreadyContainer ? el.dataset.mermaidSource : (el.textContent || '').trim();
      rawSource = rawSource
        .replace(/^```(?:mermaid)?\s*/i, '')
        .replace(/```\s*$/, '')
        .trim();
      if (!rawSource) continue;

      const container = isAlreadyContainer ? el : document.createElement('div');
      if (!isAlreadyContainer) {
        container.className = 'mermaid-chart';
        container.dataset.mermaidSource = rawSource;
        container.style.display = 'flex';
        container.style.justifyContent = 'center';
        container.style.margin = '28px 0';
        container.style.overflowX = 'auto';
        container.style.borderRadius = 'var(--radius-md)';
        container.style.padding = '16px';
        container.style.background = 'var(--bg-surface)';
        container.style.border = '1px solid var(--border)';
        const pre = el.closest('pre') || el;
        pre.replaceWith(container);
      }

      try {
        const id = `mermaid-chart-${chartIdx}-${Date.now()}`;
        const { svg } = await mermaidModule.render(id, rawSource);
        container.innerHTML = svg;
      } catch (err) {
        console.error('Mermaid render error:', err);
      }
    }
  }

  // Initial render
  function initPage() {
    renderMermaidDiagrams();
    applySyntaxHighlighting();
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', initPage);
  } else {
    initPage();
  }
})();
