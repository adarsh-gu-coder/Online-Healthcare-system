#!/bin/sh
# Compile and start the CarePlus backend (needs JDK 11+ : javac and java on PATH)
cd "$(dirname "$0")"
mkdir -p out
javac -d out src/com/careplus/*.java || exit 1
java -cp out com.careplus.Main "${1:-8080}" ../frontend
