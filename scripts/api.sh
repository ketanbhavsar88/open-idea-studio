#!/usr/bin/env bash
# Run Maven goals for apps/api. Uses the local JDK 25 via ./mvnw when available,
# otherwise a Maven + Temurin 25 container (host networking so it can reach the
# infra/ Postgres on localhost and Docker for Testcontainers).
#
#   scripts/api.sh spring-boot:run   # start the API on :8080
#   scripts/api.sh verify            # build + tests
set -euo pipefail
cd "$(dirname "$0")/../apps/api"

if command -v java >/dev/null 2>&1 && java -version 2>&1 | grep -qE 'version "(2[5-9]|[3-9][0-9])'; then
  exec ./mvnw "$@"
fi

echo "JDK 25 not found; running Maven in Docker." >&2
sock=/var/run/docker.sock
# Forward configuration overrides from the shell (infra/.env is mounted and read too).
env_args=()
while IFS= read -r name; do env_args+=(-e "$name"); done \
  < <(compgen -e | grep -E '^(AI_|GEMINI_|OLLAMA_|POSTGRES_|API_PORT$)' || true)
exec docker run --rm -i --network host \
  -u "$(id -u):$(id -g)" -e HOME=/tmp -e MAVEN_CONFIG=/tmp/.m2 \
  -v "$HOME/.m2:/tmp/.m2" -v "$PWD:/w" -w /w \
  -v "$PWD/../../infra:/infra:ro" "${env_args[@]}" \
  -v "$sock:$sock" --group-add "$(stat -c %g "$sock")" \
  maven:3.9.11-eclipse-temurin-25 \
  mvn -Dmaven.repo.local=/tmp/.m2/repository "$@"
