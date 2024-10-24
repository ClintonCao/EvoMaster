package org.evomaster.core.search.algorithms

import org.evomaster.core.EMConfig
import org.evomaster.core.logging.LoggingUtil
import org.evomaster.core.search.EvaluatedIndividual
import org.evomaster.core.search.Individual
import org.evomaster.core.search.service.mutator.MutatedGeneSpecification
import java.io.File
import java.util.ArrayList

class MishMosaAlgorithm<T> : MishAlgorithm<T>() where T: Individual {

    private data class EvalData(val eInd: EvaluatedIndividual<*>,  var crowdingDistance: Double ,var trace: String, var rank: Int)

    private var population: MutableList<EvalData> = mutableListOf()

    override fun getType(): EMConfig.Algorithm {
        return EMConfig.Algorithm.MISHMOSA
    }

    override fun setupBeforeSearch() {
        population.clear()
        indBatchNr = 0
        initMIFramework()
        population = evaluateIndividuals(initPopulation())
        iteration = 0
    }

    private fun initPopulation(): MutableList<Individual> {
        LoggingUtil.getInfoLogger().info("MISH ---- Initializing Population")
        val initPop = mutableListOf<Individual>()
        while (initPop.size < (config.populationSize)) {
            initPop.add(sampler.sample()) // generate a random individual
        }
        return initPop
    }

    fun sortPopulation() {
        val notCovered = archive.notCoveredTargets()

        if(notCovered.isEmpty()){
            //Trivial problem: everything covered in first population
            return
        }

        val fronts = preferenceSorting(notCovered, population)

        var remain: Int = config.populationSize // Math.max(config.populationSize, fronts[0]?.size ?: config.populationSize)
        var index = 0
        population.clear()

        // Obtain the next front
        var front = fronts[index]

        while (front!=null && remain > 0 && remain >= front.size && front.isNotEmpty()) {
            // Add the individuals of this front
            calculatePairwiseDiversity(front)
            for (d in front) {
                population.add(d)
            }

            // Decrement remain
            remain = remain - front.size

            // Obtain the next front
            index += 1
            if (remain > 0) {
                front = fronts[index]
            } // if
        } // while

        // Remain is less than front(index).size, insert only the best one
        if (remain > 0 && front!=null && front.isNotEmpty()) {
            calculatePairwiseDiversity(front)
            var front2 = front.sortedWith(compareBy<EvalData> { - it.crowdingDistance })
                .toMutableList()
            for (k in 0..remain - 1) {
                population.add(front2[k])
            } // for

        } // if

        for (evalInds in population) {
            LoggingUtil.getInfoLogger().info("Crowding distance = ${evalInds.crowdingDistance}, rank = ${evalInds.rank}")
        }
    }

    /*
      See: Preference sorting as discussed in the TSE paper for DynaMOSA
    */
    private fun preferenceSorting(notCovered: Set<Int>, list: List<EvalData>): HashMap<Int, List<EvalData>> {

        val fronts = HashMap<Int, List<EvalData>>()

        // compute the first front using the Preference Criteria
        val frontZero = mosaPreferenceCriterion(notCovered, list)
        fronts.put(0, ArrayList(frontZero))
        LoggingUtil.getInfoLogger().apply {
            info("First front size : ${frontZero.size}")
        }

        // compute the remaining non-dominated Fronts
        val remaining_solutions: MutableList<EvalData> = mutableListOf()
        remaining_solutions.addAll(list)
        remaining_solutions.removeAll(frontZero)

        var selected_solutions = frontZero.size
        var front_index = 1

        while (selected_solutions < config.populationSize && remaining_solutions.isNotEmpty()){
            var front: MutableList<EvalData> = getNonDominatedFront(notCovered, remaining_solutions)
            fronts.put(front_index, front)
            for (sol in front){
                sol.rank = front_index
            }
            remaining_solutions.removeAll(front)

            selected_solutions += front.size

            front_index += 1

            LoggingUtil.getInfoLogger().apply {
                debug("Selected Solutions : ${selected_solutions}")
            }
        }
        return fronts
    }

    /**
     * It retrieves the front of non-dominated solutions from a list
     */
    private fun getNonDominatedFront(notCovered: Set<Int>, remaining_sols: List<EvalData>): MutableList<EvalData>{
        var front: MutableList<EvalData> = mutableListOf()
        var isDominated: Boolean

        for (p in remaining_sols) {
            isDominated = false
            val dominatedSolutions = ArrayList<EvalData>(remaining_sols.size)
            for (best in front) {
                val flag = compare(p, best, notCovered)
                if (flag == -1) {
                    dominatedSolutions.add(best)
                }
                if (flag == +1) {
                    isDominated = true
                }
            }

            if (isDominated)
                continue

            front.removeAll(dominatedSolutions)
            front.add(p)

        }
        return front
    }

    /**
     * Fast routine based on the Dominance Comparator discussed in
     * "Automated Test Case Generation as a Many-Objective Optimisation Problem with Dynamic
     *  Selection of the Targets"
     */
    private fun compare(x: EvalData, y: EvalData, notCovered: Set<Int>): Int {
        var dominatesX = false
        var dominatesY = false

        for (index in 1..notCovered.size) {
            if (x.eInd.fitness.getHeuristic(index) > y.eInd.fitness.getHeuristic(index))
                dominatesX = true
            if (y.eInd.fitness.getHeuristic(index) > x.eInd.fitness.getHeuristic(index))
                dominatesY = true

            // if the both do not dominate each other, we don't
            // need to iterate over all the other targets
            if (dominatesX && dominatesY)
                return 0
        }

        if (dominatesX == dominatesY)
            return 0

        else if (dominatesX)
            return -1

        else (dominatesY)
        return +1
    }

    private fun evaluateIndividuals(individuals: MutableList<Individual>): MutableList<EvalData>{
//        LoggingUtil.getInfoLogger().info("MISH ---- Evaluating new batch of individuals")
        val evaluatedPopulation: MutableList<EvalData> = mutableListOf()

        // Run each individual
//        LoggingUtil.getInfoLogger().info("MISH ---- Running individuals")
        for (i in 0 until individuals.size) {
            if (!time.shouldContinueSearch()) {
                break
            }
            val individual:T = individuals[i] as T
            val ei = ff.calculateCoverage(individual)
            if (ei != null) {
                archive.addIfNeeded(ei)
                evaluatedPopulation.add(EvalData(ei, -1.0, "", -1))
            }
        }

//        LoggingUtil.getInfoLogger().info("MISH ---- Finished running individuals")

        // Saving execution statistics of the individuals
//        LoggingUtil.getInfoLogger().info("MISH ---- Saving execution stats of individuals")
        ff.getExecutionInfoReporter().saveIntermediateExecutionStats(indBatchNr.toString())

        // Generate traces for the individuals we just ran
//        LoggingUtil.getInfoLogger().info("MISH ---- Generating traces for the individuals")
        generateTraces(indBatchNr.toString())

        // Update the model using the generated traces
//        LoggingUtil.getInfoLogger().info("MISH ---- Updating model")
        forwardTracesToModelInferenceFrameWork(true, "new")

//        waitForOutput(config.fitnessDir + "model_batch_nr_${indBatchNr}.dot")

        waitForLearningStatus()

        //Compute the fitness of the individuals using their traces
        forwardTracesToModelInferenceFrameWork(false, "new")

        waitForOutput(config.fitnessDir + "ff_fitness_new.txt")

//        LoggingUtil.getInfoLogger().info("MISH ---- Updating fitness of individuals using model")
        // Update the fitness values.
        updateDiversity(evaluatedPopulation, "new")

        this.indBatchNr++ // update the batch of individuals that we have just run.

        return evaluatedPopulation
    }

    private fun updateDiversity(evalInds: MutableList<EvalData>, outName: String) {
        val fitnessFile = File("${config.fitnessDir}ff_fitness_${outName}.txt")
        val tracesFile = File("${config.tracesDir}EvoMaster_logs_traces_${outName}.txt")

        val fitnessValues = fitnessFile.readLines().filter { it.isNotBlank() }
        val traces = tracesFile.readLines().filter { it.isNotBlank() }

        for (i in 0 until evalInds.size) {
            if (fitnessValues.size <= i) {
                LoggingUtil.getInfoLogger().info("MISH ff not available")
                evalInds[i].crowdingDistance = 0.0
            } else {
                evalInds[i].crowdingDistance = fitnessValues[i].trim().toDouble()
            }
            evalInds[i].trace = traces[i]
        }
    }

    private fun mosaPreferenceCriterion(notCovered: Set<Int>, list: List<EvalData>): HashSet<EvalData> {
        var frontZero: HashSet<EvalData> = HashSet<EvalData>()

        notCovered.forEach { t ->
            var chosen = list[0]
            list.forEach { data ->
                if (data.eInd.fitness.getHeuristic(t) > chosen.eInd.fitness.getHeuristic(t)) {
                    // recall: maximization problem
                    chosen = data
                } else if (data.eInd.fitness.getHeuristic(t) == chosen.eInd.fitness.getHeuristic(t)
                  //&& data.eInd.individual.size() < chosen.eInd.individual.size()){ //data.crowdingDistance > chosen.crowdingDistance){
                    && data.crowdingDistance > chosen.crowdingDistance){
                    // Secondary criterion based on tests lengths
                  chosen = data
                }
            }
            // MOSA preference criterion: the best for a target gets Rank 0
            chosen.rank = 0
            frontZero.add(chosen)
        }
        return frontZero
    }

    override fun searchOnce() {
        if (!time.shouldContinueSearch()) {
            return
        }

        // First make a copy of the previous generation.
        val parentPop: MutableList<EvalData> = population.map{it.copy()}.toMutableList()
        // Then we generate the offsprings
        val offspringPop: MutableList<Individual> = generateOffspringPop(parentPop)
        // Next we run the offsprings to update the model
        val evalOffspringPop: MutableList<EvalData> = evaluateIndividuals(offspringPop)

        // Generate temporary temp trace file from individuals of previous generation.
        generateTempTraceFile(parentPop, "tempParent")

        waitForOutput(config.tracesDir + "EvoMaster_logs_traces_tempParent.txt")

        // Compute new fitness of the parent based on the updated model.
        forwardTracesToModelInferenceFrameWork(false, "tempParent")

        waitForOutput(config.fitnessDir + "ff_fitness_tempParent.txt")

        // Collect and update the fitness of the parent
        updateDiversity(parentPop, "tempParent")

        // Select new population
        population.clear()
        population.addAll(parentPop)
        population.addAll(evalOffspringPop)
        sortPopulation()

        if (!time.shouldContinueSearch()) {
            return
        }

        iteration++
    }

    private fun generateOffspringPop(candidates:MutableList<EvalData>): MutableList<Individual> {
        val offspringPop: MutableList<Individual> = mutableListOf()
        while(offspringPop.size < config.populationSize - 3) {
            // select best candidate based on tournament selection
            val bestCandidate = selection(candidates)

            // Then we randomly mutate this selected candidate
            val mutatedGenes = MutatedGeneSpecification()
            val mutatedCandidate = getMutatator().mutate(bestCandidate, archive.notCoveredTargets(), mutatedGenes)

            // Add it to the new population
            offspringPop.add(mutatedCandidate)
        }

        // Add a few random indviduals to increase exploration.
        while (offspringPop.size < config.populationSize) {
            offspringPop.add(sampler.sample())
        }

        return offspringPop
    }

    private fun selection(candidates: MutableList<EvalData>): EvaluatedIndividual<T> {

        // the population is not fully sorted
        var min = randomness.nextInt(candidates.size)

        (0 until config.tournamentSize-1).forEach {
            val sel = randomness.nextInt(candidates.size)
            if (candidates[sel].rank < candidates[min].rank) {
                min = sel
            } else if (candidates[sel].rank == candidates[min].rank){
                if (candidates[sel].crowdingDistance > candidates[min].crowdingDistance)
                    min = sel
            }
        }

        return (candidates[min].eInd as EvaluatedIndividual<T>).copy()
    }

    private fun generateTempTraceFile(evalInds: MutableList<EvalData>, outName: String) {
        val writer = File("${config.tracesDir}EvoMaster_logs_traces_${outName}.txt").bufferedWriter()
        for (i in 0 until evalInds.size) {
            writer.write(evalInds[i].trace + "\n")
        }
        writer.close()
    }

    // Optimized function to calculate the length of the Longest Common Substring between two lists of tokens
    fun longestCommonSubstringLength(tokens1: List<String>, tokens2: List<String>): Int {
        if (tokens1.isEmpty() || tokens2.isEmpty()) return 0

        // Ensure tokens1 is the smaller list (to optimize space)
        val (smaller, larger) = if (tokens1.size <= tokens2.size) Pair(tokens1, tokens2) else Pair(tokens2, tokens1)

        // Create two arrays to store only the current and previous row of the dynamic programming table
        val prevRow = IntArray(smaller.size + 1)
        val currRow = IntArray(smaller.size + 1)

        var maxLength = 0

        // Iterate over larger list (outer loop)
        for (i in 1..larger.size) {
            for (j in 1..smaller.size) {
                if (larger[i - 1] == smaller[j - 1]) {
                    currRow[j] = prevRow[j - 1] + 1
                    maxLength = maxOf(maxLength, currRow[j]) // Update max length
                } else {
                    currRow[j] = 0 // Reset if no match
                }
            }
            // Swap rows: current becomes previous
            System.arraycopy(currRow, 0, prevRow, 0, currRow.size)
        }

        return maxLength
    }

    // Function to calculate Longest Common Substring-based distance
    fun longestCommonSubstringDistance(tokens1: List<String>, tokens2: List<String>): Double {
        val lcsLen = longestCommonSubstringLength(tokens1, tokens2)
        val maxLen = maxOf(tokens1.size, tokens2.size)

        // Return the LCS distance: (1 - normalized LCS length)
        return 1.0 - (lcsLen.toDouble() / maxLen)
    }

    // Optimized version to calculate pairwise diversity with minimum distance calculation
    private fun calculatePairwiseDiversity(individuals: List<EvalData>) {
        val n = individuals.size

        // Precompute the split tokens once
        val tokenizedTraces = individuals.map { it.trace.split(" ") }

        // Initialize a minDistance vector to store minimum distances for each individual
        val minDistances = DoubleArray(n) { Double.MAX_VALUE }

        // Calculate pairwise LCS distances and track minimum distances
        for (i in 0 until n) {
            for (j in i + 1 until n) {
                // Calculate LCS distance once for the pair (i, j)
                val distance = longestCommonSubstringDistance(tokenizedTraces[i], tokenizedTraces[j])

                // Update the minimum distances for both i and j
                minDistances[i] = minOf(minDistances[i], distance)
                minDistances[j] = minOf(minDistances[j], distance)
            }
        }

        // Update crowding distances after all minimum distances are calculated
        for (i in 0 until n) {
            individuals[i].crowdingDistance += minDistances[i]
        }
    }
}
