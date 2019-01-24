package ganttproject;

import static jbse.meta.Analysis.assume;
import static jbse.meta.Analysis.assumeClassNotInitialized;

import sushi.logging.Level;
import sushi.logging.Logger;

public class Driver {
	private DependencyGraph g;
	private Node root;
	
	public void setDependencyGraph(DependencyGraph g) { this.g = g; }
	public void setRoot(Node root) { this.root = root; }
	
	public boolean entry() {
		assume(this.g != null);
		assume(this.root != null);
		return this.g.removeImplicitDependencies(this.root);
	}
	
	public boolean test() {
		assumeClassNotInitialized("ganttproject/Task");
		assumeClassNotInitialized("ganttproject/DependencyGraph$Node");
    	this.g = new DependencyGraph();
    	Transaction t = new Transaction();
    	this.root = new Node(new TaskImpl(null, 0), t);
    	ImplicitSubSuperTaskDependency iss = new ImplicitSubSuperTaskDependency(this.root, this.root);
    	this.root.addOutgoing(iss);
    	ExplicitDependencyImpl ed = new ExplicitDependencyImpl(null, new Node(null, null), null);
    	t.start();
    	this.root.addIncoming(ed);
		return entry();
	}

}
