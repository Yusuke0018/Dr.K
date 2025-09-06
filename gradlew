#!/usr/bin/env sh

# SPDX-License-Identifier: Apache-2.0

##############################################################################
# Gradle start up script for UN*X
##############################################################################

DIRNAME=$(dirname "$0")
APP_BASE_NAME=$(basename "$0")
APP_HOME=$(cd "$DIRNAME"; pwd)

# Add default JVM options here. You can also use JAVA_OPTS and GRADLE_OPTS to pass JVM options to this script.
DEFAULT_JVM_OPTS="-Xmx64m -Xms64m"

# Use the maximum available, or set MAX_FD != -1 to use that value.
MAX_FD="maximum"

warn () {
    echo "$*"
}

die () {
    echo
    echo "$*"
    echo
    exit 1
}

CLASS_PATH=$APP_HOME/gradle/wrapper/gradle-wrapper.jar

# Determine the Java command to use to start the JVM.
if [ -n "$JAVA_HOME" ] ; then
    if [ -x "$JAVA_HOME/jre/sh/java" ] ; then
        # IBM's JDK on AIX uses strange locations for the executables
        JAVACMD="$JAVA_HOME/jre/sh/java"
    else
        JAVACMD="$JAVA_HOME/bin/java"
    fi
    if [ ! -x "$JAVACMD" ] ; then
        die "ERROR: JAVA_HOME is set to an invalid directory: $JAVA_HOME\n\nPlease set the JAVA_HOME variable in your environment to match the\nlocation of your Java installation."
    fi
else
    JAVACMD="java"
    which java >/dev/null 2>&1 || die "ERROR: JAVA_HOME is not set and no 'java' command could be found in your PATH.\n\nPlease set the JAVA_HOME variable in your environment to match the\nlocation of your Java installation."
fi

# Increase the maximum file descriptors if we can.
if [ "$MAX_FD" != "maximum" ] ; then
    MAX_FD_LIMIT=$MAX_FD
    if [ "$cygwin" = "false" ] && [ "$darwin" = "false" ] ; then
        ulimit -n $MAX_FD_LIMIT
    fi
else
    if [ "$cygwin" = "false" ] && [ "$darwin" = "false" ] ; then
        MAX_FD_LIMIT=$(ulimit -H -n)
        if [ $? -eq 0 ] ; then
            ulimit -n $MAX_FD_LIMIT
        fi
    fi
fi

exec "$JAVACMD" $DEFAULT_JVM_OPTS $JAVA_OPTS $GRADLE_OPTS \
  -classpath "$CLASS_PATH" org.gradle.wrapper.GradleWrapperMain "$@"

