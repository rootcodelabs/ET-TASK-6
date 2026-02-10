#!/bin/sh

if [ ! -f "/app/ssl/keystore.p12" ]; then
    keytool -genkeypair -alias xtr-server -keyalg RSA -keysize 4096 \
            -keystore /app/ssl/keystore.p12 -storetype PKCS12 \
            -dname "CN=XTR, OU=Development, O=Buerokratt, L=Tallinn, ST=Harjumaa, C=EE" \
            -ext "SAN=dns:localhost,dns:xtr,ip:127.0.0.1" \
            -storepass "$KEY_PASS" -validity 365

    # Create a truststore containing the server cert (for clients to trust us)
    keytool -export -alias xtr-server -keystore /app/ssl/keystore.p12 -storepass "$KEY_PASS" -file /app/ssl/server.crt
    keytool -import -alias xtr-server -file /app/ssl/server.crt -keystore /app/ssl/truststore.p12 -storepass "$KEY_PASS" -noprompt
fi

java -cp /app:/app/lib/* ee.buerokratt.xtr.XTRApplication