document.querySelectorAll('[data-stateful]').forEach((el)=>{
  const down=()=>el.classList.add('is-pressed');
  const up=()=>el.classList.remove('is-pressed');
  el.addEventListener('pointerdown',down); el.addEventListener('pointerup',up); el.addEventListener('pointercancel',up); el.addEventListener('pointerleave',up);
});
