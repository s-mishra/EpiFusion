import java.io.File;
import java.io.FileWriter;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;


public class Loggers {
    File folder;
    public FileWriter trajectories;
    public FileWriter likelihoods;
    public FileWriter params;
    public FileWriter acceptance;
    public FileWriter completed;
    public FileWriter betas;
    public FileWriter cumInfections;
    public FileWriter positiveTests;
    public FileWriter particleLikelihoods;
    public String[] timewiseParamLabels;
    public FileWriter[] timewiseParams;
    public FileWriter allLikelihoods;
    public FileWriter treeIndexes;
    private String filePath;
    private int chainID;

    public Loggers(int chainID) throws IOException {
        this.filePath = Storage.folder;
        this.chainID = chainID;
        startTrajectories();
        startLikelihoods();
        startParams();
        startAcceptance();
        startCompleted();
        startBetas();
        startCumInfections();
        //this.allLikelihoods = new FileWriter(filePath + "/alllikelihoods.txt");
        //this.treeIndexes = new FileWriter(filePath + "/sampledTrees.txt");
        if (!Storage.isPhyloOnly()) {
            startPositiveTests();
        }
    }


    public void startTrajectories() throws IOException {
        FileWriter trajectories = new FileWriter(filePath+"/trajectories_chain"+chainID+".csv");
        this.trajectories = trajectories;
        trajectoryHeader();
    }
    public void startLikelihoods() throws IOException {
        FileWriter likelihoods = new FileWriter(filePath+"/likelihoods_chain"+chainID+".txt");
        this.likelihoods = likelihoods;
    }
    public void startParams() throws IOException {
        FileWriter params = new FileWriter(filePath+"/params_chain"+chainID+".csv");
        this.params = params;
        paramsHeader();
        //Find Timewise Params
        int numTimewiseParams = 0;
        for (int i = 0; i < Storage.priors.parameters.length; i++) {
            Parameter p = Storage.priors.parameters[i];
            if (p.buffer > 0) {
                numTimewiseParams++;
            }
        }
        this.timewiseParamLabels = new String[numTimewiseParams];
        this.timewiseParams = new FileWriter[numTimewiseParams];
        int index = 0;
        for (int i = 0; i < Storage.priors.parameters.length; i++) {
            Parameter p = Storage.priors.parameters[i];
            if (p.buffer > 0) {
                timewiseParamLabels[index] = Storage.priors.parameters[i].label;
                timewiseParams[index] = new FileWriter(filePath+"/"+Storage.priors.parameters[i].label+"_chain"+chainID+".csv");
                index++;
            }
        }
        timeWiseParamsHeader();

    }
    public void startAcceptance() throws IOException {
        FileWriter acceptance = new FileWriter(filePath+"/acceptance_chain"+chainID+".txt");
        this.acceptance = acceptance;
    }
    public void startCompleted() throws IOException {
        FileWriter completed = new FileWriter(filePath+"/completed_chain"+chainID+".txt");
        this.completed = completed;
    }
    public void startBetas() throws IOException {
        FileWriter betas = new FileWriter(filePath+"/betas_chain"+chainID+".txt");
        this.betas = betas;
        betaHeader();
    }
    public void startCumInfections() throws IOException {
        FileWriter cumInfections = new FileWriter(filePath+"/cuminfections_chain"+chainID+".txt");
        this.cumInfections = cumInfections;
        cumInfectionsHeader();
    }
    public void startPositiveTests() throws IOException {
        FileWriter positiveTests = new FileWriter(filePath+"/positivetests_chain"+chainID+".csv");
        this.positiveTests = positiveTests;
        positiveTestHeader();
    }

    public void trajectoryHeader() throws IOException {
        String toWrite = "";
        for (int i = (Storage.resampleEvery*Storage.firstStep); i < Storage.T+1; i++) {
            toWrite = toWrite + "T_"+ i + ",";
        }
        toWrite = toWrite + "\n";
        //System.out.println(toWrite);
        trajectories.write(toWrite);
    }
    public void cumInfectionsHeader() throws IOException {
        String toWrite = "";
        for (int i = (Storage.resampleEvery*Storage.firstStep); i < Storage.T+1; i++) {
            toWrite = toWrite + "T_"+ i + ",";
        }
        toWrite = toWrite + "\n";
        //System.out.println(toWrite);
        cumInfections.write(toWrite);
    }
    public void betaHeader() throws IOException {
        String toWrite = "";
        for (int i = (Storage.resampleEvery*Storage.firstStep); i < Storage.T+1; i++) {
            toWrite = toWrite + "T_"+ i + ",";
        }
        toWrite = toWrite + "\n";
        //System.out.println(toWrite);
        betas.write(toWrite);
    }
    public void positiveTestHeader() throws IOException {
        String toWrite = "";
        for (int i = 0; i < Storage.incidence.times.length; i++) {
            toWrite = toWrite + "T_"+ i + ",";
        }
        toWrite = toWrite + "\n";
        //System.out.println(toWrite);
        positiveTests.write(toWrite);
    }
    public void paramsHeader() throws IOException {
        String toWrite = "";
        for (int i=0; i<Storage.priors.labels.size(); i++) {
            toWrite = toWrite + Storage.priors.labels.get(i) + ",";
        }
        toWrite = toWrite + "\n";
        params.write(toWrite);
    }

    public void timeWiseParamsHeader() throws IOException {
        String toWrite = "";
        for (int i = (Storage.resampleEvery*Storage.firstStep); i < Storage.T+1; i++) {
            toWrite = toWrite + "T_"+ i + ",";
        }
        toWrite = toWrite + "\n";
        //System.out.println(toWrite);
        for (FileWriter file : timewiseParams) {
            file.write(toWrite);
        }
    }

    public void logTrajectory(Trajectory trajectory) throws IOException {
        String toWrite = "";
        for (Day d : trajectory.trajectory) {
            toWrite = toWrite + d.I + ",";
        }
        toWrite = toWrite + "\n";
        //System.out.println(toWrite);
        trajectories.write(toWrite);
    }

    public void logBeta(ArrayList<Double> betaArray) throws IOException {
        String toWrite = "";
        for (Double aDouble : betaArray) {
            toWrite = toWrite + aDouble + ",";
        }
        toWrite = toWrite + "\n";
        //System.out.println(toWrite);
        betas.write(toWrite);
    }

    public void logParameter(ParticleFilter particleFilter) throws IOException {
        for (int i = 0; i < timewiseParams.length; i++) {
            String toWrite = "";
            if (timewiseParamLabels[i].equals("phi")) {
                for (double[] d : particleFilter.getCurrentRates()) {
                    toWrite = toWrite + d[3] + ",";
                }
            } else if (timewiseParamLabels[i].equals("psi")) {
                for (double[] d : particleFilter.getCurrentRates()) {
                    toWrite = toWrite + d[2] + ",";
                }
            } else if (timewiseParamLabels[i].equals("gamma")) {
                for (double[] d : particleFilter.getCurrentRates()) {
                    toWrite = toWrite + d[1] + ",";
                }
            }
            toWrite = toWrite + "\n";
            //System.out.println(toWrite);
            timewiseParams[i].write(toWrite);
        }
    }


    public void logCumInfections(ArrayList<Integer> rArray) throws IOException {
        String toWrite = "";
        for (Integer aDouble : rArray) {
            toWrite = toWrite + aDouble + ",";
        }
        toWrite = toWrite + "\n";
        //System.out.println(toWrite);
        cumInfections.write(toWrite);
    }
    public void logLogLikelihoodAccepted(Double likelihood) throws IOException {
        String toWrite = likelihood + "\n";
        this.likelihoods.write(toWrite);
    }
    public void logallLikelihoodAccepted(Double likelihood) throws IOException {
        String toWrite = likelihood + "\n";
        this.allLikelihoods.write(toWrite);
    }
    public void logParams(double[] paramSet) throws IOException {
        String toWrite = "";
        for (Double param : paramSet) {
            toWrite = toWrite + param + ",";
        }
        toWrite = toWrite + "\n";
        this.params.write(toWrite);
    }
    public void logAcceptance(double accept) throws IOException {
        String toWrite = accept + "\n";
        this.acceptance.write(toWrite);
    }
    public void logCompleted(Double complete) throws IOException {
        String toWrite = complete + "\n";
        this.completed.write(toWrite);
    }
    public void logTreeIndex(int treeIndex) throws IOException {
        String toWrite = treeIndex + "\n";
        this.treeIndexes.write(toWrite);
    }

    public void logPositiveTests(ArrayList<Integer> positives) throws IOException {
        String toWrite = "";
        for (Integer p : positives) {
            toWrite = toWrite + p + ",";
        }
        toWrite = toWrite + "\n";
        //System.out.println(toWrite);
        positiveTests.write(toWrite);
    }

/*
    public void logParticleLikelihoods(Particle p) throws IOException {

            String toWrite = "";
            for (Double d : p.likelihoodVector) {
                toWrite = toWrite + d + ",";
            }
            toWrite = toWrite + "\n";
            //System.out.println(toWrite);
            particleLikelihoods.write(toWrite);

    }*/

    public void saveParticleLikelihoodBreakdown(double[][] likelihoodBreakdown, int step, double likelihood) throws IOException {
        FileWriter likelihoodBreakdownFile = new FileWriter(Storage.folder+"/likelihoodbreakdown_"+step+"_"+likelihood+".csv");
        for (double[] r : likelihoodBreakdown) {
            String toWrite = "";
            for (double c : r) {
                toWrite = toWrite + c + ",";
            }
            toWrite = toWrite + "\n";
            likelihoodBreakdownFile.write(toWrite);
        }
        likelihoodBreakdownFile.close();
    }

    public void saveParticleTrajectoryBreakdown(int[][] trajectories) throws IOException {
        FileWriter likelihoodBreakdownFile = new FileWriter(Storage.folder+"/trajectorybreakdown.csv");
        for (int[] r : trajectories) {
            String toWrite = "";
            for (int c : r) {
                toWrite = toWrite + c + ",";
            }
            toWrite = toWrite + "\n";
            likelihoodBreakdownFile.write(toWrite);
        }
        likelihoodBreakdownFile.close();
    }

    public void saveParticleBetaBreakdown(double[][] betaBreakdown) throws IOException {
        FileWriter likelihoodBreakdownFile = new FileWriter(Storage.folder+"/betabreakdown.csv");
        for (double[] r : betaBreakdown) {
            String toWrite = "";
            for (double c : r) {
                toWrite = toWrite + c + ",";
            }
            toWrite = toWrite + "\n";
            likelihoodBreakdownFile.write(toWrite);
        }
        likelihoodBreakdownFile.close();
    }

    public void flexiLogger(String filename, ArrayList<Double> list) throws IOException{
        FileWriter file = new FileWriter(filePath+"/"+filename+"_"+chainID);
        for (Double a:list) {
            file.write(a+"\n");
        }
        file.close();
    }

    public void flexiLogger(String filename, double[] list) throws IOException{
        FileWriter file = new FileWriter(filePath+"/"+filename+"_"+chainID);
        for (Double a:list) {
            file.write(a+"\n");
        }
        file.close();
    }

    public void log(ParticleFilter particleFilter, int accepted) throws IOException { //This bit is super messed up for debugging be aware!
        logLogLikelihoodAccepted(particleFilter.getLogLikelihoodCurrent());
        logTrajectory(particleFilter.currentSampledParticle.traj);
        particleFilter.currentSampledParticle.traj.printTrajectory();
        if (Storage.analysisType != 0 && Storage.analysisType != 3) {
            logBeta(particleFilter.currentSampledParticle.beta);
        }
        //logRs(rtCalculator.calculateRt(particleFilter.currentSampledParticle));
        logCumInfections(particleFilter.currentSampledParticle.cumInfections);
        logParams(particleFilter.getCurrentParameters());
        logCompleted((double) Storage.completedRuns[particleFilter.chainID]/Storage.logEvery);
        logAcceptance((double) accepted/Storage.logEvery);
        if (!Storage.isPhyloOnly()) {
            logPositiveTests(particleFilter.currentSampledParticle.positiveTestsFit);
        }
        logParameter(particleFilter);
    }

    public void debugLog(ParticleFilter particleFilter, int accepted) throws IOException { //This bit is super messed up for debugging be aware!
        logLogLikelihoodAccepted(particleFilter.getLogLikelihoodCandidate());
        logTrajectory(particleFilter.particles.particles[0].traj);
        particleFilter.particles.particles[0].traj.printTrajectory();
        if (Storage.analysisType != 0 && Storage.analysisType != 3) {
            logBeta(particleFilter.particles.particles[0].beta);
        }
        //logRs(rtCalculator.calculateRt(particleFilter.currentSampledParticle));
        logCumInfections(particleFilter.particles.particles[0].cumInfections);
        logParams(particleFilter.getCandidateParameters());
        logCompleted((double) Storage.completedRuns[particleFilter.chainID]/Storage.logEvery);
        logAcceptance((double) accepted/Storage.logEvery);
        if (!Storage.isPhyloOnly()) {
            logPositiveTests(particleFilter.particles.particles[0].positiveTestsFit);
        }
        logParameter(particleFilter);
        //logTreeIndex(particleFilter.sampledTree);
        //logParticleLikelihoods(particleFilter.particles.particles[0]);
    }

    public void terminateLoggers() throws IOException {
        trajectories.close();
        likelihoods.close();
        params.close();
        acceptance.close();
        betas.close();
        cumInfections.close();
        completed.close();
        //treeIndexes.close();
        //particleLikelihoods.close();
        //allLikelihoods.close();
        if (!Storage.isPhyloOnly()) {
            positiveTests.close();
        }
        for (FileWriter file : timewiseParams) {
            file.close();
        }

    }

}
