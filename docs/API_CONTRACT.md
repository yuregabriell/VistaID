# Vista ID ↔ VistaStream · contrato de autenticação v1

Backend: Supabase Auth + Edge Function `/functions/v1/vista-auth`. Só `app_id="vs"` está ativo. Cada aplicativo adicional terá a sua própria tabela e permissão.

## Autenticação clássica no VistaStream

- **E-mail + palavra-passe**: Supabase `POST /auth/v1/token?grant_type=password` com `{ "email": "...", "password": "..." }`.
- **Nome de utilizador + palavra-passe**: Edge Function com `{ "action":"username_password", "username":"nome", "password":"..." }`. Devolve sessão Supabase `access_token`, `refresh_token`, `expires_at`.
- Exibir opção **Continuar sem conta**. Não criar a sessão local de outro app com base só num nome/UID; validar JWT com Supabase.

## Código alfanumérico rotativo (mostrado no Vista ID)

1. Vista ID autenticado envia `{ "action":"code_now" }` com JWT; recebe `{ "code":"ABCDEFGH", "expires_at": 1790000000 }`. O código muda em períodos de 30 segundos. Só utilizar servidor para o segredo HMAC.
2. VS envia `{ "action":"code_login", "username":"nome", "code":"ABCDEFGH" }` sem JWT. Sucesso: `{ "token_hash":"...", "type":"email" }`. Há rate limit e prevenção de reutilização.
3. VS envia `POST /auth/v1/verify` com `{ "token_hash":"...", "type":"email" }`, recebe tokens específicos desse dispositivo e guarda-os via Android Keystore.
4. A solicitação deve ser executada apenas quando o utilizador confirmar a tentativa de login no VS. Não guardar o código, não anexá-lo a logs.

## QR Code da televisão (sem levar senha ao VS)

1. VS inicia pedido `{ "action":"pair_create", "app_id":"vs" }` sem JWT. Recebe:
   ```json
   {"request_id":"UUID", "poll_secret":"SEGREDO_ALEATORIO",
    "verification_code":"ABC234", "qr_uri":"vistaid://pair?request=UUID",
    "expires_in":120}
   ```
2. VS mostra o QR contendo apenas `qr_uri`, **mais o código visual de 6 caracteres**. Mantém `poll_secret` só em memória, nunca no QR nem em logs. O pedido expira em 2 minutos.
3. Vista ID lê QR, consulta `{ "action":"pair_details","request_id":"UUID" }` e mostra o código; o utilizador confirma que a televisão mostra o mesmo.
4. Vista ID autenticado envia `{ "action":"pair_approve", "request_id":"UUID" }` com bearer JWT.
5. VS consulta `{ "action":"pair_redeem","request_id":"UUID","poll_secret":"SEGREDO_ALEATORIO" }` a cada 4–5 s, até aprovação ou expiração. Antes da aprovação recebe `{"status":"pending"}`.
6. Quando aprovado, recebe `{ "status":"approved","token_hash":"...","type":"email" }` **uma única vez**, e troca `token_hash` no `/auth/v1/verify` para criar sessão própria na TV.

As funções `code_login`, `username_password`, `pair_create`, `pair_details`, `pair_redeem` são públicas e aplicam validações/rate limit. `pair_approve`, `code_now`, `profile_update`, `vs_reset` exigem um JWT autenticado válido.

## Dados do VistaStream

Leitura: `GET /rest/v1/vs_user_data?select=settings,favorites,progress,version,updated_at,connected_at&limit=1` com `Authorization: Bearer <JWT>` e `apikey: <PUBLISHABLE_KEY>`.

Escrita pelo VS: `PATCH /rest/v1/vs_user_data?user_id=eq.<OWN_USER_ID>` com `settings`, `favorites` ou `progress`. RLS restringe ao dono e GRANT impede alterar `connected_at`, `user_id` ou `version`. Recomendações: atualizar só colunas modificadas, debounce de progresso (15–30s), manter offline queue, merge por episódio e verificar `version` para evitar conflitos entre TV/telemóvel. A migração atual só garante versionamento incremental; a lógica de merge deve ser implementada no VS antes de disponibilizar multi-dispositivo.

O único JSON do utilizador mantém dados pequenos e tipados. Não transferir ficheiros de vídeo nem P2P para Supabase. 
