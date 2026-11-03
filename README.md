# Vista ID · Supabase Identity (preview)

Uma identidade central para as aplicações Vista. **VistaStream é a primeira aplicação integrada no contrato da API.** Não há IDs aleatórios locais nem simulação de autenticação: as sessões são emitidas pelo Supabase Auth.

## Funcionalidades implementadas no Vista ID Android

- Registar com e-mail, nome de utilizador único e palavra-passe; **sem envio de códigos por e-mail** quando `Confirm email` estiver desativado no projeto.
- Entrar com e-mail + palavra-passe diretamente pelo Supabase Auth, ou nome de utilizador + palavra-passe pelo endpoint protegido `vista-auth`.
- Guardar access/refresh tokens em armazenamento cifrado por Android Keystore; renovar tokens automaticamente e permitir logout.
- Ver VistaStream no painel, consultar dados cloud, alterar nome apresentado, mudar senha e limpar dados sincronizados mediante confirmação.
- Mostrar código temporário de **8 caracteres alfanuméricos**, alterado a cada **30 segundos**, obtido por HTTPS do backend autenticado.
- Ler QR via câmara e aprovar pedidos de ligação do VistaStream, confirmando o código de 6 caracteres mostrado na TV.
- O servidor disponibiliza `pair_create`, `pair_redeem` e `code_login` para integração do VistaStream. **O código consumidor no VistaStream ainda precisa ser ligado à UI da app.**

## Ligar ao projeto Supabase

1. Cria um projeto Supabase (Free serve para testes de perfis e sincronização; NÃO para alojar vídeos).
2. Em **Authentication → Sign In / Providers → Email**, ativa a autenticação por e-mail e **desativa Confirm email**. Isto elimina o envio de códigos, **mas torna os e-mails não verificados**. Não atives recuperação por e-mail sem rever previamente este modelo.
3. Aplica `supabase/migrations/202611030001_vista_identity.sql` no SQL Editor, uma única vez. Requer permissões de proprietário. A criação de conta gera automaticamente `vid_profiles`, `vs_user_data` e um segredo privado de autenticador.
4. Implanta a Edge Function: `supabase functions deploy vista-auth --no-verify-jwt`. As ações privilegiadas validam o JWT dentro da função. Configura `SUPABASE_SERVICE_ROLE_KEY`, `SUPABASE_URL` e `SUPABASE_ANON_KEY` somente no servidor (variáveis padrão do Supabase); **nunca coloques a service-role key num APK/GitHub público**.
5. O projeto **Vista Cloud** (`hleekwlptmisfvamgext`) já está configurado como padrão no build com a sua URL e a **publishable key pública**, sem qualquer chave de serviço. Para alterar de projeto, define `VISTA_SUPABASE_URL` e `VISTA_SUPABASE_PUBLISHABLE_KEY` nas configurações do build; o GitHub Actions aceita-os como secrets opcionais.
6. Compila através do workflow `android-release.yml`. O projeto Vista Cloud já recebeu a migração SQL e a Edge Function `vista-auth` em 3 de novembro de 2026. **Ainda é necessário desativar `Confirm email` no painel Auth** para que novos registos dispensem e-mail; o conector atual não suporta modificar essa opção.

Comandos CLI locais opcionais:

```sh
supabase login
supabase link --project-ref SEU_PROJECT_REF
supabase db push
supabase functions deploy vista-auth --no-verify-jwt
```

## Dados na nuvem e política de armazenamento

- `auth.users`: conta e credenciais administradas pelo Supabase Auth.
- `vid_profiles`: uma linha por utilizador (nome global; a RLS permite ler apenas o próprio perfil).
- `vs_user_data`: uma linha por utilizador **apenas para VistaStream**; `settings` (máx 16 KiB), `favorites` (32 KiB), `progress` (64 KiB), `version`, `updated_at` e `connected_at`.
- `vid_auth_secrets`, `vid_device_pairings`, `vid_auth_attempts`: dados internos protegidos sem leitura direta por clientes. A função executa com service role e valida permissão, TTL e limites.
- JSONB evita tabelas com milhares de registos pequenos; PostgreSQL comprime os valores grandes via TOAST quando aplicável. **Não é seguro sincronizar um único JSONB enorme em cada segundo**: guardar progresso em intervalos e só enviar alterações. Se VS crescer, separar progresso/histórico em tabelas indexadas.

Nada de filmes, segmentos P2P, magnets de terceiros, tokens de outros serviços ou credenciais no Storage. O Vista ID não distribui vídeos.

## Segurança e limites importantes

- É necessária ligação à internet para os códigos rotativos (são gerados no servidor e expostos apenas a sessões autenticadas). Um código só pode ser consumido uma vez por período e existe limitação de tentativas por identificador/IP.
- QR apresenta `request_id` público; o segredo de polling fica **só na TV**. O Vista ID exige confirmação visual do código e consentimento.
- O fluxo QR/código cria `token_hash` de uso único via Supabase Auth; a TV troca-o em `/auth/v1/verify` para receber a própria sessão. Nunca partilhar refresh tokens entre TV e telefone.
- A alteração de senha não é um mecanismo de recuperação. Sem e-mail confirmado, **contas que percam as credenciais podem ficar irrecuperáveis**. Definir recuperação segura antes de abrir o registo a utilizadores finais.
- A integração no VistaStream requer UI e cliente próprios, seguindo `docs/API_CONTRACT.md`.
- Esta branch é uma **prévia de implementação**: a migração e a função não foram implantadas num projeto Supabase nesta conversa, e as integrações Android/TV ainda carecem de teste end-to-end.

## Build

Java 17, Android SDK 35, Gradle 8.10.2. Não colocar segredos administrativos no código.

```sh
gradle --no-daemon :app:assembleDebug
```

Licença: MIT.
