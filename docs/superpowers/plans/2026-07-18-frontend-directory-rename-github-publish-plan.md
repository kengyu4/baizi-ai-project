# Frontend Directory Rename and GitHub Publish Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rename the nested Vue application directory from `frontend` to `frontend`, keep every build and deployment reference valid, add a root project README, and publish the repository through the configured SSH GitHub remote.

**Architecture:** Move the Vue/Vite project contents up one directory without changing its internal source layout. Update Compose, ignore rules, and all maintained documentation path references. Replace the backend runtime-only Dockerfile with a multi-stage Maven build so Docker no longer depends on a pre-existing local JAR.

**Tech Stack:** Vue 3, Vite, TypeScript, Java 17, Spring Boot, Docker Compose, Git, GitHub SSH.

---

### Task 1: Record the current state and identify path dependencies

**Files:**
- Inspect: `.gitignore`
- Inspect: `xiaorong-teacher-assistant/docker-compose.yml`
- Inspect: `xiaorong-teacher-assistant/Dockerfile`
- Inspect: `dev-docs/**/*.md`

- [x] Verify the old frontend directory contains no name that conflicts with its parent directory.
- [x] Search tracked configuration and documentation files for `frontend/`.
- [x] Record the prior Docker root cause: the runtime image copies `target/*.jar` and cannot build without a host-generated artifact.

### Task 2: Move the frontend and repair configuration paths

**Files:**
- Move: `frontend/*` to `frontend/`
- Modify: `.gitignore`
- Modify: `xiaorong-teacher-assistant/docker-compose.yml`
- Modify: `xiaorong-teacher-assistant/Dockerfile`
- Modify: `dev-docs/**/*.md`

- [x] Move all files including dotfiles from the legacy nested directory into `frontend/`, then remove the empty legacy directory.
- [x] Change all source, Compose, documentation and ignore-rule references to the new `frontend/` path.
- [x] Use a multi-stage Maven Dockerfile so `docker compose build app` builds its JAR in-container.

### Task 3: Add repository-facing documentation and repository hygiene

**Files:**
- Create: `README.md`
- Modify: `.gitignore`
- Modify: `dev-docs/README.md`

- [x] Add a Chinese root README that explains capabilities, architecture, local startup, environment variables, tests, Docker deployment, and security guidance.
- [x] Ignore session/recovery logs, build output, IDE artifacts, runtime environment files and local database volumes while preserving required example configuration and source files.
- [x] Update the documentation index to reflect the `frontend/` directory.

### Task 4: Verify and publish

**Files:**
- Verify: `frontend/package.json`
- Verify: `xiaorong-teacher-assistant/docker-compose.yml`

- [x] Run frontend tests, type checking and production build from `frontend/`.
- [x] Run Maven tests and package from `xiaorong-teacher-assistant/`.
- [x] Validate Compose configuration and Docker image builds.
- [ ] Check the SSH remote and push the verified working tree to `git@github.com:kengyu4/baizi-ai-project.git`.