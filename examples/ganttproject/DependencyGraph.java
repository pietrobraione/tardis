/*
Copyright 2012 GanttProject Team

This file is part of GanttProject, an opensource project management tool.

GanttProject is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

GanttProject is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with GanttProject.  If not, see <http://www.gnu.org/licenses/>.
*/
package ganttproject;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import com.google.common.base.Objects;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import com.google.common.collect.Range;
import com.google.common.collect.Sets;
import com.google.common.collect.TreeMultimap;

import ganttproject.Task;
import jbse.meta.Analysis;

/**
 * A graph of dependencies between tasks which is used for scheduling algorithm.
 * In this graph nodes are tasks, and edges are either explicit or implicit dependencies between tasks.
 *
 * Graph is topologically ordered, and each node knows its level. Adding or removing dependencies or moving tasks
 * in the task hierarchy may change node levels.
 *
 * @author dbarashev
 */
public class DependencyGraph {
  /**
   * Dependency defines a constraint on its target task start and end dates. Constraints
   * are normally either points or semi-open intervals on the date axis.
   */

  /**
   * Explicit dependency is constructed from {@link TaskDependency} instances and corresponds
   * to dependencies explicitly created by a user
   */

  private final Transaction myTxn = new Transaction();

  private GraphData myData = new GraphData(myTxn);

  public DependencyGraph() {
  }

  private void removeEdge(DependencyEdge edge) {
    edge.getSrc().removeOutgoing(edge);
    edge.getDst().removeIncoming(edge);

    Deque<DependencyEdge> queue = new LinkedList<DependencyEdge>();
    queue.add(edge);
    while (!queue.isEmpty()) {
      edge = queue.pollFirst();
      if (edge.getDst().demoteLayer(myData = myData.withTransaction())) {
        queue.addAll(edge.getDst().getOutgoing());
      }
    }
  }

  public boolean removeImplicitDependencies(final Node root) {
    boolean removed = false;
    Deque<Node> queue = new LinkedList<>(); //Lists.newLinkedList();
    queue.add(root);
    for (DependencyEdge outgoing : Lists.newArrayList(root.getOutgoing())) {
      if (outgoing instanceof ImplicitSubSuperTaskDependency) {
        removed = true;
        removeEdge(outgoing);
      }
    }
    while (!queue.isEmpty()) {
      Node node = queue.pollFirst();
      for (DependencyEdge incoming : Lists.newArrayList(node.getIncoming())) {
        if (incoming instanceof ImplicitInheritedDependency) {
          if (((ImplicitInheritedDependency)incoming).myExplicitDep.getDst() != root) {
            removed = true;
            removeEdge(incoming);
          }
        }
        if (incoming instanceof ImplicitSubSuperTaskDependency) {
          queue.add(incoming.getSrc());
        }
      }
    }
    return removed;
  }

  public void startTransaction() {
	  Analysis.assume(this.myData.myTxn == this.myTxn);
	  if (myTxn.isRunning()) {
		  return;
	  }
	  myTxn.start();
  }

  public void rollbackTransaction() {
	  Analysis.assume(this.myData.myTxn == this.myTxn);
	  if (!myTxn.isRunning()) {
		  return;
	  }
	  myData = myData.rollback();
	  myTxn.rollback();
  }

}
