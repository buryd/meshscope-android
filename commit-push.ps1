$ErrorActionPreference = "Stop"
Set-Location "C:\Users\dell\MeshScopeAndroid"

git init -b main
git add .
git status
git commit -m "Add MeshScope Android app for MeshCore live traffic maps."
gh repo create buryd/meshscope-android --public --source=. --remote=origin --push
git status -sb
git remote -v
Write-Host "REMOTE_OK"
