# Di-source oleh skrip lain. Memaksa JAVA_HOME ke JDK 17 (Gradle 8.7 tidak mendukung JDK 22+).
for d in /usr/lib/jvm/java-17-openjdk-* /usr/lib/jvm/temurin-17-* "$HOME"/.sdkman/candidates/java/17*; do
  if [ -x "$d/bin/java" ]; then export JAVA_HOME="$d"; break; fi
done
if [ -z "${JAVA_HOME:-}" ] || ! "$JAVA_HOME/bin/java" -version 2>&1 | grep -q "\"17\."; then
  echo "JDK 17 tidak ditemukan. Jalankan: sudo apt-get update && sudo apt-get install -y openjdk-17-jdk"
  return 1 2>/dev/null || exit 1
fi
export PATH="$JAVA_HOME/bin:$PATH"
