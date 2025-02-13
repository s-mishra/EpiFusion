import java.util.concurrent.*;
import java.util.Random;
import java.io.IOException;
import java.util.Arrays;


public class Particles {
    Particle[] particles;
    int N;
    double[][] betas;

    public Particles(int numParticles){
        N = numParticles;
        particles = new Particle[numParticles];
        for (int i = 0; i< N; i++) {
            particles[i] = new Particle(i);
        }
        betas = new double[N][Storage.T];
    }

    //Printers
    public void printParticles() {
        for (int i = 0; i < N; i++) {
            particles[i].printStatus();
            particles[i].printBeta();
        }
    }
    public void printLikelihoods() {
        for (int i = 0; i < N; i++) {
            System.out.println("Particle: "+i+", State: "+particles[i].getState());
            if (!Storage.isEpiOnly()) {
                System.out.println("PhyloLikelihood: "+particles[i].getPhyloLikelihood());
            }
            if (!Storage.isPhyloOnly()) {
                System.out.println("EpiLikelihood: "+Math.log(particles[i].getEpiLikelihood()));
            }
        }
    }
    public void printWeights() {
        for (int i = 0; i < N; i++) {
            System.out.println("Particle: "+i);
            if (!Storage.isPhyloOnly()) {
                System.out.println("EpiWeight: "+particles[i].getEpiWeight());
            }
            if (!Storage.isEpiOnly()){
                System.out.println("PhyloWeight: "+particles[i].getPhyloWeight());
            }
            System.out.println("CombinedWeight: "+particles[i].getWeight());
        }
    }
    public void printTrajectories() {
        for (Particle particle : particles) {
            particle.traj.printTrajectory(particle.particleID);
        }
    }
    public void printBetas() {
        for (int i = 0; i < N; i++) {
            particles[i].printBeta();
        }
    }

    public void setInitialBeta(Double beta, double stdDev) {
        try {
            ExecutorService executor = Executors.newFixedThreadPool(Storage.numThreads);

            for (Particle particle : particles) {
                executor.submit(() -> particle.setBeta(beta, stdDev));
            }

            executor.shutdown();
            boolean done = executor.awaitTermination(Long.MAX_VALUE, TimeUnit.SECONDS);
            if (!done) {
                System.err.println("Not all tasks completed within the specified timeout.");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while waiting", e);
        }
    }
    public void setInitialStates(int newState) {
        try {
            ExecutorService executor = Executors.newFixedThreadPool(Storage.numThreads);

            for (Particle particle : particles) {
                executor.submit(() -> particle.setInitialState(newState));
            }

            executor.shutdown();
            boolean done = executor.awaitTermination(Long.MAX_VALUE, TimeUnit.SECONDS);
            if (!done) {
                System.err.println("Not all tasks completed within the specified timeout.");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while waiting", e);
        }
    }

    //Likelihood things
    public void getEpiLikelihoods(int incidence) {
        try {
            ExecutorService executor = Executors.newFixedThreadPool(Storage.numThreads);

            for (Particle particle : particles) {
                executor.submit(() -> {
                    double newLikelihood = EpiLikelihood.poissonLikelihood(incidence, particle);
                    particle.setEpiLikelihood(newLikelihood);
                });
            }

            executor.shutdown();
            boolean done = executor.awaitTermination(Long.MAX_VALUE, TimeUnit.SECONDS);
            if (!done) {
                System.err.println("Not all tasks completed within the specified timeout.");
            }
        } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new RuntimeException("Interrupted while waiting", e);
    }
    }
    public boolean checkEpiLikelihoods() {
        //Case 1: all NaN. Can happen if state is negative in which case the pf needs quitting
        boolean allNaN = true;
        for (Particle particle : particles) {
            if (!(Double.isNaN(particle.getEpiLikelihood()))) {
                allNaN = false;
                break;
            }
        }
        if (allNaN) {
            return true;
        }
        else {
            //Case 2: all -Inf. Could happen if the state is very far from incidence, or is 0.
            boolean allNegInf = true;
            for (Particle particle : particles) {
                if (!(Double.isInfinite(particle.getEpiLikelihood()))) {
                    allNegInf = false;
                    break;
                }
            }
            return allNegInf;
        }
    }

    public boolean checkPhyloLikelihoods() {
        boolean allNaN = true;
        for (Particle particle : particles) {
            if (!(Double.isNaN(particle.getPhyloLikelihood()))) {
                allNaN = false;
                break;
            }
        }
        if (allNaN) {
            return true;
        }
        else {
            boolean allNegInf = true;
            for (Particle particle : particles) {
                if (!(Double.isInfinite(particle.getPhyloLikelihood()))) {
                    allNegInf = false;
                    break;
                }
            }
            /*
                try {
                    ExecutorService executor = Executors.newFixedThreadPool(4);

                    for (Particle particle : particles) {
                        executor.submit(() -> particle.setPhyloWeight(1/ (double) N));
                    }

                    executor.shutdown();
                    boolean done = executor.awaitTermination(Long.MAX_VALUE, TimeUnit.SECONDS);
                    if (!done) {
                        System.err.println("Not all tasks completed within the specified timeout.");
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Interrupted while waiting", e);
                } */
            return allNegInf;

        }
    }
    public boolean checkLikelihoods() {
        boolean allBothNegInf = true;
        for (Particle particle : particles) {
            if (!(Double.isInfinite(Math.log(particle.getEpiLikelihood())) && Double.isInfinite(particle.getPhyloLikelihood()))) {
                allBothNegInf = false;
                break;
            }
        }
        return allBothNegInf;
    }

    //Weights utilities
    public void updateWeights(double confidenceSplit) {
        try {
            ExecutorService executor = Executors.newFixedThreadPool(Storage.numThreads);

            for (Particle particle : particles) {
                executor.submit(() -> particle.updateWeight(confidenceSplit));
            }

            executor.shutdown();
            boolean done = executor.awaitTermination(Long.MAX_VALUE, TimeUnit.SECONDS);
            if (!done) {
                System.err.println("Not all tasks completed within the specified timeout.");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while waiting", e);
        }
    }
    public double scalePhyloWeights() {
        double[] particleWeights = new double[N];
        double[] logParticleWeights = new double[N];
        double maxLogWeight = Double.NEGATIVE_INFINITY;


        int iter = 0;
        for (Particle particle : particles) {
            if (Double.isNaN(particle.getPhyloLikelihood())) {
                particle.setPhyloLikelihood(Double.NEGATIVE_INFINITY);
            }
            maxLogWeight = Math.max(particle.phyloLikelihood, maxLogWeight);
            logParticleWeights[iter] = particle.phyloLikelihood;
            iter++;
        }

        double sumOfScaledWeights = 0;
        for (int p=0; p<N; p++){
            particleWeights[p] = Math.exp(logParticleWeights[p] - maxLogWeight);
            sumOfScaledWeights += particleWeights[p];
        }

        for (int p=0; p<N; p++){
            particles[p].setPhyloWeight(particleWeights[p]/sumOfScaledWeights);
        }

        double logP = Math.log(sumOfScaledWeights/N) + maxLogWeight;
        return logP;
    }
    public double scaleEpiWeights() {
        double[] particleWeights = new double[N];
        double[] logParticleWeights = new double[N];
        double maxLogWeight = Double.NEGATIVE_INFINITY;

        int iter = 0;
        for (Particle particle : particles) {
            //if (Double.isInfinite(Math.log(particle.epiLikelihood))) {
            if (Double.isInfinite(particle.epiLikelihood)) {
                particle.epiWeight = Double.NEGATIVE_INFINITY;
            }
            maxLogWeight = Math.max(particle.epiWeight, maxLogWeight);
            logParticleWeights[iter] = particle.epiWeight; //log form
            iter++;
        }

        double sumOfScaledWeights = 0;
        for (int p=0; p<N; p++) {
            particleWeights[p] = Math.exp(logParticleWeights[p] - maxLogWeight);
            sumOfScaledWeights += particleWeights[p];
        }

        for (int p=0; p<N; p++){
            particles[p].setEpiWeight(particleWeights[p]/sumOfScaledWeights);
        }

        double logP = Math.log(sumOfScaledWeights/N) + maxLogWeight; //Tims version had this but I've read ab the below
        //double logP = Math.log(sumOfScaledWeights);
        return logP;
    }
    public double scaleWeightsAndGetLogP(double split) {
        if (Storage.isEpiOnly()) {
            double logP = scaleEpiWeights();
            for (int p=0; p<N; p++){
                particles[p].setWeight(particles[p].getEpiWeight());
            }
            return logP;
        }
        else if (Storage.isPhyloOnly()) {
            double logP = scalePhyloWeights();
            for (int p=0; p<N; p++){
                particles[p].setWeight(particles[p].getPhyloWeight());
            }
            return logP;
        }
        else {
            updateWeights(split);
            double[] particleWeights = new double[N];
            double[] logParticleWeights = new double[N];
            double maxLogWeight = Double.NEGATIVE_INFINITY;

            int iter = 0;
            for (Particle particle : particles) {
                maxLogWeight = Math.max(particle.weight, maxLogWeight);
                logParticleWeights[iter] = particle.weight;
                iter++;
            }

            double sumOfScaledWeights = 0;
            for (int p=0; p<N; p++){
                particleWeights[p] = Math.exp(logParticleWeights[p] - maxLogWeight);
                sumOfScaledWeights += particleWeights[p];
            }

            for (int p=0; p<N; p++){
                particles[p].setWeight(particleWeights[p]/sumOfScaledWeights);
            }
            double logP = Math.log(sumOfScaledWeights/N) + maxLogWeight;
            return logP;
        }

    }


    //Actual propagation
    public void predictAndUpdate(int step, Tree tree, double[][] rates, int increments, int origin){
        ExecutorService executor = Executors.newFixedThreadPool(Storage.numThreads);
        try {
            int t = step*Storage.resampleEvery;
            //Tree segments made here
            TreeSegment[] treeSegments = new TreeSegment[increments];

            int ind = 0;
            for (int i=t; i<t+increments; i++) {
                treeSegments[ind] = tree.segmentedTree[i];
                ind++;
            }



            for (Particle particle : particles) {
                executor.submit(() -> ProcessModel.step(particle, treeSegments, step, rates, origin));
            }

            executor.shutdown();
            boolean done = executor.awaitTermination(Long.MAX_VALUE, TimeUnit.SECONDS);
            if (!done) {
                System.err.println("Not all tasks completed within the specified timeout.");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while waiting", e);
        }
    }
    public void epiOnlyPredictAndUpdate(int step, double[][] rates, int increments, int origin){
        ExecutorService executor = Executors.newFixedThreadPool(Storage.numThreads);
        try {
            for (Particle particle : particles) {
                executor.submit(() -> ProcessModel.epiOnlyStep(particle, step, rates, increments, origin));
            }
            executor.shutdown();
            boolean done = executor.awaitTermination(Long.MAX_VALUE, TimeUnit.SECONDS);
            if (!done) {
                System.err.println("Not all tasks completed within the specified timeout.");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while waiting", e);
        }
    }


    //Resampling
    public void resampleParticles(int resamplingStep) throws IOException {

        int[] resampledParticleIDs = new int[N];
        int[] resampledParticleStates = new int[N];
        double[] resampledParticleLikelihoods = new double[N];

        Particle[] resampledParticles = new Particle[N];
        double totalWeight = 0.0;

        // Calculate the total weight of all particles
        for (Particle particle : particles) {
            totalWeight += particle.weight;
        }
        //System.out.println(totalWeight);

        /*
        for (int i = 0; i < N; i++) {
            betas[i][0+(resamplingStep*7)] = particles[i].beta.get(0+(resamplingStep*7));
            betas[i][1+(resamplingStep*7)] = particles[i].beta.get(1+(resamplingStep*7));
            betas[i][2+(resamplingStep*7)] = particles[i].beta.get(2+(resamplingStep*7));
            betas[i][3+(resamplingStep*7)] = particles[i].beta.get(3+(resamplingStep*7));
            betas[i][4+(resamplingStep*7)] = particles[i].beta.get(4+(resamplingStep*7));
            betas[i][5+(resamplingStep*7)] = particles[i].beta.get(5+(resamplingStep*7));
            betas[i][6+(resamplingStep*7)] = particles[i].beta.get(6+(resamplingStep*7));
        }*/


        // Generate a random number between 0 and total weight
        Random random = new Random();
        for (int i = 0; i < N; i++) {
            //while (resampledParticles[i] == null) {
                double randomWeight = random.nextDouble() * totalWeight;
                double runningSum = 0.0;

                // Iterate through particles and select based on weights
                for (Particle particle : particles) {
                    runningSum += particle.weight;
                    if (particle.weight == 0.0) {
                        //System.out.println("skipping particle with 0 weight");
                        continue;
                    }
                    if (runningSum >= randomWeight) {
                        resampledParticleIDs[i] = particle.particleID;
                        resampledParticleStates[i] = particle.getState();
                        resampledParticleLikelihoods[i] = particle.getPhyloLikelihood();
                        resampledParticles[i] = new Particle(particle, i);
                        break;
                    }
                }
            //}
        }
        /*
        if (!Storage.initialised) {
            Storage.particleLoggers.resamplingUpdate(resampledParticleIDs, resampledParticleStates, resampledParticleLikelihoods);
        }
        System.out.println("checkpoint"); */
        particles = resampledParticles;
    }

    //Checkstates
    public void checkStates(int limit) {
        ExecutorService executor = Executors.newFixedThreadPool(Storage.numThreads);
        Storage.tooBig = true;
        try {
            for (Particle particle : particles) {
                executor.submit(() -> particle.checkState(limit));
            }
            executor.shutdown();
            boolean done = executor.awaitTermination(Long.MAX_VALUE, TimeUnit.SECONDS);
            if (!done) {
                System.err.println("Not all tasks completed within the specified timeout.");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while waiting", e);
        }
    }

}
