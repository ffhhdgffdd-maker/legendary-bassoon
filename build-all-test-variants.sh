#!/usr/bin/env bash
set -euo pipefail

: "${WOLFOX_KEYSTORE_PASS:?Set WOLFOX_KEYSTORE_PASS in the environment}"
: "${WOLFOX_KEY_PASS:=$WOLFOX_KEYSTORE_PASS}"
: "${WOLFOX_KEY_ALIAS:=wolfox-module}"
export WOLFOX_KEYSTORE_PASS WOLFOX_KEY_PASS WOLFOX_KEY_ALIAS

project_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
result_dir="$project_dir/dist"
profile_file="$project_dir/app/src/main/java/com/wolfox/gps/ModuleConfig.java"
backup_file="$(mktemp)"
cp "$profile_file" "$backup_file"
trap 'cp "$backup_file" "$profile_file"; rm -f "$backup_file"' EXIT

mkdir -p "$result_dir" "$project_dir/keys"
export WOLFOX_KEYSTORE_PATH="${WOLFOX_KEYSTORE_PATH:-$project_dir/keys/wolfox-test.jks}"

for profile in SAFE CAMERA MULTI; do
  # shellcheck disable=SC1090
  source "$project_dir/profiles/$profile.properties"
  python3 - "$profile_file" "$BUILD_PROFILE" "$ENABLE_LOCATION" \
    "$ENABLE_CAMERA_LAB" "$ENABLE_MULTI_LOCATION" <<'PY'
from pathlib import Path
import re, sys
p = Path(sys.argv[1])
s = p.read_text()
replacements = {
    'BUILD_PROFILE': '"%s"' % sys.argv[2],
    'ENABLE_LOCATION': sys.argv[3],
    'ENABLE_CAMERA_LAB': sys.argv[4],
    'ENABLE_MULTI_LOCATION': sys.argv[5],
}
for key, value in replacements.items():
    s = re.sub(r'(public static final (?:String|boolean) '+key+r' = )[^;]+;',
               r'\g<1>'+value+';', s)
p.write_text(s)
PY
  "$project_dir/build-local.sh"
  cp "$project_dir/build/local/apk/WolFox_GPS_Module_v3.1.0_SIGNED.apk" \
    "$result_dir/WolFox-Test-$profile-v3.2.0-SIGNED.apk"
done

sha256sum "$result_dir"/*.apk > "$result_dir/SHA256SUMS.txt"
echo "Built SAFE, CAMERA and MULTI in $result_dir"
