#!/bin/bash
# Start Sign API with Test Kurum Configuration
# Usage: ./start-test-kurum.sh [kurum_no]
# Examples:
#   ./start-test-kurum.sh 17         # Kurum 17 - EC384 (default)
#   ./start-test-kurum.sh 2          # Kurum 2 - EC384
#   ./start-test-kurum.sh 3          # Kurum 3 - EC384
#
# 2026-10: RSA test sertifikalari Kamu SM tarafinda yenilenmedigi icin
# kaldirildi; tum kurumlar EC384 setinden secilir.

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DOCKER_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

# Parametreler
KURUM_NO=${1:-17}
CERT_TYPE=${2:-ec384}

# Sertifika mapping
declare -A CERT_MAP
CERT_MAP["17_ec384"]="testkurum17_ec384@test.gov.tr_328829.pfx|328829|testkurum17"
CERT_MAP["2_ec384"]="testkurum02_ec384@test.com.tr_825095.pfx|825095|testkurum02_ec"
CERT_MAP["3_ec384"]="testkurum03_ec384@test.com.tr_540425.pfx|540425|testkurum03_ec"

KEY="${KURUM_NO}_${CERT_TYPE}"
CERT_INFO="${CERT_MAP[$KEY]}"

if [ -z "$CERT_INFO" ]; then
    echo "❌ Hata: Geçersiz kombinasyon: Kurum $KURUM_NO / $CERT_TYPE"
    echo ""
    echo "Kullanım:"
    echo "  ./start-test-kurum.sh [kurum_no] [cert_type]"
    echo ""
    echo "Geçerli kombinasyonlar:"
    echo "  ./start-test-kurum.sh 17         # Kurum 17 - EC384 (default)"
    echo "  ./start-test-kurum.sh 2          # Kurum 2 - EC384"
    echo "  ./start-test-kurum.sh 3          # Kurum 3 - EC384"
    exit 1
fi

IFS='|' read -r CERT_FILE CERT_PIN CERT_ALIAS <<< "$CERT_INFO"

echo "🚀 Starting Sign API with Test Kurum $KURUM_NO ($CERT_TYPE)"
echo "📂 Certificate: $CERT_FILE"
echo ""

cd "$DOCKER_DIR"

# Geçici .env dosyası oluştur
cat > .env.temp << ENVEOF
# Test Kurum $KURUM_NO Configuration ($CERT_TYPE)
PFX_PATH=/app/certs/$CERT_FILE
CERTIFICATE_PIN=$CERT_PIN
CERTIFICATE_ALIAS=$CERT_ALIAS
TUBITAK_USERNAME=$CERT_ALIAS
TUBITAK_PASSWORD=password$KURUM_NO

# Application Configuration
SERVER_PORT=8085
SPRING_PROFILES_ACTIVE=prod

# Monitoring
PROMETHEUS_PORT=9090
GRAFANA_PORT=3000
ALERTMANAGER_PORT=9093
ENVEOF

docker-compose --env-file .env.temp up -d

echo ""
echo "✅ Services started successfully!"
echo "🌐 Sign API:      http://localhost:8085"
echo "📊 Prometheus:    http://localhost:9090"
echo "📈 Grafana:       http://localhost:3000 (admin/admin)"
echo ""
echo "📝 View logs: docker-compose logs -f sign-api"
echo "🛑 Stop: docker-compose down"
echo ""
echo "💡 Farklı kombinasyonlar için:"
echo "   ./start-test-kurum.sh 2        # Kurum 2 - EC384"
echo "   ./start-test-kurum.sh 3        # Kurum 3 - EC384"
