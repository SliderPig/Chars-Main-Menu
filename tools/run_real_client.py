#!/usr/bin/env python3
# Starts the real game in a throwaway folder (run-real/) with just this mod and Embeddium, never touching any real profile.
# The jar must be built with -Psmoke so the walk-through (src/smoke/.../Walk.java) is in it.
#   gradlew build -Psmoke -x test    then    python tools/run_real_client.py look|full|lab [seconds] [scene=<folder>]
import glob
import json
import os
import shutil
import subprocess
import sys

M = os.path.join(os.environ['APPDATA'], 'ModrinthApp')
META = os.path.join(M, 'meta')
VERSION = '1.20.1-47.4.28'
JAVA = r'C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot\bin\java.exe'
PROFILE_MODS = os.path.join(M, 'profiles', 'Powers', 'mods')
NEEDED = ['embeddium-']
RUN = os.path.abspath('run-real')
MODE = sys.argv[1] if len(sys.argv) > 1 else 'full'
LIMIT = int(sys.argv[2]) if len(sys.argv) > 2 and sys.argv[2].isdigit() else 420


def allowed(rules):
    ok = not rules
    for r in rules or []:
        name = (r.get('os') or {}).get('name')
        if name in (None, 'windows') and 'features' not in r:
            ok = r['action'] == 'allow'
    return ok


def library_path(lib):
    art = (lib.get('downloads') or {}).get('artifact')
    if art and art.get('path'):
        return art['path']
    group, name, ver = lib['name'].split(':')[:3]
    return f"{group.replace('.', '/')}/{name}/{ver}/{name}-{ver}.jar"


def main():
    d = json.load(open(os.path.join(META, 'versions', VERSION, VERSION + '.json'), encoding='utf-8'))
    cp, seen = [], set()
    for lib in d['libraries']:
        if lib.get('include_in_classpath') is False or not allowed(lib.get('rules')) or 'natives' in lib['name'].split(':')[-1] and 'windows' not in lib['name']:
            continue
        path = os.path.join(META, 'libraries', library_path(lib))
        if os.path.exists(path) and path not in seen:
            seen.add(path)
            cp.append(path)
    cp.append(os.path.join(META, 'versions', VERSION, VERSION + '.jar'))

    os.makedirs(os.path.join(RUN, 'mods'), exist_ok=True)
    # last run's world stays, under another name, so the world list has something in it
    last = os.path.join(RUN, 'saves', 'charmenu-check')
    if os.path.isdir(last):
        keep = os.path.join(RUN, 'saves', 'Cherry World')
        shutil.rmtree(keep, ignore_errors=True)
        shutil.move(last, keep)
    if MODE != 'lab':
        shutil.rmtree(os.path.join(RUN, 'screenshots'), ignore_errors=True)
    os.makedirs(os.path.join(RUN, 'lab'), exist_ok=True)
    go = os.path.join(RUN, 'lab', 'go')
    if os.path.exists(go):
        os.remove(go)
    # a brand new game folder opens the first-launch Narrator screen and waits for a click for ever
    options_file = os.path.join(RUN, 'options.txt')
    old = []
    if os.path.exists(options_file):
        with open(options_file, encoding='utf-8') as f:
            old = [line for line in f.read().splitlines() if not line.startswith(('onboardAccessibility:', 'pauseOnLostFocus:', 'skipMultiplayerWarning:'))]
    with open(options_file, 'w', encoding='utf-8') as f:
        f.write('\n'.join(old + ['onboardAccessibility:false', 'pauseOnLostFocus:false', 'skipMultiplayerWarning:true']) + '\n')
    for jar in glob.glob(os.path.join(RUN, 'mods', '*.jar')):
        os.remove(jar)
    for name in os.listdir(PROFILE_MODS):
        if name.endswith('.jar') and any(name.startswith(n) for n in NEEDED):
            shutil.copy(os.path.join(PROFILE_MODS, name), os.path.join(RUN, 'mods', name))
    built = [p for p in sorted(glob.glob('build/libs/charmenu-*.jar'), key=os.path.getmtime) if 'sources' not in p]
    shutil.copy(built[-1], os.path.join(RUN, 'mods', 'charmenu-smoke.jar'))
    print('mods:', sorted(os.listdir(os.path.join(RUN, 'mods'))))

    natives = os.path.join(META, 'natives', VERSION)
    values = {'natives_directory': natives, 'launcher_name': 'charmenu-test', 'launcher_version': '1', 'classpath': os.pathsep.join(cp), 'classpath_separator': os.pathsep,
              'library_directory': os.path.join(META, 'libraries'), 'version_name': VERSION, 'auth_player_name': 'SliderP', 'game_directory': RUN, 'assets_root': os.path.join(META, 'assets'),
              'assets_index_name': d['assets'], 'auth_uuid': '00000000000000000000000000000abc', 'auth_access_token': '0', 'clientid': '0', 'auth_xuid': '0', 'user_type': 'legacy', 'version_type': 'release'}

    def fill(text):
        for key, value in values.items():
            text = text.replace('${' + key + '}', value)
        return text

    def flat(args):
        out = []
        for a in args:
            if isinstance(a, str):
                out.append(fill(a))
            elif allowed(a.get('rules')):
                v = a['value']
                out.extend(fill(x) for x in ([v] if isinstance(v, str) else v))
        return out

    extra = [f'-Dcharmenu.walk={MODE}', '-Dcharmenu.steve=true']
    for a in sys.argv:
        if a.startswith('scene='):
            extra.append('-Dcharmenu.scenedir=' + os.path.abspath(a[len('scene='):]))
    cmd = [JAVA, '-Xmx2000M'] + extra + flat(d['arguments']['jvm']) + [d['mainClass']] + flat(d['arguments']['game']) + ['--width', '1600', '--height', '900']
    with open(os.path.join(RUN, 'launch.log'), 'w', encoding='utf-8', errors='replace') as log:
        try:
            result = subprocess.run(cmd, cwd=RUN, stdout=log, stderr=subprocess.STDOUT, timeout=LIMIT)
            print('exit', result.returncode)
        except subprocess.TimeoutExpired:
            print('stopped after', LIMIT, 's')
    print('screenshots:', len(glob.glob(os.path.join(RUN, 'screenshots', '*.png'))))
    return 0


if __name__ == '__main__':
    sys.exit(main())
