package parser;


import com.github.javaparser.ast.Node;
import com.google.common.base.Strings;

public class NodeIterator {
	   public interface NodeHandler {
	       boolean handle(Node node);
	   }
	   
	   private NodeHandler nodeHandler;
	   
	   
	   public NodeIterator(NodeHandler nodeHandler) {
	       this.nodeHandler = nodeHandler;
	   }
	   
	   
	   public void explore(Node node) {
	       if (nodeHandler.handle(node)) {
	           for (Node child : node.getChildrenNodes()) {
	               explore(child);
	           }
	       }
	    }
	}

