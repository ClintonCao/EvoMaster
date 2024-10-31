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
    applications = ['catwatch', 'features-service', 'scout-api', 'proxyprint', 'languagetool']
    application_to_paper_application_name = {'catwatch': 'CatWatch', 'features-service': 'Features-Service', 'scout-api': 'Scout-API', 'proxyprint': 'ProxyPrint', 'languagetool': 'LanguageTool'}
    methods = ['MOSA', 'MISH', 'MISHMOSA']
    base_budget = np.linspace(0, 100, 101)
    fitness_abbrev = {'lower_median': 'LM', 'weighted_size': 'WS'}
    runs = 10
    versions = ['v200', 'v320']

    for application in tqdm(applications):
        for version in versions:
            method_run_data = []
            plot_output_folder = f'../convergence_plots/{application}'
            if not os.path.exists(plot_output_folder):
                os.makedirs(plot_output_folder)
            
            for m in methods:
                for fitness_function in fitness_abbrev.keys():
                    results_folder = f'../../results_{application}_{version}_{fitness_function}_latest'

                    # We don't use MISH's fitness functions for MOSA, so we only need to collect the results from the folder of lower median.
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
            # Create the line plot
            plt.figure(figsize=(12, 8))
            # set hue pallette to be more colorblind friendly
            sns.set_palette("colorblind")
            # also add markers to make plots more colorblind friendely
            sns.lineplot(data=df, x='Budget', y='Coverage', hue='Method', style='Method', markers=True, alpha=0.7, dashes=False, errorbar=('ci', 95), linewidth=4.0)

            # Customize the plot
            plt.title(f'Coverage Over Time - {application_to_paper_application_name[application]}')
            plt.xlabel('Budget Used (%)')
            plt.ylabel('Coverage (Targets)')
            plt.grid(True)
            plt.legend(title='Algorithm')
            plt.savefig(f'{plot_output_folder}/{application}_{version}_coverage_over_time_plot.pdf', format='pdf')
            # clear the plot for the next version
            plt.clf()


if __name__ == '__main__':
    main()



