package ganttproject;

import java.util.HashSet;
import java.util.Set;

public class Transaction {
    private boolean isRunning;
    private final Set<Node> myTouchedNodes = /*INSTRUMENTATION: new HashSet<>()*/new common.LinkedList<>();

    public boolean isRunning() {
      return isRunning;
    }

    public void touch(Node node) {
      myTouchedNodes.add(node);
    }

    public void start() {
      isRunning = true;
    }

    public void rollback() {
      for (Node node : myTouchedNodes) {
        node.revertData();
      }
      isRunning = false;
    }
}
