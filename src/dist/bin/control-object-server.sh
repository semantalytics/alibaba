#!/bin/sh

base="$(dirname "${0}")/.."
lib="$base/lib"
dist="$base/dist"
logging="$(dirname "${0}")/logging.properties"
JVM_ARGS="-Dfile.encoding=UTF-8 -Djava.awt.headless=true"
CLASSPATH="$dist/$(ls "$dist"|xargs |sed "s; ;:$dist/;g"):$lib/$(ls "$lib"|xargs |sed "s; ;:$lib/;g")"

exec java $JVM_ARGS -cp "$CLASSPATH" org.openrdf.http.object.ServerControl $*
