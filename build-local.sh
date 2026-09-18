#!/usr/bin/env bash
set -euo pipefail

project_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
sdk_root="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-$project_dir/../toolchain/android-sdk}}"
platform_jar="${ANDROID_PLATFORM_JAR:-$sdk_root/platforms/android-34/android.jar}"
build_tools="${ANDROID_BUILD_TOOLS:-$sdk_root/build-tools/34.0.0}"
output_dir="$project_dir/build/local"
version_name="3.1.0"
version_code="312"
ecj_jar="${ECJ_JAR:-$project_dir/../toolchain/downloads/ecj-3.38.0.jar}"

if [[ ! -f "$platform_jar" || ! -x "$build_tools/aapt2" ]]; then
  echo "Android platform jar and build tools are required. Set ANDROID_PLATFORM_JAR and ANDROID_BUILD_TOOLS when using a non-standard SDK layout." >&2
  exit 2
fi

rm -rf "$output_dir"
mkdir -p "$output_dir/stub-classes" "$output_dir/classes" "$output_dir/dex" "$output_dir/apk"

if command -v javac >/dev/null 2>&1; then
  mapfile -d '' stub_sources < <(find "$project_dir/stubs" -name '*.java' -print0 | sort -z)
  javac -encoding UTF-8 -source 8 -target 8 -classpath "$platform_jar" \
    -d "$output_dir/stub-classes" "${stub_sources[@]}"
else
  if [[ ! -f "$ecj_jar" ]]; then
    echo "javac is unavailable and ECJ was not found at: $ecj_jar" >&2
    exit 3
  fi
  mapfile -d '' stub_sources < <(find "$project_dir/stubs" -name '*.java' -print0 | sort -z)
  java -jar "$ecj_jar" -proc:none -encoding UTF-8 -source 1.8 -target 1.8 \
    -classpath "$platform_jar" -d "$output_dir/stub-classes" "${stub_sources[@]}"
fi

(cd "$output_dir/stub-classes" && zip -q -r "$output_dir/xposed-stubs.jar" .)

mapfile -d '' main_sources < <(find "$project_dir/app/src/main/java" -name '*.java' -print0 | sort -z)
if command -v javac >/dev/null 2>&1; then
  javac -encoding UTF-8 -source 8 -target 8 \
    -classpath "$platform_jar:$output_dir/xposed-stubs.jar" \
    -d "$output_dir/classes" "${main_sources[@]}"
else
  java -jar "$ecj_jar" -proc:none -encoding UTF-8 -source 1.8 -target 1.8 \
    -classpath "$platform_jar:$output_dir/xposed-stubs.jar" \
    -d "$output_dir/classes" "${main_sources[@]}"
fi

mapfile -d '' class_files < <(find "$output_dir/classes" -name '*.class' -print0 | sort -z)
"$build_tools/d8" --release --min-api 21 \
  --lib "$platform_jar" --lib "$output_dir/xposed-stubs.jar" \
  --output "$output_dir/dex" "${class_files[@]}"

"$build_tools/aapt2" link \
  -I "$platform_jar" \
  --manifest "$project_dir/app/src/main/AndroidManifest.xml" \
  --min-sdk-version 21 --target-sdk-version 34 \
  --version-code "$version_code" --version-name "$version_name" \
  -o "$output_dir/apk/base.apk"

cp "$output_dir/apk/base.apk" "$output_dir/apk/WolFox_GPS_Module_v3.1.0_unsigned.apk"
cp "$output_dir/dex/classes.dex" "$output_dir/apk/classes.dex"
(cd "$project_dir/app/src/main" && zip -q -r "$output_dir/apk/WolFox_GPS_Module_v3.1.0_unsigned.apk" assets)
(cd "$output_dir/apk" && zip -q -u WolFox_GPS_Module_v3.1.0_unsigned.apk classes.dex)

"$build_tools/zipalign" -f 4 \
  "$output_dir/apk/WolFox_GPS_Module_v3.1.0_unsigned.apk" \
  "$output_dir/apk/WolFox_GPS_Module_v3.1.0_aligned.apk"

key_store="${WOLFOX_KEYSTORE_PATH:-$output_dir/wolfox-module-build.jks}"
key_alias="${WOLFOX_KEY_ALIAS:-wolfox-module}"
key_store_pass="${WOLFOX_KEYSTORE_PASS:?Set WOLFOX_KEYSTORE_PASS in the environment}"
key_pass="${WOLFOX_KEY_PASS:-$key_store_pass}"
if [[ ! -f "$key_store" ]]; then
  keytool -genkeypair -noprompt -keystore "$key_store" \
    -storepass "$key_store_pass" -keypass "$key_pass" -alias "$key_alias" \
    -keyalg RSA -keysize 3072 -validity 3650 \
    -dname "CN=WolFox Module,OU=Build,O=WolFox,L=Riyadh,C=SA" >/dev/null 2>&1
fi

"$build_tools/apksigner" sign \
  --ks "$key_store" --ks-key-alias "$key_alias" \
  --ks-pass "pass:$key_store_pass" --key-pass "pass:$key_pass" \
  --v1-signing-enabled true --v2-signing-enabled true --v3-signing-enabled true \
  --out "$output_dir/apk/WolFox_GPS_Module_v3.1.0_SIGNED.apk" \
  "$output_dir/apk/WolFox_GPS_Module_v3.1.0_aligned.apk"

"$build_tools/apksigner" verify --verbose --print-certs \
  "$output_dir/apk/WolFox_GPS_Module_v3.1.0_SIGNED.apk"
sha256sum "$output_dir/apk/WolFox_GPS_Module_v3.1.0_SIGNED.apk"
