
import java.io.IOException;

public class Main {
    public static void main(String[] args) throws IOException {
        System.out.println("EpiFusion");

        //Define params
        int numParticles = 1;
        int numIterations = 0;
        Storage.setEpiGrainyResolution();
        Storage.setPhyloOnly();
        int resampleEvery = 7;
        Storage.setResampling(resampleEvery);
        int T;

        //Read in tree
        Tree tree = new Tree("/Users/ciarajudge/Desktop/PhD/EpiFusionData/basesim2_simulatedtree.txt");

        //Read in case incidence
        Incidence caseIncidence = new Incidence("/Users/ciarajudge/Desktop/PhD/EpiFusionData/basesim2_weeklyincidence.txt");

        //Define more params based on data
        int epiLength = Storage.isEpiGrainyResolution() ? resampleEvery * caseIncidence.length : caseIncidence.length;
        double phyloLength = tree.age;
        T = Math.max(epiLength, (int) Math.round(phyloLength));

        //Initialise particle filter instance
        double[] initialParameters = {0.06, -0.043, 0.4, 0.233, 0.007};
        ParticleFilter particleFilter = new ParticleFilter(numParticles, initialParameters, tree, caseIncidence, T, resampleEvery);

        //Initialise and run MCMC instance
        MCMC particleMCMC = new MCMC(particleFilter);
        particleMCMC.runMCMC(numIterations);

        particleFilter.loggers.trajectories.close();
        //Save output
        //double[][] finalParams = particleFilter.getCurrentParameters();

    }
}