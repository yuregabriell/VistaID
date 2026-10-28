# Vista ID - Future Cloud API (design only)

The local Android preview does not make remote API requests.

Proposed service:
- `POST /v1/accounts`: server generates a globally unique 13-character Vista ID; validates password, rate limits and stores only salted password verifiers.
- `POST /v1/sessions`: validates credentials and issues scoped sessions.
- `POST /v1/sessions/refresh` and `DELETE /v1/sessions/current`: rotation and revocation.
- `GET/PUT /v1/data/{app}/{document}`: authenticated, versioned data, with optimistic concurrency.
- `POST /v1/recovery`: account recovery method (to be designed and audited).
- `GET/POST /v1/playlists`: social collections, permissions, public/private/unlisted controls.

Supported apps: VistaStream, VistaBrowser, VistaTube, VistaMusic, VistaLauncher.
Users will be able to use each app without an account and later import local data.
Public playlist sharing requires authorization, anti-abuse protection and moderation.
Keep client-side cache and cloud document conflict resolution explicit.

Never treat a local preview profile as a verified cloud identity.
