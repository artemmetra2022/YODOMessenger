export const REPOSITORY = 'https://github.com/artemmetra2022/YODOMessenger';
export const RELEASES = `${REPOSITORY}/releases/latest`;
export const RELEASE_API = 'https://api.github.com/repos/artemmetra2022/YODOMessenger/releases/latest';

export function trustedReleaseUrl(value, asset = false) {
  try {
    const url = new URL(value);
    const prefix = '/artemmetra2022/YODOMessenger/releases/';
    if (url.protocol !== 'https:' || url.hostname !== 'github.com' || url.port || url.username || url.password || url.search || url.hash) return null;
    if (!url.pathname.startsWith(prefix)) return null;
    if (asset && (!url.pathname.startsWith(`${prefix}download/`) || !url.pathname.toLowerCase().endsWith('.apk'))) return null;
    return url.href;
  } catch { return null; }
}

export function describeRelease(data) {
  if (!data || typeof data !== 'object' || data.draft || data.prerelease || typeof data.tag_name !== 'string' || !data.tag_name.trim()) return null;
  const assets = Array.isArray(data.assets) ? data.assets : [];
  const apk = assets.find(asset => asset && typeof asset.name === 'string' && /\.apk$/i.test(asset.name) &&
    asset.state === 'uploaded' && Number.isSafeInteger(asset.size) && asset.size > 0 && trustedReleaseUrl(asset.browser_download_url, true));
  return {
    version: String(data.tag_name).slice(0, 80),
    releaseUrl: trustedReleaseUrl(data.html_url) || RELEASES,
    publishedAt: typeof data.published_at === 'string' && Number.isFinite(Date.parse(data.published_at)) ? data.published_at : null,
    apkUrl: apk ? trustedReleaseUrl(apk.browser_download_url, true) : null,
    size: apk ? apk.size : null
  };
}

export function formatSize(bytes) {
  return Number.isFinite(bytes) && bytes > 0 ? `${new Intl.NumberFormat('ru-RU', { maximumFractionDigits: 1 }).format(bytes / 1_000_000)} МБ` : '';
}

export async function fetchLatestRelease(fetcher = fetch, timeoutMs = 8000) {
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), timeoutMs);
  try {
    const response = await fetcher(RELEASE_API, { signal: controller.signal, headers: { Accept: 'application/vnd.github+json' }, cache: 'no-store', credentials: 'omit' });
    if (response.status === 404) return { kind: 'empty' };
    if (response.status === 403 || response.status === 429) return { kind: 'limited' };
    if (!response.ok) throw new Error(`HTTP ${response.status}`);
    const release = describeRelease(await response.json());
    return release ? { kind: release.apkUrl ? 'ready' : 'pending', release } : { kind: 'empty' };
  } catch { return { kind: 'error' }; }
  finally { clearTimeout(timeout); }
}
