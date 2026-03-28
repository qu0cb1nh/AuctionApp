# Auction App

[![CI](https://github.com/qu0cb1nh/AuctionApp/actions/workflows/maven.yml/badge.svg?branch=main)](https://github.com/qu0cb1nh/AuctionApp/actions/workflows/maven.yml)

## Requirements:
- Java 11+
- Maven
- Scene Builder

## Do not commit to `main`

This repository uses a PR + CI workflow.

- `main` is protected and should only receive changes through Pull Requests
- Workflow status: https://github.com/qu0cb1nh/AuctionApp/actions/workflows/maven.yml
- Create a feature branch for each task.
- Push your branch, open a PR to `main`, and wait for CI to pass.
- Merge only after all CI checks are green (note: currently you can merge to main yourself, but please be careful)
- Auto release: Push a `v*` tag (e.g., `v1.0.0`) to create a GitHub release with Client/Server JARs. Requires non-SNAPSHOT version and tag on `main` history.

### For more details: https://docs.github.com/en/pull-requests/collaborating-with-pull-requests/proposing-changes-to-your-work-with-pull-requests

### Recommended branch names

- `feat/<short-topic>`
- `fix/<short-topic>`
- `ci/<short-topic>`
- `docs/<short-topic>`

### Commit message format (Conventional Commits)

Use English commit messages:

- `feat: add basic auction session model`
- `fix: handle invalid bid lower than current price`
- `ci: update maven workflow release guards`
- `docs: document PR workflow`
- for more details: https://www.conventionalcommits.org/en/v1.0.0/

### Safe workflow for commiting and pushing changes

- Enter these commands to create a new branch, commit, and push:
```bash
git switch -c branch_name
git status
git add .
git commit -m "<type>: <short description>"
git push -u origin branch_name
```

- Then open a Pull Request to `main` through the Github website and merge only when all checks are green.

### Merge commit strategy

- Please **Squash and merge** with a clean Conventional Commit title for readable changelogs.

- Delete the branch after merging to keep the repository clean.

![RRoqr-YQCOC.png](https://i.postimg.cc/yY5TLbzP/RRoqr-YQCOC.png)

![kji-TNGLd-Iw.png](https://i.postimg.cc/RVjc7Jc2/kji-TNGLd-Iw.png)

### After merging on GitHub: sync local `main` and delete merged branches

If you merge a PR on GitHub (web UI), your local `main` does not update automatically.
Remote `origin/main` may be ahead by one merge/squash commit (or more).

Run this before starting a new task to merge and delete the old branch:

```bash
git switch main
git fetch origin --prune
git pull --ff-only origin main
git branch -D old-branch-name
```

Quick check:

```bash
git status
git branch -vv
```

## Setup guide
1. Clone the project:

- ssh (recommended, requires a configured SSH key on GitHub):
`git clone git@github.com:qu0cb1nh/AuctionApp.git`

- Tutorial for setting up a ssh key (please use Git Bash or any Unix terminal):
- https://www.theodinproject.com/lessons/foundations-setting-up-git

- Or https (not recommended for workflow, but works without SSH setup):
`git clone https://github.com/qu0cb1nh/AuctionApp.git`

2. Running in Intellij

- Open the project in IntelliJ

- Open Project Structure (top left), set Project SDK to Java 11

![img](https://i.ibb.co/JjsBkgL0/idea64-Rx-Dt-JXDh6-B.png)

- Start the Application

![img](https://i.ibb.co/wNW19zCj/idea64-0e2-C5-B8h-Ja.png)

Press run Server to start the server, then run Client to open the JavaFX application