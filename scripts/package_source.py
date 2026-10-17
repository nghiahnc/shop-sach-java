"""Create a portable source archive; reject exact private credential values."""
import hashlib,json,zipfile
from pathlib import Path

root=Path(__file__).resolve().parents[1]
excluded={'.tools','.local','.vercel','.git','node_modules','target','private','__pycache__'}
secrets=[]
for file in (root/'.local').glob('env-*.json'):
    for key,value in json.loads(file.read_text(encoding='utf-8')).items():
        if ('PASSWORD' in key or 'SECRET' in key) and len(str(value))>12:secrets.append((key,str(value).encode()))
files=[]
for p in root.rglob('*'):
    if not p.is_file():continue
    rel=p.relative_to(root)
    if any(part in excluded for part in rel.parts):continue
    if p.name.startswith('.env') and p.name!='.env.example':continue
    if p.suffix in {'.zip','.jar','.pyc'} or p.name=='delivery.json':continue
    # Keep final verification and images; historical container build logs are noisy.
    if p.suffix=='.log' and p.name not in {'maven-verify.log','redesign-tests.log','redesign-migration-preservation.log','performance-tests.log'}:continue
    if p.name in {'home-1440.png','preview-heading.png'} and rel.parent==Path('artifacts'):continue
    if p.name=='qa-browser-failure.txt':continue
    data=p.read_bytes()
    for key,value in secrets:
        if value in data:raise RuntimeError(f'Private credential in {rel} ({key})')
    files.append(p)
archive=root/'artifacts/TramSach-source.zip'
with zipfile.ZipFile(archive,'w',zipfile.ZIP_DEFLATED,compresslevel=6) as z:
    for p in files:
        name=p.relative_to(root).as_posix();info=zipfile.ZipInfo.from_file(p,name)
        if name=='mvnw':info.create_system=3;info.external_attr=0o100755<<16
        info.compress_type=zipfile.ZIP_DEFLATED;z.writestr(info,p.read_bytes())
with zipfile.ZipFile(archive) as z:
    assert z.testzip() is None
    assert all(not any(part in excluded for part in Path(n).parts) for n in z.namelist())
jar=root/'target/tram-sach-1.0.0.jar'
manifest={'sourceFiles':len(files),'sourceZip':archive.name,'sourceSha256':hashlib.sha256(archive.read_bytes()).hexdigest(),'jar':str(jar.relative_to(root)),'jarSha256':hashlib.sha256(jar.read_bytes()).hexdigest(),'privateCredentialMatches':0}
(root/'artifacts/delivery.json').write_text(json.dumps(manifest,indent=2)+'\n',encoding='utf-8')
print(f'Packaged {len(files)} source/evidence files; no exact private credential values; ZIP integrity OK')
