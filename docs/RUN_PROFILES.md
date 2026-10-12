# Mersel DSS Signer — Run / Debug Profilleri

> **TL;DR** — Repo'yu klonla, JDK 8+ kurulu olsun, IDE'de aç. `.run/` klasöründeki "Signer · Default (KURUM17 EC-384)" config'iyle direkt Run veya Debug bas. PFX repo'da hazır, ortam değişkeni set etmek gerekmiyor.

Bu doküman üç farklı geliştirici kanalını anlatır:

1. **IntelliJ IDEA / Cursor** — `.run/*.run.xml` shared configurations
2. **VS Code** — `.vscode/launch.json` + `.vscode/tasks.json`
3. **CLI** — `scripts/dev-run.sh` (POSIX) ve `scripts/dev-run.bat` (Windows)

Üç kanal da aynı **Spring profile katmanlarını** kullanır; farklı bir keystore'a geçmek için tek tıkla / tek argümanla profil değişiyor.

---

## 1. Spring Profile Mimarisi

Bu repo "composable" Spring profile yaklaşımıyla çalışır. Her profile **tek bir konuya** sahiptir, üst üste yığılır:

| Profile | Sorumluluk |
|---|---|
| `local` | Network'i kapatır (CertificateChain offline, KamuSM TSP off, trusted root resolver no-op), DEBUG log seviyesini açar. **Tüm dev senaryolar bunu içerir.** |
| `pfx-kurum17-ec384` | KURUM17 EC-P384 test PFX (default sahnede otomatik aktif) |
| `pfx-kurum02-ec384` | KURUM02 EC-P384 — ECDSA imza yolu testi |
| `pfx-kurum03-ec384` | KURUM03 EC-P384 |
| `mali-muhur-akis-mac` | TÜBİTAK Mali Mühür AKİS macOS sürücüsü (`/usr/local/lib/libakisp11.dylib`) |
| `mali-muhur-akis-linux` | Mali Mühür AKİS Linux sürücüsü (`/usr/local/lib/libakisp11.so`) |
| `mali-muhur-akis-windows` | Mali Mühür AKİS Windows sürücüsü (`C:/Windows/System32/akisp11.dll`) |
| `hsm-sansec-docker` | Sansec ağ HSM'i (`libupkcs11.so`). **Sadece linux/amd64 Docker container içinde** çalışır — macOS/Apple Silicon JVM Linux `.so`'yu yükleyemez. Bkz. §8. |

### Aktivasyon Kuralı

Her zaman **`local` + bir alt-profile** birlikte aktive edilir:

```
--spring.profiles.active=local,pfx-kurum02-ec384
--spring.profiles.active=local,mali-muhur-akis-mac
```

`local` ortak ortam ayarlarını tek bir yerden çekmemizi sağlıyor (DRY); alt-profile yalnızca keystore'a özgü override'ları taşır.

### PFX vs HSM — Neden Ayrı?

- **PFX profilleri** repo'daki KamuSM publicly published test sertifikalarını kullanır. PIN dosya adında açıkta; geliştirici hiçbir env var girmek zorunda değil. Üç OS'te de aynı dosya çalışır — `PFX_PATH` repo-relatif.
- **HSM profilleri** OS'in PKCS#11 sürücü yolunu farklı yerden aldığı için ayrılmıştır. Operatör akıllı kart takıp `CERTIFICATE_PIN` env'ini doldurur; PFX'in aksine kart bağımlı olduğu için profile dosyasında PIN tutulmaz.

---

## 2. IntelliJ IDEA / Cursor

`.run/` klasörü repo'ya commit'li; IDE bunları otomatik tanır.

### Mevcut configurations

| Config adı | Profile | Notu |
|---|---|---|
| `Signer · Default (KURUM17 EC-384)` | `local,pfx-kurum17-ec384` | Yeni geliştirici için başlangıç noktası |
| `Signer · PFX EC-384 (KURUM02)` | `local,pfx-kurum02-ec384` | ECDSA r||s plain regression testi için |
| `Signer · PFX EC-384 (KURUM03)` | `local,pfx-kurum03-ec384` | Üçüncü kurum, EC |
| `Signer · Mali Mühür AKİS (macOS)` | `local,mali-muhur-akis-mac` | Gerçek AKİS kartı + PIN |
| `Signer · Mali Mühür AKİS (Linux)` | `local,mali-muhur-akis-linux` | Gerçek AKİS kartı + PIN |
| `Signer · Mali Mühür AKİS (Windows)` | `local,mali-muhur-akis-windows` | Gerçek AKİS kartı + PIN |
| `Signer · HSM Sansec (Docker Compose Up)` | `local,hsm-sansec-docker` | Sansec `.so`'yu linux/amd64 container'da IN-PROCESS koşar (bkz. §8) |
| `Signer · HSM Sansec (Docker attach 5005)` | — (Remote JVM Debug) | Container JVM'ine JDWP :5005 attach — breakpoint'ler çalışır |
| `CLI · List PFX Certificates (KURUM17)` | (yok — `--list-certificates`) | Spring kalkmaz, sadece PFX içeriğini stdout'a basar |
| `CLI · List HSM Certificates (Mali Mühür AKİS · force-login)` | (yok — `--list-certificates --force-login`) | Spring kalkmaz; AKİS token'ına C_Login yapıp HSM sertifikalarını listeler. TÜBİTAK kurulumu tamam olan makinede kullanılır |
| `Tests · Unit (mvn test)` | — | Default suite (560 test, ~50 sn) |
| `Tests · XAdES SigningTime (issue 7)` | — | Issue #7 regression suite |
| `Tests · Verifier E2E (Docker)` | — | Verifier-e2e tag'li suite (Docker gerekir) |

### Run / Debug

1. IntelliJ üst-sağ Run dropdown'ından config'i seç.
2. **Run** (▶) veya **Debug** (🐞). Debug breakpoint'leri direkt çalışır.
3. HSM senaryolarında ilk run öncesi config'i "Edit Configurations…" ile aç, `CERTIFICATE_PIN` env'inde `REPLACE_WITH_YOUR_PIN` yazısını **kendi PIN'inle** değiştir.

> **JDK ayarı**: IntelliJ "Project Structure → Project SDK" sekmesinden 8+ bir JDK seçtiğin sürece bu config'ler ek JRE path'i istemez. Repo Java 8 baseline ile build edilir.

---

## 3. VS Code (Java Extension Pack)

`.vscode/launch.json` aynı senaryoları VS Code Java debugger'ına maple eder.

### Run / Debug

1. **Run and Debug** panelini aç (`Ctrl+Shift+D` / `⇧⌘D`).
2. Üstteki dropdown'dan senaryoyu seç (örn. `Signer :: PFX EC-384 (KURUM02)`).
3. Yeşil ▶ butonuna bas — debugger attach edilir.

HSM senaryolarında `launch.json` içindeki `env.CERTIFICATE_PIN` field'ını kendi PIN'inle değiştir.

### Tasks

`.vscode/tasks.json` aşağıdaki Maven hedeflerini sağlar:

- **mvn test (unit)** — default test suite (`Ctrl+Shift+B` ile çağrılan default)
- **mvn test - XAdES SigningTime (issue 7)** — sadece SigningTime regression
- **mvn test - Verifier E2E (Docker)** — verifier-e2e tag'li
- **mvn spring-boot:run (local default)** — terminal'de Spring Boot başlatır

`Ctrl+Shift+P` → "Tasks: Run Task" ile çağrılır.

---

## 4. CLI (IDE'siz)

JDK + Maven kurulu makinede IDE açmadan koşmak için.

```bash
# Default (KURUM17 EC-P384), OS auto-detect
./scripts/dev-run.sh

# Diğer senaryolar
./scripts/dev-run.sh kurum02-ec384
./scripts/dev-run.sh kurum03-ec384

# Mali Mühür AKİS — OS'a göre doğru profile seçilir
CERTIFICATE_PIN=1234 ./scripts/dev-run.sh mali-muhur-akis

# HSM sertifikalarını --force-login ile listele (Spring kalkmaz)
CERTIFICATE_PIN=1234 ./scripts/dev-run.sh list-hsm

# Mevcut senaryoları listele
./scripts/dev-run.sh list
```

Windows:

```bat
REM Default
scripts\dev-run.bat

REM EC-P384
scripts\dev-run.bat kurum02-ec384

REM HSM (PIN env ile)
set CERTIFICATE_PIN=1234
scripts\dev-run.bat mali-muhur-akis
```

Script `OSTYPE` / `uname` ile OS tespiti yapar, `mvn` veya `./mvnw` arar; HSM senaryosunda PIN yoksa fail-fast davranır.

---

## 5. Sertifika Listeleme (Smoke Test)

Spring container kaldırmadan PFX'in sağlıklı olduğunu hızlıca doğrulamak için:

**IntelliJ**: `CLI · List PFX Certificates (KURUM17)` config'ini çalıştır.

**VS Code**: `CLI :: List PFX Certificates (KURUM17)` debug config'ini çalıştır.

**CLI (POSIX)**:

```bash
PFX_PATH=resources/test-certs/testkurum17_ec384@test.gov.tr_328829.pfx \
CERTIFICATE_PIN=328829 \
mvn -q exec:java -Dexec.mainClass=io.mersel.dss.signer.api.SignatureApplication \
                  -Dexec.args="--list-certificates"
```

### HSM (Mali Mühür AKİS) sertifikalarını listelemek — `--force-login`

TÜBİTAK kurulumu tamamlandıktan sonra HSM'deki sertifikaları listelemek için.
AKİS gibi bazı HSM cihazları public objeleri (sertifika / public key) oturum
açılmadan **göstermez**; login'siz listeleme boş liste döner veya init
`CKR_USER_NOT_LOGGED_IN` ile patlar. `--force-login` bayrağı token'a dair ilk
sorgudan önce PIN ile `C_Login` yaptırır (env karşılığı: `PKCS11_FORCE_LOGIN=true`).

> `--list-certificates` yolu Spring context kaldırmadığı için Spring profile ve
> `local-secrets.properties` **okunmaz** — bu yüzden `PKCS11_LIBRARY` ve
> `CERTIFICATE_PIN` env olarak explicit verilir.

**IntelliJ / Cursor**: `CLI · List HSM Certificates (Mali Mühür AKİS · force-login)`
config'ini aç, env'deki `CERTIFICATE_PIN=REPLACE_WITH_YOUR_PIN` değerini kendi
PIN'inle değiştir, Run bas.

**CLI (script, OS auto-detect)**:

```bash
CERTIFICATE_PIN=1234 ./scripts/dev-run.sh list-hsm
```

**CLI (elle, POSIX)**:

```bash
PKCS11_LIBRARY=/usr/local/lib/libakisp11.dylib \
PKCS11_NULL_INIT_ARGS=true \
CERTIFICATE_PIN=1234 \
mvn -q exec:java -Dexec.mainClass=io.mersel.dss.signer.api.SignatureApplication \
                  -Dexec.args="--list-certificates --force-login"
```

Linux'ta sürücü `.../libakisp11.so`, Windows'ta `C:/Windows/System32/akisp11.dll`;
`dev-run.sh list-hsm` / `dev-run.bat list-hsm` bu default'ları OS'a göre kendi seçer,
farklı yol için `PKCS11_LIBRARY` env'i ile override edilir.

---

## 6. Sık Karşılaşılan Sorunlar

### "PFX dosyası bulunamadı"

PFX yolu **repo-relatif** (`resources/test-certs/...`). IDE Run Configuration `WORKING_DIRECTORY=$PROJECT_DIR$` koymuş; eğer dosya bulunamıyorsa çalışma dizini repo kökü değil demektir. `.run/*.run.xml` içindeki `WORKING_DIRECTORY` değerini kontrol et.

### HSM "token bulunamadı"

1. Kart fiziksel olarak takılı mı? (macOS: `pcsctest`, Linux: `pcsc_scan`, Windows: cihaz yöneticisi)
2. `PKCS11_LIBRARY` env'i doğru yolu gösteriyor mu? Profile dosyasındaki default'lar OS-spesifik ama operatör farklı kurulum yaptıysa env override gerekir.
3. macOS'ta AKİS sürücüsü `CKR_ARGUMENTS_BAD` veriyorsa `PKCS11_NULL_INIT_ARGS=true` zaten `application-mali-muhur-akis-mac.properties`'te aktif.

### HSM listing boş dönüyor / `CKR_USER_NOT_LOGGED_IN`

Bazı HSM cihazları public objeleri (sertifika/public key) ve mekanizma listesini bile oturum açılmadan göstermez. `--list-certificates` boş liste veriyor ya da init `CKR_USER_NOT_LOGGED_IN` ile patlıyorsa force-login modunu kullanın — token'a dokunan ilk sorgudan önce `C_Login` yapılır:

```bash
# CLI
java -jar mersel-dss-signer.jar --list-certificates --force-login

# veya env ile (server modunda da geçerli; remote helper'a otomatik aktarılır)
export PKCS11_FORCE_LOGIN=true
```

Bu modda `CERTIFICATE_PIN` zorunludur; yanlış PIN fail-fast hata verir.

### SafeNet HSM — `CKR_NO_SESSION_KEYS` (`0x80000387`) idle hatası

SafeNet Luna / ProtectServer / ProtectToolkit'te uzun idle sonrası ilk imza isteği vendor hata kodu `CKR_NO_SESSION_KEYS` ile patlıyorsa secure messaging session-key'i HSM tarafında reap edilmiştir. Eskiden dışarıdan periyodik "boş XML imza" cron'u ile çözülen senaryo; artık in-process scheduler ile yönetilir:

```bash
export HSM_HEARTBEAT_ENABLED=true
export HSM_HEARTBEAT_INTERVAL_SECONDS=60   # Luna Network için 30-45sn önerilir
```

Detay: README'deki [HSM Heartbeat](../README.md#hsm-heartbeat-safenet-ckr_no_session_keys-workaround) bölümü.

### "Active Profiles ekranda görünmüyor"

IntelliJ Spring Boot run config'inde "Active Profiles" alanı boşsa, "Edit Configurations…" → ilgili config'i seç → "Active Profiles" alanını kontrol et. Bu repo'daki configs `local,pfx-kurum17-ec384` formatında (virgülle ayrılmış) bekler.

### Production'a Sızma Endişesi

`local` profile **sadece** explicit aktivasyon ile yüklenir. Production deployment `java -jar` ile çalıştırıldığında `SPRING_PROFILES_ACTIVE` set edilmediği için bu dosyalar hiç parse edilmez. Test PFX'leri repo'da olmasına rağmen production env'ine taşınmaz; production'da HSM'den PIN gelir.

---

## 7. Yeni Senaryo Eklemek

1. Yeni `application-pfx-<key>.properties` veya `application-mali-muhur-<key>-<os>.properties` dosyasını oluştur.
2. `.run/` altına bir XML kopyala-yapıştır, `ACTIVE_PROFILES` değerini güncelle.
3. `.vscode/launch.json` array'ine yeni entry ekle.
4. `scripts/dev-run.sh` ve `dev-run.bat` `resolve_profile` switch'lerine ekle.
5. Bu dokümandaki tabloları güncelle.

> Tek bir kaynaktan profile metadata yönetmek istersek (örn. JSON), `dev-run.sh` o JSON'u tüketen bir templating'e geçirilebilir. Şu an 5 PFX + 3 HSM kombinasyonu için manuel düzen yeterli; sahnede daha fazla cert tipi çoğalırsa o yapıyı kurarız.

---

## 8. Sansec HSM (Linux `.so`) — macOS'te Docker ile Çalıştırma

### Sorun

Vendor kütüphaneleri `libupkcs11.so` (PKCS#11 sarmalayıcı) + `libswsds.so` (SDS ağ
taşıma katmanı) **Linux x86-64 ELF** binary'leridir. Java bytecode platform
bağımsız olsa da, PKCS#11 modülü JNI ile **JVM'in kendi process'ine `dlopen`**
edilir; native kod platforma özeldir:

| Faktör | `.so` dosyaları | macOS host |
|---|---|---|
| OS formatı | Linux **ELF** | Mach-O (`.dylib`) ister |
| Mimari | **x86-64** | çoğunlukla arm64 (Apple Silicon) |

macOS JVM bu `.so`'yu **yükleyemez** → `UnsatisfiedLinkError` (`wrong
architecture`). Kaynak kod olmadan `.dylib`'e derlenemez.

> **Bit köprüsü (`PKCS11_BRIDGE_MODE`) bunu ÇÖZMEZ.** O köprü aynı OS'te 32↔64
> bit uyuşmazlığı içindir ve helper'ı **lokal** olarak fork eder; macOS'te
> fork'lanan JVM de Linux `.so`'yu yükleyemez. Bit köprüsü OS/ABI köprüsü değildir.

### Çözüm

Bu repo'nun kendi kanıtlanmış deseni (bkz. `devops/docker/Dockerfile.pkcs11-tests`
başlığı): **native lib + JVM AYNI process'te olmak zorunda → JVM'i `.so` ile aynı
ABI'ye taşı.** Yani tüm Signer'ı `linux/amd64` bir container içinde çalıştırırız;
`.so` orada IN-PROCESS yüklenir. Docker Desktop Apple Silicon'da bunu Rosetta/QEMU
ile emüle eder.

Sansec bir **ağ HSM'idir** (`swsds0.ini` içindeki `ip:port`); fiziksel kart/okuyucu
gerekmez — yalnızca HSM'in container'dan **ağ olarak erişilebilir** olması yeterli.

### Bileşenler

| Dosya | Rolü |
|---|---|
| `src/main/resources/application-hsm-sansec-docker.properties` | Spring profili (`hsm-sansec-docker`): container-içi PKCS#11 yolları |
| `devops/docker/Dockerfile.hsm-dev` | `linux/amd64` JDK 8 + `.so` runtime bağımlılıkları + JDWP; jar'ı içinde build etmez |
| `devops/docker/docker-compose.hsm-dev.yml` | HSM dizinini + host jar'ını mount eder, env + portları (8085, 5005) kurar |
| `devops/docker/.env.hsm-dev.example` | `HSM_LIB_DIR` + `CERTIFICATE_PIN` şablonu (kopyası gitignored) |
| `.run/Signer · HSM Sansec (Docker Compose Up)` | Container'ı IntelliJ'den başlatır |
| `.run/Signer · HSM Sansec (Docker attach 5005)` | Container JVM'ine debugger attach eder |

### Adımlar

```bash
# 1) Konfigürasyon (bir kez): PIN + .so dizinini gir
cp devops/docker/.env.hsm-dev.example devops/docker/.env.hsm-dev
#   HSM_LIB_DIR = libupkcs11.so + libswsds.so + swsds0.ini + upkcs11.ini + OBJSP02 dizini
#   CERTIFICATE_PIN = HSM login PIN'i

# 2) Host'ta jar üret (bytecode; linux/amd64 container'da sorunsuz koşar)
./mvnw -q -DskipTests package          # veya IntelliJ "Make Project"

# 3) Container'ı başlat (ilk sefer image build eder)
docker compose -f devops/docker/docker-compose.hsm-dev.yml \
     --env-file devops/docker/.env.hsm-dev up --build
```

- **API**: <http://localhost:8085/actuator/health>
- **Debug**: IntelliJ'de `Signer · HSM Sansec (Docker attach 5005)` → **Debug (🐞)**.
  `suspend=n` olduğundan uygulama beklemeden başlar; istediğin an attach edersin.
- **Modülü bağımsız doğrula** (image `pkcs11-tool` içerir):
  ```bash
  docker compose -f devops/docker/docker-compose.hsm-dev.yml exec signer-hsm \
       pkcs11-tool --module /opt/hsm/libupkcs11.so -L
  ```

### Sık Sorunlar

- **`up` anında "CERTIFICATE_PIN ... set edin"** → `.env.hsm-dev` oluşturulmamış
  veya PIN boş. `--env-file` bayrağını verdiğinden emin ol.
- **`/app icinde ...jar bulunamadı`** → Önce `./mvnw -DskipTests package`. compose
  `target/`'ı read-only mount eder; entrypoint en yeni (mtime) jar'ı seçer.
- **HSM'e bağlanılamıyor / timeout** → `swsds0.ini`'deki `ip:port` container'ın
  ağından erişilebilir mi? (`exec signer-hsm sh -c 'apt-get ... ping/nc'` veya
  VPN/route kontrolü). Bu HSM ağ üzerindedir; yerel değil.
- **Apple Silicon'da yavaş başlangıç** → `linux/amd64` emülasyonu (Rosetta/QEMU)
  beklenen davranış; dev/test için kabul edilebilir, üretim Linux amd64 host'ta
  native koşar.
- **Not**: `.so` çalışırken `HSM_LIB_DIR` içine `swsds*.log`, `upkcs11.log` ve
  `OBJSP02` cache yazar (mount rw). Bu dosyalar host dizininde birikir.
