package tardis.implementation;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Class that predicts the possible label of a given path condition by comparing the
 * bloom filter structure of the item to be classified with all the items in the training set.
 * Class uses Jaccard's distance and the top 3 elements of the training set to perform the
 * classification; used to calculate the infeasibility index.
 */
public class Knn {

	public static Object[] knn(LinkedBlockingQueue<TrainigSetItem> trainingset, BitSet[] query) {
		int k = 3;
		List<Result> resultList = new ArrayList<Result>();
		int countLabel0 = 0;
		int countLabel1 = 0;
		int countDistance0 = 0;
		int countDistance0Label0 = 0;
		int countDistance0Label1 = 0;
		double sumOfDistance = 0;

		//calculates the Jaccard distance between the query and each element of the training set
		for (TrainigSetItem item : trainingset) {
			double both=0;
			double atLeast=0;
			for (int i = 0; i < 16 ; i++) {
				for (int j = 0; j < 64; j++) {
					if (item.getBloomFilterStructure()[i].get(j) == true && query[i].get(j) == true)
						both++;
					else if (item.getBloomFilterStructure()[i].get(j) == false && query[i].get(j) == true)
						atLeast++;
					else if (item.getBloomFilterStructure()[i].get(j) == true && query[i].get(j) == false)
						atLeast++;
				}
			}
			double jaccardDistance = both/(both+atLeast);
			resultList.add(new Result(jaccardDistance, item.getLabel()));
		}
		//sorts the results by Jaccard distance (in descending order)
		Collections.sort(resultList, new DistanceComparator());
		//analyzes the top 3 elements
		int[] nearestKPoints = new int[k];
		double[] nearestKPointsDistance = new double[k];
		for(int l = 0; l < k; l++){
			nearestKPoints[l] = resultList.get(l).label;
			nearestKPointsDistance[l] = resultList.get(l).distance;
			if (nearestKPoints[l] == 0)
				++countLabel0;
			else
				++countLabel1;
		}
		for(int i=0; i<nearestKPointsDistance.length; i++){
			//handles corner cases
			if (nearestKPointsDistance[i] == 0) {
				++countDistance0;
			}
			else {
				if (nearestKPoints[i] == 0)
					++countDistance0Label0;
				else
					++countDistance0Label1;
			}
			sumOfDistance += nearestKPointsDistance[i];
		}
		double average = sumOfDistance/nearestKPointsDistance.length;
		
		//handles cases where one or more distances are zero: 
		//there are no valid items in the training set for that query yet
		if (countDistance0 == 3 || countDistance0 == 2) {
			//3 or 2 distances are 0: not a valid classification 
			Object[] output = new Object[]{0, 3, 0.0};
			return output;
		}
		else if (countDistance0==1) {
			if (countDistance0Label0==countDistance0Label1) {
				//1 distance is zero and the other two elements
				//have different label: not reliable classification
				Object[] output = new Object[]{0, 3, average};
				return output;
			}
			else if (countDistance0Label0==2) {
				//1 distance is zero and the other two elements
				//have label == 0: poor classification
				Object[] output = new Object[]{0, 2, average};
				return output;
			}
			else if (countDistance0Label1==2) {
				//1 distance is zero and the other two elements
				//have label == 1: poor classification
				Object[] output = new Object[]{1, 2, average};
				return output;
			}		
		}
		//standard cases
		if (countLabel0>countLabel1) {
			Object[] output = new Object[]{0, countLabel0, average};
			return output;
		}
		else {
			Object[] output = new Object[]{1, countLabel1, average};
			return output;
		}
	}

	static class Result {
		double distance;
		int label;
		public Result(double distance, int label){
			this.label = label;
			this.distance = distance;	    	    
		}
	}

	static class DistanceComparator implements Comparator<Result> {
		@Override
		public int compare(Result a, Result b) {
			return a.distance > b.distance ? -1 : a.distance == b.distance ? 0 : 1;
		}
	}
}
