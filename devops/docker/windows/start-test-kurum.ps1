# Start Sign API with Test Kurum Configuration
# Usage: .\start-test-kurum.ps1 [kurum_no]
# Examples:
#   .\start-test-kurum.ps1 17         # Kurum 17 - EC384 (default)
#   .\start-test-kurum.ps1 2          # Kurum 2 - EC384
#   .\start-test-kurum.ps1 3          # Kurum 3 - EC384
#
# 2026-10: RSA test sertifikalari Kamu SM tarafinda yenilenmedigi icin
# kaldirildi; tum kurumlar EC384 setinden secilir.

param(
    [int]$KurumNo = 17,
    [string]$CertType = "ec384"
)

$ErrorActionPreference = "Stop"

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$DockerDir = Split-Path -Parent $ScriptDir

# Sertifika mapping
$CertMap = @{
    "17_ec384" = @{
        File = "testkurum17_ec384@test.gov.tr_328829.pfx"
        Pin = "328829"
        Alias = "testkurum17"
    }
    "2_ec384" = @{
        File = "testkurum02_ec384@test.com.tr_825095.pfx"
        Pin = "825095"
        Alias = "testkurum02_ec"
    }
    "3_ec384" = @{
        File = "testkurum03_ec384@test.com.tr_540425.pfx"
        Pin = "540425"
        Alias = "testkurum03_ec"
    }
}

$Key = "${KurumNo}_${CertType}"
$CertInfo = $CertMap[$Key]

if (-not $CertInfo) {
    Write-Host "❌ Hata: Geçersiz kombinasyon: Kurum $KurumNo / $CertType" -ForegroundColor Red
    Write-Host ""
    Write-Host "Kullanım:"
    Write-Host "  .\start-test-kurum.ps1 [kurum_no] [cert_type]"
    Write-Host ""
    Write-Host "Geçerli kombinasyonlar:"
    Write-Host "  .\start-test-kurum.ps1 17         # Kurum 17 - EC384 (default)"
    Write-Host "  .\start-test-kurum.ps1 2          # Kurum 2 - EC384"
    Write-Host "  .\start-test-kurum.ps1 3          # Kurum 3 - EC384"
    exit 1
}

Write-Host "🚀 Starting Sign API with Test Kurum $KurumNo ($CertType)" -ForegroundColor Green
Write-Host "📂 Certificate: $($CertInfo.File)" -ForegroundColor Cyan
Write-Host ""

Set-Location $DockerDir

# Geçici .env dosyası oluştur
$EnvContent = @"
# Test Kurum $KurumNo Configuration ($CertType)
PFX_PATH=/app/certs/$($CertInfo.File)
CERTIFICATE_PIN=$($CertInfo.Pin)
CERTIFICATE_ALIAS=$($CertInfo.Alias)
TUBITAK_USERNAME=$($CertInfo.Alias)
TUBITAK_PASSWORD=password$KurumNo

# Application Configuration
SERVER_PORT=8085
SPRING_PROFILES_ACTIVE=prod

# Monitoring
PROMETHEUS_PORT=9090
GRAFANA_PORT=3000
ALERTMANAGER_PORT=9093
"@

$EnvContent | Out-File -FilePath ".env.temp" -Encoding ASCII

docker-compose --env-file .env.temp up -d

Write-Host ""
Write-Host "✅ Services started successfully!" -ForegroundColor Green
Write-Host "🌐 Sign API:      http://localhost:8085" -ForegroundColor Cyan
Write-Host "📊 Prometheus:    http://localhost:9090" -ForegroundColor Cyan
Write-Host "📈 Grafana:       http://localhost:3000 (admin/admin)" -ForegroundColor Cyan
Write-Host ""
Write-Host "📝 View logs: docker-compose logs -f sign-api" -ForegroundColor Yellow
Write-Host "🛑 Stop: docker-compose down" -ForegroundColor Yellow
Write-Host ""
Write-Host "💡 Farklı kombinasyonlar için:" -ForegroundColor Yellow
Write-Host "   .\start-test-kurum.ps1 2        # Kurum 2 - EC384"
Write-Host "   .\start-test-kurum.ps1 3        # Kurum 3 - EC384"
