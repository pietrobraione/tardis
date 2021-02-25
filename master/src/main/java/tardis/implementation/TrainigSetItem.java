package tardis.implementation;

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
}
