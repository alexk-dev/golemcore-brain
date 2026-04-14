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
- **Search that can be rebuilt** - index title and body content from the files, with optional semantic search when embeddings are configured.
- **Roles and access control** - use admin, editor, and viewer roles, public read-only mode, and API keys for programmatic access.
- **LLM-ready workflows** - configure model providers once, then use them for semantic indexing, space chat, and space-scoped Dynamic APIs.
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

## Checks

```bash
./mvnw test
cd frontend && npm run lint && npm run build
```
