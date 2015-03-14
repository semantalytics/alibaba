#!/bin/sh

# resolve links - $0 may be a softlink
PRG="$0"

while [ -h "$PRG" ] ; do
  ls=`ls -ld "$PRG"`
  link=`expr "$ls" : '.*-> \(.*\)$'`
  if expr "$link" : '/.*' > /dev/null; then
    PRG="$link"
  else
    PRG=`dirname "$PRG"`/"$link"
  fi
done

PRGDIR=$(cd `dirname "$PRG"`;pwd)

if [ -z "$NAME" ] ; then
  NAME=`basename "$PRG" | sed 's/\.sh$//'`
fi

# Only set BASEDIR if not already set
[ -z "$BASEDIR" ] && BASEDIR=`cd "$PRGDIR/.." >/dev/null; pwd`

# Read relative config paths from BASEDIR
cd "$BASEDIR"

TMPDIR="tmp"
lib="lib"
dist="dist"
logging="bin/logging.properties"
JVM_ARGS="-Xms256m -Xmx1024m -XX:MaxPermSize=256m -XX:MinHeapFreeRatio=10 -XX:MaxHeapFreeRatio=20 -XX:+UseParNewGC -XX:-UseConcMarkSweepGC -Xshare:off -XX:+CMSClassUnloadingEnabled -XX:+HeapDumpOnOutOfMemoryError -Dfile.encoding=UTF-8 -Dhttp.keepAlive=true -Dhttp.maxConnections=32 -Dhttps.protocols=TLSv1,TLSv1.1,TLSv1.2 -Djava.awt.headless=true"
CLASSPATH="$dist/$(ls "$dist"|xargs |sed "s; ;:$dist/;g"):$lib/$(ls "$lib"|xargs |sed "s; ;:$lib/;g")"

exec java $JVM_ARGS -Djava.io.tmpdir="$TMPDIR" -Djava.util.logging.config.file="$logging" -XX:OnOutOfMemoryError='kill %p' -cp "$CLASSPATH" org.openrdf.http.object.Server $*
