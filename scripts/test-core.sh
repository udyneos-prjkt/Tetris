#!/usr/bin/env bash
# Kompilasi dan jalankan unit test core di host (hitungan detik, tanpa Android).
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
mkdir -p "$ROOT/build"
g++ -std=c++17 -Wall -Wextra -Werror -O1 -fsanitize=address,undefined \
    -I"$ROOT/app/src/main/cpp" \
    "$ROOT/tests/core_tests.cpp" "$ROOT/app/src/main/cpp/tetris_core.cpp" \
    -o "$ROOT/build/core_tests"
"$ROOT/build/core_tests"
