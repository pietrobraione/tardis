package caching;
/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */



import jbse.meta.Analysis;


/**
 * A <code>List</code> implementation that stores a cache of internal Node objects
 * in an effort to reduce wasteful object creation.
 * <p>
 * A linked list creates one Node for each item of data added. This can result in
 * a lot of object creation and garbage collection. This implementation seeks to
 * avoid that by maintaining a store of cached nodes.
 * <p>
 * This implementation is suitable for long-lived lists where both add and remove
 * are used. Short-lived lists, or lists which only grow will have worse performance
 * using this class.
 * <p>
 * <b>Note that this implementation is not synchronized.</b>
 * 
 * @since Commons Collections 3.0
 * @version $Revision: 1.1 $ $Date: 2010/05/10 18:19:40 $
 * 
 * @author Jeff Varszegi
 * @author Rich Dougherty
 * @author Phil Steitz
 * @author Stephen Colebourne
 */
/**
 * @Invariant 
 *		( this.header!=null ) &&
 *		( this.header.next!=null ) &&
 *		( this.header.previous!=null ) &&
 *		( this.size==#(this.header.*next @- null)-1 ) &&
 *		( this.size>=0 ) &&
 *		( this.cacheSize <= this.maximumCacheSize ) &&
 *		( this.DEFAULT_MAXIMUM_CACHE_SIZE == 20 ) &&
 *		( this.cacheSize == #(this.firstCachedNode.*next @- null) ) &&
 *		(all m: LinkedListNode | ( m in this.firstCachedNode.*next @- null ) => (
 *				  m !in m.next.*next @- null &&
 *				  m.previous==null &&
 *				  m.value==null )) &&
 *		(all n: LinkedListNode | ( n in this.header.*next @- null ) => (
 *				  n!=null &&
 *				  n.previous!=null &&
 *				  n.previous.next==n &&
 *				  n.next!=null &&
 *				  n.next.previous==n )) ; 
 *
 */
/**
 * @SpecField myseq: seq LinkedListNode from this.header, LinkedListNode.next, LinkedListNode.previous | (
 *		( #(this.myseq) = #(this.header.*next @- this.header @- null) ) && 
 *		( (this.header.next!=null) => ( (this.myseq[0]=this.header.next ) && 
 *		( all j: int | ( ( 0<=j && j < ( #(this.myseq) - 1) ) => ( this.myseq[j+1] = (this.myseq[j]).next ) ) )
 *		))) ;
 */

public class NodeCachingLinkedList {

//INSTRUMENTATION BEGIN
    /*
     * Shadow fields for initial values 
     */
    private int _initialSize;
    private int _initialCacheSize;
    //no need of an _initialHeader shadow field
        
	/*
     * Other instrumentation variables
     */
    private int _minSize;
    private int _minCacheSize;
    
    /*
     * Triggers
     */
    @SuppressWarnings("unused")
    private static class HEXTriggers {
        private static void _got_NodeCachingLinkedList(NodeCachingLinkedList l) {
        	l._initialSize = l.size;
        	l._initialCacheSize = l.cacheSize;
        	l._minSize = 0;
        	l._minCacheSize = 0;
        	Analysis.assume(l._initialSize >= l._minSize);
        	Analysis.assume(l._initialCacheSize >= l._minCacheSize);
        	Analysis.assume(l._initialCacheSize <= l.maximumCacheSize);
        }
        
        private static void _got_NodeCachingLinkedList_noinv(NodeCachingLinkedList l) {
        	l._initialSize = l.size;
        	l._minSize = 0;
        	Analysis.assume(l._initialSize >= l._minSize);
        }
    }
//INSTRUMENTATION END

	private/*@ nullable @*/transient LinkedListNode header;
	private transient int size;
	private transient int modCount;

	/**
	 * The default value for {@link #maximumCacheSize}.
	 */
	private static final int DEFAULT_MAXIMUM_CACHE_SIZE = 20;

	/**
	 * The first cached node, or <code>null</code> if no nodes are cached.
	 * Cached nodes are stored in a singly-linked list with
	 * <code>next</code> pointing to the next element.
	 */
	private /*@ nullable @*/transient LinkedListNode firstCachedNode;

	/**
	 * The size of the cache.
	 */
	private transient int cacheSize;

	/**
	 * The maximum size of the cache.
	 */
	private int maximumCacheSize;

	//-----------------------------------------------------------------------
	private LinkedListNode createHeaderNode() {
//INSTRUMENTATION BEGIN
		//return new LinkedListNode();
		return new LinkedListNode(this);
//INSTRUMENTATION END
	}

	//-----------------------------------------------------------------------
	/**
	 * Constructor that creates.
	 */
	
    public NodeCachingLinkedList() {
		header = createHeaderNode();
		size = 0;
		cacheSize = 0;
		firstCachedNode = null;
//		DEFAULT_MAXIMUM_CACHE_SIZE = 20;
		maximumCacheSize = DEFAULT_MAXIMUM_CACHE_SIZE;
//INSTRUMENTATION BEGIN
    	//initializates instrumentation fields for concrete objects
    	_initialSize = 0;
    	_initialCacheSize = 0;
    	_minSize = 0;
    	_minCacheSize = 0;
//INSTRUMENTATION END
	}
	

	//-----------------------------------------------------------------------
	/**
	 * Gets the maximum size of the cache.
	 * 
	 * @return the maximum cache size
	 */
	private int getMaximumCacheSize() {
		return maximumCacheSize;
	}

	/**
	 * Sets the maximum size of the cache.
	 * 
	 * @param maximumCacheSize  the new maximum cache size
	 */
	private void setMaximumCacheSize(int maximumCacheSize) {
		this.maximumCacheSize = maximumCacheSize;
		shrinkCacheToMaximumSize();
	}

	/**
	 * Reduce the size of the cache to the maximum, if necessary.
	 */
	private void shrinkCacheToMaximumSize() {
		// Rich Dougherty: This could be more efficient.
		while (cacheSize > maximumCacheSize) {
			getNodeFromCache();
		}
	}

	/**
	 * Gets a node from the cache. If a node is returned, then the value of
	 * {@link #cacheSize} is decreased accordingly. The node that is returned
	 * will have <code>null</code> values for next, previous and element.
	 *
	 * @return a node, or <code>null</code> if there are no nodes in the cache.
	 */
	private LinkedListNode getNodeFromCache() {
		if (cacheSize == 0) {
			return null;
		}
		LinkedListNode cachedNode = firstCachedNode;
		firstCachedNode = cachedNode.next;
		cachedNode.next = null; // This should be changed anyway, but defensively
		// set it to null.                    
		cacheSize--;
		return cachedNode;
	}

	/**
	 * Checks whether the cache is full.
	 * 
	 * @return true if the cache is full
	 */
	private boolean isCacheFull() {
		return cacheSize >= maximumCacheSize;
	}

	/**
	 * Adds a node to the cache, if the cache isn't full.
	 * The node's contents are cleared to so they can be garbage collected.
	 * 
	 * @param node  the node to add to the cache
	 */
	private void addNodeToCache(LinkedListNode node) {
		if (isCacheFull()) {
			// don't cache the node.
			return;
		}
		// clear the node's contents and add it to the cache.
		LinkedListNode nextCachedNode = firstCachedNode;
		node.previous = null;
		node.next = nextCachedNode;
		node.setValue(null);
		firstCachedNode = node;
		cacheSize++;
	}

	//-----------------------------------------------------------------------    
	/**
	 * Creates a new node, either by reusing one from the cache or creating
	 * a new one.
	 * 
	 * @param value  value of the new node
	 * @return the newly created node
	 */
	private LinkedListNode createNode(Object value) {
		LinkedListNode cachedNode = getNodeFromCache();
		if (cachedNode == null) {
			return super_createNode(value);
		} else {
			cachedNode.setValue(value);
			return cachedNode;
		}
	}

	/**
	 * Removes the specified node from the list.
	 *
	 * @param node  the node to remove
	 * @throws NullPointerException if <code>node</code> is null
	 */
	private void super_removeNode(LinkedListNode node) {
		node.previous.next = node.next;
		node.next.previous = node.previous;
		size--;
		modCount++;
	}

	/**
	 * Removes the node from the list, storing it in the cache for reuse
	 * if the cache is not yet full.
	 * 
	 * @param node  the node to remove
	 */
	private void removeNode(LinkedListNode node) {
		super_removeNode(node);
		addNodeToCache(node);
	}

	/**
	 * Removes all the nodes from the list, storing as many as required in the
	 * cache for reuse.
	 * 
	 */
	/*public void removeAllNodes() {
		// Add the removed nodes to the cache, then remove the rest.
		// We can add them to the cache before removing them, since
		// {@link AbstractLinkedList.removeAllNodes()} removes the
		// nodes by removing references directly from {@link #header}.
		int numberOfNodesToCache = Math_min(size, maximumCacheSize - cacheSize);
		LinkedListNode node = header.next;
		for (int currentIndex = 0; currentIndex < numberOfNodesToCache; currentIndex++) {
			LinkedListNode oldNode = node;
			node = node.next;
			addNodeToCache(oldNode);
		}
		super_removeAllNodes();
	}*/

	private int Math_min(int left, int right) {
		return left < right ? left : right;
	}

	//-----------------------------------------------------------------------    

	private LinkedListNode super_createNode(Object value) {
//INSTRUMENTATION BEGIN
		//return new LinkedListNode(value);
		return new LinkedListNode(value, this);
//INSTRUMENTATION END
	}

	//-----------------------------------------------------------------------    

	private void super_removeAllNodes() {
		header.next = header;
		header.previous = header;
		size = 0;
		modCount++;
	}

	//-----------------------------------------------------------------------    

	public boolean remove(/*@ nullable @*/Object value) {
		for (LinkedListNode node = header.next; node != header; node = node.next) {
			if (isEqualValue(node.getValue(), value)) {
				removeNode(node);
				return true;
			}
		}
		return false;
	}

	//-----------------------------------------------------------------------    

	private/*@ pure @*/boolean isEqualValue(Object value1, Object value2) {
		return ((value1 == null ? value1 == value2 : value1
				.equals(value2)));
	}

	//-----------------------------------------------------------------------    

	public boolean add(Object value) {
		addLast(value);
		return true;
	}

	//-----------------------------------------------------------------------    

	/**
	 * @Modifies_Everything
	 * 
	 * @Ensures return==true; 
	 */
	public boolean addLast(/*@ nullable @*/Object o) {
		addNodeBefore(header, o);
		return true;
	}

	//-----------------------------------------------------------------------    

	private void addNodeBefore(LinkedListNode node, Object value) {
		LinkedListNode newNode = createNode(value);
		addNode(newNode, node);
	}

	//-----------------------------------------------------------------------    

	private void addNode(LinkedListNode nodeToInsert,
			LinkedListNode insertBeforeNode) {
		nodeToInsert.next = insertBeforeNode;
		nodeToInsert.previous = insertBeforeNode.previous;
		insertBeforeNode.previous.next = nodeToInsert;
		insertBeforeNode.previous = nodeToInsert;
		size++;
		modCount++;
	}

	//-----------------------------------------------------------------------    

	/**
	 * @Modifies_Everything
	 * 
	 * @Ensures (some i: int | ( 0<=i && i<#(this.myseq) && this.myseq[i].value=arg ) )  
	 *          <=>  return==true ;
     *
	 */
	public boolean contains(/*@ nullable @*/Object value) {
		return indexOf(value) != -1;
	}

	//-----------------------------------------------------------------------    

	public/*@ pure @*/int indexOf(Object value) {
		int i = 0;
		for (LinkedListNode node = header.next; node != header; node = node.next) {
			if (isEqualValue(node.getValue(), value)) {
				return i;
			}
			i++;
		}
		return -1;
	}

	//-----------------------------------------------------------------------

	/**
	 * @NormalBehavior
	 *   @Requires index >=0 && index<this.size ;
	 *   @Modifies_Everything
	 *   @Ensures
  	 *		  ( #(this.myseq)=#(@old(this.myseq))-1 )  &&
	 *		  ( all i: int | ( 0<=i && i<#(this.myseq) ) => (
	 *				 (  i < index => this.myseq[i]=@old(this.myseq[i])   ) && 
	 *				 (  i>= index => this.myseq[i]=@old(this.myseq)[i+1] ) 
	 *				));
	 *
	 * @ExceptionalBehavior
	 *   @Requires index<0 || index>=this.size ;
	 *   @Ensures throw in IndexOutOfBoundsException ;
	 */
	public/*@ nullable @*/Object removeIndex(int index) {
		LinkedListNode node = getNode(index, false);
		Object oldValue = node.getValue();
		removeNode(node);
		return oldValue;
	}

	//-----------------------------------------------------------------------
	private LinkedListNode getNode(int index, boolean endMarkerAllowed)
			throws RuntimeException {
		// Check the index is within the bounds
		if (index < 0) {
			throw new RuntimeException("Couldn't get the node: "
//STUB BEGIN
//					+ "index (" + index + ") less than zero.");
					);
//STUB END
		}
		if (!endMarkerAllowed && index == size) {
			throw new RuntimeException("Couldn't get the node: "
//STUB BEGIN
//					+ "index (" + index + ") is the size of the list.");
					);
//STUB END
		}
		if (index > size) {
			throw new IndexOutOfBoundsException("Couldn't get the node: "
//STUB BEGIN
//					+ "index (" + index + ") greater than the size of the "
//					+ "list (" + size + ").");
					);
//STUB END
		}
		// Search the list and get the node
		LinkedListNode node;
/*INSTRUMENTATION*/	//if (index < (size / 2)) { 
			// Search forwards
			node = header.next;
			for (int currentIndex = 0; currentIndex < index; currentIndex++) {
				node = node.next;
			}
/*INSTRUMENTATION*/		/*} else {
			// Search backwards
			node = header;
			for (int currentIndex = size; currentIndex > index; currentIndex--) {
				node = node.previous;
			}
		}*/
		return node;
	}
	//-----------------------------------------------------------------------
	
	private static class LinkedListNode {

	    /** A pointer to the node before this node */
	    public /*@ nullable @*/LinkedListNode previous;
	    /** A pointer to the node after this node */
	    public /*@ nullable @*/LinkedListNode next;
	    /** The object contained within this node */
	    public /*@ nullable @*/Object value;

	//INSTRUMENTATION BEGIN
	    // No shadow fields for triggers: Entry.element/next/previous
	    // are not used by the triggers

	    /*
	     * Other instrumentation fields
	     */
	    NodeCachingLinkedList _owner; 


	    /*
	     * Triggers.
	     */
	    @SuppressWarnings("unused")
	    private static void _got_LinkedListNode_nonroot(LinkedListNode e) {
	    	//_owner is resolved (always by alias!) by HEX constraints upon first access below
	        ++e._owner._minSize;
	        Analysis.assume(e._owner._initialSize >= e._owner._minSize);
	    }

	    @SuppressWarnings("unused")
	    private static void _handleListClosure(LinkedListNode e) {
	        Analysis.assume(e._owner._initialSize == e._owner._minSize);
	    }

	    /**
	     * Enforces the strongest invariant for NodeCachingLinkedList.cacheSize
	     * as a node is discovered.
	     */
	    @SuppressWarnings("unused")
	    private static void _got_CachedListNode(LinkedListNode e) {
	        ++e._owner._minCacheSize;
	        Analysis.assume(e._owner._initialCacheSize >= e._owner._minCacheSize);
	    }

	    @SuppressWarnings("unused")
	    private static void _handleCacheClosure(LinkedListNode e) {
	        Analysis.assume(e._owner._initialCacheSize == e._owner._minCacheSize);
	    }

	    @SuppressWarnings("unused")
	    private static void _handleCacheClosureEmpty(NodeCachingLinkedList l) {
	        Analysis.assume(l._initialCacheSize == l._minCacheSize); //l._minCacheSize must be zero in this case
	    }
	//INSTRUMENTATION END
	    
	    /**
	     * Constructs a new header node.
	     */
	//INSTRUMENTATION BEGIN
	    //protected LinkedListNode() {
	    protected LinkedListNode(NodeCachingLinkedList _owner) {
	//INSTRUMENTATION END
	        super();
	        previous = this;
	        next = this;
	//INSTRUMENTATION BEGIN
	        this._owner = _owner;
	//INSTRUMENTATION END
	    }

	    /**
	     * Constructs a new node.
	     * 
	     * @param value  the value to store
	     */
	//INSTRUMENTATION BEGIN
	    //protected LinkedListNode(Object value) {
	    protected LinkedListNode(Object value, NodeCachingLinkedList _owner) {
	//INSTRUMENTATION END
	        super();
	        this.value = value;
	//INSTRUMENTATION BEGIN
	        this._owner = _owner;
	//INSTRUMENTATION END
	    }
	    
	    /**
	     * Constructs a new node.
	     * 
	     * @param previous  the previous node in the list
	     * @param next  the next node in the list
	     * @param value  the value to store
	     */
	//INSTRUMENTATION BEGIN
	    //protected LinkedListNode(LinkedListNode previous, LinkedListNode next, Object value) {
	    protected LinkedListNode(LinkedListNode previous, LinkedListNode next, Object value, NodeCachingLinkedList _owner) {
	//INSTRUMENTATION END
	        super();
	        this.previous = previous;
	        this.next = next;
	        this.value = value;
	//INSTRUMENTATION BEGIN
	        this._owner = _owner;
	//INSTRUMENTATION END
	    }
	    
	    /**
	     * Gets the value of the node.
	     * 
	     * @return the value
	     * @since Commons Collections 3.1
	     */
	    protected /*@ pure @*/Object getValue() {
	        return value;
	    }
	    
	    /**
	     * Sets the value of the node.
	     * 
	     * @param value  the value
	     * @since Commons Collections 3.1
	     */
	    protected void setValue(Object value) {
	        this.value = value;
	    }
	    
	    /**
	     * Gets the previous node.
	     * 
	     * @return the previous node
	     * @since Commons Collections 3.1
	     */
	    protected LinkedListNode getPreviousNode() {
	        return previous;
	    }
	    
	    /**
	     * Sets the previous node.
	     * 
	     * @param previous  the previous node
	     * @since Commons Collections 3.1
	     */
	    protected void setPreviousNode(LinkedListNode previous) {
	        this.previous = previous;
	    }
	    
	    /**
	     * Gets the next node.
	     * 
	     * @return the next node
	     * @since Commons Collections 3.1
	     */
	    protected LinkedListNode getNextNode() {
	        return next;
	    }
	    
	    /**
	     * Sets the next node.
	     * 
	     * @param next  the next node
	     * @since Commons Collections 3.1
	     */
	    protected void setNextNode(LinkedListNode next) {
	        this.next = next;
	    }
	}
}