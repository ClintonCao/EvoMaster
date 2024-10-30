import matplotlib.pyplot as plt
import numpy as np
import seaborn as sns
import pandas as pd
from tqdm import tqdm
import os

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
    application_to_paper_application_name = {'catwatch': 'CatWatch', 'features-service': 'Features-Service', 'languagetool': 'LanguageTool', 'proxyprint': 'ProxyPrint', 'scout-api': 'Scout-API'}
    for application in tqdm(application_to_paper_application_name.keys(), desc='Applications'):
        methods = ['MOSA', 'MISH', 'MISHMOSA']
        base_budget = np.linspace(0, 100, 101)
        runs = 10
        versions = ['v200']
        plot_out_folder = f'../convergence_plots/{application}/'
        if not os.path.exists(plot_out_folder):
            os.makedirs(plot_out_folder)
        for version in versions:
            fitness_functions = ['lower_median', 'weighted_size']
            fitness_abbrev = {'lower_median': 'LM', 'weighted_size': 'WS'}
            method_run_data = []
            for fitness_function in fitness_functions:
                results_folder = f'../../results_{application}_{version}_{fitness_function}_latest'
                for m in methods:
                    if m == 'MOSA' and fitness_function == 'weighted_size':
                        continue

                    for i in range(1, runs + 1):
                        filename = f'{results_folder}/{application}_with_500_faults_EM_logs_{m}_{i}.txt'
                        coverage_info = read_log_file(filename)
                        budget = [x[0] for x in coverage_info]
                        covered_targets = [x[1] for x in coverage_info]
                        interp_coverage = interp_coverage_values(base_budget, budget, covered_targets)
                        for j in range(len(interp_coverage)):
                            if m == 'MOSA':
                                method_run_data.append([m, i, base_budget[j], interp_coverage[j]])
                            else:
                                method_run_data.append([m + '-' + fitness_abbrev[fitness_function], i, base_budget[j], interp_coverage[j]])
                
            df = pd.DataFrame(method_run_data, columns=['Method', 'Run', 'Budget', 'Coverage'])
            # df.to_csv(f'{results_folder}/coverage_data.csv', index=False)
            # Create the line plot
            plt.figure(figsize=(12, 8))
            # set heu to be colorblind friendly
            sns.set_palette("colorblind")
            sns.lineplot(data=df, x='Budget', y='Coverage', hue='Method', style='Method', markers=True, alpha=0.7, dashes=False, errorbar=('ci', 95), linewidth=4.0)
            
            # Customize the plot
            plt.title(f'Coverage Over Time - {application_to_paper_application_name[application]}')
            plt.xlabel('Budget Used (%)')
            plt.ylabel('Coverage (Targets)')
            plt.grid(True)
            plt.legend(title='Algorithm')
            plt.savefig(f'{plot_out_folder}/{application}_{version}_coverage_over_time_plot.png')
            # clear the plot for the next version
            plt.clf()


if __name__ == '__main__':
    main()



