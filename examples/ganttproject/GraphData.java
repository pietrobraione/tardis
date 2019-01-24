package ganttproject;

import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;

import com.google.common.base.Preconditions;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import com.google.common.collect.TreeMultimap;

public class GraphData {
	private static class IntegerComparator implements Comparator<Integer> {
	    @Override
	    public int compare(Integer o1, Integer o2) {
	      return o1.compareTo(o2);
	    }
	}

	private static class NodeComparator implements Comparator<Node> {
	    @Override
	    public int compare(Node o1, Node o2) {
	      return o1.myTask.getTaskID() - o2.myTask.getTaskID();
	    }
	}


    private static Multimap<Integer, Node> createEmptyLayers() {
      return TreeMultimap.<Integer, Node>create(new IntegerComparator(), new NodeComparator());
    }
    final Multimap<Integer, Node> myLayers = createEmptyLayers();
    final GraphData myBackup;
    final Transaction myTxn;

    public GraphData(GraphData backup, Transaction txn) {
      myBackup = backup;
      myTxn = txn;
    }

    public GraphData(Transaction txn) {
      this(null, txn);
    }

    GraphData withTransaction() {
      if (!myTxn.isRunning() || myBackup != null) {
        return this;
      }
      return new GraphData(this, myTxn);
    }

    void addToLevel(int level, Node node) {
      myLayers.put(level, node);
    }

    void removeFromLevel(int level, Node node) {
      if (!myTxn.isRunning()) {
        myLayers.remove(level, node);
      } else {
        myLayers.put(-level - 1, node);
      }
    }

    Collection<Node> getLayer(int num) {
      Collection<Node> result = myLayers.get(num);
      if (!myTxn.isRunning() || myBackup == null) {
        return Collections.unmodifiableCollection(result);
      }
      if (myLayers.get(num).size() + myLayers.get(-num - 1).size() == 0) {
        return Collections.unmodifiableCollection(result);
      }
      result = Sets.newLinkedHashSet(myBackup.getLayer(num));
      result.addAll(myLayers.get(num));
      result.removeAll(myLayers.get(-num - 1));
      return result;
    }

    int checkLayerValidity() {
      int prev = -1;
      Multimap<Integer, Node> layers;
      if (!myTxn.isRunning() || myBackup == null) {
        layers = myLayers;
      } else {
        layers = createEmptyLayers();
        int maxBackupLayerNum = 0;
        for (Integer num : myBackup.myLayers.keySet()) {
          Collection<Node> layer = myBackup.myLayers.get(num);
          if (myLayers.get(num).size() + myLayers.get(-num - 1).size() > 0) {
            layer = Sets.newLinkedHashSet(layer);
            layer.addAll(myLayers.get(num));
            layer.removeAll(myLayers.get(-num - 1));
          }
          if (!layer.isEmpty()) {
            layers.putAll(num, layer);
          }
          maxBackupLayerNum = Math.max(maxBackupLayerNum, num);
        }
        for (Integer num : myLayers.keySet()) {
          if (num > maxBackupLayerNum) {
            layers.putAll(num, myLayers.get(num));
          }
        }
      }
      for (Integer num : layers.keySet()) {
        Preconditions.checkState(num == prev + 1, "It appears that there is a dependency loop. Task layers are:\n%s", myLayers);
        prev = num;
      }
      return layers.keySet().size();
    }

    GraphData rollback() {
      return (!myTxn.isRunning() || myBackup == null) ? this : myBackup.rollback();
    }
  }

