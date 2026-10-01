# `resources/test-certs/` — Kamu SM test sertifikaları

Bu dizin, Kamu Sertifikasyon Merkezi (Kamu SM) test ortamında **publicly published**
mali mühür test sertifikalarını barındırır. Buradaki PFX'ler:

- Production değildir — Kamu SM tarafından test amaçlı yayımlanmış,
  şifreleri dosya adının son segmentinde **bilerek açıkta** tutulan
  sertifikalardır.
- Mali mühür yetkisi yoktur, gerçek e-Belge gönderiminde kullanılamaz.
- Repo'ya commit edilir çünkü hem CI hem de yerel `verifier-e2e` suite'i
  bunlara bağımlıdır.

> Üretim mali mühür PFX'i bu repo'ya **asla** girmez — production'da
> HSM/PKCS#11 üzerinden referans alınır.

## Naming convention

```
{kurum}_{algo}[_{status}]@{domain}_{password}.pfx
```

- `kurum` — `testkurum17`, `testkurum02`, `testkurum03` (pozitifler) veya
  `testkurum` (negatifler — Kamu SM'in tek bir test cert publish ettiği case'ler)
- `algo` — `ec384` (2026-10 itibarıyla tek set; RSA kaldırıldı)
- `status` (opsiyonel) — `revoked` / `expired` / `suspended`
  (yoksa = `valid`, pozitif default)
- `domain` — Kamu SM ZIP'inde geçen sahiplik adı (`test.gov.tr`, `test.com.tr`)
- `password` — PKCS#12 password (dosya adının son `_` ile başlayan segmenti);
  `PfxTestKey.parsePassword()` regex'i: `^.+_([A-Za-z0-9]+)\.pfx$`

Alias her PFX için sabit: `"1"` (`PfxTestKey.DEFAULT_ALIAS`).

## Pozitif (Status.VALID) — mevcut envanter (✓)

Bu PFX'ler repo'da var ve default `verifier-e2e` matriksinde aktif.
2026-10 itibarıyla tamamı **EC-P384** (RSA seti Kamu SM'de yenilenmediği için
kaldırıldı; EC384 sertifikaları 2028-05'e kadar geçerli):

| Dosya | Algoritma | Sahiplik domain |
|---|---|---|
| `testkurum17_ec384@test.gov.tr_328829.pfx` | EC-P384 | test.gov.tr |
| `testkurum02_ec384@test.com.tr_825095.pfx` | EC-P384 | test.com.tr |
| `testkurum03_ec384@test.com.tr_540425.pfx` | EC-P384 | test.com.tr |

## Negatif (Status.REVOKED / EXPIRED / SUSPENDED) — mevcut envanter (✓)

`CertificateLifecycleNegativeE2ETest` aşağıdaki 3 PFX'i bekler (2026-10
itibarıyla repo'da; RSA negatifleri sertifikaları expire olduğu için
kaldırıldı):

| # | Dosya | Beklenen verifier davranışı |
|---|---|---|
| 1 | `testkurum_revoked_ec384@test.com.tr_670246.pfx` | INDETERMINATE/TOTAL_FAILED, sub=REVOKED |
| 2 | `testkurum_expired_ec384@test.com.tr_041207.pfx` | INDETERMINATE/TOTAL_FAILED, sub=OUT_OF_BOUNDS_NOT_FRESH / EXPIRED |
| 3 | `testkurum_suspended_ec384@test.com.tr_242195.pfx` | INDETERMINATE, sub=CERTIFICATE_HOLD |

### Adım adım indirme (negatif PFX yenilenmesi gerekirse)

1. https://kamusm.bilgem.tubitak.gov.tr/hizmetler/test_sistemi/test_sistemi_kullanici_sertifikalari.jsp
   → "Mali Mühür Sertifikaları" bölümü.
2. İptal / süresi geçmiş / askıda kategorilerindeki ZIP'leri ayrı ayrı indir.
   Her ZIP için Kamu SM email onayı + captcha isteyebilir; otomasyona uygun
   değildir.
3. ZIP'i aç → içinde 1 adet `.pfx` ve 1 adet `pass.txt` (veya benzer)
   vardır. `pass.txt`'deki PKCS#12 şifresini hatırla.
4. PFX'i `resources/test-certs/` altına aşağıdaki adla **rename ederek**
   kopyala:

   ```
   testkurum_revoked_ec384@test.com.tr_{PASS.TXT İÇERİĞİ}.pfx
   ```

   Örnek: pass.txt → `847291` ise dosya adı
   `testkurum_revoked_ec384@test.com.tr_847291.pfx` olur.

5. PFX dosyalarının repo'da görüldüğünü doğrula:

   ```bash
   ls -la resources/test-certs/testkurum_*.pfx
   ```

### Şifrenin dosya adında olmasının nedeni

`PfxTestKey` enum constructor'ı dosya adının son `_` segmentinden PKCS#12
şifresini parse eder (yani enum'a şifre **yazmaz**). Bu yaklaşım:

- Yeni PFX eklemek için kod değişikliği gerektirmez (sadece enum'a yeni
  constant eklenir, dosya adından şifre okunur).
- Test sertifikaları zaten public olduğu için "güvenlik" gerekçesi yok.
- Production yolunda **kullanılmaz**: production'da PIN HSM'de saklanır,
  PFX/şifre kombinasyonu yoktur.

## Testleri çalıştırma

PFX'leri yerleştirdikten sonra:

```bash
# Sadece lifecycle negatif testleri (verifier-e2e tag'i)
mvn test \
    -Dtest=CertificateLifecycleNegativeE2ETest \
    -Dgroups=verifier-e2e \
    -DexcludedGroups=

# Tüm verifier-e2e suite (pozitif + negatif)
mvn test -Dgroups=verifier-e2e -DexcludedGroups=
```

Dosya yoksa testler `Assumptions.assumeTrue(...)` ile graceful skip eder —
suite kırılmaz, sadece "skipped" raporlanır.

## Klasör hijyeni

- `*.pfx` dosyaları repo'ya commit edilir (yukarıda açıklandığı üzere — test material).
- ZIP, `pass.txt` veya başka indirilmiş artefakt **bu dizine konmaz**;
  sadece rename edilmiş `.pfx` dosyası kalır.
- Bu README'yi güncel tut: yeni Kamu SM test sertifikası kategorisi
  eklendiğinde önce bu tabloya satır ekle, sonra `PfxTestKey` enum'una.
