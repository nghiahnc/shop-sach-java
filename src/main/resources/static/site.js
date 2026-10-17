const header = document.querySelector('.site-header');
const isPublic = header?.dataset.publicPage === 'true';
const restoredButtons = new Map();
let navigationTimer, slowTimer, pendingGet, csrfPromise;
function updateHeader() { header?.classList.toggle('scrolled', window.scrollY > 40); }
window.addEventListener('scroll', updateHeader, { passive: true });
updateHeader();
document.querySelectorAll('[data-open]').forEach(button => button.addEventListener('click', () => document.getElementById(button.dataset.open)?.showModal()));
document.querySelectorAll('[data-close]').forEach(button => button.addEventListener('click', () => button.closest('dialog').close()));
document.querySelectorAll('dialog').forEach(dialog => dialog.addEventListener('click', event => {
  if (event.target === dialog) { const r = dialog.getBoundingClientRect(); if (event.clientX < r.left || event.clientX > r.right || event.clientY < r.top || event.clientY > r.bottom) dialog.close(); }
}));
document.querySelector('[data-demo-login]')?.addEventListener('click', event => {
  document.getElementById('username').value = event.currentTarget.dataset.email;
  document.getElementById('password').value = event.currentTarget.dataset.password;
});
document.querySelector('[data-clear-login]')?.addEventListener('click', () => {
  document.getElementById('username').value = ''; document.getElementById('password').value = ''; document.getElementById('username').focus();
});
document.querySelector('#cover')?.addEventListener('change', event => {
  const image = document.querySelector('[data-cover-preview]');
  if (image) image.src = event.target.selectedOptions[0].dataset.coverUrl;
});
const status = document.createElement('div'); status.className = 'navigation-status'; status.hidden = true;
status.setAttribute('role', 'status'); status.setAttribute('aria-live', 'polite');
const statusText = document.createElement('span'); status.append(statusText);
const retry = document.createElement('button'); retry.type = 'button'; retry.textContent = 'Thử tải trang'; retry.hidden = true;
retry.addEventListener('click', () => { if (pendingGet) location.assign(pendingGet); }); status.append(retry); document.body.append(status);
function showNavigation(getUrl) {
  clearTimeout(navigationTimer); clearTimeout(slowTimer); pendingGet = getUrl || null;
  navigationTimer = setTimeout(() => { status.hidden = false; statusText.textContent = 'Đang tải trang…'; retry.hidden = true; }, 250);
  slowTimer = setTimeout(() => { status.hidden = false; statusText.textContent = 'Máy chủ đang phản hồi chậm. Vui lòng chờ thêm.'; retry.hidden = !pendingGet; }, 10000);
}
function resetNavigation() {
  clearTimeout(navigationTimer); clearTimeout(slowTimer); status.hidden = true; pendingGet = null;
  restoredButtons.forEach((label, button) => { button.disabled = false; if (label !== null) button.textContent = label; }); restoredButtons.clear();
  document.querySelectorAll('[data-csrf]').forEach(form => { delete form.dataset.submitting; });
}
function lockForm(form) {
  const button = form.querySelector('button[type=submit]'); if (!button) return;
  if (!restoredButtons.has(button)) restoredButtons.set(button, form.matches('[data-once]') ? (button.dataset.label || button.textContent) : null);
  button.disabled = true; if (form.matches('[data-once]')) button.textContent = 'Đang xử lý…';
}
function formError(form, message) {
  resetNavigation(); let error = form.querySelector('[data-submit-error]');
  if (!error) { error = document.createElement('p'); error.className = 'field-error'; error.setAttribute('role', 'alert'); error.dataset.submitError = ''; form.append(error); }
  error.textContent = message;
}
async function fetchJson(url) {
  const response = await fetch(url, { credentials: 'same-origin', cache: 'no-store', signal: AbortSignal.timeout(30000) });
  if (!response.ok) throw Error('Máy chủ chưa phản hồi. Vui lòng thử lại.');
  return response.json();
}
async function hydrateContext() {
  if (!isPublic) return;
  try {
    const context = await fetchJson('/storefront/context');
    document.querySelectorAll('[data-account-link]').forEach(a => { a.href = context.signedIn ? '/account/orders' : '/login'; });
    document.querySelectorAll('[data-cart-count]').forEach(n => { n.textContent = context.cartCount; n.hidden = context.cartCount === 0; });
    document.querySelectorAll('[data-admin-link]').forEach(n => { n.hidden = !context.isAdmin; });
    document.querySelectorAll('[data-register-link]').forEach(n => { n.hidden = context.signedIn; });
    document.querySelectorAll('[data-logout]').forEach(n => { n.hidden = !context.signedIn; });
  } catch { /* The public page stays usable; a click can retry the server. */ }
}
document.addEventListener('click', event => {
  if (event.defaultPrevented || event.button !== 0 || event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) return;
  const link = event.target.closest('a[href]');
  if (!link || link.target || link.hasAttribute('download')) return;
  const url = new URL(link.href, location.href);
  if (url.origin !== location.origin || (url.pathname === location.pathname && url.search === location.search && url.hash)) return;
  showNavigation(url.href);
});
document.addEventListener('submit', async event => {
  if (event.defaultPrevented) return;
  const form = event.target;
  if (form.matches('[data-csrf]')) {
    event.preventDefault(); if (form.dataset.submitting) return; form.dataset.submitting = 'true'; lockForm(form); showNavigation();
    try {
      const csrf = await (csrfPromise ||= fetchJson('/storefront/csrf').catch(error => { csrfPromise = null; throw error; }));
      let input = form.querySelector('input[name="' + csrf.parameterName + '"]');
      if (!input) { input = document.createElement('input'); input.type = 'hidden'; input.name = csrf.parameterName; form.append(input); }
      input.value = csrf.token;
      HTMLFormElement.prototype.submit.call(form);
    } catch { formError(form, 'Chưa kết nối được máy chủ. Chưa gửi thao tác; vui lòng bấm lại.'); }
  } else {
    if (form.matches('[data-once]')) lockForm(form);
    if ((form.getAttribute('method') || 'GET').toUpperCase() === 'GET') {
      const url = new URL(form.action, location.href); url.search = new URLSearchParams(new FormData(form)).toString(); showNavigation(url.href);
    } else showNavigation();
  }
});
window.addEventListener('pageshow', () => { csrfPromise = null; resetNavigation(); hydrateContext(); });
