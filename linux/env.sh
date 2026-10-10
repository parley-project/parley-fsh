# Sourced by run.sh and fsh.sh: sets CLASSPATH and LIB_DIR for running the shell from this project.
# The dependency classpath comes from Maven and is cached in target/ until pom.xml changes.

LIB_DIR=$(cd "$(dirname "$0")" && pwd)
PROJECT_DIR=$(dirname "$LIB_DIR")
cp_file="$PROJECT_DIR/target/runtime-classpath.txt"

if [ ! -d "$PROJECT_DIR/target/classes" ]; then
	(cd "$PROJECT_DIR" && mvn -q compile) || exit 1
fi
if [ ! -f "$cp_file" ] || [ "$PROJECT_DIR/pom.xml" -nt "$cp_file" ]; then
	(cd "$PROJECT_DIR" && mvn -q dependency:build-classpath -Dmdep.includeScope=runtime -Dmdep.outputFile="$cp_file") || exit 1
fi

export CLASSPATH="$PROJECT_DIR/target/classes:$(cat "$cp_file")"
