# GolemCore Brain

GolemCore Brain is a self-hosted knowledge workspace for teams that want wiki content to stay simple, portable, and AI-ready.

It stores pages as markdown files on disk, adds a modern web UI for editing and navigation, and builds search indexes that can be regenerated from the source files. The goal is to keep the knowledge base easy to operate, easy to back up, and useful both for humans and automation.

## What it is for

Use Brain when you need a lightweight internal wiki for:

- runbooks, operating procedures, and team notes
- product or engineering documentation that should remain readable as plain markdown
- isolated workspaces for different teams, projects, or customers
- searchable knowledge that can also power LLM-backed workflows
- simple self-hosting without adopting a full CMS or primary content database

## Core capabilities

- **Markdown-first wiki** — pages and sections are stored as local files, so content remains portable and transparent.
- **Organized spaces** — split knowledge into isolated workspaces while running one server.
- **Fast editing flow** — create, edit, rename, move, copy, reorder, and delete pages from the UI.
- **Rich content support** — attach images and files to pages and insert them directly into markdown.
- **Search** — find pages by title/body text, with optional semantic search through configured embeddings.
- **Access control** — use admin/editor/viewer roles, public read-only mode, and API keys for programmatic access.
- **LLM integrations** — configure providers and models once, then use them for semantic indexing and space-scoped Dynamic APIs.
- **Simple deployment** — run as a Spring Boot application with the frontend bundled into the same artifact.

## How it stores data

Brain keeps source content in the configured storage directory, `data/wiki` by default. Markdown files are the source of truth; indexes are derived data and can be rebuilt.

At a high level:

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

A section is a directory with an `index.md`; a page is a standalone `.md` file. `.order.json` stores the user-defined order of sibling pages/sections. Search index files are implementation details and do not replace the markdown source.

## Run locally

Build the backend and bundled frontend:

```bash
./mvnw package
java -jar target/golemcore-brain-*.jar
```

The app starts on `http://localhost:8080` by default.

For frontend-only development:

```bash
cd frontend
npm install
npm run dev
```

The Vite dev server proxies `/api` to the backend on `http://localhost:8080`.

## Configuration essentials

The default profile can read the initial admin account from environment variables:

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

Common optional settings:

- `BRAIN_STORAGE_ROOT` — where wiki files and derived indexes are stored
- `BRAIN_SITE_TITLE` — product name shown in the UI
- `BRAIN_ADMIN_EMAIL` — initial admin email
- `BRAIN_SESSION_TTL_SECONDS` — session lifetime
- `BRAIN_DEFAULT_SPACE_SLUG` and `BRAIN_DEFAULT_SPACE_NAME` — initial space identity

## Checks

```bash
./mvnw test
cd frontend && npm run lint && npm run build
```

## Releases

Merges to `main` run the release workflow. Releasable commits create a tagged GitHub Release with the packaged jar and publish container images to `ghcr.io`.

Use the latest image when you want the standard container deployment path:

```bash
docker pull ghcr.io/<owner>/golemcore-brain:latest
```
