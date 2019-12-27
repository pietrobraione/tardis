package tree.implementation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import jbse.mem.Clause;
import tardis.implementation.JBSEResult;


public final class TreePath {
	private final class Node {
		private final Clause clause;
		private final List<Node> children = new ArrayList<>();
		private JBSEResult item = null;

		public Node(Clause clause) {
			this.clause = clause;
		}

		/** Constructor for the root node; all the
		 *  other nodes have a clause.
		 */
		public Node() { 
			this(null);
		}

		public Node findChild(Clause possibleChild) {
			for (Node current : this.children) {
				if (current.clause.equals(possibleChild)) {
					return current;
				}
			}
			return null;
		}

		public Node addChild(Clause newChild) {
			final Node retVal = new Node(newChild);
			this.children.add(retVal);
			return retVal;
		}

	}


	public Node getRoot() {
		return this.root;
	}

	public Node root = new Node();

	public void insertPath(Collection<Clause> path, JBSEResult item) {
		Node currentInTree = this.root;

		for (Clause currentInPath : path) {
			final Node possibleChild = currentInTree.findChild(currentInPath);
			if (possibleChild == null) {
				currentInTree = currentInTree.addChild(currentInPath);
			} else {
				currentInTree = possibleChild;
			}
		}

		currentInTree.item = item;
	}

	public void insertAndCleanPath(Collection<Clause> path) {
		Node currentInTree = this.root;

		for (Clause currentInPath : path) {
			final Node possibleChild = currentInTree.findChild(currentInPath);
			if (possibleChild == null) {
				currentInTree = currentInTree.addChild(currentInPath);
			} else {
				currentInTree = possibleChild;
				currentInTree.item = null;
			}
		}
	}

	public boolean containsPath(Collection<Clause> path) {
		Node currentInTree = this.root;

		for (Clause currentInPath : path) {
			final Node child = currentInTree.findChild(currentInPath);
			if (child == null) {
				return false;
			}
			currentInTree = child;
		}
		return true;
	}

	public void print(String appender) {
		printNode(this.root, appender);
	}

	private void printNode(Node node, String appender) {
		System.out.println(appender + node.clause);
		node.children.forEach(each ->  printNode(each, appender + appender));
	}

	public List<JBSEResult> getItems() {
		final ArrayList<JBSEResult> retVal = new ArrayList<>();
		getItemsNode(this.root, retVal);
		return retVal;
	}

	private void getItemsNode(Node node, ArrayList<JBSEResult> retVal) {
		if (node.item != null) {
			retVal.add(node.item);
		}
		node.children.forEach(each -> getItemsNode(each, retVal));
	}
}
