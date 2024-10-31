from tqdm import tqdm
import pandas as pd
import re
from drain3 import TemplateMiner
from drain3.template_miner import TemplateMinerConfig
from drain3.file_persistence import FilePersistence
from datetime import datetime
import argparse as ap


# REGEX for detecting timestamp formats.
SHORT_TS_REG = re.compile(r'\d{2}:\d{2}:\d{2}.\d{3}')
LONG_TS_REG = re.compile(r'\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}.\d{3}')
LONG_TS_INFO_FIRST_REG = re.compile(r' \[\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}.\d{3}\]')
LONG_TIMESTAMP_WITH_TZ = re.compile(r'\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2} \+[0-9]{4}')
PERSISTENCE = FilePersistence(f'./mish/scripts/drain3_state.bin')
CONFIG = TemplateMinerConfig()
CONFIG.load(f"./mish/scripts/drain3.ini")
CONFIG.profiling_enabled = False
DRAIN_MINER = TemplateMiner(PERSISTENCE, CONFIG)

def check_for_timestamp_format(line):
    """
    Function for detecting timestamp format in log line.

    Args:
        line (str): log line.

    Returns:
        str: 'short' if short timestamp format is detected, 'long' if long timestamp format is detected, None if no timestamp format is detected.
    """
    if LONG_TS_INFO_FIRST_REG.search(line):
        # for the case that the log line starts with log type and then timestamp e.g. INFO [2024-01-23 12:34:56.789] ....
        return 'long_info_first'
    elif LONG_TIMESTAMP_WITH_TZ.search(line):
        # for the case that the log line starts with long timestamp and timezone e.g. 2024-01-23 12:34:56 +0000 INFO
        return 'long_tz'
    elif LONG_TS_REG.search(line):
        # for the case that the log line starts with long timestamp e.g. 2024-01-23 12:34:56.789 [XXXX] INFO ...
        return 'long'
    elif SHORT_TS_REG.search(line):
        # for the case that the log line starts with short timestamp e.g. 12:34:56.789 [XXXX] INFO ...
        return 'short'
    else:
        return None

def get_log_message_content(line):
    """
    Function to the log message content from log line.

    Args:
        line (str): log line.

    Returns:
        int: the log message content
    """
    parts = line.split(' ')
    print(parts)
    for i in range(0, len(parts)):
        #count number of "." in the string
        if parts[i].count('.') >= 2:
            return ' '.join(parts[i:])
        elif 'WARN' in parts[i] or 'INFO' in parts[i] or 'ERROR' in parts[i]:
                return ' '.join(parts[i + 1:])
        else:
            continue


def construct_ts(line):
    """
    Function to extract and construct the timestamp of a log line.

    Args:
        line (str): log line.

    Returns:
        str: timestamp of the log line

    """
    if check_for_timestamp_format(line) == 'short':
        return line.split(' ')[1]
    elif check_for_timestamp_format(line) == 'long':
        splitted = line.split(' ')
        return splitted[1] + ' ' + splitted[2]
    elif check_for_timestamp_format(line) == 'long_info_first':
        return line.split(' [')[1].split(']')[0].replace(',', '.')
    elif check_for_timestamp_format(line) == 'long_tz':
        splitted = line.split(' ')
        return splitted[1] + ' ' + splitted[2] + ' ' + splitted[3]
    else:
        raise ValueError('Cannot construct timestamp for log line as timestamp format is not recognized.')

def parse_log_file(log_file, read_from=None):
    """
    Function to parse the log file.

    Args:
        log_file (str): path to the log file.
        read_from (str): the line where we should to read from.

    Returns:
        list: list of timestamps of the log lines.
        list: list of log processed log messages.
    """
    ts = []
    log_messages = []
    with open(log_file, 'r') as f:
        logs = f.readlines()
        starting_point = 0
        if read_from is None:
            for i in range(len(logs)):
#                 print(logs[i])
                if len(logs[i]) > 0 and start_of_application(logs[i]):
#                     print(start_of_application(logs[i]))
                    starting_point = i + 1
#                     print(starting_point)
                    break
        else:
            starting_point = int(read_from)

        for log in tqdm(logs[starting_point:]):
            if len(log) > 0 and (check_for_timestamp_format(log) != None) and "SUT: " in log:
                ts.append(construct_ts(log))
                log_messages.append(get_log_message_content(log).split('\n')[0])
            else:
                continue

    return ts, log_messages

def check_start_based_on_keyword_set(log_line, keywords_set):
    """
    Check whether a log line is the start of the application based on given keyword set.
    """
    line_is_start = True
    for keyword in keywords_set:
        if keyword not in log_line:
            line_is_start = False
            break

    return line_is_start


def start_of_application(log_line):
    """
    Function to determine if the log line is the start of the application using multiple keyword sets.

    Args:
        log_line (str): log line.

    Returns:
        bool: True if log line is the start of the application, False otherwise.
    """

    keywords_sets = [
        ['Started ', ' in ', ' seconds (JVM running for ', 'INFO'],
        ['INFO', 'Started @', 'ms'],
        ["+0000",  "Server started"]
    ]

    is_start = False
    for keywords_set in keywords_sets:
        if check_start_based_on_keyword_set(log_line, keywords_set):
            return True
    
    return is_start



def group_logs_by_ts(logs_ts, logs_messages, execution_stats):
    """
    Function to group logs by their corresponding test case star and end timestamps.

    Args:
        test_ts (list): list of timestamps of the test cases.
        logs_ts (list): list of timestamps of the log lines.

    Returns:
        pd.DataFrame: grouped logs.
    """
    test_to_log_mapping = {}
    test_start_stop_time_mapping = {}
    # test_to_test_actions_mapping = {}
    # test_to_test_actions_size_mapping = {}

    for execution in tqdm(execution_stats):
        start_time = execution[1].split(' ')[1]
        end_time = execution[2].split(' ')[1].split('\n')[0]
        test_id = execution[0]
        test_start_stop_time_mapping[test_id] = (start_time, end_time)

        # if test_id not in test_to_test_actions_mapping:
        #     test_to_test_actions_size_mapping[test_id] = execution[3]
        #     test_to_test_actions_mapping[test_id] = execution[4].split('\n')[0]

        if test_id not in test_to_log_mapping:
            test_to_log_mapping[test_id] = []

        for i in range(len(logs_ts)):
            # convert ts to datetime object
            start_time_dt = datetime.strptime(start_time, '%H:%M:%S.%f')
            end_time_dt = datetime.strptime(end_time, '%H:%M:%S.%f')

            if check_for_timestamp_format(logs_ts[i]) == 'short':
                log_ts_dt = datetime.strptime(logs_ts[i], '%H:%M:%S.%f')
            elif check_for_timestamp_format(logs_ts[i]) == 'long_tz':
                log_ts_dt = datetime.strptime(logs_ts[i].split(' ')[1] + '.000', '%H:%M:%S.%f')
            else:
                log_ts_dt = datetime.strptime(logs_ts[i].split(' ')[1], '%H:%M:%S.%f')
                # change to same timezone (this is the case with scout-api)
                hours_diff = end_time_dt.hour - log_ts_dt.hour
                # increment the hours by the difference
                # print(f"Hours diff: {hours_diff}")
                # print(f"Log ts: {log_ts_dt}")
                log_ts_dt = log_ts_dt.replace(hour=log_ts_dt.hour + hours_diff)
                # print(f"Log ts after: {log_ts_dt}")
               


            if start_time_dt <= log_ts_dt <= end_time_dt:
                test_to_log_mapping[test_id].append((logs_ts[i], logs_messages[i]))
            else:
                continue


    test_logs_mapping_list = []
    for test_id in test_to_log_mapping:
        if len(test_to_log_mapping[test_id]) == 0:
            test_logs_mapping_list.append(
                [test_id,
                # test_to_test_actions_mapping[test_id],
                # test_to_test_actions_size_mapping[test_id],
                test_start_stop_time_mapping[test_id][0],
                test_start_stop_time_mapping[test_id][1],
                'None',
                'None'
                ]
            )
        else:
            for mapping in test_to_log_mapping[test_id]:
                test_logs_mapping_list.append(
                    [test_id,
                    # test_to_test_actions_mapping[test_id],
                    # test_to_test_actions_size_mapping[test_id],
                    test_start_stop_time_mapping[test_id][0],
                    test_start_stop_time_mapping[test_id][1],
                    mapping[0],
                    mapping[1]
                    ]
                )

    return pd.DataFrame(test_logs_mapping_list, columns=['Test ID', 'Test Start', 'Test Stop', 'Log TimeStamp', 'Log Message'])

def generate_traces_for_test_cases(grouped_logs):
    test_id_to_trace_mapping = {}

    test_ids = grouped_logs['Test ID'].values.tolist()
    log_messages = grouped_logs['Log Message'].values.tolist()

    for i in tqdm(range(len(test_ids))):
        log_message = log_messages[i]
        test_id = test_ids[i]
        if test_id not in test_id_to_trace_mapping:
            test_id_to_trace_mapping[test_id] = []

        clusterId = DRAIN_MINER.match(log_message).cluster_id
        test_id_to_trace_mapping[test_id].append(clusterId)

    test_id_to_trace_mapping = dict(sorted(test_id_to_trace_mapping.items()))
    traces = []
    for test_id in test_id_to_trace_mapping:
        traces.append('0 ' + str(len(test_id_to_trace_mapping[test_id])) + ' ' + ' '.join([str(x) for x in test_id_to_trace_mapping[test_id]]) + '\n')

    return traces


def cluster_logs(grouped_logs):
    """
    Function to learn the log templates using Drain3.

    Args:
        grouped_logs (pd.DataFrame): logs grouped by their corresponding test cases.
    """
    log_messages = grouped_logs['Log Message'].values
    # Learn log templates
    for log in tqdm(log_messages):
        DRAIN_MINER.add_log_message(log)

    # Save intermediate state of the miner.
    DRAIN_MINER.save_state("intermediate state")



def parse_execution_stats(execution_stats_file):
    """
    Parse the execution stats file generated by EvoMaster.

    Args:
        execution_info_file (str): path to the execution info file.

    Returns:
        list: list of execution info
    """
    with open(execution_stats_file, 'r') as f:
        lines = f.readlines()
        execution_stats = []
        for line in lines:
            execution_stats.append(line.split(','))

        return execution_stats

def main():
    arg_parser = ap.ArgumentParser(description='Process logs produced by test cases and convert them to traces for FlexFringe.')
    arg_parser.add_argument('--log_file', type=str, help='Path to the log file.')
    arg_parser.add_argument('--execution_stats_file', type=str, help='Path to the execution stats file.')
    arg_parser.add_argument('--read_from', type=str, help='Where to start reading from in the log file.', default=None)
    arg_parser.add_argument('--output_file', type=str, help='Path to the output containing the traces.', default='traces.txt')
    args = arg_parser.parse_args()

    ts, logs =  parse_log_file(args.log_file, args.read_from)
    execution_stats = parse_execution_stats(args.execution_stats_file)
    grouped_logs = group_logs_by_ts(ts, logs, execution_stats)
    cluster_logs(grouped_logs)
    traces = generate_traces_for_test_cases(grouped_logs)
    print("Writing traces to file")
    with open(args.output_file, 'w') as f:
        f.writelines(traces)

if __name__ == "__main__":
    main()
