# Auction App

## Do not commit to `main`

This repository uses a PR + CI workflow.

- `main` is protected and should only receive changes through Pull Requests.
- Create a feature branch for each task.
- Push your branch, open a PR to `main`, and wait for CI to pass.
- Merge only after review/approval.

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

### Safe workflow

```bash
git checkout -b feat/<topic>
git add .
git commit -m "feat: <short description>"
git push -u origin feat/<topic>
```

Then open a Pull Request to `main` and merge only when all checks are green.

### Merge commit strategy

- Please **Squash and merge** with a clean Conventional Commit title for readable changelogs.

- Delete the branch after merging to keep the repository clean.

[![RRoqr-YQCOC.png](https://i.postimg.cc/yY5TLbzP/RRoqr-YQCOC.png)](https://postimg.cc/jLHN5v2w)

[![kji-TNGLd-Iw.png](https://i.postimg.cc/RVjc7Jc2/kji-TNGLd-Iw.png)](https://postimg.cc/hf1zgjZ0)

## Requirements:
- Java 11+
- Maven

## Setup guide
1. Clone the project:

- ssh (if you know what you are doing):
`git clone git@github.com:qu0cb1nh/AuctionApp.git`

- or https (if you use github desktop):
`git clone https://github.com/qu0cb1nh/AuctionApp.git`

2. Running in Intellij

- Open the project in IntelliJ

- Open Project Structure (top left), set Project SDK to Java 11

![img](https://i.ibb.co/JjsBkgL0/idea64-Rx-Dt-JXDh6-B.png)

- Start the Application

![img](https://i.ibb.co/wNW19zCj/idea64-0e2-C5-B8h-Ja.png)

Press run Server to start the server, then run Client to open the JavaFX application