#!/usr/bin/env bash
# Launch Sattune
DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
exec java -jar "$DIR/target/sattune-1.0.0.jar" "$@"
