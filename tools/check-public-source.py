#!/usr/bin/env python3
"""Check tracked source before publishing; never print suspected secret values."""
from pathlib import Path
import re
import subprocess

root = Path(__file__).resolve().parents[1]
rows = subprocess.check_output(['git', 'ls-files', '-s', '-z'], cwd=root).split(b'\0')
problems = []
count = 0
for row in rows:
    if not row:
        continue
    meta, raw_path = row.split(b'\t', 1)
    if meta.startswith(b'160000'):
        continue
    name = raw_path.decode()
    path = root / name
    count += 1
    if path.suffix.lower() in {'.apk', '.aab', '.p12', '.pfx', '.jks', '.keystore'}:
        problems.append((name, 'private or generated distribution'))
    if name.startswith(('private/', 'secrets/')) or name.endswith('local.properties'):
        problems.append((name, 'local configuration'))
    data = path.read_bytes()
    if re.search(rb'\bsk-[A-Za-z0-9]{24,}', data):
        problems.append((name, 'possible API credential'))
    if re.search(rb'-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----', data):
        problems.append((name, 'private key'))
    try:
        text = data.decode('utf8')
    except UnicodeDecodeError:
        continue
    if re.search(r'/Users/[A-Za-z0-9_-]+/', text):
        problems.append((name, 'personal machine path'))
    if name.endswith('features/study/StudyModels.kt'):
        for field in ['apiKey', 'smtpUser', 'smtpPassword', 'recipient']:
            if not re.search(r'val '+field+r': String = ""', text):
                problems.append((name, 'account default must be blank: '+field))
    if 'provisionBundledKey' in text or 'HOSHI_STUDY_API_KEY_FILE' in text:
        # This checker itself contains the identifiers being forbidden elsewhere.
        if name != 'tools/check-public-source.py':
            problems.append((name, 'private provisioning entry point'))
if problems:
    for name, reason in problems:
        print(f'{name}: {reason}')
    raise SystemExit(1)
print(f'Public source checks passed ({count} tracked files).')
