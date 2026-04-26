# core

This package contains the low-level building blocks that commands delegate to for all repository state management.

## .minigit repository layout

When a repository is initialised, mini-git creates a `.minigit/` directory at the project root. It mirrors the structure of a real `.git/` folder:

```
.minigit/
├── HEAD                      # pointer to the current branch or commit
├── config                    # repository configuration (INI format)
├── ignore                    # patterns for files mini-git should not track
├── index                     # staging area — files queued for the next commit
├── objects/                  # content-addressed store — every blob and commit lives here
└── refs/
    ├── main                  # SHA of the latest commit on the local main branch
    └── remotes/
        └── origin/
            └── master        # SHA of the last known commit on the remote master branch
```

### File details

| Path | Format | Purpose |
|------|--------|---------|
| `HEAD` | text | Symbolic ref (`ref: refs/main`) or bare SHA in detached-HEAD mode. Always points at the current branch. |
| `config` | INI | Stores `[core]` settings and `[remote]` section with the GitHub URL and auth token. |
| `ignore` | glob patterns, one per line | Files matching any pattern are excluded from `status`, `add`, and commits. |
| `index` | `<sha> <path>` lines | The staging area. Updated by `add`; consumed by `commit` to build the tree. |
| `objects/<sha>` | raw bytes | A content-addressed file whose name is its SHA-1 hash. Stores blobs (file snapshots) and commit objects. |
| `refs/main` | SHA text | The tip commit of the local `main` branch. Written by `commit`. |
| `refs/remotes/origin/<branch>` | SHA text | The last known tip of a remote branch. Updated by `fetch`. |

## IndexManager

Manages the staging area stored in `.minigit/index`. The index is a flat text file where each line holds a SHA-1 hash and a file path separated by a space.

- `loadIndex()` — reads the index file and returns a `Map<path, sha>`.
- `writeIndex(map)` — serialises the map back to disk.

## ObjectStore

Manages the content-addressed object store under `.minigit/objects`. Each object is a file whose name is its SHA-1 hash.

- `writeObject(content)` — hashes a string, writes it as an object, returns the SHA.
- `storeBlob(bytes)` — same but for raw byte arrays (used for binary files).
- `computeHash(path)` — returns the SHA-1 of a file on disk without storing it.
- `sha1Hex(bytes)` — pure utility: computes a SHA-1 hex string from bytes.

## RefManager

Manages `HEAD` and branch references under `.minigit/refs/`. Supports both symbolic refs (`ref: refs/heads/main`) and detached-HEAD mode (bare SHA).

- `resolveHead()` — follows the `HEAD` pointer and returns the current commit SHA, or `null` if the branch has no commits yet.
- `updateHead(sha)` — writes `sha` to whatever ref `HEAD` points at (or directly to `HEAD` in detached mode).
- `getRefName()` — returns a human-readable branch name (e.g. `heads/main`) or a short SHA for display purposes.

## RemoteContext

Encapsulates everything needed to talk to a GitHub repository via the GitHub REST API. Loaded once from `.minigit/config` and then passed through commands that need network access.

- `load(configPath)` — reads `[remote]` section (url + token + branch) from config, parses the owner/repo from the GitHub URL, and builds an `HttpClient`.
- Git data API wrappers — `getRef`, `updateRef`, `getCommit`, `createCommit`, `getTree`, `createTree`, `getBlob`, `createBlob` — each maps directly to one GitHub API endpoint, returning the raw JSON response body.