#!/bin/bash

# List of algorithms and applications
ALGORITHMS=("MOSA" "MISH" "MISHMOSA")
APPLICATIONS=("catwatch" "proxyprint" "languagetool" "features-service" "scout-api")

# Docker image name and other arguments
DOCKER_IMG=$1
NAMING_FORMAT=$2
TIME_BUDGET=$3

# Loop through each algorithm and application
for method in "${ALGORITHMS[@]}"; do
    for app in "${APPLICATIONS[@]}"; do
        echo "Running ${method} on ${app}"

        # Create a tmux session with the name of the method
        if [ -n "$NAMING_FORMAT" ]; then
            session_name="${method}_${app}_session_${NAMING_FORMAT}"
            docker_cmd="docker run -it --name ${method}_${app}_${NAMING_FORMAT}_container $DOCKER_IMG"
        else
            session_name="${method}_${app}_session"
            docker_cmd="docker run -it --name ${method}_${app}_container $DOCKER_IMG"
        fi

        # Start a new tmux session
        tmux new-session -d -s "$session_name"

        # Start Docker container inside the tmux session
        tmux send-keys -t "$session_name" "$docker_cmd" C-m

        # Run the command inside the Docker container
        command_inside_docker="cd /home/EvoMaster && ./mish/scripts/run_experiment.sh $method $TIME_BUDGET with_500_faults $app"
        tmux send-keys -t "$session_name" "$command_inside_docker" C-m
    done
done

echo "All tmux sessions started and Docker containers are running."
