# GolemCore Brain

GolemCore Brain is a self-hosted knowledge workspace for teams that want a clean web UI without giving up plain Markdown files.

It keeps docs, runbooks, notes, and project knowledge on disk as readable `.md` files, then adds the application layer teams need day to day: editing, navigation, search, assets, permissions, and optional LLM-powered workflows.

## Demo

[![GolemCore Brain demo](docs/assets/demo-poster.jpg)](docs/assets/demo.mp4)

## Why Brain

Most internal knowledge tools make teams choose between a good product experience and portable content. Brain is built for the middle ground: people work in a focused wiki, while the source of truth stays simple enough to back up, diff, move, and rebuild.

Use it for:

- team handbooks, runbooks, and operating procedures
- product and engineering documentation
- customer, project, or team-specific knowledge spaces
- Markdown notes that need a real browser-based workspace
- searchable context for internal tools and LLM workflows

## Core Capabilities

- **Markdown source of truth** - pages are stored as local Markdown files instead of being locked inside a primary database.
- **Organized spaces** - keep teams, projects, customers, or environments separated in one running instance.
- **Fast editing flow** - create, edit, rename, move, copy, reorder, convert, and delete pages from the UI.
- **Assets where the docs live** - upload images and files, manage them from the page, and insert them into Markdown.
- **Search that can be rebuilt** - index title and body content from the files, with optional hybrid vector search when embeddings are configured.
- **Roles and access control** - use admin, editor, and viewer roles, public read-only mode, and API keys for programmatic access.
- **LLM-ready workflows** - configure model providers once, then use them for embedding indexing, space chat, and space-scoped Dynamic APIs.
- **Agent-native editing** - section-level `PATCH` with optimistic concurrency, atomic multi-page transactions, YAML frontmatter (`tags`, `summary`), a wiki link-graph summary, and automatic read-access tracking expose the Brain wiki as an LLM memory surface.
- **Simple self-hosting** - run a Spring Boot application with the frontend bundled into the same artifact.

## How Content Is Stored

Brain stores wiki content under `data/wiki` by default. Markdown files are the source of truth; search indexes and generated metadata are derived data.

```text
data/wiki/
  spaces/
    <space-id>/
      index.md
      guides/
        index.md
        runbook.md
      .order.json
  .indexes/
```

A section is a directory with an `index.md`. A page is a standalone `.md` file. `.order.json` stores the order users set in the UI.

## Run Locally

Build the backend and bundled frontend:

```bash
./mvnw package
java -jar target/golemcore-brain-*.jar
```

The app starts on `http://localhost:8080`.

To run the published container image with Docker Compose:

```bash
export BRAIN_ADMIN_PASSWORD='replace-this-password'
export BRAIN_JWT_SECRET="$(openssl rand -hex 32)"
docker compose up -d
```

The example [docker-compose.yml](docker-compose.yml) stores wiki data in a named Docker volume and exposes the app on `http://localhost:8080`.

For frontend-only development:

```bash
cd frontend
npm install
npm run dev
```

The Vite dev server proxies `/api` to the backend on `http://localhost:8080`.

## Configuration

Set the initial admin account before the first run:

```bash
BRAIN_ADMIN_USERNAME=admin \
BRAIN_ADMIN_PASSWORD=change-me \
java -jar target/golemcore-brain-*.jar
```

For production, enable the `prod` profile and provide a strong JWT secret:

```bash
SPRING_PROFILES_ACTIVE=prod \
BRAIN_ADMIN_USERNAME=admin \
BRAIN_ADMIN_PASSWORD=change-me \
BRAIN_JWT_SECRET=change-me-change-me-change-me-change-me \
java -jar target/golemcore-brain-*.jar
```

Common settings:

- `BRAIN_STORAGE_ROOT` - where wiki files and derived indexes are stored
- `BRAIN_SITE_TITLE` - product name shown in the UI
- `BRAIN_PUBLIC_ACCESS` - enables read-only public access when set to `true`
- `BRAIN_SESSION_TTL_SECONDS` - session lifetime
- `BRAIN_DEFAULT_SPACE_SLUG` and `BRAIN_DEFAULT_SPACE_NAME` - initial space identity

## Public Integration API

Use a session cookie from `POST /api/auth/login` or an API key with `Authorization: Bearer <token>`. Viewer access can read space content and search. Editor access can mutate pages, imports, and assets. Space admin access manages space-scoped automation. Global admin access manages spaces, global API keys, and reindex operations.

| Method | Path | Access | Purpose |
| --- | --- | --- | --- |
| `GET` | `/api/config` | Public | Read site configuration and default space metadata. |
| `GET` | `/api/auth/config` | Public | Read authentication mode and current user summary when a session is present. |
| `POST` | `/api/auth/login` | Public | Create a session cookie from username/email and password. |
| `POST` | `/api/auth/logout` | Authenticated | Revoke the current session cookie. |
| `POST` | `/api/auth/password` | Authenticated | Change the current user's password and clear the old session. |
| `GET` | `/api/auth/me` | Authenticated | Read the current authenticated user state. |
| `GET` | `/api/spaces` | Viewer | List spaces visible to the caller. |
| `POST` | `/api/spaces` | Admin | Create a space. Body: `slug`, optional `name`. |
| `DELETE` | `/api/spaces/{slug}` | Global admin | Delete a space registration; filesystem content is retained for manual purge. |
| `GET` | `/api/spaces/{slug}/tree` | Viewer | Read the space page tree. |
| `GET` | `/api/spaces/{slug}/page?path=...` | Viewer | Read a page by path. |
| `GET` | `/api/spaces/{slug}/pages/by-path?path=...` | Viewer | Read a page by path; stable alias for integrations. |
| `GET` | `/api/spaces/{slug}/pages/lookup?path=...` | Viewer | Resolve path segments and existence metadata. |
| `POST` | `/api/spaces/{slug}/pages` | Editor | Create a page. Body: `parentPath`, `title`, optional `slug`, `content`, `kind`, `tags`, `summary`. `tags`/`summary` are stored as YAML frontmatter. |
| `POST` | `/api/spaces/{slug}/pages/ensure` | Editor | Create or return a page at an exact path. Body: `path`, `targetTitle`. |
| `PUT` | `/api/spaces/{slug}/page?path=...` | Editor | Update page title, slug, content, revision guard, and optional `tags`/`summary` frontmatter. |
| `PATCH` | `/api/spaces/{slug}/page?path=...` | Editor | Section-level patch. Body: `operation` (`APPEND`, `PREPEND`, `REPLACE_SECTION`), `expectedRevision`, `content`, optional `heading` for `REPLACE_SECTION`. Returns 409 on stale revision. |
| `DELETE` | `/api/spaces/{slug}/page?path=...` | Editor | Delete a page or section. |
| `POST` | `/api/spaces/{slug}/page/move?path=...` | Editor | Move or rename a page. |
| `POST` | `/api/spaces/{slug}/page/copy?path=...` | Editor | Copy a page into another section. |
| `POST` | `/api/spaces/{slug}/page/convert?path=...` | Editor | Convert a page between page and section forms. |
| `PUT` | `/api/spaces/{slug}/section/sort?path=...` | Editor | Persist child order for a section. |
| `GET` | `/api/spaces/{slug}/page/history?path=...` | Viewer | List stored page versions. |
| `GET` | `/api/spaces/{slug}/page/history/version?path=...&versionId=...` | Viewer | Read one stored page version. |
| `POST` | `/api/spaces/{slug}/page/history/restore?path=...&versionId=...` | Editor | Restore a stored page version. |
| `POST` | `/api/spaces/{slug}/search` | Viewer | Search pages. Body: `query`, `mode` (`auto`, `fts`, `hybrid`), optional `limit`. Response `mode` is the effective retrieval path actually used (`fts`, `hybrid`, `fts-fallback`, `empty-query`), not just the requested preference. `semanticReady=true` means vector retrieval participated in ranking for that response. |
| `GET` | `/api/spaces/{slug}/search/status` | Viewer | Read full-text and embedding index readiness. |
| `POST` | `/api/spaces/{slug}/import/markdown/plan` | Editor | Upload a Markdown archive and preview import actions. Multipart: `file`, optional `options`. |
| `POST` | `/api/spaces/{slug}/import/markdown/apply` | Editor | Upload a Markdown archive and apply selected import actions. |
| `GET` | `/api/spaces/{slug}/links?path=...` | Viewer | Validate links from a page. |
| `GET` | `/api/spaces/{slug}/wiki/graph` | Viewer | Summarize wiki link graph: orphan pages (not linked from anywhere) and dangling outgoing links across the space. |
| `POST` | `/api/spaces/{slug}/wiki/tx` | Editor | Apply an ordered batch of wiki operations atomically. Body: `operations[]` where each item has `op` (`CREATE`, `UPDATE`, `DELETE`) and the fields required by that operation (including `expectedRevision` for `UPDATE`). Batch is rejected with HTTP 409 if any `expectedRevision` is stale. |
| `GET` | `/api/spaces/{slug}/wiki/access/top?limit=N` | Viewer | List the most-read pages for the space. Read counts and `lastAccessedAt` are recorded automatically on each page read. |
| `GET` | `/api/spaces/{slug}/pages/assets?path=...` | Editor | List assets attached to a page. |
| `POST` | `/api/spaces/{slug}/pages/assets?path=...` | Editor | Upload an asset for a page. Multipart: `file`. |
| `PUT` | `/api/spaces/{slug}/pages/assets/rename?path=...` | Editor | Rename a page asset. Body: `oldName`, `newName`. |
| `DELETE` | `/api/spaces/{slug}/pages/assets?path=...&name=...` | Editor | Delete a page asset. |
| `GET` | `/api/spaces/{slug}/assets?path=...&name=...` | Viewer | Download or render a page asset. |
| `POST` | `/api/spaces/{slug}/chat` | Viewer | Ask the space chat model. Body: `message`, optional `modelConfigId`, `summary`, `turnCount`, `history`. |
| `GET` | `/api/spaces/{slug}/dynamic-apis` | Space admin | List configured Dynamic APIs in a space. |
| `POST` | `/api/spaces/{slug}/dynamic-apis` | Space admin | Create a Dynamic API configuration. |
| `PUT` | `/api/spaces/{slug}/dynamic-apis/{apiId}` | Space admin | Update a Dynamic API configuration. |
| `DELETE` | `/api/spaces/{slug}/dynamic-apis/{apiId}` | Space admin | Delete a Dynamic API configuration. |
| `POST` | `/api/spaces/{slug}/dynamic-apis/{apiSlug}/run` | Viewer | Execute an enabled Dynamic API with a JSON payload. |
| `GET` | `/api/api-keys` | Global admin | List global API keys. |
| `POST` | `/api/api-keys` | Global admin | Issue a global API key. Body: `name`, optional `roles`, `expiresAt`. |
| `GET` | `/api/spaces/{slug}/api-keys` | Space admin | List API keys scoped to one space. |
| `POST` | `/api/spaces/{slug}/api-keys` | Space admin | Issue a space-scoped API key. |
| `DELETE` | `/api/api-keys/{keyId}` | Global or space admin | Revoke a global key or a key scoped to an administered space. |
| `POST` | `/api/admin/spaces/{slug}/reindex` | Global admin | Queue a full search reindex for one space. |
| `POST` | `/api/admin/spaces/reindex` | Global admin | Queue a full search reindex for all spaces. |

## Checks

```bash
./mvnw test
cd frontend && npm run lint && npm run build
```

## License

GolemCore Brain is licensed under the [Apache License 2.0](LICENSE).
