# 🚀 Test Sertifikaları Hızlı Başvuru (Cheatsheet)

## ⚠️ ÖNEMLİ UYARI

**Bu test sertifikaları SADECE geliştirme/test içindir!**  
**Production'da ASLA kullanmayın!** Production için resmi CA sertifikası kullanın.

## 📋 Hızlı Komutlar

### Docker ile Başlatma (Önerilen) - Parametreli Script

```bash
# Unix/Linux/macOS
cd devops/docker

# Kurum 17 - EC384 (default)
./unix/start-test-kurum.sh 17         # testkurum17 (EC384)

# Kurum 2 - EC384
./unix/start-test-kurum.sh 2          # testkurum02 (EC384)

# Kurum 3 - EC384
./unix/start-test-kurum.sh 3          # testkurum03 (EC384)
```

```powershell
# Windows (PowerShell)
cd devops\docker

# Kurum 17 - EC384 (default)
.\windows\start-test-kurum.ps1 17         # testkurum17 (EC384)

# Kurum 2 - EC384
.\windows\start-test-kurum.ps1 2          # testkurum02 (EC384)

# Kurum 3 - EC384
.\windows\start-test-kurum.ps1 3          # testkurum03 (EC384)
```

### Docker Compose (Manuel)

```bash
cd devops/docker

# Varsayılan (Kurum 1 - RSA)
docker-compose up -d

# Script .env.temp oluşturduğu için manuel kullanım:
# Script kullanmanız önerilir, ancak manuel yapmak isterseniz:
docker-compose --env-file .env.test.kurum1 up -d  # Kurum 1 (RSA)

# Logları izle
docker-compose logs -f sign-api

# Durdur
docker-compose down
```

### Manuel Başlatma (Yerel - Docker olmadan)

```bash
# EC384 Sertifikalar (2026-10 itibarıyla tek set — RSA kaldırıldı)

# Kurum 17 - EC384 (default)
export PFX_PATH=./resources/test-certs/testkurum17_ec384@test.gov.tr_328829.pfx
export CERTIFICATE_PIN=328829
export CERTIFICATE_ALIAS=testkurum17
mvn spring-boot:run

# Kurum 2 - EC384
export PFX_PATH=./resources/test-certs/testkurum02_ec384@test.com.tr_825095.pfx
export CERTIFICATE_PIN=825095
export CERTIFICATE_ALIAS=testkurum02
mvn spring-boot:run

# Kurum 3 - EC384
export PFX_PATH=./resources/test-certs/testkurum03_ec384@test.com.tr_540425.pfx
export CERTIFICATE_PIN=540425
export CERTIFICATE_ALIAS=testkurum3_ec
mvn spring-boot:run
```

## 🧪 Test Komutları

### Otomatik Test

```bash
# API'yi başlat (KURUM17 EC384, default)
./scripts/dev-run.sh &
API_PID=$!
sleep 15

# curl örnekleriyle manuel test (aşağıdaki bölümler)
# ...

# API'yi durdur
kill $API_PID
```

### Manuel Test - XML İmzalama

```bash
echo '<?xml version="1.0"?><test>data</test>' > test.xml
curl -X POST http://localhost:8085/v1/xadessign \
  -F "document=@test.xml" \
  -F "documentType=None" \
  -o signed.xml
```

### Manuel Test - PDF İmzalama

```bash
curl -X POST http://localhost:8085/v1/padessign \
  -F "document=@document.pdf" \
  -F "appendMode=false" \
  -o signed.pdf
```

### Manuel Test - SOAP İmzalama

```bash
curl -X POST http://localhost:8085/v1/wssecuritysign \
  -F "document=@soap.xml" \
  -F "soap1Dot2=false" \
  -o signed-soap.xml
```

## 📊 Sertifika Bilgileri

| Kurum | Algoritma | Dosya | Parola | Alias |
|-------|-----------|-------|--------|-------|
| **Kurum 17** | **EC384** | `testkurum17_ec384@test.gov.tr_328829.pfx` | `328829` | `testkurum17` |
| **Kurum 2** | **EC384** | `testkurum02_ec384@test.com.tr_825095.pfx` | `825095` | `testkurum02_ec` |
| **Kurum 3** | **EC384** | `testkurum03_ec384@test.com.tr_540425.pfx` | `540425` | `testkurum03_ec` |

**Not:** Tüm sertifikalar `resources/test-certs/` klasöründe bulunuyor.
2026-10 itibarıyla RSA test sertifikaları Kamu SM tarafından yenilenmediği
için kaldırıldı; EC384 seti 2028-05'e kadar geçerli.

### Kurum Özellikleri

- **Kurum 17:** EC384 (default)
- **Kurum 2:** EC384
- **Kurum 3:** EC384

## 🔍 Sertifika İnceleme

### Keytool ile İnceleme

```bash
# Kurum 17 - EC384
keytool -list -v -keystore resources/test-certs/testkurum17_ec384@test.gov.tr_328829.pfx \
  -storetype PKCS12 -storepass 328829

# Kurum 2 - EC384
keytool -list -v -keystore resources/test-certs/testkurum02_ec384@test.com.tr_825095.pfx \
  -storetype PKCS12 -storepass 825095

# Kurum 3 - EC384
keytool -list -v -keystore resources/test-certs/testkurum03_ec384@test.com.tr_540425.pfx \
  -storetype PKCS12 -storepass 540425
```

### OpenSSL ile Detaylı İnceleme

```bash
# Public key algoritmasını kontrol et
openssl pkcs12 -in resources/test-certs/testkurum17_ec384@test.gov.tr_328829.pfx \
  -passin pass:328829 -nokeys -clcerts | \
  openssl x509 -text -noout | grep -E "(Subject:|Public Key Algorithm:|Public-Key:)"

# Sertifika zincirini görüntüle
openssl pkcs12 -info -in resources/test-certs/testkurum02_ec384@test.com.tr_825095.pfx \
  -passin pass:825095 -nokeys
```

## 🌐 API Endpoint'leri

| Endpoint | Açıklama |
|----------|----------|
| `http://localhost:8085` | API Base URL |
| `http://localhost:8085/index.html` | Swagger UI (API Dokümantasyonu) |
| `http://localhost:8085/actuator/health` | Health Check (Sağlık Kontrolü) |
| `http://localhost:8085/actuator/info` | Application Info (Uygulama Bilgisi) |
| `http://localhost:8085/actuator/prometheus` | Prometheus Metrics (Monitoring) |
| `http://localhost:8085/actuator/metrics` | Metrics Detail (JSON) |
| `http://localhost:8085/v1/xadessign` | XAdES İmzalama |
| `http://localhost:8085/v1/padessign` | PAdES (PDF) İmzalama |
| `http://localhost:8085/v1/wssecuritysign` | WS-Security İmzalama |
| `http://localhost:8085/api/tubitak/credit` | TÜBİTAK Kontör |

## 🛠️ Faydalı Komutlar

### API Durumu Kontrolü

```bash
# API sağlık kontrolü
curl -s http://localhost:8085/actuator/health

# Uygulama bilgileri
curl -s http://localhost:8085/actuator/info

# Prometheus metrics
curl -s http://localhost:8085/actuator/prometheus | head -20

# Belirli metrik detayı
curl -s http://localhost:8085/actuator/metrics/http.server.requests | jq

# Port dinleniyor mu?
lsof -i :8085

# Process ID bul
ps aux | grep java | grep spring-boot
```

### Log Kontrolü

```bash
# Canlı log izle
tail -f logs/application.log

# Hata logları
tail -f logs/error.log

# İmzalama logları
tail -f logs/signature.log

# Son 100 satır
tail -n 100 logs/application.log
```

### Cleanup (Temizlik)

```bash
# Maven temizle
mvn clean

# Log'ları temizle
rm -f logs/*.log

# Test dosyalarını temizle
rm -f test*.xml signed*.xml signed*.pdf
```

## 🔄 Sertifika Değiştirme (Çalışırken)

### Docker ile (Önerilen)

```bash
cd devops/docker

# 1. Mevcut servisi durdur
docker-compose down

# 2. Farklı kurum/algoritma ile başlat
./unix/start-test-kurum.sh 2 ec384  # Kurum 2 - EC384'e geç
# veya
./unix/start-test-kurum.sh 3 rsa    # Kurum 3 - RSA'ya geç

# 3. Logları kontrol et
docker-compose logs -f sign-api
```

### Yerel Ortamda

```bash
# 1. API'yi durdur (Ctrl+C veya)
pkill -f "spring-boot:run"

# 2. Yeni sertifika ayarla (örnek: EC384'e geç)
export PFX_PATH=./resources/test-certs/testkurum2_ec384@test.com.tr_825095.pfx
export CERTIFICATE_PIN=825095
export CERTIFICATE_ALIAS=testkurum2

# 3. Yeniden başlat
mvn spring-boot:run
```

## 📦 Toplu İşlemler

### Tüm Testleri Çalıştır

```bash
# API'yi başlat (KURUM17 EC384, default)
./scripts/dev-run.sh &
API_PID=$!

# API'nin başlamasını bekle
sleep 15

# Aşağıdaki curl örnekleriyle endpoint'leri dene

# API'yi durdur
kill $API_PID
```

### Tüm Sertifikalarla Test (Docker)

```bash
cd devops/docker

# Test dökümanı oluştur
echo '<?xml version="1.0"?><test>data</test>' > test.xml

# Test kombinasyonları
declare -a TESTS=(
  "1:rsa"       # Kurum 1 - RSA
  "2:rsa"       # Kurum 2 - RSA
  "2:ec384"     # Kurum 2 - EC384
  "3:rsa"       # Kurum 3 - RSA
  "3:ec384"     # Kurum 3 - EC384
)

# Her kombinasyon için test et
for test in "${TESTS[@]}"; do
  IFS=':' read -r kurum type <<< "$test"
  echo "🔐 Test Kurum $kurum ($type) ile test başlıyor..."
  
  ./unix/start-test-kurum.sh $kurum $type
  echo "Servisin başlaması bekleniyor..."
  sleep 30
  
  # XAdES imzalama testi
  curl -s -X POST http://localhost:8085/v1/xadessign \
    -F "document=@test.xml" \
    -F "documentType=None" \
    -o "signed-kurum${kurum}-${type}.xml"
  
  echo "✅ Kurum $kurum ($type) testi tamamlandı"
  
  docker-compose down
  sleep 5
done

echo "🎉 Tüm testler tamamlandı!"
ls -lh signed-kurum*.xml
```

## 🐛 Sorun Giderme

### "Connection refused"

```bash
# API'nin çalıştığını doğrula
curl http://localhost:8085/index.html

# Port'un dinlendiğini doğrula
lsof -i :8085
```

### "Keystore yüklenemedi"

```bash
# Dosyanın varlığını kontrol et
ls -la $PFX_PATH

# Dosya tipini kontrol et
file $PFX_PATH

# Parolayı kontrol et
echo $CERTIFICATE_PIN
```

### "Maven bulunamadı"

```bash
# Maven versiyonunu kontrol et
mvn -version

# Maven'i yükle (macOS)
brew install maven

# Maven'i yükle (Ubuntu/Debian)
sudo apt-get install maven
```

### "Java versiyonu uyumsuz"

```bash
# Java versiyonunu kontrol et
java -version

# Java'yı güncelle (macOS)
brew install openjdk@11

# JAVA_HOME ayarla
export JAVA_HOME=/path/to/java
```

## 📚 Detaylı Dökümanlar

- [TEST_CERTIFICATES.md](TEST_CERTIFICATES.md) - Tam test sertifikaları rehberi
- [Hızlı Başlangıç](https://dss.mersel.dev/getting-started/quick-start) - Genel hızlı başlangıç
- [Ana Dokümantasyon](https://dss.mersel.dev) - Merkezi dokümantasyon
- [examples/README.md](examples/README.md) - Kullanım örnekleri

## 💡 Yararlı İpuçları

1. **Docker ile RSA ve EC384 karşılaştırma:**
   ```bash
   # RSA ile test (Kurum 2)
   cd devops/docker && ./unix/start-test-kurum.sh 2 rsa
   curl -X POST http://localhost:8085/v1/xadessign -F "document=@test.xml" -F "documentType=None" -o rsa-signed.xml
   
   # EC384 ile test (Kurum 2)
   docker-compose down && ./unix/start-test-kurum.sh 2 ec384
   curl -X POST http://localhost:8085/v1/xadessign -F "document=@test.xml" -F "documentType=None" -o ec-signed.xml
   ```

2. **Debug mode:**
   ```bash
   export LOGGING_LEVEL_ROOT=DEBUG
   mvn spring-boot:run
   ```

3. **Timestamp etkinleştir:**
   ```bash
   export IS_TUBITAK_TSP=true
   export TS_USER_ID=your-id
   export TS_USER_PASSWORD=your-password
   mvn spring-boot:run
   ```

4. **Docker ile hızlı yeniden başlatma:**
   ```bash
   cd devops/docker
   docker-compose down && docker-compose up -d && docker-compose logs -f sign-api
   ```

5. **Prometheus + Grafana ile monitoring:**
   ```bash
   # Docker Compose otomatik olarak başlatır
   cd devops/docker && ./unix/start-test-kurum.sh 1
   
   # URL'ler:
   # - Sign API: http://localhost:8085
   # - Prometheus: http://localhost:9090
   # - Grafana: http://localhost:3000 (admin/admin)
   ```

---

**Not:** Bu döküman test sertifikaları için hazırlanmıştır. Production ortamı için [merkezi dokümantasyonu](https://dss.mersel.dev) ziyaret edin.

