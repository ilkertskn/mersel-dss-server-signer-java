#!/bin/sh
# Sansec HSM dev container entrypoint.
# En yeni (mtime) mersel jar dosyasini /app bind-mount icinden secer ve calistirir.
# JAVA_OPTS compose tarafindan verilir (heap + JDWP :5005 agent).
set -e

JAR=$(ls -t /app/mersel-dss-signer-api-*.jar 2>/dev/null | grep -v "\.original$" | head -n1)

if [ -z "$JAR" ]; then
  echo "HATA: /app icinde mersel-dss-signer-api-*.jar bulunamadi."
  echo "      Once host uzerinde jar uretin:  ./mvnw -DskipTests package"
  echo "      (compose target dizinini /app icine read-only mount eder.)"
  exit 1
fi

echo "PKCS11_LIBRARY = ${PKCS11_LIBRARY}"
echo "LD_LIBRARY_PATH = ${LD_LIBRARY_PATH}"
echo "SPRING_PROFILES_ACTIVE = ${SPRING_PROFILES_ACTIVE}"
echo "Baslatiliyor: ${JAR}"

exec java ${JAVA_OPTS} -Djava.security.egd=file:/dev/./urandom -jar "${JAR}"
