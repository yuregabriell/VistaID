# Migração dos perfis locais anteriores (v0.2)

A ligação local por identificador de 13 caracteres foi **removida da aplicação Android**. Não confundir os códigos de backups locais com a nova identidade de autenticação do Supabase.

- Um perfil local antigo não se torna uma conta Supabase automaticamente. Os identificadores antigos não são únicos globalmente, portanto não podem ser utilizados como identidade de login do VS.
- Os backups `vistaid-encrypted-backup` da versão antiga contêm verificadores de palavra-passe e não devem ser enviados para o Supabase ou para uma API pública. A nova aplicação não aceita nem exporta esses backups.
- Utilizadores existentes devem criar uma conta Supabase nova e preservar os dados antigos na instalação anterior até existir uma ferramenta explícita de migração de favoritos/histórico, nunca de senhas.
- No VistaStream, migra somente biblioteca, favoritos, preferências e progresso depois de autenticar a conta nova, com revisão de conflitos e consentimento. A reprodução continua possível sem conta.
- Não eliminar instalações ou cópias locais antigas sem garantir um backup do que ainda não foi migrado.

Esta migração precisa de uma rotina própria e de testes; **não está concluída** nesta versão.
