"""Package code and public design samples; never package signing keys or user backups."""
from pathlib import Path
import hashlib
import re
import zipfile

ROOT = Path(__file__).resolve().parents[1]
VERSION = re.search(r'versionName\s*=\s*"([^"]+)"', (ROOT / 'app/build.gradle.kts').read_text(encoding='utf-8')).group(1)
OUT = ROOT / 'deliverables'
OUT.mkdir(exist_ok=True)
apk = OUT / f'Shiling-{VERSION}-release.apk'
if not apk.is_file():
    raise SystemExit('Build and sign the release APK first.')

source = OUT / f'ShilingAndroid-source-{VERSION}.zip'
root_files = ['.gitignore', 'README.md', 'HANDOFF.md', 'build.gradle.kts', 'settings.gradle.kts', 'gradle.properties', 'gradlew', 'gradlew.bat', 'build.ps1', 'sign-release.ps1']
files = [ROOT / name for name in root_files]
files += [p for folder in ['app', 'gradle', 'tools'] for p in (ROOT / folder).rglob('*') if p.is_file() and not any(part in {'build', '.cxx', '__pycache__'} for part in p.relative_to(ROOT).parts)]
files += [ROOT / 'design-previews/share-imagegen-v2/prompts.json', ROOT / 'design-previews/share-imagegen-v2/README.md', ROOT / 'research/share-card-references/README.md']
with zipfile.ZipFile(source, 'w', zipfile.ZIP_DEFLATED) as archive:
    for p in sorted(set(files)):
        if p.is_file():
            archive.write(p, str(Path('ShilingAndroid') / p.relative_to(ROOT)))
with zipfile.ZipFile(source) as archive:
    assert archive.testzip() is None
    assert not any('/signing/' in n or 'private' in n.lower() or '/restore-points/' in n for n in archive.namelist())

designs = OUT / f'Shiling-{VERSION}-share-designs.zip'
with zipfile.ZipFile(designs, 'w', zipfile.ZIP_DEFLATED) as archive:
    for folder, target in [('design-previews/share-imagegen-v2', 'imagegen-reference'), ('design-previews/share-1.2.1', 'app-rendered')]:
        for p in sorted((ROOT / folder).glob('*')):
            if p.is_file():
                archive.write(p, f'{target}/{p.name}')
with zipfile.ZipFile(designs) as archive:
    assert archive.testzip() is None

lines = []
for p in [apk, source, designs]:
    digest = hashlib.sha256(p.read_bytes()).hexdigest().upper()
    lines.append(f'{digest}  {p.name}')
    print(f'{p.name}: {p.stat().st_size} bytes, SHA256 {digest}')
(OUT / f'Shiling-{VERSION}-SHA256.txt').write_text('\n'.join(lines) + '\n', encoding='utf-8')
