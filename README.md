# Vista ID — Ecossistema Vista

**v0.2.0-local: pré-visualização Android com ligação local autorizada, sem conta online.**

Este projeto prepara a identidade opcional dos apps VistaStream, VistaBrowser, VistaTube, VistaMusic e VistaLauncher.

## Funcionalidades desta versão
- Criação de perfis locais com Vista ID alfanumérico de 13 caracteres
- Palavra-passe de 8 ou mais caracteres com maiúscula, minúscula, número e símbolo
- Login e logout neste dispositivo
- Verificadores PBKDF2-HMAC-SHA256 com salt (sem guardar passwords em texto simples)
- Exportação/importação de perfis em JSON encriptado com AES-256-GCM
- Ligação voluntária a apps Vista instaladas no mesmo dispositivo: confirmação por código e palavra-passe no Vista ID, sem partilhar a palavra-passe. O app solicitante recebe só nome e ID local.\n- App offline; **sem serviço cloud ativo, sem registo global dos códigos e sem autenticação remota ou sincronização entre dispositivos**

**Atenção:** o APK é uma versão de depuração, e a assinatura poderá mudar em builds futuras; guarda o backup antes de substituir ou desinstalar a aplicação.

## Build
Java 17, Gradle 8.10.2, Android SDK 35:
```
gradle :app:assembleDebug
```
O workflow GitHub Actions compila o APK e publica uma pré-release em `v0.2.0-local` na branch `main`.

Código para experimentação. Não utilizar como serviço de autenticação de produção sem revisão de segurança, API central de autenticação, política de recuperação e testes de sincronização.

Licença: MIT.
