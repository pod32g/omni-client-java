#!/usr/bin/env bash
# Builds omni-client-java with nothing but a JDK (21 or newer):
# compiles the library, packages it as a jar, compiles the tests, and runs them.
#
#   ./build.sh          build + test
#   ./build.sh clean    remove build output
set -euo pipefail
cd "$(dirname "$0")"

if [[ "${1:-}" == "clean" ]]; then
  rm -rf out
  exit 0
fi

rm -rf out

echo "compiling library"
javac --release 21 -Xlint:all -Werror -d out/main $(find src/main/java -name '*.java')
jar --create --file out/omni-client.jar -C out/main .

echo "compiling tests"
javac --release 21 -Xlint:all -d out/test -cp out/main $(find src/test/java -name '*.java')

echo "running tests"
tests=$(find src/test/java -name '*Test.java' | sort | sed -e 's|^src/test/java/||' -e 's|\.java$||' -e 's|/|.|g')
# shellcheck disable=SC2086
java -cp out/main:out/test io.github.pod32g.omni.TestRunner $tests
