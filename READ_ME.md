# Implementation Steps

## Step 1 - Command Line Interface
- Parse arguments from `main(String[] args)`
- Dispatch commands like:
    - init
    - add <file>
    - commit <message>
    - log
    - status

---

## Step 2 - Repository Initialization
Command:
java MiniGit init

What it should do:
- Create hidden folder `.minigit`
- Create subfolders:
    - objects/
    - commits/
- Create empty index file
- Create HEAD file pointing to main branch

---

## Step 3 - Add Command (Staging)
Command:
java MiniGit add file.txt

What it should do:
- Read file content
- Compute SHA-1 hash
- Save content as object
- Add entry to index file

---

## Step 4 - Commit Command
Command:
java MiniGit commit "message"

What it should do:
- Read staged files
- Create commit object containing:
    - commit id (SHA-1)
    - timestamp
    - message
    - parent commit id
    - tracked file references
- Save commit in commits folder
- Clear index

---

## Step 5 - Log Command
Command:
java MiniGit log

What it should do:
- Read HEAD commit
- Traverse parent commits
- Print commit history