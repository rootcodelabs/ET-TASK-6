#!/bin/bash
# Export the XTR client certificate to import into Security Server

KEYSTORE_PASS="${KEYSTORE_PASSWORD:-changeit}"
CERT_FILE="/app/ssl/xtr-client.crt"

# Export the certificate from the keystore
keytool -exportcert \
    -alias xtr-server \
    -keystore /app/ssl/keystore.p12 \
    -storepass "${KEYSTORE_PASS}" \
    -storetype PKCS12 \
    -rfc \
    -file "${CERT_FILE}"

echo "Certificate exported to ${CERT_FILE}"
cat "${CERT_FILE}"
