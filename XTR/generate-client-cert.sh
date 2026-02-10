#!/bin/sh

if [ -z "$KEY_PASS" ]; then
  echo "Error: KEY_PASS environment variable is not set."
  exit 1
fi

CLIENT_NAME=${1:-client}

echo "Generating client certificate for: $CLIENT_NAME"

# 1. Generate Client Key Pair
keytool -genkeypair -alias $CLIENT_NAME -keyalg RSA -keysize 4096 \
        -keystore /app/ssl/client-$CLIENT_NAME.p12 -storetype PKCS12 \
        -dname "CN=$CLIENT_NAME, OU=Client, O=Buerokratt, C=EE" \
        -storepass "$KEY_PASS" -validity 365

# 2. Export Client Certificate
keytool -export -alias $CLIENT_NAME -keystore /app/ssl/client-$CLIENT_NAME.p12 -storepass "$KEY_PASS" -file /app/ssl/$CLIENT_NAME.crt

# 3. Import Client Cert into Server's Truststore (so Server trusts Client)
echo "Importing client cert into server truststore..."
keytool -import -alias $CLIENT_NAME -file /app/ssl/$CLIENT_NAME.crt -keystore /app/ssl/truststore.p12 -storepass "$KEY_PASS" -noprompt

echo "Done. /app/ssl/client-$CLIENT_NAME.p12 created."
