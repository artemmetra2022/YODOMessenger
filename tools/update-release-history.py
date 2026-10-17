#!/usr/bin/env python3
"""Refresh a short, offline release catalogue from public GitHub releases. No credentials."""
import json
import re
import urllib.request
from datetime import datetime
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DEST = ROOT / 'app/src/main/assets/release_history.json'

def summarize(body):
    # Release descriptions begin with a human-written overview, before the detailed list.
    blocks = re.split(r'\n\s*\n', body.replace('\r', '').strip())
    for block in blocks:
        if block.lstrip().startswith(('#', '```', '•', '- ', '* ', '>')):
            continue
        text = re.sub(r'\[([^\]]+)\]\([^)]*\)', r'\1', block)
        text = re.sub(r'[*_`]', '', text)
        text = re.sub(r'\s+', ' ', text).strip()
        if text:
            return text if len(text) <= 240 else text[:237].rsplit(' ', 1)[0] + '…'
    return 'Обновления и исправления приложения.'

def catalogue(releases):
    result = []
    for release in releases:
        tag = release.get('tag_name', '')
        if release.get('draft') or release.get('prerelease') or not re.fullmatch(r'v?\d+\.\d+\.\d+', tag):
            continue
        stamp = release.get('published_at')
        try:
            date = datetime.fromisoformat(stamp.replace('Z', '+00:00')).date().isoformat()
        except (TypeError, AttributeError, ValueError):
            date = None
        result.append(dict(version=tag.removeprefix('v'), date=date, summary=summarize(release.get('body') or '')))
    result.sort(key=lambda x: tuple(map(int, x['version'].split('.'))), reverse=True)
    return list({x['version']: x for x in reversed(result)}.values())[::-1][:8]

def main():
    request=urllib.request.Request('https://api.github.com/repos/artemmetra2022/YODOMessenger/releases?per_page=30',
                                   headers={'User-Agent':'YodoMessenger-Build','Accept':'application/vnd.github+json'})
    with urllib.request.urlopen(request, timeout=20) as response:
        rows=catalogue(json.load(response))
    if not rows:
        raise ValueError('No published stable versions; keep the bundled catalogue')
    DEST.parent.mkdir(parents=True, exist_ok=True)
    DEST.write_text(json.dumps(rows, ensure_ascii=False, indent=2)+'\n', encoding='utf-8')
    print(f'Offline history refreshed: {len(rows)} versions')

if __name__ == '__main__':
    main()
