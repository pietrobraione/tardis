package ganttproject;

import java.util.List;

import jbse.meta.Analysis;

public class Node {
    final Task myTask;
    NodeData myData;

    private static void _got_myData(Node n) {
    	Analysis.assume(n.myData.myLevel >= 0); 
    	Analysis.assume(n.myData.myLevel < 3); 
    }

    public Node(Task task, Transaction txn) {
      //assert task != null;
      myTask = task;
      myData = new NodeData(this, txn);
    }

    public void revertData() {
      myData = myData.revert();
    }

    boolean promoteLayer(GraphData data) {
      int maxLevel = -1;
      for (DependencyEdge edge : myData.getIncoming()) {
        maxLevel = Math.max(maxLevel, edge.getSrc().getLevel());
      }
      if (maxLevel + 1 == myData.getLevel()) {
        return false;
      }
      data.removeFromLevel(myData.getLevel(), this);
      myData = myData.setLevel(maxLevel + 1);
      data.addToLevel(myData.getLevel(), this);
      return true;
    }

    boolean demoteLayer(GraphData data) {
      int maxLevel = -1;
      for (DependencyEdge edge : myData.getIncoming()) {
        maxLevel = Math.max(maxLevel, edge.getSrc().getLevel());
      }
      if (maxLevel + 1 == myData.getLevel()) {
        return false;
      }
      //assert maxLevel + 1 < myData.getLevel();
      data.removeFromLevel(myData.getLevel(), this);
      myData = myData.setLevel(maxLevel + 1);
      data.addToLevel(myData.getLevel(), this);
      return true;
    }

    public int getLevel() {
      return myData.getLevel();
    }

    public List<DependencyEdge> getOutgoing() {
      return myData.getOutgoing();
    }

    public List<DependencyEdge> getIncoming() {
      return myData.getIncoming();
    }

    public void addOutgoing(DependencyEdge dep) {
      myData = myData.addOutgoing(dep);
    }

    public void addIncoming(DependencyEdge dep) {
      myData = myData.addIncoming(dep);
    }


    void removeOutgoing(DependencyEdge edge) {
      myData = myData.removeOutgoing(edge);
    }

    void removeIncoming(DependencyEdge edge) {
      myData = myData.removeIncoming(edge);
    }

    public Task getTask() {
      return myTask;
    }

    @Override
    public String toString() {
      return myTask.toString();
    }
  }
