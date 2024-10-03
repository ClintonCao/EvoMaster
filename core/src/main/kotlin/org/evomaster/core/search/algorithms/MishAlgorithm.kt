package org.evomaster.core.search.algorithms

import org.evomaster.core.EMConfig
import org.evomaster.core.search.EvaluatedIndividual
import org.evomaster.core.search.Individual
import org.evomaster.core.search.service.FitnessFunction
import org.evomaster.core.search.service.SearchAlgorithm
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Implementation of MISH (Model Inference Search Heuristic) algorithm for test-case generation.
 * This algorithm uses a constantly updated state machine to infer which test cases is producing
 * new behaviour in the system. The aim is that we do better exploration of the system using model
 * inference to guide the generation of test cases.
 */
class MishAlgorithm<T> : SearchAlgorithm<T>() where T: Individual {

    private var iteration: Int = 0

    private class Data(val ind: Individual, val fitness: Double)

    private var population: MutableList<Data> = mutableListOf()

    override fun getType(): EMConfig.Algorithm {
        return EMConfig.Algorithm.MISH
    }

    /**
     * In MISH, we start with a random population of random test cases.
     */
    override fun setupBeforeSearch() {
        population.clear()
        iteration = 0
        initPopulation()
        initMIFramework()
    }

    override fun searchOnce() {
        if (iteration > 0) {
            // We select the top N best candidates and then repopulate the population with
            // the best candidates. TODO: We still have to add mutation to introduce variance.
            population = rePopulate(selection())

        }

        // Run each individual
        population.forEach { item ->
            ff.calculateCoverage(item.ind as T)
        }

        // After running one population, expxort statitics
        ff.getExecutionInfoReporter().saveIntermediateExecutionStats(iteration)

        // Update fitness value


        iteration++
    }



    private fun sortPopulation() {
        population.sortByDescending {it.fitness}
    }

    private fun selection(): MutableList<Individual> {
        sortPopulation()
        val bestIndividuals:MutableList<Individual> = mutableListOf()
        for (i in 0..config.selectNBestIndividuals) {
            bestIndividuals.add(population[i].ind.copy())
        }
        return bestIndividuals
    }

    private fun rePopulate(bestCandidates: MutableList<Individual>): MutableList<Data> {
        val newPopulation: MutableList<Data> = mutableListOf()
        for (i in 0..config.populationSize) {
            val randomElement = bestCandidates.random()
            newPopulation.add(Data(randomElement.copy(), -1.0))
        }
        return newPopulation
    }

    private fun initPopulation() {
        val n = config.populationSize
        for (i in 1..n) {
            val individual = sampler.sample(true) // generate a random individual
            population.add(Data(individual, -1.0))
        }
    }

    /**
     * This function is used to start the daemon for the model inference
     * framework. It first checks whether the daemon is already started
     * and starts the daemon if this is not the case.
     */
    private fun initMIFramework() {
        if (MIFrameworkRunning()) {
            return
        }
        else {
            val flexFringePath = System.getenv("FLEXFRINGE_PATH")
            val flexfringeComm = "$flexFringePath/flexfringe"
            val iniPath = "$flexFringePath/ini/css-stream.ini"
            val command = listOf(flexfringeComm, "--ini", iniPath)
            val processBuilder = ProcessBuilder(command)
            processBuilder.start()
        }
    }

    private fun MIFrameworkRunning(): Boolean {
        val processBuilder = ProcessBuilder("ps", "-e", "-o", "cmd")
        val process = processBuilder.start()
        val reader = BufferedReader(InputStreamReader(process.inputStream))
        val allText = reader.readText()
        return allText.contains("/flexfringe --ini")
    }

}