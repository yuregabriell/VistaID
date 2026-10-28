# Vista ID — ligação local e preservação de memória

Estado: `v0.2.0-local`, **não é autenticação cloud**.

## Integração atual

Os clientes Android suportados fazem `startActivityForResult` com:

```text
action: com.vista.id.action.LINK_LOCAL_PROFILE
package: com.vista.id
```

O Vista ID abre um ecrã próprio para o utilizador inserir ID e palavra-passe.
Após confirmar a autorização, devolve apenas:
- `com.vista.id.profile_id`: ID alfanumérico local
- `com.vista.id.profile_name`: nome de perfil
- `com.vista.id.mode`: `local`

Não devolve a palavra-passe, o hash, a chave de backup ou qualquer token de sessão.
A ligação não é uma prova de uma conta online e não permite consultar dados
de outras aplicações. Cada app mantém a sua base de dados e preferências locais
sob o sandbox normal do Android.

## Contrato do modo sem conta

- A aplicação funciona sem o Vista ID instalado.
- Ligar ou desligar a identidade local nunca apaga histórico, favoritos,
  biblioteca, playlists, fontes, ficheiros ou configurações anteriores.
- Caches efémeras e buffers do player não são dados de conta nem devem ser
  exportados para sincronização cloud. Android TV mantém buffer temporário
  pequeno e não guarda os filmes completos por defeito.
- Histórico de navegação do browser deve ser opt-in se/quando existir cloud.
- Até existir servidor seguro, não usar a identidade local para autorização
  de publicações públicas, playlists sociais ou acesso a dados remotos.

## Próxima fase: serviço cloud

É necessário adicionar HTTPS, registo global de IDs, hashes de password
no servidor, recuperação, rotação de sessões, acesso por dispositivos,
consentimento para importar dados locais, sincronização offline-first com
revisões e resolução explícita de conflitos. A TV deverá poder iniciar
sessão por pairing temporário, sem digitar passwords com o comando.

Até isso estar operacional, a identidade local não representa uma conta
verificada nem realiza backup automático entre dispositivos.
