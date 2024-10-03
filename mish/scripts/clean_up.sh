#!/bin/sh

# First remove all files within the "execution_stats" folder
rm -rf $PWD/mish/execution_stats/*

# Then we move onto the "logs" folder
rm -rf $PWD/mish/logs/msa_logs.txt

# Then we move onto the "fitness" folder
rm -rf $PWD/mish/fitness/*

# Then we remove the state stored by drain (optional)
rm -rf $PWD/mish/scripts/drain3_state.bin

# Then we clean up the "traces" folder
rm -rf $PWD/mish/traces/*

# Finally, we terminate the running FlexFringe Daemon if it is still alive
PID=$(pgrep -f "flexfringe --ini")

# Check if a PID was found
if [ -z "$PID" ]; then
  echo "No process found with the command 'flexfringe --ini'."
else
  # Kill the process
  kill -9 $PID
  echo "Process with PID $PID has been killed."
fi

# Kill the SUT if one is already running.
PID=$(pgrep -f "jar 40100 12345 .")
if [ -z "$PID" ]; then
    echo "No process found with the command 'jar 40100 12345 .'"
else
    # Kill the process
    kill -9 $PID
    echo "Process with PID $PID has been killed."
fi

# Kill the SUT if one is already running.
PID=$(pgrep -f "Devomaster.javaagent.external.port")
if [ -z "$PID" ]; then
    echo "No process found with the command 'Devomaster.javaagent.external.port'."
else
    # Kill the process
    kill -9 $PID
    echo "Process with PID $PID has been killed."
fi