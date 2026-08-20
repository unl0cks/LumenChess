(() => {
  const params = new URLSearchParams(location.search);
  if (params.get('view') === 'phone') document.body.classList.add('view-phone');
  if (params.get('view') === 'states') document.body.classList.add('view-states');
  if (params.get('view') === 'tokens') document.body.classList.add('view-tokens');

  document.querySelectorAll('[data-group]').forEach(button => {
    button.addEventListener('click', () => {
      const group = button.dataset.group;
      document.querySelectorAll(`[data-group="${group}"]`).forEach(peer => {
        peer.classList.toggle('is-selected', peer === button);
        peer.setAttribute('aria-checked', peer === button ? 'true' : 'false');
      });
    });
  });

  document.querySelectorAll('.selector:not(.demo-selector)').forEach(selector => {
    selector.addEventListener('click', () => {
      selector.classList.toggle('is-focused');
      selector.setAttribute('aria-expanded', selector.classList.contains('is-focused') ? 'true' : 'false');
    });
  });

  document.addEventListener('keydown', event => {
    if (event.key.toLowerCase() !== 'p') return;
    const el = document.activeElement;
    if (el && el.matches('.selector,.segment,.choice,.primary-cta')) el.classList.toggle('is-pressed');
  });
})();
