import org.apache.commons.math3.distribution.PoissonDistribution;
import java.util.Arrays;
import cern.jet.random.Poisson;

import static cern.jet.random.Poisson.staticNextInt;

public class ProcessModel {
    //Steps functions
    public static void step(Particle particle, TreeSegment[] treeSegments, int step, double[][] rates, int origin) {
        int t = step*Storage.resampleEvery;
        int increments = treeSegments.length;

        if (Storage.analysisType == 4) {
            particle.betaLinearSpline(increments);
        }

        for (int i=0; i<increments; i++) {
            //Turn on tree if we've reached it
            int actualDay = t+i;

            if (!particle.treeOn && !(treeSegments[i].lineages == 0 && treeSegments[i].births == 0)) { //So if storage is false, and (characteristics of an inactive tree) is false, this means it's time to activate the tree
                particle.treeOn = true;
                particle.haveReachedTree = true;
            } else if (particle.haveReachedTree && (treeSegments[i].lineages == 0 && treeSegments[i].births == 0)) {
                particle.treeOn = false;
            }
            if (particle.treeOn) { //If the tree is on we can just do the day no questions asked
                if (actualDay < origin) {
                    preOriginDay(particle, actualDay);
                } else {
                    if (!Storage.segmentedDays) {
                        day(particle, treeSegments[i], actualDay, rates[i]);
                    } else {
                        segmentedDay(particle, treeSegments[i], actualDay, rates[i]);
                    }
                }
            } else if (!(Storage.isPhyloOnly())) { //If tree is off but we are running a combo this means we can use the epi only day
                if (actualDay < origin) {
                    preOriginDay(particle, actualDay);
                } else {
                    epiOnlyDay(particle, actualDay, rates[i]);
                }
            } else if (Storage.isPhyloOnly()) { //If tree is off and we are phylo only we first need to know if it's before or after tree activation
                if (particle.haveReachedTree) {
                    //System.out.println("Tree finished, quitting");
                    break;
                } else {
                    //System.out.println("Day "+actualDay+" tree not active yet");
                    if (actualDay < origin) {
                        preOriginDay(particle, actualDay);
                    } else {
                        epiOnlyDay(particle, actualDay, rates[i]);
                    }
                }
            }
        }
    }

    public static void epiOnlyStep(Particle particle, int step, double[][] rates, int increments, int origin) {
        int t = step*Storage.resampleEvery;
        if (Storage.analysisType == 4) {
            particle.betaLinearSpline(increments);
        }
        for (int i=0; i<increments; i++) {
            int actualDay = t+i;
            //System.out.println("Sending particle "+particle.particleID+" for day "+actualDay+", State currently: "+particle.getState());
            epiOnlyDay(particle, actualDay, rates[i]);
            //here
        }

    }

    //Days functions
    public static void day(Particle particle, TreeSegment tree, int t, double[] dayRates) {
        //Check if the particle phylo likelihood is negative infinity, if so just quit
        //add positive tests to Epi
        int state = particle.getState();
        double[] rates = new double[] {dayRates[0], dayRates[1], dayRates[2], dayRates[3]};
        particle.positiveTests = particle.positiveTests +  (int)  Math.round(state*rates[3]);

        if (Storage.analysisType == 1) { //If beta is RW, get new propensity[0]
            particle.nextBeta(dayRates[0]);
            rates[0] = particle.beta.get(particle.beta.size()-1);
        } else if (Storage.analysisType == 2) { //old analysis type I'm not pursuing
            particle.nextBeta(rates[0]);
            rates[0] = particle.beta.get(particle.beta.size()-1);
        } else if (Storage.analysisType == 4) {
            rates[0] = particle.beta.get(t);
        }

        if (Double.isInfinite(particle.getPhyloLikelihood())) {
            Day tmpDay = new Day(t, particle.getState(), 0, 0);
            particle.updateTrajectory(tmpDay);
            return;
        } else if (state == 0 & tree.lineages > 0) {
            Day tmpDay = new Day(t, particle.getState(), 0, 0);
            particle.updateTrajectory(tmpDay);
            particle.setPhyloLikelihood(Double.NEGATIVE_INFINITY);
            return;
        }

        double[] propensities = particle.getVanillaPropensities(rates);

        if (tree.lineages == 0) { //this means the tree must start in this interval, so we do a new propensity calc
            double segmentBeginning = t + 1 - tree.birthTimes.get(0);
            double segmentEnding = 1 - segmentBeginning;
            int newBirths = poissonSampler(propensities[0]*segmentBeginning);
            state = state + newBirths;
            propensities = transformPropensities(propensities, segmentEnding);
        }


        //Divide the propensities into their bits
        double unobservedInfectProp = state > 0
                ? propensities[0] * (1.0 - tree.lineages * (tree.lineages - 1) / (double) state/(state+1))
                : 0.0;
        if (unobservedInfectProp < 0.0) {
            particle.setPhyloLikelihood(Double.NEGATIVE_INFINITY);
            return;
        }

        double observedInfectProp = propensities[0] - unobservedInfectProp;

        double allowedRecovProp, forbiddenRecovProp;
        if (state > tree.lineages + propensities[1] +tree.samplings) { //Previous version of this was: tree.lineages + propensities[1] + tree.samplings + 1 (stops recov past limit issue)
            allowedRecovProp = propensities[1];
            forbiddenRecovProp = 0.0;
        }
        else {
            allowedRecovProp = 0.0;
            forbiddenRecovProp = propensities[1];
        }

        double sampleProp = propensities[2];

        //Calculate the events
        int births = poissonSampler(unobservedInfectProp);
        int deaths = poissonSampler(allowedRecovProp);
        state = state + births - deaths;
        particle.setState(state);
        particle.todaysInfs = particle.todaysInfs + births;


        if (tree.lineages > 0) {
            double[] adjustedPropensities = new double[]{observedInfectProp, unobservedInfectProp, allowedRecovProp, forbiddenRecovProp, sampleProp};
            double todayPhyloLikelihood = PhyloLikelihood.calculateLikelihood(tree, particle, adjustedPropensities, t);
            /*if (t > 405 & t < 413) {
                System.out.println("Particle "+particle.particleID+", day "+t+", likelihood: "+todayPhyloLikelihood);
            }*/
            if (Double.isInfinite(todayPhyloLikelihood)) {
                particle.setPhyloLikelihood(Double.NEGATIVE_INFINITY); //set phylo Likelihood of that particle to negative infinity which will quit the loop
                return;
            }

            particle.setPhyloLikelihood(particle.getPhyloLikelihood()+todayPhyloLikelihood);
        } else {
            particle.setState(particle.getState()+tree.births);
        }
        Day tmpDay = new Day(t, particle.getState(), births, deaths);
        particle.updateTrajectory(tmpDay);
        particle.incrementCumInfections();


        //If there's incidence for this day, calc epi likelihood
        if (!Storage.isPhyloOnly()) {
            if (Arrays.stream(Storage.incidence.times).anyMatch(num -> num == t)) {
                particle.setEpiLikelihood(EpiLikelihood.epiLikelihood(Storage.incidence.pairedData.get(t), particle));
            }
        }
    }

    public static void segmentedDay(Particle particle, TreeSegment tree, int t, double[] dayRates) {
        //Check if the particle phylo likelihood is negative infinity, if so just quit
        double[] rates = new double[] {dayRates[0], dayRates[1], dayRates[2], dayRates[3]};
        if (Storage.analysisType == 1) { //If beta is RW, get new propensity[0]
            particle.nextBeta(dayRates[0]);
            rates[0] = particle.beta.get(particle.beta.size()-1);
            //System.out.println("Beta: "+rates[0]);
        } else if (Storage.analysisType == 4) {
            rates[0] = particle.beta.get(t);
        }

        if (Double.isInfinite(particle.getPhyloLikelihood())) {
            Day tmpDay = new Day(t, particle.getState(), 0, 0);
            particle.updateTrajectory(tmpDay);
            return;
        }



        int prevState = particle.getState();
        particle.positiveTests = particle.positiveTests +  (int)  Math.round(prevState*rates[3]);


        double prevLikelihood = particle.getPhyloLikelihood();
        double deltaT, nextT;
        double currentT = t;
        int state, births, deaths, eventType;
        double[] propensities, adjustedPropensities;
        double unobservedInfectProp, observedInfectProp, allowedRecovProp, forbiddenRecovProp, sampleProp;
        int treeLineages = tree.lineages;

        //Loop through thingy
        for (int e = 0; e < tree.observationOrder.length + 1; e++ ) {
            //Figure out what the segment time is
            nextT = e == tree.observationOrder.length ? t+1 : tree.observationTimes.get(e);
            deltaT = nextT - currentT;

            //Get propensities and state
            state = particle.getState();
            propensities = particle.getSegmentPropensities(rates, deltaT);
            double recovProp = propensities[1];

            //Adjust the propensities
            unobservedInfectProp = state > 0
                    ? propensities[0] * (1.0 - treeLineages * (treeLineages - 1) / (double) state/(state+1))
                    : 0.0;
            if (unobservedInfectProp < 0.0) {
                particle.setPhyloLikelihood(Double.NEGATIVE_INFINITY);
                return;
            }
            observedInfectProp = propensities[0] - unobservedInfectProp;
            if (state > treeLineages + propensities[1] + tree.samplings) { //Previous version of this was: tree.lineages + propensities[1] + tree.samplings + 1 (stops recov past limit issue)
                allowedRecovProp = recovProp;
                forbiddenRecovProp = 0.0;
            } else {
                allowedRecovProp = 0.0;
                forbiddenRecovProp = recovProp;
            }
            sampleProp = propensities[2];

            //Increment the states
            births = poissonSampler(unobservedInfectProp);
            deaths = poissonSampler(allowedRecovProp);
            state = state + births - deaths;
            particle.setState(state);
            particle.todaysInfs = particle.todaysInfs + births;


            //Phylo likelihood calculation
            adjustedPropensities = new double[]{observedInfectProp, unobservedInfectProp, allowedRecovProp, forbiddenRecovProp, sampleProp};
            eventType = nextT == t+1 ? 2 : tree.observationOrder[e];
            double todayPhyloLikelihood = PhyloLikelihood.calculateSegmentLikelihood(particle, adjustedPropensities, eventType, t);
            if (Double.isInfinite(todayPhyloLikelihood) || Double.isNaN(todayPhyloLikelihood)) {
                particle.setPhyloLikelihood(Double.NEGATIVE_INFINITY);
                return;
            }
            particle.setPhyloLikelihood(particle.getPhyloLikelihood()+todayPhyloLikelihood);
            //Adjust treeLineages and nextT
            currentT = nextT;
            if (eventType == 0) {
                treeLineages += 1;
            } else if (eventType == 1) {
                treeLineages -= 1;
            }
        }

        Day tmpDay = new Day(t, particle.getState(), 0, 0);
        particle.updateTrajectory(tmpDay);
        particle.incrementCumInfections();

        //particle.likelihoodVector.add(particle.phyloLikelihood);

        if (!Storage.isPhyloOnly()) {
            if (Arrays.stream(Storage.incidence.times).anyMatch(num -> num == t)) {
                particle.setEpiLikelihood(EpiLikelihood.epiLikelihood(Storage.incidence.pairedData.get(t), particle));
            }
        }
    }

    public static void epiOnlyDay(Particle particle, int t, double[] dayRates) {
        if (particle.getEpiLikelihood() == Double.NEGATIVE_INFINITY) {
            Day tmpDay = new Day(t, particle.getState(), 0, 0);
            particle.updateTrajectory(tmpDay);
            return;
        }

        //Check if the particle phylo likelihood is negative infinity, if so just quit
        int state = particle.getState();
        if (state < 0) {
            particle.setEpiLikelihood(Double.NEGATIVE_INFINITY);
            Day tmpDay = new Day(t, particle.getState(), 0, 0);
            particle.updateTrajectory(tmpDay);
            return;
        } else if (state == 0) {
            particle.setEpiLikelihood(Double.NEGATIVE_INFINITY);
            Day tmpDay = new Day(t, state, 0, 0);
            particle.updateTrajectory(tmpDay);
            return;
        }
        double[] rates = new double[] {dayRates[0], dayRates[1], dayRates[2], dayRates[3]};
        particle.positiveTests = particle.positiveTests +  (int)  Math.round(state*rates[3]);

        //System.out.println("EpiOnlyDay "+t+" Particle "+particle.particleID+" state: "+state);
        if (Storage.analysisType == 1) {
            particle.nextBeta(dayRates[0]);
            rates[0] = particle.beta.get(particle.beta.size()-1);
        } else if (Storage.analysisType == 2) {
            particle.nextBeta(rates[0]);
            rates[0] = particle.beta.get(particle.beta.size()-1);
        } else if (Storage.analysisType == 4) {
            rates[0] = particle.beta.get(t);
        }

        double[] propensities = particle.getVanillaPropensities(rates);

        //Calculate the events
        int births = poissonSampler(propensities[0]);
        particle.todaysInfs = particle.todaysInfs + births;
        //System.out.println("[Day "+t+" Particle "+particle.particleID+"]"+"births: "+births);
        int deaths = 0;
        if (t > 1) {
            deaths = poissonSampler(propensities[1]);
            //System.out.println("[Day "+t+" Particle "+particle.particleID+"]"+"deaths: "+deaths);
        }
        state = state + births - deaths;
        particle.setState(state);

        Day tmpDay = new Day(t, particle.getState(), births, deaths);
        particle.updateTrajectory(tmpDay);
        particle.incrementCumInfections();

        if (!Storage.isPhyloOnly()) {
            if (Arrays.stream(Storage.incidence.times).anyMatch(num -> num == t)) {
                double ll = EpiLikelihood.epiLikelihood(Storage.incidence.pairedData.get(t), particle);
                //System.out.println("EpiOnlyDay "+t+" Particle "+particle.particleID+" likelihood: "+ll);
                particle.setEpiLikelihood(ll);
            }
        }
    }

    public static void preOriginDay(Particle particle, int t) {
        int state = 0;
        int births = 0;
        int deaths = 0;

        Day tmpDay = new Day(t, state, births, deaths);
        particle.updateTrajectory(tmpDay);
        particle.incrementCumInfections();
    }

    //Housekeeping functions
    public static double[] transformPropensities(double[] propensities, double segmentTime) {
        double[] newPropensities = new double[propensities.length];
        for ( int i=0; i<propensities.length; i++){
            newPropensities[i] = propensities[i] * segmentTime;
        }
        return newPropensities;
    }

    public static int poissonSampler(double rate) {
        /*if (rate <= 0.0) {
            return 0;
        }*/
        int res = staticNextInt(rate);
        return res;
    }

}
