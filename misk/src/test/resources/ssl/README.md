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
