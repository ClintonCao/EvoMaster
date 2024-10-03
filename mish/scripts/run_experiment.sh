#!/bin/sh
ALGORITHM=$1 # MOSA, MISH, RANDOM, MISHMOSA
TIME_BUDGET=$2 # time budget in minutes
OUTPUT_NAME_FORMAT=$3 # output name format
APPLICATION=$4 # application name
#ulimit -v unlimited
#ulimit -c unlimited
#ulimit -s unlimited
#ulimit -t unlimited
#ulimit -n 65535
export JAVA_HOME=$(/usr/libexec/java_home -v 1.8)
export JAVA_HOME_8=$(/usr/libexec/java_home -v 1.8)
mkdir -p $PWD/mish/logs
mkdir -p $PWD/mish/execution_stats
mkdir -p $PWD/mish/traces
mkdir -p $PWD/mish/fitness


if [ -z "$ALGORITHM" ]; then
  echo "No algorithm specified. Please provide an algorithm to run."
  exit 1
fi

if [ -z "$TIME_BUDGET" ]; then
  echo "No time budget specified. Please provide a time budget in minutes."
  exit 1
fi

if [ -z "$OUTPUT_NAME_FORMAT" ]; then
  echo "No output name format specified. Please provide an output name format."
  exit 1
fi

# APPLICATION_JAR_FOLDER=$HOME/Documents/Git/EMB/jdk_8_maven/emb_jars/
APPLICATION_JAR=$PWD/$APPLICATION-evomaster-runner.jar

if [ ! -f "$APPLICATION_JAR" ]; then
  echo "The application jar file does not exist. Please check the path."
  exit 1
fi

if [ "$ALGORITHM" = "MISH" ] || [ "$ALGORITHM" = "MISHMOSA" ]; then
    # Clean up temp file from previous runs.
    ./mish/scripts/clean_up.sh
fi


for i in {4..4}; do

    if [ "$ALGORITHM" = "MISH" ] || [ "$ALGORITHM" = "MISHMOSA" ]; then
        # Clean the previous temp files
        ./mish/scripts/clean_up.sh

        # Then start the SUT
        {
          {
            java -jar -Devomaster.instrumentation.jar.path=evomaster-agent.jar $APPLICATION_JAR 40100 12345 . 2>&1
          } | tee mish/logs/msa_logs.txt
        } > /dev/null &

    else
        # Start the SUT
        ./mish/scripts/clean_up.sh
        java -jar -Devomaster.instrumentation.jar.path=evomaster-agent.jar $APPLICATION_JAR 40100 12345 . > /dev/null 2>&1 &
    fi

    sleep 5 # Wait for the SUT to start

    # Then start EvoMaster
    {
        {
            java -jar $PWD/core/target/evomaster.jar --maxTime "$TIME_BUDGET"m --algorithm $ALGORITHM --outputFolder "test_evo/"$APPLICATION"_"$OUTPUT_NAME_FORMAT"_"$ALGORITHM"_$i"
        } 2>&1 | ts '[%Y-%m-%d %H:%M:%S]';
    } | tee $PWD/"$APPLICATION"_"$OUTPUT_NAME_FORMAT"_EM_logs_"$ALGORITHM"_$i.txt

    # Kill the SUT
    PID=$(pgrep -f "$APPLICATION_JAR 40100 12345 .")
    if [ -z "$PID" ]; then
        echo "No process found with the command '$APPLICATION_JAR 40100 12345 .'."
    else
        # Kill the process
        kill -9 $PID
        echo "Process with PID $PID has been killed."
    fi

    sleep 5 # Wait for things to be completely cleaned up

done
