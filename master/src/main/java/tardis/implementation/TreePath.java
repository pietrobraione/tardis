package tardis.implementation;

import static tardis.implementation.Util.shorten;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import jbse.mem.Clause;

/**
 * Stores the tree of the explored and yet-to-explored paths, 
 * with information about their path conditions, covered branches, 
 * and neighbor branches. Used to calculate heuristic indices.
 * 
 * @author Pietro Braione
 * @author Matteo Modonato
 */
public final class TreePath {
    /**
     * The covered items.
     */
    private final HashSet<String> coverage = new HashSet<>();

    /** Map used to track the times the tests hit a branch. */
    private final HashMap<String, Integer> hitsCounterMap = new HashMap<>(); //TODO ConcurrentHashMap?
    
    /** Set used as training set to calculate the infeasibility index. */
    public final HashSet<TrainigSetItem> trainingSet = new HashSet<>();
    
    private enum NodeStatus { ATTEMPTED, COVERED };
    
    /** The size of the bloom filter structures. */
    private final int N_ROWS = 16;
	private final int N_COLUMNS = 64;
    
    /**
     * A node in the {@link TreePath}.
     * 
     * @author Pietro Braione
     */
    private final class Node {
        /** The {@link Clause} associated to this node. */
        private final Clause clause;
        
        /** 
         * The status of this node (i.e., of the path
         * from the root to this node). 
         */
        private NodeStatus status = NodeStatus.ATTEMPTED;
        
        /** The children nodes. */
        private final List<Node> children = new ArrayList<>();
        
        /** The branches covered by the path. */
        private HashSet<String> coveredBranches = new HashSet<>();
        
        /** 
         * The neighbor frontier branches to the path used to 
         * calculate the improvability index.
         */
        private HashSet<String> neighborFrontierBranches = new HashSet<>();
        
        private BitSet[] bloomFilterStructure = new BitSet[N_ROWS];
        
        private int improvabilityIndex;
        
        private int noveltyIndex;
        
        private int infeasibilityIndex;

        /**
         * Constructor for a nonroot node.
         * 
         * @param clause The {@link Clause} stored
         *        in the node.
         */
        Node(Clause clause) {
            this.clause = clause;
        }

        /** 
         * Constructor for the root node. It does
         * not store any {@link Clause}.
         */
        Node() { 
            this(null);
        }

        /**
         * Determines whether this {@link Node} has
         * a child.
         * 
         * @param possibleChild The possible child 
         *        {@link Clause}.
         * @return The child of this Node that stores
         *         {@code possibleChild}, otherwise 
         *         {@code null}.
         */
        Node findChild(Clause possibleChild) {
            for (Node current : this.children) {
                if (current.clause.equals(possibleChild)) {
                    return current;
                }
            }
            return null;
        }

        /**
         * Adds a child to this {@link Node}.
         * 
         * @param newChild a {@link Clause}.
         * @return the created child {@link Node}, 
         *         that will store {@code newChild}.
         */
        Node addChild(Clause newChild) {
            final Node retVal = new Node(newChild);
            this.children.add(retVal);
            return retVal;
        }

		public BitSet[] getBloomFilterStructure() {
			return bloomFilterStructure;
		}

		public void setBloomFilterStructure(BitSet[] bloomFilterStructure) {
			this.bloomFilterStructure = bloomFilterStructure;
		}

		public int getImprovabilityIndex() {
			return improvabilityIndex;
		}

		public void setImprovabilityIndex(int improvabilityIndex) {
			this.improvabilityIndex = improvabilityIndex;
		}

		public int getNoveltyIndex() {
			return noveltyIndex;
		}

		public void setNoveltyIndex(int noveltyIndex) {
			this.noveltyIndex = noveltyIndex;
		}

		public int getInfeasibilityIndex() {
			return infeasibilityIndex;
		}

		public void setInfeasibilityIndex(int infeasibilityIndex) {
			this.infeasibilityIndex = infeasibilityIndex;
		}
    }

    /**
     * The root {@link Node}.
     */
    private final Node root = new Node();

    /**
     * Returns the root.
     * 
     * @return a {@link Node}.
     */
    synchronized Node getRoot() {
        return this.root;
    }

    /**
     * Checks whether an item is covered.
     * 
     * @param branch the item to be checked.
     * @return {@code true} iff the branch is covered.
     */
    synchronized boolean covers(String branch) {
        return this.coverage.contains(branch);
    }

    /**
     * Returns the number of covered items.
     * 
     * @return a positive {@code int}, the total number of covered branches.
     */
    synchronized int totalCovered() {
        return this.coverage.size();
    }

    /**
     * Returns the number of covered items matching
     * a given pattern.
     * 
     * @param pattern a {@link String}, a regular expression.
     * @return a positive {@code int}, the total number of covered 
     *         branches matching {@code pattern}.
     */
    synchronized int totalCovered(String pattern) {
        final Pattern p = Pattern.compile(pattern); 
        final Set<String> filtered = this.coverage.stream().filter(s -> { final Matcher m = p.matcher(s); return m.matches(); }).collect(Collectors.toSet());
        return filtered.size();
    }

    /**
     * Inserts a path in this {@link TreePath}.
     * 
     * @param path a sequence (more precisely, an {@link Iterable}) 
     *        of {@link Clause}s. The first in the sequence is the closer
     *        to the root, the last is the leaf.
     * @param coveredBranches a {@link Set}{@code <}{@link String}{@code >}, 
     *        the branches covered by {@code path}.
     * @param neighborFrontierBranches a {@link Set}{@code <}{@link String}{@code >} 
     *        containing the neighbor frontier branches next to {@code path}, used 
     *        to calculate the improvability index.
     * @param covered a {@code boolean}, {@code true} iff the path is
     *        covered by a test.
     * @return if {@code covered == true}, the {@link Set} of the elements in 
     *         {@code coveredBranches} that were not already covered before the 
     *         invocation of this method, otherwise returns {@code null}.
     */
    synchronized Set<String> insertPath(Collection<Clause> path, Set<String> coveredBranches, Set<String> neighborFrontierBranches, boolean covered) {
        int index = 0;
    	Node currentInTree = this.root;
        if (covered) {
            currentInTree.status = NodeStatus.COVERED;
        }

        for (Clause currentInPath : path) {
            final Node possibleChild = currentInTree.findChild(currentInPath);
            if (possibleChild == null) {
                currentInTree = currentInTree.addChild(currentInPath);
            } else {
                currentInTree = possibleChild;
            }
            if (covered) {
                currentInTree.status = NodeStatus.COVERED;
            }
            if (index == path.size() - 1) {
                currentInTree.coveredBranches.addAll(coveredBranches);
                currentInTree.neighborFrontierBranches.addAll(neighborFrontierBranches);
                if (!covered) {
                	currentInTree.setBloomFilterStructure(PCToBloomFilter(path));
                }
            }
            ++index;
        }

        if (covered) {
            final Set<String> retVal = coveredBranches.stream().filter(s -> !covers(s)).collect(Collectors.toSet());
            this.coverage.addAll(coveredBranches);
            increaseHits(coveredBranches);
            return retVal;
        } else {
            return null;
        }
    }
    
    /**
     * Increases by one the number of hits of a set of branches.
     * 
     * @param coveredBranches a {@link Set}{@code <}{@link String}{@code >}.
     */
    private synchronized void increaseHits(Set<String> coveredBranches) {
        for (String branch : coveredBranches) {
            final Integer hitsCounter = this.hitsCounterMap.get(branch);
            if (hitsCounter == null) {
                this.hitsCounterMap.put(branch, 1);
            } else {
                this.hitsCounterMap.put(branch, hitsCounter + 1);
            }
        }
    }

    /**
     * Checks whether a path exists in this {@link TreePath}.
     * 
     * @param path a sequence (more precisely, an {@link Iterable}) 
     *        of {@link Clause}s. The first in the sequence is the closer
     *        to the root, the last is the leaf.
     * @param covered a {@code boolean}, {@code true} iff the path must be
     *        covered by a test. 
     * @return {@code true} iff the {@code path} was inserted by means
     *         of one or more calls to {@link #insertPath(Iterable) insertPath}, 
     *         and in case {@code covered == true}, if it is also covered 
     *         by a test.
     */
    synchronized boolean containsPath(Iterable<Clause> path, boolean covered) {
        Node currentInTree = this.root;
        if (covered && currentInTree.status != NodeStatus.COVERED) {
            return false;
        }

        for (Clause currentInPath : path) {
            final Node child = currentInTree.findChild(currentInPath);
            if (child == null) {
                return false;
            }
            currentInTree = child;
            if (covered && currentInTree.status != NodeStatus.COVERED) {
                return false;
            }
        }
        return true;
    }
    
    /**
     * Saves the values of the heuristic indices associated to a given
     * path in this {@link TreePath} for future use.
     * 
     * @param indexValue an int. The value to be stored.
     * @param path a sequence (more precisely, an {@link Iterable}) 
     *        of {@link Clause}s. The first in the sequence is the closer
     *        to the root, the last is the leaf.
     * @param indexName a String. The name of the heuristic index:
     *        improvability, novelty or infeasibility.
     */
    synchronized void cacheIndices(int indexValue, Collection<Clause> path, String indexName) {
    	int index = 0;
    	Node currentInTree = this.root;

        for (Clause currentInPath : path) {
            final Node child = currentInTree.findChild(currentInPath);
            if (child == null) {
                return;
            }
            currentInTree = child;
            if (index == path.size() - 1) {
            	switch (indexName) {
            	case "improvability":
            		currentInTree.setImprovabilityIndex(indexValue);
            		break;
            	case "novelty":
            		currentInTree.setNoveltyIndex(indexValue);
            		break;
            	case "infeasibility":
            		currentInTree.setInfeasibilityIndex(indexValue);
            		break;
            	}
            }
            ++index;
        }
    }
    
    /**
     * Retrieves the values of the heuristic indices associated to a
     * given path previously saved in this {@link TreePath}.
     * 
     * @param path a sequence (more precisely, an {@link Iterable}) 
     *        of {@link Clause}s. The first in the sequence is the closer
     *        to the root, the last is the leaf.
     * @param indexName a String. The name of the heuristic index:
     *        improvability, novelty or infeasibility.
     */
    synchronized int getCachedIndices(Collection<Clause> path, String indexName) {
    	Node currentInTree = this.root;

        for (Clause currentInPath : path) {
            final Node child = currentInTree.findChild(currentInPath);
            if (child == null) {
                return -1;
            }
            currentInTree = child;
        }
        switch (indexName) {
    	case "improvability":
    		return currentInTree.getImprovabilityIndex();
    	case "novelty":
    		return currentInTree.getNoveltyIndex();
    	case "infeasibility":
    		return currentInTree.getInfeasibilityIndex();
    	}
        return -1;
    }
    
    /**
     * Returns the covered branches associated to a given path.
     * 
     * @param path an {@link Iterable}{@code <}{@link Clause}{@code >}. 
     *        The first is the closest to the root, the last is the leaf.
     * @return a {@link Set}{@code <}{@link String}{@code >} containing 
     *         the branches covered by the path.
     */
    synchronized Set<String> getCoveredBranches(Iterable<Clause> path) {
    	Node currentInTree = this.root;

        for (Clause currentInPath : path) {
            final Node child = currentInTree.findChild(currentInPath);
            if (child == null) {
                return null;
            }
            currentInTree = child;
        }
        return currentInTree.coveredBranches;
    }
    
    /**
     * Returns the neighbor frontier branches next to a given path, used 
     * to calculate the improvability index.
     * 
     * @param path an {@link Iterable}{@code <}{@link Clause}{@code >}. 
     *        The first is the closest to the root, the last is the leaf.
     * @return a {@link Set}{@code <}{@link String}{@code >} containing 
     *         the branches used to calculate the improvability index, or
     *         {@code null} if {@code path} is not present in this {@link TreePath}.
     */
    synchronized Set<String> getNeighborFrontierBranches(Iterable<Clause> path) {
    	Node currentInTree = this.root;

        for (Clause currentInPath : path) {
            final Node child = currentInTree.findChild(currentInPath);
            if (child == null) {
                return null;
            }
            currentInTree = child;
        }
        return currentInTree.neighborFrontierBranches;
    }
    
    /**
     * Updates the neighbor frontier branches next to a given path, used to 
     * calculate the improvability index, by removing a set of covered branches 
     * from the neighbor frontier branches.
     * 
     * @param path an {@link Iterable}{@code <}{@link Clause}{@code >}. 
     *        The first is the closest to the root, the last is the leaf.
     * @param coveredBranches a {@link Set}{@code <}{@link String}{@code >}, 
     *        the covered branches. 
     */
    synchronized void clearNeighborFrontierBranches(Iterable<Clause> path, Set<String> coveredBranches) {
        final Set<String> branches = getNeighborFrontierBranches(path);
        if (branches == null) {
            return;
        }
        branches.removeAll(coveredBranches);
    }
    
    /**
     * Calculates the improvability index of a given path.
     * 
     * @param path an {@link Iterable}{@code <}{@link Clause}{@code >}. 
     *        The first is the closest to the root, the last is the leaf.
     * @return the improvability index (an {@code int} between {@code 0} 
     *         and {@code 10}) or {@code -1} if {@code path} is not 
     *         present in this {@link TreePath}.
     */
    synchronized int getImprovabilityIndex(Iterable<Clause> path) {
        final Set<String> branches = getNeighborFrontierBranches(path);
        if (branches == null) {
            return -1;
        }
        final int retVal = (branches.size() > 9 ? 10 : branches.size());
        cacheIndices(retVal, (Collection<Clause>) path, "improvability");
        return retVal;
    }
    
    /**
     * Calculates the minimum of the values relating to how many times
     * the code branches of a particular path were hit by the tests.
     * 
     * @param path an {@link Iterable}{@code <}{@link Clause}{@code >}. 
     *        The first is the closest to the root, the last is the leaf.
     * @return The novelty index (an {@code int} between {@code 0} 
     *         and {@code 10}) or {@code -1} if {@code path} is not 
     *         present in this {@link TreePath}.
     */
    synchronized int getNoveltyIndex(Iterable<Clause> path) {
        final Set<String> branches = getCoveredBranches(path);
        if (branches == null) {
            return -1;
        }
        final HashSet<Integer> hitsCounters = new HashSet<>();
        for (String branch : branches) {
            final Integer hitsCount = this.hitsCounterMap.get(branch);
            if (hitsCount != null) {
                hitsCounters.add(hitsCount);
            }
        }
        final int minimum = Collections.min(hitsCounters);
        final int retVal = minimum > 9 ? 10 : minimum;
        cacheIndices(retVal, (Collection<Clause>) path, "novelty");
        return retVal;
    }
    
    /**
     * Calculates the infeasibility index of a given path.
     * 
     * @param path an {@link Iterable}{@code <}{@link Clause}{@code >}. 
     *        The first is the closest to the root, the last is the leaf.
     * @return the infeasibility index (an {@code int} between {@code 0} 
     *         and {@code 3}) -->
     *         3: feasible(1) with voting 3,
     *         2: feasible(1) with voting 2,
     *         1: infeasible(0) with voting 2,
     *         0: infeasible(0) with voting 3
     */
    synchronized int getInfeasibilityIndex(Iterable<Clause> path) {
    	int index = 0;
    	if (trainingSet.size() >= 3) {
    		final BitSet[] bloomFilterStructure = getInfeasibilityIndexBloomFilterStructure(path);
    		if (bloomFilterStructure == null) {
                return -1;
            }
    		Object[] result = Knn.knn(trainingSet, bloomFilterStructure);
    		final int label = (int) result[0];
    		final int voting = (int) result[1];
    		//average not used by now
    		final double average = (double) result[2];
    		
    		if (label == 1 && voting == 3) {
    			index = 3;
    		}
    		else if (label == 1 && voting == 2) {
    			index = 2;
    		}
    		else if (label == 0 && voting == 2) {
    			index = 1;
    		}
    		else if (label == 0 && voting == 3) {
    			index = 0;
    		}
    	}
    	cacheIndices(index, (Collection<Clause>) path, "infeasibility");
    	return index;
    }
    
    /**
     * Returns the bloom filter structure previously saved in the TreePath of
     * a modified path condition, used to calculate the infeasibility index.
     * 
     * @param path an {@link Iterable}{@code <}{@link Clause}{@code >}. 
     *        The first is the closest to the root, the last is the leaf.
     * @return The bloomFilterStructure an array of BitSet.
     */
    synchronized BitSet[] getInfeasibilityIndexBloomFilterStructure(Iterable<Clause> path) {
    	Node currentInTree = this.root;

        for (Clause currentInPath : path) {
            final Node child = currentInTree.findChild(currentInPath);
            if (child == null) {
                return null;
            }
            currentInTree = child;
        }
        return currentInTree.getBloomFilterStructure();
    }
    
    /**
     * Calculates the bloom filter structure of a modified path condition,
     * used to calculate the infeasibility index.
     * 
     * @param path an {@link Iterable}{@code <}{@link Clause}{@code >}. 
     *        The first is the closest to the root, the last is the leaf.
     * @return The bloomFilterStructure an array of BitSet.
     */
    synchronized BitSet[] PCToBloomFilter(Collection<Clause> path) {
    	if(path != null) {
    		Object[] clauseArray = shorten(path).toArray();
    		String PathToString = Util.stringifyPathCondition(shorten(path));
    		//split pc clauses into array
    		String[] generalArray = PathToString.split(" && ");
    		String[] specificArray = PathToString.split(" && ");
    		//generate general clauses
    		for (int i=0; i < generalArray.length; i++){
    			generalArray[i]=generalArray[i].replaceAll("[0-9]", "");
    		}
    		//Slicing call
    		Object[] outputSliced = SlicingManager.Slicing(specificArray, clauseArray, generalArray);
    		String[] specificArraySliced = (String[]) outputSliced[0];
    		String[] generalArraySliced = (String[]) outputSliced[1];
    		BitSet[] bloomFilterStructure = bloomFilter(specificArraySliced, generalArraySliced);
    		return bloomFilterStructure;
    	}
    	else {
    		BitSet[] bloomFilterStructure = new BitSet[N_ROWS];
    		return bloomFilterStructure;
    	}
    }
    
    /**
     * Adds the bloom filter structure of a path condition already taken as input by EvoSuite
     * to the training set with the correct label. Used to calculate the infeasibility index.
     * 
     * @param path an {@link Iterable}{@code <}{@link Clause}{@code >}. 
     *        The first is the closest to the root, the last is the leaf.
     * @param evosuiteResult a boolean true if EvoSuite generated a test case from path,
     *        false otherwise.
     */
    synchronized void PCToBloomFilterEvosuite(Collection<Clause> path, boolean evosuiteResult) {
  		if(path != null) {
  			BitSet[] bloomFilterStructure = getInfeasibilityIndexBloomFilterStructure(path);
  			//add to trainingSet
  			if (evosuiteResult) {
  				trainingSet.add(new TrainigSetItem(bloomFilterStructure, 1));
  			}
  			else {
  				trainingSet.add(new TrainigSetItem(bloomFilterStructure, 0));
  			}
  		}
  	}
    
    /**
     * Calculates multiple bloom filter structures from a path condition generated by an EvoSuite seed test.
     * Adds the bloom filter structures to the training set with the feasible label. Used to
     * calculate the infeasibility index.
     * 
     * @param path an {@link Iterable}{@code <}{@link Clause}{@code >}. 
     *        The first is the closest to the root, the last is the leaf.
     * @param evosuiteResult a boolean true if EvoSuite generated a test case from path,
     *        false otherwise.
     */
    synchronized void PCToBloomFilterEvosuiteSeed(Collection<Clause> path) {
  		if(path != null) {
  			Object[] clauseArray = shorten(path).toArray();
  			String PathToString = Util.stringifyPathCondition(shorten(path));
  			//split pc clauses into array
  			String[] generalArray = PathToString.split(" && ");
  			String[] specificArray = PathToString.split(" && ");
  			//generate general clauses
  			for (int i=0; i < generalArray.length; i++){
  				generalArray[i]=generalArray[i].replaceAll("[0-9]", "");
  			}
  			//Slicing call
  			Object[] outputSliced = SlicingManager.Slicing(specificArray, clauseArray, generalArray);
  			String[] specificArraySliced = (String[]) outputSliced[0];
  			String[] generalArraySliced = (String[]) outputSliced[1];
  			BitSet[] bloomFilterStructure = bloomFilter(specificArraySliced, generalArraySliced);
  			//add to trainingSet
  			trainingSet.add(new TrainigSetItem(bloomFilterStructure, 1));
  			
  			//remove the last clause and rerun the workflow
  			for (int i=specificArray.length - 1; i > 0; i--) {
  				String[] specificArrayNoLast = new String[i];
  				Object[] clauseArrayNoLast = new Object[i];
  				String[] generalArrayNoLast = new String[i];
  				System.arraycopy(specificArray, 0, specificArrayNoLast, 0, i);
  				System.arraycopy(clauseArray, 0, clauseArrayNoLast, 0, i);
  				System.arraycopy(generalArray, 0, generalArrayNoLast, 0, i);
  				
  				//Slicing call
  				Object[] outputSlicedNoLast = SlicingManager.Slicing(specificArrayNoLast, clauseArrayNoLast, generalArrayNoLast);
  				String[] specificArraySlicedNoLast = (String[]) outputSlicedNoLast[0];
  				String[] generalArraySlicedNoLast = (String[]) outputSlicedNoLast[1];
  				BitSet[] bloomFilterStructureNoLast = bloomFilter(specificArraySlicedNoLast, generalArraySlicedNoLast);
  				trainingSet.add(new TrainigSetItem(bloomFilterStructureNoLast, 1));
  			}
  		}
  	}

    //generates the bloomFilter structure using the concrete and abstract path conditions
    synchronized BitSet[] bloomFilter(String[] specificArray, String[] generalArray) {
    	//creates empty bloom filter structure
    	BitSet[] bloomFilterStructure = new BitSet[N_ROWS];
    	for (int i=0; i < bloomFilterStructure.length; i++){
    		bloomFilterStructure[i] =  new BitSet(N_COLUMNS);
    	}
    	int[] primeNumber = new int[] {7, 11, 13};
    	
    	for (int i = 0; i < specificArray.length; i++) {
    		//applies different hash functions to the general and specific condition
    		for (int j = 0; j < primeNumber.length; j++) {
    			long hashGeneral = primeNumber[j];
    			long hashSpecific = primeNumber[j];
    			hashGeneral = 31*hashGeneral + generalArray[i].hashCode();
    			hashSpecific = 31*hashSpecific + specificArray[i].hashCode();
    			long hashToPositiveGeneral = Math.abs(hashGeneral);
    			long hashToPositiveSpecific = Math.abs(hashSpecific);
    			//TODO find a way to improve hash functions and reduce collision
    			//resize the hashes in the range of the dimension of the two-dimensional array
    			int indexGeneral = (int) (hashToPositiveGeneral%64);
    			int indexSpecific = (int) (hashToPositiveSpecific%15);
    			//sets the bit corresponding to the general index on the first line to 1 than
    			//sets the bit corresponding to the specific index on the column of the previous general bit to 1
    			bloomFilterStructure[0].set(indexGeneral);
    			bloomFilterStructure[indexSpecific+1].set(indexGeneral);
    		}  
    	}
    	return bloomFilterStructure;
    }
}