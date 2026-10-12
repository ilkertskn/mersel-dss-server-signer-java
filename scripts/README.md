# 🛠️ Scripts Klasörü

Bu klasörde Sign API için yardımcı script'ler bulunmaktadır.

> **2026-10 notu:** Eski `start-test{1,2,3}.{sh,ps1}` ve
> `quick-start-with-test-certs.{sh,ps1}` script'leri kaldırıldı — Kamu SM
> RSA test sertifikalarının geçerliliği doldu ve script'ler uzun süredir
> stale dosya adlarına referans veriyordu. Test sertifikalarıyla başlatma
> için `dev-run.sh` / `dev-run.bat` senaryolarını
> (`kurum17-ec384` [default], `kurum02-ec384`, `kurum03-ec384`) veya
> `devops/docker/{unix,windows}/start-test-kurum.{sh,ps1}` script'lerini
> kullanın.

## 📁 Klasör Yapısı

```
scripts/
├── dev-run.sh         # Senaryo tabanlı başlatma (PFX / HSM / list-hsm)
├── dev-run.bat        # Windows karşılığı
├── prepare-github-release.sh
├── serve-pages-locally.sh
└── README.md          # Bu dosya
```

## 🚀 Hızlı Kullanım

```bash
# Default (KURUM17 EC-P384 test PFX, network kapalı)
./scripts/dev-run.sh

# Diğer EC384 kurumları
./scripts/dev-run.sh kurum02-ec384
./scripts/dev-run.sh kurum03-ec384

# Mali Mühür AKİS HSM (CERTIFICATE_PIN zorunlu)
CERTIFICATE_PIN=1234 ./scripts/dev-run.sh mali-muhur-akis

# Senaryo listesi
./scripts/dev-run.sh list
```

Windows karşılığı: `scripts\dev-run.bat` (aynı senaryolar).

## 🚀 Diğer

| Script | Açıklama |
|--------|----------|
| `prepare-github-release.sh` | GitHub release hazırlama |
| `serve-pages-locally.sh` | Statik sayfaları lokal serve etme |
