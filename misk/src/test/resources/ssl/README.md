These keystores and certificates are created specifically to enable testing against 
servers listening on 127.0.0.1. Specifically, they require an IP subject-alt-name to
allow verification againt 127.0.0.1 

To recreate the server keystore and certificate, run:

```bash
keytool -genkeypair -alias 1 -keyalg RSA -keystore server_keystore.jceks \
  -storetype jceks -storepass serverpassword -keypass serverpassword \
  -keysize 2048 -validity 1800 \
  -ext SAN=IP:127.0.0.1 \
  -dname "CN=misk-server,OU=Server,O=Misk,L=San Francisco,ST=CA,C=US" 

 keytool -exportcert -rfc -keystore server_keystore.jceks \
  -alias 1 -storepass serverpassword \
  -file server_cert.pem
```

To recreate the client keystore and certificate, run:

```bash
keytool -genkeypair -alias 1 -keyalg RSA -keystore client_keystore.jceks \
  -storetype jceks -storepass clientpassword -keypass clientpassword \
  -keysize 2048 -validity 1800 \
  -ext SAN=IP:127.0.0.1 \
  -dname "CN=misk-client,OU=Client,O=Misk,L=San Francisco,ST=CA,C=US" 

 keytool -exportcert -rfc -keystore client_keystore.jceks \
  -alias 1 -storepass clientpassword \
  -file client_cert.pem
``` 
 
Dump the private keys in `client_keystore.jceks` and `server_keystore.jceks` using a tool such as
 KeyStore Explorer into `client.pem` and `server.pem` then run:

```bash
 openssl rsa -in client.pem -out client_rsa.pem
 
 cat server.pem server_cert.pem > server_cert_key_combo.pem
 cat client.pem client_cert.pem > client_cert_key_combo.pem
 cat client_rsa.pem client_cert.pem > client_rsa_cert_key_combo.pem
```

`keystore.jks` created from the `client_rsa_cert_key_combo.pem` using the following commands

```bash
openssl pkcs12 -export -in misk/src/test/resources/ssl/client_rsa_cert_key_combo.pem -name combined-key-cert -out combined.p12
keytool -importkeystore -srckeystore combined.p12 -srcstoretype pkcs12 -destkeystore keystore.jks -deststoretype JKS
```

`truststore.jks` created from `client_cert.pem` using the following command

```bash
keytool -import -file misk/src/test/resources/ssl/client_cert.pem -alias ca -keystore truststore.jks -noprompt -storepass changeit -storetype JKS
```
