package org.evomaster.core.search.algorithms

import org.evomaster.core.EMConfig
import org.evomaster.core.logging.LoggingUtil
import org.evomaster.core.search.EvaluatedIndividual
import org.evomaster.core.search.Individual
import org.evomaster.core.search.service.SearchAlgorithm
import java.io.*
import java.nio.file.Files
import java.nio.file.Paths
import java.time.Instant
import java.time.Duration

/**
 * Implementation of MISH (Model Inference Search Heuristic) algorithm for test-case generation.
 * This algorithm uses a constantly updated state machine to infer which test cases is producing
 * new behaviour in the system. The aim is that we do better exploration of the system using model
 * inference to guide the generation of test cases.
 */
open class MishAlgorithm<T> : SearchAlgorithm<T>() where T: Individual {

    var indBatchNr = 0

    var iteration = 0

    private var lastReadLine: Int = 0

    private var numDoneResponse: Int = 0

    private data class EvalData(val eInd: EvaluatedIndividual<*>, var fitness: Double, var trace: String)

    private var population: MutableList<EvalData> = mutableListOf()

    override fun getType(): EMConfig.Algorithm {
        return EMConfig.Algorithm.MISH
    }

    /**
     * In MISH, we start with a random population of random test cases.
     */
    override fun setupBeforeSearch() {
        population.clear()
        indBatchNr = 0
        initMIFramework()
        population = evaluateIndividuals(initPopulation())
        iteration = 0
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

        if (!waitForOutput(config.tracesDir + "EvoMaster_logs_traces_tempParent.txt")) {
            generateTempTraceFile(parentPop, "tempParent")
            waitForOutput(config.tracesDir + "EvoMaster_logs_traces_tempParent.txt")
        }

        // Compute new fitness of the parent based on the updated model.
        forwardTracesToModelInferenceFrameWork(false, "tempParent")

        if (!waitForOutput(config.fitnessDir + "ff_fitness_tempParent.txt")) {
            forwardTracesToModelInferenceFrameWork(false, "tempParent")
            waitForOutput(config.fitnessDir + "ff_fitness_tempParent.txt")
        }

        // Collect and update the fitness of the parent
        collectAndUpdateFitness(parentPop, "tempParent")

        // Select new population
        population = generateNewPopulation(parentPop, evalOffspringPop)

        if (!time.shouldContinueSearch()) {
            return
        }

        iteration++
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
                evaluatedPopulation.add(EvalData(ei, -1.0, ""))
            }
        }

//        LoggingUtil.getInfoLogger().info("MISH ---- Finished running individuals")

        // Saving execution statistics of the individuals
//        LoggingUtil.getInfoLogger().info("MISH ---- Saving execution stats of individuals")
        ff.getExecutionInfoReporter().saveIntermediateExecutionStats(indBatchNr.toString())
        if (!waitForOutput("${config.executionStatsDir}EvoMasterExecutionStats_${indBatchNr}.csv")) {
            ff.getExecutionInfoReporter().saveIntermediateExecutionStats(indBatchNr.toString())
            waitForOutput("${config.executionStatsDir}EvoMasterExecutionStats_${indBatchNr}.csv")
        }

        // Generate traces for the individuals we just ran
//        LoggingUtil.getInfoLogger().info("MISH ---- Generating traces for the individuals")
        generateTraces(indBatchNr.toString())

        // Update the model using the generated traces
//        LoggingUtil.getInfoLogger().info("MISH ---- Updating model")
        forwardTracesToModelInferenceFrameWork(true, indBatchNr.toString())

        if (!waitForLearningStatus()) {
            LoggingUtil.getInfoLogger().info("MISH ---- No \"Done learning\" update received, trying to restart the model inference framework.")
            restartMIFramework()
            forwardTracesToModelInferenceFrameWork(true, indBatchNr.toString())
            waitForLearningStatus()
        }

        //Compute the fitness of the individuals using their traces
        forwardTracesToModelInferenceFrameWork(false, indBatchNr.toString())

        // Wait for the output to be written by the model inference framework.
        if (!waitForOutput(config.fitnessDir + "ff_fitness_${indBatchNr}.txt")) {
            forwardTracesToModelInferenceFrameWork(false, indBatchNr.toString())
            waitForOutput(config.fitnessDir + "ff_fitness_${indBatchNr}.txt")
        }

//        LoggingUtil.getInfoLogger().info("MISH ---- Updating fitness of individuals using model")
        // Update the fitness values.
        collectAndUpdateFitness(evaluatedPopulation, indBatchNr.toString())

        this.indBatchNr++ // update the batch of individuals that we have just run.

        return evaluatedPopulation
    }

    private fun selection(candidates: MutableList<EvalData>): EvaluatedIndividual<T> {
        // Execute tournament selection
        // First select a random individual from the evaluated population.
        var bestInd: Int = randomness.nextInt(candidates.size)

        // Then we compare this with other k-1 (randomly) selected individuals to see
        // whether this individual is the best one from the tournament.
        for (i in 0 until config.tournamentSize-1) {
            val nextInd = randomness.nextInt(candidates.size)
            if (candidates[bestInd].fitness > candidates[nextInd].fitness) {
                bestInd = nextInd // update the individual if we found a better one in the tournament.
            }
        }

        // Finally, return the best individual from the tournament.
        return (candidates[bestInd].eInd as EvaluatedIndividual<T>).copy()
    }

    private fun generateOffspringPop(candidates:MutableList<EvalData>): MutableList<Individual> {
        val offspringPop: MutableList<Individual> = mutableListOf()
        while(offspringPop.size < config.populationSize - (config.populationSize * 0.1).toInt()) {
            // select best candidate based on tournament selection
            val bestCandidate = selection(candidates)

            // Then we randomly mutate this selected candidate
            val mutatedCandidate = getMutatator().mutate(bestCandidate).copy()

            // Add it to the new population
            offspringPop.add(mutatedCandidate)
        }

        // Add a few random indviduals to increase exploration.
        while (offspringPop.size < config.populationSize) {
            offspringPop.add(sampler.sample(true))
        }

        return offspringPop
    }


    private fun generateNewPopulation(parentPop: MutableList<EvalData>, evalOffspringPop: MutableList<EvalData>): MutableList<EvalData> {
        parentPop.addAll(evalOffspringPop.map{it.copy()}.toMutableList())
        parentPop.sortByDescending{it.fitness}
        return parentPop.take(config.populationSize).toMutableList()
    }

    private fun initPopulation(): MutableList<Individual> {
//        LoggingUtil.getInfoLogger().info("MISH ---- Initializing Population")
        val initPop = mutableListOf<Individual>()
        while (initPop.size < config.populationSize) {
            initPop.add(sampler.sample(true)) // generate a random individual
        }
        return initPop
    }

    /**
     * This function is used to start the daemon for the model inference
     * framework. It first checks whether the daemon is already started
     * and starts the daemon if this is not the case.
     */
    fun initMIFramework() {
        if (modelInfFrameworkRunning()) {
            return
        }
        else {
//            LoggingUtil.getInfoLogger().info("MISH ---- Initializing Flexfringe")
//            val flexFringePath = System.getenv("FLEXFRINGE_PATH")
            val flexfringeComm = "./flexfringe"
            val iniPath = "css-stream.ini"
            val outputDir = config.fitnessDir
            val command = listOf(flexfringeComm, "--ini", iniPath, "--outputdir", outputDir)
            val processBuilder = ProcessBuilder(command)
            processBuilder.start()
        }
    }

    protected fun modelInfFrameworkRunning(): Boolean {
        val processBuilder = ProcessBuilder("ps", "-e", "-o", "command")
        val process = processBuilder.start()
        val reader = BufferedReader(InputStreamReader(process.inputStream))
        val allText = reader.readText()
        return allText.contains("/flexfringe --ini")
    }

    fun generateTraces(outName: String) {
        val executionStatsFilePath = "${config.executionStatsDir}EvoMasterExecutionStats_${outName}.csv"
        if (!checkForFile(executionStatsFilePath)) {
            throw RuntimeException("Cannot find execution stats file: $executionStatsFilePath")
        }
        val command = mutableListOf("python3", config.modelInfLogProcessorScriptPath)
        command.add("--log_file")
        command.add(config.logFilePath)
        command.add("--execution_stats_file")
        command.add(executionStatsFilePath)
        command.add("--output_file")
        command.add("${config.tracesDir}EvoMaster_logs_traces_${outName}.txt")

        if (indBatchNr > 0) {
            command.add("--read_from")
            command.add((lastReadLine + 1).toString())
        }

        val processBuilder = ProcessBuilder(command)
        processBuilder.redirectOutput(File("log_processor_script.log"))
        processBuilder.redirectError(File("log_processor_script_errors.log"))
        val process = processBuilder.start()
        process.waitFor()
        lastReadLine = getNumLinesFromFile(config.logFilePath)
    }

    private fun collectAndUpdateFitness(evalInds: MutableList<EvalData>, outName: String) {
        val fitnessValues = File("${config.fitnessDir}ff_fitness_${outName}.txt").readLines()
        val traces = File("${config.tracesDir}EvoMaster_logs_traces_${outName}.txt").readLines()
        for (i in 0 until evalInds.size) {
            if (fitnessValues.size <= i) {
                LoggingUtil.getInfoLogger().info("MISH --- Mismatch between number of fitness values and population size. Using default value of 0.0")
                evalInds[i].fitness = 0.0
            } else {
                evalInds[i].fitness = fitnessValues[i].trim().toDouble()
            }
            evalInds[i].trace = traces[i]
        }
    }

    fun forwardTracesToModelInferenceFrameWork(updateModel: Boolean, outName: String) {
        var traceFilePath =  "${config.tracesDir}EvoMaster_logs_traces_${outName}.txt"

        if (!checkForFile(traceFilePath)) {
            throw RuntimeException("Cannot find trace file: $traceFilePath")
        }

        val command = mutableListOf("echo")

        if (!updateModel) {
            traceFilePath += " --fitness"
        }

        traceFilePath += " --out_name $outName"
        command.add(traceFilePath)
        val processBuilder = ProcessBuilder(command)
        processBuilder.redirectOutput(File(config.modelInfFrameworkNamedFilePath))
        val process = processBuilder.start()
        process.waitFor()
    }

    fun waitForOutput(outputFilePath: String): Boolean {
        val start = Instant.now()
        while (Duration.between(start, Instant.now()).toMillis() < config.timeOutForWaitingOutput) {
            if (checkForFile(outputFilePath) && getNumLinesFromFile(outputFilePath) == config.populationSize) {
                return true
            }
        }

        return false
    }

    protected fun checkForFile(filePath: String) : Boolean {
        val file = File(filePath)
        return file.exists() && file.length() > 0
    }

    private fun generateTempTraceFile(evalInds: MutableList<EvalData>, outName: String) {
        val writer = File("${config.tracesDir}EvoMaster_logs_traces_${outName}.txt").bufferedWriter()
        for (i in 0 until evalInds.size) {
            writer.write(evalInds[i].trace + "\n")
        }
        writer.close()
    }

    protected fun createDirectoryIfNotExists(path: String) {
        val directory = File(path)
        if (!directory.exists()) {
            directory.mkdirs()
        } else{
            return
        }
    }

    fun waitForLearningStatus(): Boolean {
        val statFileName = config.fitnessDir + "ff_learn_stat.txt"
        val start = Instant.now()
        while (Duration.between(start, Instant.now()).toMillis() < config.timeOutForWaitingOutput) {
            if (checkForFile(statFileName)) {
                val numLines = getNumLinesFromFile(statFileName)
                if (numLines > numDoneResponse) {
                    numDoneResponse = numLines
                    return true
                }
            }
        }

        return false
    }

    private fun getNumLinesFromFile(filePath: String): Int {
        return Files.lines(Paths.get(filePath)).use { it.count().toInt() }
    }

    fun killDaemon(daemonCommand: String) {
        try {
            // Run the 'ps' command to find the daemon process by its command
            val processBuilder = ProcessBuilder("bash", "-c", "ps aux | grep '$daemonCommand' | grep -v grep")
            val process = processBuilder.start()

            // Read the output of 'ps' command
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val processes = reader.readLines()

            if (processes.size == 0) {
                return // in case that the daemon has already been exited
            }

            // Iterate over the processes and extract the PID (process ID)
            for (processLine in processes) {
                LoggingUtil.getInfoLogger().info("MISH (Restarting Daemon) --- Found process: $processLine")
                val parts = processLine.trim().split("\\s+".toRegex())
                if (parts.size > 1) {
                    val pid = parts[1] // The second column is the PID
                    LoggingUtil.getInfoLogger().info("MISH (Restarting Daemon) --- Stopping MISH Daemon with PID: $pid")

                    // Kill the process by PID
                    val killProcess = ProcessBuilder("kill", pid).start()
                    killProcess.waitFor() // Wait for the kill command to execute
                    LoggingUtil.getInfoLogger().info("MISH (Restarting Daemon) --- Deamon with PID $pid is stopped.")
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun restartMIFramework() {
        val daemonCommand = "./flexfringe --ini css-stream.ini" // The command that starts the daemon
        killDaemon(daemonCommand) // Kill the running daemon
        initMIFramework() // Restart the daemon
    }

}
