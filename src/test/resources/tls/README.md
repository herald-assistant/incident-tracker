# Fikcyjny certyfikat do testów HTTPS

`localhost.p12` zawiera wyłącznie testowy, samopodpisany certyfikat i klucz.
Hasło: `test-only`. SAN obejmuje DNS localhost, bez adresu IP; pozwala to
osobno sprawdzić niezaufany certyfikat oraz błędną nazwę hosta przy lokalnym
truststore testu. Nie zawiera danych ani kluczy żadnego środowiska.

Wygenerowany lokalnym keytool: RSA 2048, CN=localhost, OU=Tests, O=Example,
C=PL, SAN=dns:localhost, start 2020-01-01, ważność 36500 dni, PKCS12.
Testy nie instalują certyfikatu w JVM ani systemie.
