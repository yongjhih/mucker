// Theme toggle with localStorage persistence
(function () {
  const themeToggle = document.getElementById('themeToggle');
  const savedTheme = localStorage.getItem('mucker-theme') || 'dark';
  document.documentElement.setAttribute('data-theme', savedTheme);

  if (themeToggle) {
    themeToggle.addEventListener('click', () => {
      const current = document.documentElement.getAttribute('data-theme') || 'dark';
      const next = current === 'dark' ? 'light' : 'dark';
      document.documentElement.setAttribute('data-theme', next);
      localStorage.setItem('mucker-theme', next);
    });
  }

  // Copy code blocks
  document.querySelectorAll('.markdown-body pre').forEach((pre) => {
    const container = document.createElement('div');
    container.style.position = 'relative';
    pre.parentNode.insertBefore(container, pre);
    container.appendChild(pre);

    const btn = document.createElement('button');
    btn.innerText = 'Copy';
    btn.className = 'btn btn-secondary';
    btn.style.position = 'absolute';
    btn.style.top = '8px';
    btn.style.right = '8px';
    btn.style.padding = '3px 8px';
    btn.style.fontSize = '0.72rem';
    btn.style.opacity = '0.7';

    btn.addEventListener('click', () => {
      const code = pre.querySelector('code')?.innerText || pre.innerText;
      navigator.clipboard.writeText(code).then(() => {
        btn.innerText = 'Copied!';
        setTimeout(() => (btn.innerText = 'Copy'), 2000);
      });
    });

    container.appendChild(btn);
  });
})();
