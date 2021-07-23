package tardis.implementation.data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;

/**
 * Class that predicts the possible label of a given path condition by comparing the
 * bloom filter structure of the item to be classified with all the items in the training set.
 * Class uses Jaccard's distance and the top 3 elements of the training set to perform the
 * classification; used to calculate the infeasibility index.
 * 
 * @author Matteo Modonato
 * @author Pietro Braione
 */
final class ClassifierKNN {
    private final int k;
    private final HashSet<TrainingItem> trainingSet = new HashSet<>();
    
    public ClassifierKNN(int k) {
        this.k = k;
    }
    
    public void train(Set<TrainingItem> newTrainingSet) {
        this.trainingSet.addAll(newTrainingSet);
    }

    public ClassificationResult classify(BloomFilter query) {
    	if (this.trainingSet.size() < k) {
    		final ClassificationResult trainingSetTooSmallOutput = ClassificationResult.unknown();
    		return trainingSetTooSmallOutput;
    	}
        //fills the list of neighbors (training set items with their Jaccard 
        //distance to query) and sorts it by Jaccard distance (in descending order)
        final ArrayList<Neighbor> neighborRanking = new ArrayList<>();
        for (TrainingItem item : this.trainingSet) {
            final double jaccardDistance = query.jaccardDistance(item.getBloomFilter());
            neighborRanking.add(new Neighbor(jaccardDistance, item.getLabel()));
        }
        Collections.sort(neighborRanking, new DistanceComparator());
        
        //analyzes the top k elements and counts how many are
        //uncertain, and how many classify with each label
        int countUncertain = 0;
        int countClassifyFalse = 0;
        int countClassifyTrue = 0;
        double sumOfDistance = 0;
        for (int l = 0; l < this.k; ++l){
            final boolean label = neighborRanking.get(l).label;
            final double distance = neighborRanking.get(l).distance;
            if (distance == 0) {
                ++countUncertain;
            } else if (label) { 
                ++countClassifyTrue;
            } else { //!label
                ++countClassifyFalse;
            }
            sumOfDistance += distance;
        }
        final double averageDistance = sumOfDistance / this.k;

        
        //builds the output
        final ClassificationResult output;
        if ((countUncertain >= countClassifyFalse && countUncertain >= countClassifyTrue) ||
            countClassifyFalse == countClassifyTrue) {
            //too many uncertains, or tie between 0 and 1 classification
            output = ClassificationResult.unknown();
        } else if (countClassifyFalse < countClassifyTrue) {
            output = ClassificationResult.of(true, countClassifyFalse, averageDistance);
        } else { //countClassifyFalse > countClassifyTrue
            output = ClassificationResult.of(false, countClassifyTrue, averageDistance);
        }
        
        return output;
    }

    private static class Neighbor {
        private final double distance;
        private final boolean label;
        private Neighbor(double distance, boolean label) {
            this.label = label;
            this.distance = distance;	    	    
        }
    }

    private static class DistanceComparator implements Comparator<Neighbor> {
        @Override
        public int compare(Neighbor a, Neighbor b) {
            return a.distance > b.distance ? -1 : a.distance == b.distance ? 0 : 1;
        }
    }
    
    static class ClassificationResult {
        private static final ClassificationResult UNKNOWN = new ClassificationResult();
        
        private final boolean unknown;
        private final boolean label;
        private final int voting;
        private final double averageDistance;
        
        static ClassificationResult unknown() {
            return UNKNOWN;
        }
        
        static ClassificationResult of(boolean label, int voting, double averageDistance) {
            return new ClassificationResult(label, voting, averageDistance);
        }
        
        private ClassificationResult() {
            this.unknown = true;
            this.label = false;
            this.voting = 0;
            this.averageDistance = 0.0;
        }
        
        private ClassificationResult(boolean label, int voting, double averageDistance) {
            this.unknown = false;
            this.label = label;
            this.voting = voting;
            this.averageDistance = averageDistance;
        }
        
        public boolean isUnknown() {
            return this.unknown;
        }
        
        public boolean getLabel() {
            return this.label;
        }
        
        public int getVoting() {
            return this.voting;
        }
        
        public double getAverageDistance() {
            return this.averageDistance;
        }
    }
}
