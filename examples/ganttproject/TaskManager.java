/*
GanttProject is an opensource project management tool.
Copyright (C) 2003-2011 GanttProject Team

This program is free software; you can redistribute it and/or
modify it under the terms of the GNU General Public License
as published by the Free Software Foundation; either version 3
of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program; if not, write to the Free Software
Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package ganttproject;

import java.awt.Color;
import java.math.BigDecimal;
import java.util.Date;

/**
 * @author bard
 */
public interface TaskManager {
  abstract class TaskBuilder {
    String myName;
    Integer myId;
    Date myStartDate;
    Color myColor;
    Task myPrevSibling;
    Boolean isExpanded;
    Task myParent;
    boolean isLegacyMilestone;
    Date myEndDate;
    String myNotes;
    String myWebLink;
    Integer myCompletion;
    Task myPrototype;
    BigDecimal myCost;

    public TaskBuilder withColor(Color color) {
      myColor = color;
      return this;
    }

    public TaskBuilder withCompletion(int pctgCompletion) {
      myCompletion = pctgCompletion;
      return this;
    }

    public TaskBuilder withEndDate(Date date) {
      myEndDate = date;
      return this;
    }

    public TaskBuilder withExpansionState(boolean isExpanded) {
      this.isExpanded = isExpanded;
      return this;
    }

    public TaskBuilder withId(int id) {
      myId = id;
      return this;
    }

    public TaskBuilder withLegacyMilestone() {
      isLegacyMilestone = true;
      return this;
    }

    public TaskBuilder withName(String name) {
      myName = name;
      return this;
    }

    public TaskBuilder withNotes(String notes) {
      myNotes = notes;
      return this;
    }

    public TaskBuilder withParent(Task parent) {
      myParent = parent;
      return this;
    }
    public TaskBuilder withPrevSibling(Task sibling) {
      myPrevSibling = sibling;
      return this;
    }

    public TaskBuilder withPrototype(Task prototype) {
      myPrototype = prototype;
      return this;
    }

    public TaskBuilder withStartDate(Date startDate) {
      myStartDate = startDate;
      return this;
    }

    public TaskBuilder withWebLink(String value) {
      myWebLink = value;
      return this;
    }

    public TaskBuilder withCost(BigDecimal value) {
      myCost = value;
      return this;
    }

    public abstract Task build();
  }

  public TaskBuilder newTaskBuilder();

  Task[] getTasks();

  public Task getRootTask();

  public Task getTask(int taskId);

  public void registerTask(Task task);

  public Task createTask();

  public class Access {
    public static TaskManager newInstance() {
      return new TaskManagerImpl();
    }
  }

  public int getTaskCount();

  int getProjectCompletion();

  public TaskManager emptyClone();

  Boolean isZeroMilestones();

  DependencyGraph getDependencyGraph();
}
