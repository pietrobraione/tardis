package avlTest;

import jbse.meta.Analysis;

//Basic node stored in AVL trees
//Note that this class is not accessible outside
//of package DataStructures

class AvlNode {
//INSTRUMENTATION BEGIN
	AvlNode parent;  //initialized by HEX constraints
//INSTRUMENTATION END
	// Friendly data; accessible by other package routines
	int element; // The data in the node

	/*@ nullable @*/AvlNode left; // Left child

	/*@ nullable @*/AvlNode right; // Right child

	int height; // Height

//INSTRUMENTATION BEGIN
/*
* Shadow fields for initial values 
*/
	AvlNode _initialParent;
	int _initialElement;
	AvlNode _initialLeft;
	AvlNode _initialRight;
	int _initialHeight;

	/*
* Other instrumentation variables
*/
private Range _range;

/*
* Triggers
*/

@SuppressWarnings("unused")
private static void _got_AvlNode_onRoot(AvlNode n) {
	_got_AvlNode(n);
	n._range = new Range();
}

@SuppressWarnings("unused")
private static void _got_AvlNode_onTheLeft(AvlNode n) {
	_got_AvlNode(n);
	n._initialParent._initialLeft = n;
	n._range = n._initialParent._range.setUpper(n._initialParent._initialElement);
	Analysis.assume(n._range.inRange(n._initialElement));
	
	if (!Analysis.isResolved(n._initialParent, "_initialRight")) {
		assumeNodeWithAtLeastAChild(n._initialParent);
	} else if (n._initialParent._initialRight == null) {
		assumeNodeWithSingleChild(n._initialParent, n);
	} else {
		assumeNodeWithTwoChildren(n._initialParent, n, n._initialParent._initialRight);
	}
}

@SuppressWarnings("unused")
private static void _got_AvlNode_onTheRight(AvlNode n) {
	_got_AvlNode(n);
	n._initialParent._initialRight = n;
	n._range = n._initialParent._range.setLower(n._initialParent._initialElement);
	Analysis.assume(n._range.inRange(n._initialElement));
	
	if (!Analysis.isResolved(n._initialParent, "_initialLeft")) {
		assumeNodeWithAtLeastAChild(n._initialParent);
	} else if (n._initialParent._initialLeft == null) {
		assumeNodeWithSingleChild(n._initialParent, n);
	} else {
		assumeNodeWithTwoChildren(n._initialParent, n, n._initialParent._initialLeft);
	}
}

@SuppressWarnings("unused")
private static void _got_null_onTheLeft(AvlNode n) {
	n._initialLeft = null;
	
	if (!Analysis.isResolved(n, "_initialRight")) {
		assumeNodeWithAtMostAChild(n);
	} else if (n._initialRight == null) {
		assumeNodeWithNoChild(n);
	} else {
		assumeNodeWithSingleChild(n, n._initialRight);
	}
}

@SuppressWarnings("unused")
private static void _got_null_onTheRight(AvlNode n) {
	n._initialRight = null;

	if (!Analysis.isResolved(n, "_initialLeft")) {
		assumeNodeWithAtMostAChild(n);
	} else if (n._initialLeft == null) {
		assumeNodeWithNoChild(n);
	} else {
		assumeNodeWithSingleChild(n, n._initialLeft);
	}
}

/*
* Auxiliary method invoked by triggers
*/

private static void assumeNodeWithNoChild(AvlNode node) {
	Analysis.assume(node._initialHeight == 0);   	
}

private static void assumeNodeWithAtMostAChild(AvlNode node) {
	Analysis.assume(node._initialHeight <= 1);   
}

private static void assumeNodeWithSingleChild(AvlNode node, AvlNode child) {
	Analysis.assume(node._initialHeight == 1);    		
	Analysis.assume(child._initialHeight == 0);    	
}

private static void assumeNodeWithAtLeastAChild(AvlNode node) {
	Analysis.assume(node._initialHeight >= 1);
}

private static void assumeNodeWithTwoChildren(AvlNode node, AvlNode child1, AvlNode child2) {
	Analysis.assume(node._initialHeight >= child1._initialHeight + 1);
	Analysis.assume(node._initialHeight >= child2._initialHeight + 1);
	Analysis.assume(2 * node._initialHeight <= child1._initialHeight + child2._initialHeight + 3); //??? should be 2 * n._initialHeight == n._hl + n._hr + 3
}

private static void _got_AvlNode(AvlNode n) {
	n._initialParent = n.parent; //n.parent is resolved by HEX constraints
	n._initialElement = n.element;
	n._initialHeight = n.height;
	Analysis.assume(n._initialHeight >= 0);
}

//INSTRUMENTATION END

// Constructors
	AvlNode(final int theElement) {
		this(theElement, null, null, null);
	}

//INSTRUMENTATION BEGIN
//	AvlNode(final int theElement, final AvlNode lt, final AvlNode rt) {
	AvlNode(final int theElement, final AvlNode lt, final AvlNode rt, final AvlNode pnt) {
//INSTRUMENTATION END
		this.element = theElement;
		this.left = lt;
		this.right = rt;
//INSTRUMENTATION BEGIN
		this.parent = pnt;
//INSTRUMENTATION END
		this.height = 0;
//INSTRUMENTATION BEGIN
		
		//Initialization of instrumentation fields for creation
		//of concrete objects
		this._initialElement = this.element;
		this._initialLeft = this.left;
		this._initialRight = this.right;
		this._initialParent = this.parent;
		this._initialHeight = this.height;
		
//INSTRUMENTATION END
	}

}