package tardis.implementation;

import java.util.Arrays;
import java.util.BitSet;

/**
 * Class that defines the (bloomFilterStructure, label)
 * pair structure used in the training set. Used to
 * calculate the infeasibility index.
 */

public class TrainigSetItem {

	BitSet[] bloomFilterStructure;
	int label;

    /**
     * The items contained in the training set, used to calculate
     * the infeasibility index.
     * The items consist of a bloomFilterStructure-label pair.
     * 
     * @param bloomFilterStructure an array of BitSet. 
     * @param label a boolean true if EvoSuite generated a test case from path,
     *        false otherwise.
     */
	TrainigSetItem(BitSet[] bloomFilterStructure, int label){
		this.bloomFilterStructure = bloomFilterStructure;
		this.label = label;
	}

	BitSet[] getBloomFilterStructure(){
		return this.bloomFilterStructure;
	}

	int getLabel() {
		return this.label;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + Arrays.hashCode(bloomFilterStructure);
		result = prime * result + label;
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		TrainigSetItem other = (TrainigSetItem) obj;
		if (!Arrays.equals(bloomFilterStructure, other.bloomFilterStructure))
			return false;
		if (label != other.label)
			return false;
		return true;
	}
}
