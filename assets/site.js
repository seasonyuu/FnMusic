(() => {
  const root = document.body.dataset.root || './';
  const search = document.querySelector('[data-search]');
  const openButtons = document.querySelectorAll('[data-search-open]');
  if (!search || !openButtons.length) return;

  const input = search.querySelector('[data-search-input]');
  const results = search.querySelector('[data-search-results]');
  const count = search.querySelector('[data-search-count]');
  const close = search.querySelector('[data-search-close]');
  const background = document.querySelectorAll('.skip-link, header, main, footer');
  let pagesPromise;
  let opener;

  function getPages() {
    pagesPromise ||= fetch(`${root}search-index.json`)
      .then(response => {
        if (!response.ok) throw new Error('搜索索引无法加载');
        return response.json();
      });
    return pagesPromise;
  }

  function render(items, query) {
    results.replaceChildren();
    count.textContent = query ? `找到 ${items.length} 个相关页面` : '输入关键词搜索使用指南';
    for (const item of items) {
      const link = document.createElement('a');
      link.className = 'search-result';
      link.href = `${root}${item.url}`;
      const title = document.createElement('strong');
      title.textContent = item.title;
      const description = document.createElement('span');
      description.textContent = item.description;
      link.append(title, description);
      results.append(link);
    }
  }

  async function update() {
    const query = input.value.trim().toLocaleLowerCase('zh-CN');
    if (!query) return render([], '');
    try {
      const pages = await getPages();
      render(pages.filter(page =>
        `${page.title} ${page.description} ${page.keywords}`
          .toLocaleLowerCase('zh-CN').includes(query)
      ), query);
    } catch {
      count.textContent = '搜索暂时不可用，请使用指南导航。';
      results.replaceChildren();
    }
  }

  function hide() {
    search.hidden = true;
    document.body.classList.remove('search-visible');
    background.forEach(element => { element.inert = false; });
    openButtons.forEach(button => button.setAttribute('aria-expanded', 'false'));
    opener?.focus();
  }

  openButtons.forEach(button => button.addEventListener('click', () => {
    opener = button;
    search.hidden = false;
    document.body.classList.add('search-visible');
    openButtons.forEach(item => item.setAttribute('aria-expanded', 'true'));
    background.forEach(element => { element.inert = true; });
    input.focus();
    update();
  }));
  close.addEventListener('click', hide);
  input.addEventListener('input', update);
  search.addEventListener('click', event => {
    if (event.target === search) hide();
  });
  document.addEventListener('keydown', event => {
    if (event.key === 'Escape' && !search.hidden) hide();
    if ((event.metaKey || event.ctrlKey) && event.key.toLowerCase() === 'k') {
      event.preventDefault();
      openButtons[0].click();
    }
  });
})();
