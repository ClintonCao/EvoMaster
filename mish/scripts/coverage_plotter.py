import matplotlib.pyplot as plt
import numpy as np
import seaborn as sns
import pandas as pd
from tqdm import tqdm

def plot_coverage_over_time(coverages, title, filename):
    _, ax = plt.subplots()
    ax.plot(coverages)
    ax.set(xlabel='Time (s)', ylabel='Coverage (%)',
           title=title)
    ax.grid()
    plt.savefig(filename)
    plt.show()

def interp_coverage_values(base_coverage, budget, covered_targets):
    interp_coverage = np.interp(base_coverage, budget, covered_targets)
    return interp_coverage

def confidence_interval_with_clipping(data):
    ci = sns.algorithms.bootstrap(data, func=np.mean, n_boot=1000)
    # Clip the lower bound to 0
    return np.clip(ci, 0, None)

def read_log_file(filename):
    with open(filename, 'r') as f:
        lines = f.readlines()
        coverage_info = []
        for i in range(len(lines)):
            if ' Consumed search budget: ' in lines[i]:
                if 'v200' in filename:
                    consumed_budget = float(lines[i].split('Consumed search budget: ')[1].split('%')[0])
                    covered_targets = int(lines[i].split('covered targets: ')[1].split(';')[0])
                elif 'v320' in filename:
                    consumed_budget = float(lines[i].split(' Consumed search budget: ')[1].split('%')[0])
                    if i + 1 < len(lines) and '* Covered targets: ' in lines[i + 1]:
                        covered_targets = int(lines[i + 1].split('* Covered targets: ')[1].split(';')[0])
                    else:
                        continue

                coverage_info.append([consumed_budget, covered_targets])

            else:
                continue
        
        return coverage_info
    

def main():
    application = f'catwatch'
    methods = ['MOSA', 'MISH', 'MISHMOSA']
    base_budget = np.linspace(0, 100, 101)
    runs = 10
    version = 'v320'
    fitness_function = 'lower_median'
    results_folder = f'../../results_{application}_{version}_{fitness_function}_1hour_better_selection_strat'
    method_run_data = []
    for m in methods:
        for i in range(1, runs + 1):
            filename = f'{results_folder}/{application}_with_500_faults_EM_logs_{m}_{i}.txt'
            coverage_info = read_log_file(filename)
            budget = [x[0] for x in coverage_info]
            covered_targets = [x[1] for x in coverage_info]
            interp_coverage = interp_coverage_values(base_budget, budget, covered_targets)
            for j in range(len(interp_coverage)):
                method_run_data.append([m, i, base_budget[j], interp_coverage[j]])
        
    df = pd.DataFrame(method_run_data, columns=['Method', 'Run', 'Budget', 'Coverage'])
    df.to_csv(f'{results_folder}/coverage_data.csv', index=False)
    # Create the line plot
    plt.figure(figsize=(10, 6))
    sns.lineplot(data=df, x='Budget', y='Coverage', hue='Method', errorbar=('ci', 95))
    
    # Customize the plot
    plt.title(f'Coverage over time EvoMaster - {application} - {version}')
    plt.xlabel('Budget Used (%)')
    plt.ylabel('Coverage (Targets)')
    plt.grid(True)

    # Show the plot
    plt.show()



if __name__ == '__main__':
    main()



