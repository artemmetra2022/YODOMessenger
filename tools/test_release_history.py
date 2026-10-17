import importlib.util
import unittest
from pathlib import Path
spec=importlib.util.spec_from_file_location('history',Path(__file__).with_name('update-release-history.py'))
m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m)

class HistoryTest(unittest.TestCase):
    def test_overview_not_heading(self):
        self.assertEqual('Короткое описание.',m.summarize('## Версия\n\nКороткое описание.\n\nЧто изменилось:\n• детали'))
    def test_markdown_removed(self):
        self.assertEqual('Новое приложение',m.summarize('**Новое** [приложение](https://example.com)'))
    def test_limit(self):
        self.assertLessEqual(len(m.summarize('word '*100)),240)
    def test_no_invented_date(self):
        self.assertIsNone(m.catalogue([dict(tag_name='v1.0.0',published_at='wrong')])[0]['date'])
    def test_stable_only(self):
        rows=m.catalogue([dict(tag_name='v1.0.0'),dict(tag_name='v2.0.0',draft=True),dict(tag_name='v3.0.0',prerelease=True),dict(tag_name='nightly')])
        self.assertEqual(['1.0.0'],[r['version'] for r in rows])
    def test_order_limit_and_date(self):
        rows=m.catalogue([dict(tag_name=f'v1.{i}.0',published_at='2026-10-17T10:11:33Z') for i in range(12)])
        self.assertEqual(8,len(rows));self.assertEqual('1.11.0',rows[0]['version']);self.assertEqual('2026-10-17',rows[0]['date'])
    def test_deduplicate(self):
        self.assertEqual(1,len(m.catalogue([dict(tag_name='v1.0.0'),dict(tag_name='1.0.0')])))

if __name__=='__main__':unittest.main()
