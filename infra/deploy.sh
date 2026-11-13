#!/bin/sh
# Deploys one version of the images published on ghcr.io on this server (see docs/deployment.md):
#
#   ./deploy.sh sha-1a2b3c4     # the image of one commit of main (what the deploy workflow passes)
#   ./deploy.sh 1.2.0           # a release
#
# Run from anywhere: it works in its own folder, next to the Compose files and infra/.env.
# Pulls every image first (nothing is stopped if one is missing), then recreates the containers whose
# image changed and waits until each one is healthy. If one never gets healthy, the version that was running
# before is started again and the script fails: the server is left with a working stack.
set -eu

cd "$(dirname "$0")"

tag=${1:?usage: ./deploy.sh <image tag>, e.g. sha-1a2b3c4 or 1.2.0}

if [ ! -f .env ]; then
  echo "infra/.env is missing: copy .env.example to .env and set at least JWT_SECRET and POSTGRES_PASSWORD" >&2
  exit 1
fi

# The tag running now, written by the last successful deploy
previous=$(cat .deployed-tag 2>/dev/null || true)

compose() {
  docker compose -f docker-compose.yml -f docker-compose.ghcr.yml -f docker-compose.prod.yml --profile app "$@"
}

start() {
  # Wins over an IMAGE_TAG written in .env
  export IMAGE_TAG="$1"
  compose pull --quiet &&
    compose up --detach --wait --wait-timeout 300 --remove-orphans
}

echo "Deploying $tag (running now: ${previous:-nothing})"

if ! start "$tag"; then
  echo "$tag did not start" >&2
  compose ps >&2
  if [ -n "$previous" ] && [ "$previous" != "$tag" ]; then
    echo "Starting $previous again" >&2
    start "$previous" || echo "$previous did not start either" >&2
  fi
  exit 1
fi

echo "$tag" > .deployed-tag

# Images no container uses and older than a week: the disk does not fill up with every version of main,
# and the last ones stay here for a quick roll back
docker image prune --all --force --filter "until=168h" > /dev/null

compose ps
echo "Deployed $tag"
