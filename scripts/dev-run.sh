#!/usr/bin/env bash
# =====================================================================
#  dev-run.sh — Mersel DSS Signer'ı IDE'siz çalıştırmak için
#               OS auto-detect eden geliştirici scripti.
# =====================================================================
#  Kullanım:
#    ./scripts/dev-run.sh                  → default (KURUM17 EC-P384 PFX)
#    ./scripts/dev-run.sh kurum02-ec384    → KURUM02 EC-P384 PFX
#    ./scripts/dev-run.sh mali-muhur-akis  → Mali Mühür AKİS (OS auto)
#    ./scripts/dev-run.sh list-hsm         → HSM sertifikalarını --force-login ile listele
#    ./scripts/dev-run.sh list             → mevcut profilleri listele
#
#  Senaryo isimleri = application-<senaryo>.properties (pfx- veya mali-muhur-
#  prefix'i olmadan; script prefix'leri kendi seçer). 'list' altında detay görürsün.
#
#  Gereklilikler:
#    • Java 8+ (JDK). 'java -version' başarılı olmalı.
#    • Maven 3.6+ (./mvnw varsa onu kullanır, yoksa sistem 'mvn').
#    • Mali Mühür senaryolarında: CERTIFICATE_PIN env'i veya -p <PIN> argumanı.
# =====================================================================

set -euo pipefail

# ───────────────────────────────────────── helpers
die() { printf '\033[31m✗\033[0m %s\n' "$*" >&2; exit 1; }
info() { printf '\033[36m›\033[0m %s\n' "$*"; }
ok() { printf '\033[32m✓\033[0m %s\n' "$*"; }

# ───────────────────────────────────────── OS detection
detect_os() {
  case "${OSTYPE:-$(uname -s | tr '[:upper:]' '[:lower:]')}" in
    darwin*) echo "mac" ;;
    linux*)  echo "linux" ;;
    cygwin*|mingw*|msys*) echo "windows" ;;
    *)
      # uname -s fallback (zsh/bash farklılığı için)
      case "$(uname -s 2>/dev/null || true)" in
        Darwin) echo "mac" ;;
        Linux) echo "linux" ;;
        MINGW*|MSYS*|CYGWIN*) echo "windows" ;;
        *) echo "unknown" ;;
      esac
      ;;
  esac
}

# ───────────────────────────────────────── senaryo → profile mapping
# PFX senaryolar tüm OS'larda aynı dosyayı kullanır; Mali Mühür senaryolar OS-spesifik.
# NOT: `list` modu burada İŞLENMEZ — main()'in başında erken-dönüş yapılıyor;
#      aksi halde command substitution içindeki `exit` yalnızca subshell'i
#      kapatır, ana akış devam eder ve "list" string'i profile sanılır.
resolve_profile() {
  local scenario="$1"
  local os="$2"
  case "$scenario" in
    ""|default|kurum17-ec384)
      echo "local,pfx-kurum17-ec384" ;;
    kurum02-ec384)
      echo "local,pfx-kurum02-ec384" ;;
    kurum03-ec384)
      echo "local,pfx-kurum03-ec384" ;;
    mali-muhur-akis|mali-muhur)
      echo "local,mali-muhur-akis-${os}" ;;
    *)
      die "Bilinmeyen senaryo: '$scenario'. './scripts/dev-run.sh list' ile listele."
      ;;
  esac
}

# AKİS PKCS#11 sürücüsünün OS'a göre default kütüphane yolu.
# (application-mali-muhur-akis-<os>.properties dosyalarındaki default'larla aynı.)
# Operatör farklı yere kurduysa PKCS11_LIBRARY env'i ile override eder.
akis_default_library() {
  case "$1" in
    mac)     echo "/usr/local/lib/libakisp11.dylib" ;;
    linux)   echo "/usr/local/lib/libakisp11.so" ;;
    windows) echo "C:/Windows/System32/akisp11.dll" ;;
    *)       echo "" ;;
  esac
}

# AKİS macOS sürücüsü CKF_OS_LOCKING_OK'lı C_Initialize'ı reddettiği için
# NULL init-args default'u sadece mac'te açık; linux/windows standart init kabul eder.
akis_default_null_init_args() {
  case "$1" in
    mac) echo "true" ;;
    *)   echo "false" ;;
  esac
}

# `list` senaryosu için yardım çıktısı.
print_scenarios() {
  cat <<'EOF'
Mevcut senaryolar:
  default | kurum17-ec384     → testkurum17 EC-P384 (PIN=328829) [default]
  kurum02-ec384                → testkurum02 EC-P384       (PIN=825095)
  kurum03-ec384                → testkurum03 EC-P384       (PIN=540425)
  mali-muhur-akis | mali-muhur → Mali Mühür AKİS server'ı başlat (OS auto: mac / linux / windows)
  list-hsm | list-certs-hsm    → HSM (Mali Mühür AKİS) sertifikalarını --force-login ile LİSTELE
                                 (Spring kalkmaz; token'a C_Login yapıp sertifikaları stdout'a basar)

Kullanım:
  ./scripts/dev-run.sh                          # default
  ./scripts/dev-run.sh kurum02-ec384            # PFX EC-384
  CERTIFICATE_PIN=1234 ./scripts/dev-run.sh mali-muhur-akis
  CERTIFICATE_PIN=1234 ./scripts/dev-run.sh list-hsm    # HSM sertifikalarını listele (force-login)
EOF
}

# HSM sertifikalarını --list-certificates --force-login ile listeler.
# Spring context KALKMAZ; SignatureApplication.listCertificates() Spring
# profile / local-secrets.properties okumaz, sadece env var'ları okur — bu
# yüzden PKCS11_LIBRARY / PKCS11_NULL_INIT_ARGS / CERTIFICATE_PIN burada
# explicit set edilir. --force-login: AKİS gibi public objeleri login'siz
# göstermeyen HSM'ler için token sorgusundan önce C_Login yapılır.
list_hsm_certificates() {
  local os="$1"
  local mvn_cmd="$2"

  if [[ -z "${CERTIFICATE_PIN:-}" ]]; then
    die "HSM listeleme için CERTIFICATE_PIN env'i gerekli. Örnek:
       CERTIFICATE_PIN=1234 ./scripts/dev-run.sh list-hsm"
  fi

  # PKCS11_LIBRARY / PKCS11_NULL_INIT_ARGS zaten dışarıdan verildiyse ona saygı
  # göster; verilmediyse OS default'unu kullan.
  local pkcs11_library="${PKCS11_LIBRARY:-$(akis_default_library "$os")}"
  local null_init_args="${PKCS11_NULL_INIT_ARGS:-$(akis_default_null_init_args "$os")}"
  local slot="${PKCS11_SLOT:--1}"
  local slot_index="${PKCS11_SLOT_LIST_INDEX:--1}"

  if [[ -z "$pkcs11_library" ]]; then
    die "AKİS sürücü yolu tespit edilemedi (OS=$os). PKCS11_LIBRARY env'i ile ver:
       PKCS11_LIBRARY=/usr/local/lib/libakisp11.so CERTIFICATE_PIN=1234 ./scripts/dev-run.sh list-hsm"
  fi
  if [[ ! -e "$pkcs11_library" ]]; then
    info "UYARI: PKCS#11 kütüphanesi bulunamadı: $pkcs11_library"
    info "        (AKİS kurulu değilse veya farklı yoldaysa PKCS11_LIBRARY ile override et)"
  fi

  info "OS         : $os"
  info "Senaryo    : list-hsm (--list-certificates --force-login)"
  info "Library    : $pkcs11_library"
  info "NULL init  : $null_init_args"
  info "Force login: aktif (token sorgusundan önce C_Login)"
  ok "HSM sertifikaları listeleniyor..."

  PKCS11_LIBRARY="$pkcs11_library" \
  PKCS11_SLOT="$slot" \
  PKCS11_SLOT_LIST_INDEX="$slot_index" \
  PKCS11_NULL_INIT_ARGS="$null_init_args" \
  PKCS11_FORCE_LOGIN=true \
  exec "$mvn_cmd" -q exec:java \
    -Dexec.mainClass=io.mersel.dss.signer.api.SignatureApplication \
    -Dexec.args="--list-certificates --force-login"
}

# ───────────────────────────────────────── main
main() {
  local scenario="${1:-default}"

  # list modu — başka iş yapmadan yardımı bas ve çık.
  if [[ "$scenario" == "list" || "$scenario" == "-h" || "$scenario" == "--help" ]]; then
    print_scenarios
    exit 0
  fi

  local os
  os="$(detect_os)"

  if [[ "$os" == "unknown" ]]; then
    die "İşletim sistemi tespit edilemedi (OSTYPE=$OSTYPE)."
  fi

  # Java sağlık kontrolü (hem server hem listeleme için gerekli)
  if ! command -v java >/dev/null 2>&1; then
    die "java bulunamadı — JDK 8+ kurulu olmalı."
  fi
  local java_version
  java_version="$(java -version 2>&1 | head -n1)"

  # Maven runner — wrapper varsa onu tercih et
  local mvn_cmd="mvn"
  if [[ -x "./mvnw" ]]; then
    mvn_cmd="./mvnw"
  elif ! command -v mvn >/dev/null 2>&1; then
    die "mvn (veya ./mvnw) bulunamadı — Maven 3.6+ kurulu olmalı."
  fi

  # HSM listeleme senaryosu — Spring kaldırmadan sertifikaları --force-login ile listeler.
  if [[ "$scenario" == "list-hsm" || "$scenario" == "list-certs-hsm" ]]; then
    info "Java       : $java_version"
    info "Maven      : $mvn_cmd"
    list_hsm_certificates "$os" "$mvn_cmd"
    return
  fi

  local profiles
  profiles="$(resolve_profile "$scenario" "$os")"

  info "OS         : $os"
  info "Senaryo    : $scenario"
  info "Profiles   : $profiles"
  info "Java       : $java_version"

  # Mali Mühür (HSM) senaryosunda PIN zorunlu — fail-fast
  if [[ "$profiles" == *mali-muhur-akis* && -z "${CERTIFICATE_PIN:-}" ]]; then
    die "Mali Mühür senaryosu için CERTIFICATE_PIN env'i gerekli. Örnek:
       CERTIFICATE_PIN=1234 ./scripts/dev-run.sh mali-muhur-akis"
  fi

  info "Maven      : $mvn_cmd"

  ok "Başlatılıyor..."
  exec "$mvn_cmd" spring-boot:run -Dspring-boot.run.profiles="$profiles"
}

main "$@"
