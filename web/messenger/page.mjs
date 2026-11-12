import { fetchLatestRelease, RELEASES, formatSize } from './releases.mjs';

const menuButton = document.querySelector('#menu-toggle');
const navigation = document.querySelector('#navigation');
function closeMenu() {
  menuButton.setAttribute('aria-expanded', 'false');
  menuButton.setAttribute('aria-label', 'Открыть меню');
  navigation.classList.remove('is-open');
}
menuButton.addEventListener('click', () => {
  const open = menuButton.getAttribute('aria-expanded') !== 'true';
  menuButton.setAttribute('aria-expanded', String(open));
  menuButton.setAttribute('aria-label', open ? 'Закрыть меню' : 'Открыть меню');
  navigation.classList.toggle('is-open', open);
});
navigation.addEventListener('click', event => { if (event.target.closest('a')) closeMenu(); });
document.addEventListener('keydown', event => { if (event.key === 'Escape' && menuButton.getAttribute('aria-expanded') === 'true') { closeMenu(); menuButton.focus(); } });
document.addEventListener('click', event => { if (!event.target.closest('.site-header')) closeMenu(); });

const tabs = [...document.querySelectorAll('[role="tab"]')];
function chooseTab(tab, focus = false) {
  tabs.forEach(item => {
    const selected = item === tab;
    item.setAttribute('aria-selected', String(selected));
    item.tabIndex = selected ? 0 : -1;
    document.getElementById(item.getAttribute('aria-controls')).hidden = !selected;
  });
  if (focus) tab.focus();
}
tabs.forEach((tab, index) => {
  tab.addEventListener('click', () => chooseTab(tab));
  tab.addEventListener('keydown', event => {
    let target;
    if (event.key === 'ArrowRight') target = tabs[(index + 1) % tabs.length];
    if (event.key === 'ArrowLeft') target = tabs[(index - 1 + tabs.length) % tabs.length];
    if (event.key === 'Home') target = tabs[0];
    if (event.key === 'End') target = tabs[tabs.length - 1];
    if (target) { event.preventDefault(); chooseTab(target, true); }
  });
});

const status = document.querySelector('#release-status');
const detail = document.querySelector('#release-detail');
const download = document.querySelector('#apk-download');
const refresh = document.querySelector('#refresh-release');
const version = document.querySelector('#release-version');
let checking = false;
async function checkRelease() {
  if (checking) return;
  checking = true;
  refresh.disabled = true;
  status.setAttribute('aria-busy', 'true');
  status.textContent = 'Проверяем последний релиз…';
  download.href = RELEASES;
  download.textContent = 'Открыть релизы на GitHub';
  const result = await fetchLatestRelease();
  detail.textContent = '';
  version.textContent = 'Android 8.0 и новее';
  if (result.release) {
    const release = result.release;
    version.textContent = `${release.version} · Android 8.0+`;
    const date = release.publishedAt ? new Intl.DateTimeFormat('ru-RU', { day: 'numeric', month: 'long', year: 'numeric', timeZone: 'UTC' }).format(new Date(release.publishedAt)) : '';
    detail.textContent = [date ? `Релиз от ${date}` : '', formatSize(release.size)].filter(Boolean).join(' · ');
    if (result.kind === 'ready') {
      status.textContent = 'APK готов. Можно скачивать.';
      download.textContent = 'Скачать APK для Android';
      download.href = release.apkUrl;
    } else {
      status.textContent = 'Релиз опубликован, APK ещё готовится.';
      download.href = release.releaseUrl;
      download.textContent = 'Открыть страницу релиза';
    }
  } else if (result.kind === 'limited') {
    status.textContent = 'GitHub временно ограничил проверку. Откройте релизы напрямую.';
  } else if (result.kind === 'empty') {
    status.textContent = 'Готовый релиз пока не найден. Проверьте публикации на GitHub.';
  } else {
    status.textContent = 'Не удалось проверить релиз. Его можно открыть напрямую на GitHub.';
  }
  status.removeAttribute('aria-busy');
  refresh.disabled = false;
  checking = false;
}
refresh.addEventListener('click', checkRelease);
checkRelease();
