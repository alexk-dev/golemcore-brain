# GolemCore Brain

GolemCore Brain is a lightweight wiki application backed by markdown files on disk.
It combines a modern React frontend with a Spring Boot 4 backend and keeps content portable without a database.

## Features

- hierarchical wiki tree with sections and pages
- markdown viewing and editing in a split workflow
- filesystem-backed storage using `.md` files and `.order.json`
- create, rename, move, copy, delete, and reorder content
- live search across page titles and bodies
- dark mode and responsive layout
- SPA frontend bundled into the Spring Boot application

## Stack

### Frontend
- React 19
- TypeScript
- Vite
- Tailwind CSS 4
- Radix UI primitives
- React Markdown

### Backend
- Java 25
- Maven
- Spring Boot 4.0.5
- Lombok
- plain markdown files on local storage

## Running locally

### Backend + frontend bundle

```bash
./mvnw package
java -jar target/golemcore-brain-0.1.0-SNAPSHOT.jar
```

The app starts on `http://localhost:8080` by default.

## Configuration

The default profile can read the initial admin account from environment variables:

```bash
BRAIN_ADMIN_USERNAME=admin \
BRAIN_ADMIN_PASSWORD=change-me \
java -jar target/golemcore-brain-0.1.0-SNAPSHOT.jar
```

For production, enable the `prod` profile and provide the required secret values:

```bash
SPRING_PROFILES_ACTIVE=prod \
BRAIN_ADMIN_USERNAME=admin \
BRAIN_ADMIN_PASSWORD=change-me \
BRAIN_JWT_SECRET=change-me-change-me-change-me-change-me \
java -jar target/golemcore-brain-0.1.0-SNAPSHOT.jar
```

Optional production settings include `BRAIN_ADMIN_EMAIL`, `BRAIN_STORAGE_ROOT`, `BRAIN_SITE_TITLE`, `BRAIN_SESSION_TTL_SECONDS`, `BRAIN_JWT_ISSUER`, `BRAIN_DEFAULT_SPACE_SLUG`, and `BRAIN_DEFAULT_SPACE_NAME`.

### Frontend dev mode

```bash
cd frontend
npm install
npm run dev
```

The Vite dev server proxies `/api` to `http://localhost:8080`.

## Tests and checks

```bash
./mvnw test
cd frontend && npm run lint && npm run build
```

## Storage layout

By default the application stores content in `data/wiki`.

Example structure:

```text
data/wiki/
  index.md
  .order.json
  guides/
    index.md
    writing-notes.md
  product/
    index.md
    roadmap.md
```

Rules:
- a section is a directory with `index.md`
- a page is a standalone `.md` file
- sibling ordering is stored in `.order.json`

## API overview

- `GET /api/config`
- `GET /api/tree`
- `GET /api/page?path=...`
- `POST /api/pages`
- `PUT /api/page?path=...`
- `DELETE /api/page?path=...`
- `POST /api/page/move?path=...`
- `POST /api/page/copy?path=...`
- `PUT /api/section/sort?path=...`
- `GET /api/search?q=...`
