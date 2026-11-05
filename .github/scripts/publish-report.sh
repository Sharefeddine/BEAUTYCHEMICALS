#!/usr/bin/env bash
# Publishes build logs / test reports to the `ci-reports` branch so that the
# build result can be inspected without access to the Actions log endpoint.
set -u

NAME="${1:?report name required}"
LOG_FILE="${2:?log file required}"
shift 2

WORK="$(mktemp -d)"
REPO_URL="https://x-access-token:${TOKEN}@github.com/${GITHUB_REPOSITORY}.git"

if ! git clone --depth 1 --branch ci-reports "$REPO_URL" "$WORK" >/dev/null 2>&1; then
  git init -q "$WORK"
  git -C "$WORK" remote add origin "$REPO_URL"
  git -C "$WORK" checkout -q -b ci-reports
fi

DEST="$WORK/$NAME"
mkdir -p "$DEST"
[ -f "$LOG_FILE" ] && cp "$LOG_FILE" "$DEST/build.log"
for item in "$@"; do
  if [ -d "$item" ]; then
    cp -r "$item" "$DEST/"
  elif [ -f "$item" ]; then
    cp "$item" "$DEST/"
  fi
done

git -C "$WORK" config user.name "ci-bot"
git -C "$WORK" config user.email "ci-bot@users.noreply.github.com"
git -C "$WORK" add -A
git -C "$WORK" commit -q -m "ci: ${NAME} report (${GITHUB_RUN_ID:-local})" || echo "nothing to publish"

for attempt in 1 2 3; do
  if git -C "$WORK" push -q origin ci-reports 2>/dev/null; then
    echo "report published: ${NAME}"
    exit 0
  fi
  git -C "$WORK" pull --rebase -q origin ci-reports 2>/dev/null || true
  sleep 2
done
echo "warning: could not publish report ${NAME}"
exit 0
