package tardis.implementation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.LinkedBlockingQueue;

import jbse.mem.Clause;

/**
 * A tree of path condition clauses. Used to detect
 * whether a test's path condition has an already
 * explored path condition prefix.
 * 
 * @author Pietro Braione
 */
final class TreePath {
    enum NodeStatus { ATTEMPTED, COVERED };
    //map used to extract the path conditions related to a branch
    HashMap<String, HashSet<Collection<Clause>>> map = new HashMap<>(); //TODO ConcurrentHashMap?
    
    /**
     * A node in the {@link TreePath}.
     * 
     * @author Pietro Braione
     */
    private final class Node {
        private final Clause clause;
        private NodeStatus status = NodeStatus.ATTEMPTED;
        private final List<Node> children = new ArrayList<>();
        //variable to count every time a test hits a branch
        private int hitCounter = 0;
        //the branches covered by the path condition
        private HashSet<String> branches = new HashSet<>();
        //a List of Strings containing the branches used to calculate the improvability index
        private List<String> improvabilityIndexBranches = new ArrayList<>();

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
    public synchronized Node getRoot() {
        return this.root;
    }

    /**
     * Inserts a path in this {@link TreePath}.
     * 
     * @param path a sequence (more precisely, an {@link Iterable}) 
     *        of {@link Clause}s. The first in the sequence is the closer
     *        to the root, the last is the leaf.
     * @param branches a List of String. The branches covered by path:
     *        they are saved in the last node of the path condition in
     *        the TreePath.
     * @param improvabilityIndexBranches a List of Strings containing the
     *        branches used to calculate the improvability index: if it is
     *        not null, they are saved in the last node of the path condition
     *        in the TreePath.
     * @param covered a {@code boolean}, {@code true} iff the path is
     *        covered by a test. 
     */
    public synchronized void insertPath(Collection<Clause> path, HashSet<String> branches, List<String> improvabilityIndexBranches, boolean covered) {
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
            if (index == path.size()-1) {
            	currentInTree.branches.addAll(branches);
            	if (improvabilityIndexBranches != null) {
            		currentInTree.improvabilityIndexBranches.addAll(improvabilityIndexBranches);
            	}
            }
            ++index;
        }
        
        for (String branch : branches) {
        	addToHashSet(branch, path);
        }
    }
    
    public synchronized void addToHashSet(String branch, Collection<Clause> path) {
    	HashSet<Collection<Clause>> pathConditionsSet = this.map.get(branch);
    	if(pathConditionsSet == null) {
    		pathConditionsSet = new HashSet<Collection<Clause>>();
    		pathConditionsSet.add(path);
    		this.map.put(branch, pathConditionsSet);
    	} else {
    		pathConditionsSet.add(path);
    	}
    }

    /**
     * Checks whether a path exists in the {@link TreePath}
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
    public synchronized boolean containsPath(Iterable<Clause> path, boolean covered) {
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
     * Updates the value of times the branches of a particular path were
     * hit by a test case (hitCounter).
     * @param path A sequence (more precisely, an {@link Iterable}) 
     *        of {@link Clause}s. The first in the sequence is the closer
     *        to the root, the last is the leaf.
     */
    public synchronized void countHits(Iterable<Clause> path) {
        Node currentInTree = this.root;
        currentInTree.hitCounter = ++currentInTree.hitCounter;
        for (Clause currentInPath : path) {
            final Node child = currentInTree.findChild(currentInPath);
            if (child == null) {
            	break;
            }
            currentInTree = child;
            currentInTree.hitCounter = ++currentInTree.hitCounter;
        }
    }
    
    /**
     * Calculates the minimum of the values relating to how many times
     * branches of a particular path were hit by the tests (hitCounter).
     * @param path A sequence (more precisely, an {@link Iterable}) 
     *        of {@link Clause}s. The first in the sequence is the closer
     *        to the root, the last is the leaf.
     * @return The novelty index (an int)
     */
    public synchronized int calculateNoveltyIndex(Iterable<Clause> path) {
        Node currentInTree = this.root;
        HashSet<Integer> hitCounters = new HashSet<>();
        if (currentInTree.hitCounter > 0)
        	hitCounters.add(currentInTree.hitCounter);
        for (Clause currentInPath : path) {
            final Node child = currentInTree.findChild(currentInPath);
            if (child == null) {
            	break;
            }
            currentInTree = child;
            if (currentInTree.hitCounter > 0)
            	hitCounters.add(currentInTree.hitCounter);
        }
        return Collections.min(hitCounters);
    }
    
    /**
     * Updates the minimum of the values relating to how many times
     * branches of a particular path into the buffer were hit by the tests
     * (hitCounter), every time a new test is run.
     * @param linkedBlockingQueue A linkedBlockingQueue of JBSEResult used as pathConditionBuffer.
     */
    public synchronized void updateNoveltyIndex(LinkedBlockingQueue<JBSEResult> linkedBlockingQueue) {
    	for (JBSEResult JBSEResultInBuffer : linkedBlockingQueue) {
    		final int newMinHitValue = calculateNoveltyIndex(JBSEResultInBuffer.getFinalState().getPathCondition());
    		//JBSEResultInBuffer.setNoveltyIndex(newMinHitValue);
    	}
    }
    
    /**
     * Returns the branches related to a given path condition.
     * @param path A sequence (more precisely, an {@link Iterable}) 
     *        of {@link Clause}s. The first in the sequence is the closer
     *        to the root, the last is the leaf.
     * @return an HashSet containing the branches related to the path condition.
     */
    public synchronized HashSet<String> getBranches(Collection<Clause> path) {
    	Node currentInTree = this.root;

        for (Clause currentInPath : path) {
            final Node child = currentInTree.findChild(currentInPath);
            if (child == null) {
                return null;
            }
            currentInTree = child;
        }
        return currentInTree.branches;
    }
    
    /**
     * Returns the list of branches used to calculate the improvability index for
     * a given path condition.
     * @param path A sequence (more precisely, an {@link Iterable}) 
     *        of {@link Clause}s. The first in the sequence is the closer
     *        to the root, the last is the leaf.
     * @return a List containing the branches used to calculate the improvability index.
     */
    public synchronized List<String> getImprovabilityIndexBranches(Collection<Clause> path) {
    	Node currentInTree = this.root;

        for (Clause currentInPath : path) {
            final Node child = currentInTree.findChild(currentInPath);
            if (child == null) {
                return null;
            }
            currentInTree = child;
        }
        return currentInTree.improvabilityIndexBranches;
    }
    
    /**
     * Updates the list of branches used to calculate the improvability index for
     * a given path condition; i.e. removes the new covered branch from the list.
     * @param path A sequence (more precisely, an {@link Iterable}) 
     *        of {@link Clause}s. The first in the sequence is the closer
     *        to the root, the last is the leaf.
     * @param coveredBranch The new covered branch (a String).
     */
    public synchronized void updateImprovabilityIndexBranches(Collection<Clause> path, String coveredBranch) {
    	Node currentInTree = this.root;
    	boolean inTree = true;

        for (Clause currentInPath : path) {
            final Node child = currentInTree.findChild(currentInPath);
            if (child == null) {
            	inTree = false;
                break;
            }
            currentInTree = child;
        }
        if (inTree) {
        	currentInTree.improvabilityIndexBranches.remove(coveredBranch);
        }
    }
    
    /**
     * If necessary updates the position (the LinkedBlockingQueue chosen based on the improvability index) of JBSEResults in the buffer
     * every time a new branch is covered.
     * @param buffer HashMap of LinkedBlockingQueue used as path condition buffer.
     * @param newCoveredBranches A set of new covered branches.
     */
    public synchronized void updateImprovabilityIndex(HashMap<Integer, LinkedBlockingQueue<JBSEResult>> buffer, Set<String> newCoveredBranches) {
    	for (String branch : newCoveredBranches) {
    		for (int index : buffer.keySet()) {
    			for (JBSEResult JBSEResultInBuffer : buffer.get(index)) {
    				final List<Clause> pathCondition = JBSEResultInBuffer.getFinalState().getPathCondition();
    				if (index != 0 && getImprovabilityIndexBranches(pathCondition).contains(branch)) {
    					updateImprovabilityIndexBranches(pathCondition, branch);
    					//do nothing if the item has a new improvability index >= 10
    					if (getImprovabilityIndexBranches(pathCondition).size() < 10) {
    						buffer.get(index).remove(JBSEResultInBuffer);
    						if (buffer.get(index-1) == null) {
    							buffer.put(index-1, new LinkedBlockingQueue<JBSEResult>());
    						}
    						buffer.get(index-1).add(JBSEResultInBuffer);
    					}
    				}
    			}
    		}
    	}
    }
    
    /**
     * For a given branch, it returns in which path conditions that branch is in.
     * @param branch A String
     * @return an HashSet containing the path conditions related to the branch.
     */
    public synchronized HashSet<Collection<Clause>> getPathConditions(String branch) {
    	return this.map.get(branch);
    }
}