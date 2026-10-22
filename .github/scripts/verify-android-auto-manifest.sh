#!/usr/bin/env bash
# Derlenen APK'nin AndroidManifest.xml dosyasinda Android Auto icin gereken
# bildirimlerin bulundugunu denetler. Uygulama kodunu degistirmez.
#
# Kullanim: verify-android-auto-manifest.sh <apk>
# Test icin: MANIFEST_DUMP_FILE=<aapt2 xmltree ciktisi> ile APK yerine metin dosyasi verilebilir.
set -u

APK="${1:-}"
EXPECTED_PACKAGE="${EXPECTED_PACKAGE:-com.yasincarplay.auto}"
DUMP="$(mktemp)"
trap 'rm -f "$DUMP"' EXIT

if [ -n "${MANIFEST_DUMP_FILE:-}" ]; then
  cp "$MANIFEST_DUMP_FILE" "$DUMP"
else
  [ -f "$APK" ] || { echo "::error title=Manifest denetimi::APK bulunamadi: $APK"; exit 2; }
  SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-/usr/local/lib/android/sdk}}"
  AAPT2="$(find "$SDK/build-tools" -name aapt2 -type f 2>/dev/null | sort -V | tail -n 1)"
  [ -n "$AAPT2" ] || { echo "::error title=Manifest denetimi::aapt2 bulunamadi ($SDK/build-tools)"; exit 2; }
  "$AAPT2" dump xmltree --file AndroidManifest.xml "$APK" > "$DUMP" 2>&1 \
    || { echo "::error title=Manifest denetimi::aapt2 manifesti okuyamadi"; cat "$DUMP"; exit 2; }
fi

REPORT=""
FAILED=0

check() { # aciklama  aranan-deger
  if grep -qF "\"$2\"" "$DUMP"; then
    REPORT+="OK      $1: $2"$'\n'
  else
    REPORT+="EKSIK   $1: $2"$'\n'
    FAILED=1
  fi
}

check "Paket adi"                         "$EXPECTED_PACKAGE"
check "Android Auto uygulama bildirimi"   "com.google.android.gms.car.application"
check "Android Auto projeksiyon servisi"  "me.aap.fermata.auto.CarService"
check "Projeksiyon kategorisi"            "com.google.android.gms.car.category.CATEGORY_PROJECTION"
check "Medya tarama servisi"              "me.aap.fermata.media.service.FermataMediaService"
check "MediaBrowserService eylemi"        "android.media.browse.MediaBrowserService"
check "Car App Library servisi"           "me.aap.fermata.auto.MirrorServiceFS"
check "CarAppService eylemi"              "androidx.car.app.CarAppService"
check "Car App navigasyon kategorisi"     "androidx.car.app.category.NAVIGATION"
check "Car App en dusuk API seviyesi"     "androidx.car.app.minCarApiLevel"

echo "=== Android Auto manifest denetimi ($EXPECTED_PACKAGE) ==="
printf '%s' "$REPORT"

if [ -n "${GITHUB_STEP_SUMMARY:-}" ]; then
  {
    echo "### Android Auto manifest denetimi"
    echo '```'
    printf '%s' "$REPORT"
    echo '```'
  } >> "$GITHUB_STEP_SUMMARY"
fi

# Ayni sonucu check-run notu olarak da yaz (API ile okunabilsin)
ENC="${REPORT//'%'/'%25'}"; ENC="${ENC//$'\n'/'%0A'}"; ENC="${ENC//$'\r'/'%0D'}"
if [ "$FAILED" -eq 0 ]; then
  echo "::notice title=Android Auto manifest denetimi::${ENC}"
  exit 0
fi
echo "::error title=Android Auto manifest denetimi::${ENC}"
exit 1
