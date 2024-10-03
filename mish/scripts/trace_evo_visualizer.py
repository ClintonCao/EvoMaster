import pydot
import argparse as ap
import cv2
import imageio
from collections import Counter
from tqdm import tqdm
import os

INCREASE_FACTOR = 0.15
DECAYING_FACTOR = 0.001
WIDTH = 500
HEIGHT = 1300

NODE_COLOR_INTENSITY_MAPPING = {}

def read_model(dot_file):
    return pydot.graph_from_dot_file(dot_file)[0]

def read_state_sequence(state_sequence_file):
    with open(state_sequence_file, 'r') as f:
        lines = f.readlines()
        sequences = []
        for line in lines:
            state_sequence = [x.replace('-1', '0') for x in line.strip().split('\n')[0].split(' ')]
            sequences.append(state_sequence)
        
        return sequences


def clean_model(model):
    nodes = model.get_nodes()
    for n in nodes:
        if n.get_name() == '\"\\n\"':
            model.del_node(n)

        n.set_fillcolor(f'"0 0 1"')
        NODE_COLOR_INTENSITY_MAPPING[n.get_name()] = 0
        
    return model

def update_node_color(model, sequence):
    nodes = model.get_nodes()
    seq_node_occurrences = Counter(sequence)
    for node in nodes:
        if node.get_name() == 'legend':
            continue
        if node.get_name() in seq_node_occurrences:
            new_color_intensity = seq_node_occurrences[node.get_name()] * INCREASE_FACTOR
            NODE_COLOR_INTENSITY_MAPPING[node.get_name()] += new_color_intensity
        else:
            new_color_intensity = NODE_COLOR_INTENSITY_MAPPING[node.get_name()] - DECAYING_FACTOR
            if new_color_intensity < 0:
                new_color_intensity = 0

            NODE_COLOR_INTENSITY_MAPPING[node.get_name()] = new_color_intensity
            
        node.set_fillcolor(f'"0 {NODE_COLOR_INTENSITY_MAPPING[node.get_name()]} 1"')
    
    return model

def update_legend(model, trace_nr):
    if len(model.get_node('legend')) > 0:
        legend_node = model.get_node('legend')[0]
        legend_node.set_label(f'<<table border="0" cellborder="0" cellspacing="0" fontsize="30"><tr><td>Trace {trace_nr}</td></tr></table>>')
        # increase font size of the legend
        legend_node.set_fontsize(60)
    else:
        legend_node = pydot.Node('legend', shape='plaintext', label=f'<<table border="0" cellborder="0" cellspacing="0" fontsize="30"><tr><td>Trace {trace_nr}</td></tr></table>>')
        legend_node.set_fontsize(60)
        model.add_node(legend_node)

    return model

def main():
    aparser = ap.ArgumentParser()
    aparser.add_argument("--folder_path", type=str, required=True)
    aparser.add_argument("--nr_iter", type=int, required=True)
    aparser.add_argument("--output_name", type=str, required=True)
    args = aparser.parse_args()

    all_sequences = []
    for i in range(args.nr_iter):
        sequences = read_state_sequence(f'{args.folder_path}/ff_state_sequence_{i}.txt')
        for s in sequences:
            all_sequences.append(s)

    final_model = clean_model(read_model(f'{args.folder_path}/model_batch_nr_{args.nr_iter - 1}.dot'))

    for i in tqdm(range(len(all_sequences)), desc="Generating images"):
        final_model = update_node_color(final_model, all_sequences[i])
        final_model = update_legend(final_model, i + 1)
        final_model.write_png(f'{args.folder_path}/final_model_{i}.png')
        fin_model_img = cv2.imread(f'{args.folder_path}/final_model_{i}.png')
        resized_img = cv2.resize(fin_model_img, (WIDTH, HEIGHT), interpolation=cv2.INTER_AREA)
        cv2.imwrite(f'{args.folder_path}/final_model_resized_{i}.png', resized_img)

    # create mp4
    images = []
    for i in tqdm(range(len(all_sequences)), desc="Generating MP4"):
        images.append(imageio.imread(f'{args.folder_path}/final_model_resized_{i}.png'))
    
    imageio.mimsave(f'trace_evo_{args.output_name}.mp4', images, fps=30)

    # Remove the pngs that we created for the mp4
    for i in tqdm(range(len(all_sequences))):
        os.remove(f'{args.folder_path}/final_model_{i}.png')
        os.remove(f'{args.folder_path}/final_model_resized_{i}.png')


if __name__ == "__main__":
    main()
