/*
GanttProject is an opensource project management tool.
Copyright (C) 2004-2011 GanttProject Team

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

/**
 * @author bard
 */
public class TaskDependencyImpl implements TaskDependency {
  private int myDifference;

  private final Task myDependant;

  private final Task myDependee;

  private Hardness myHardness;

  public TaskDependencyImpl(Task dependant, Task dependee) {
    this(dependant, dependee, Hardness.STRONG, 0);
  }

  public TaskDependencyImpl(Task dependant, Task dependee, Hardness hardness, int lag) {
    myDependant = dependant;
    myDependee = dependee;
    if (dependee == null || dependant == null) {
      throw new IllegalArgumentException("invalid participants of dependency: dependee=" + dependee + " dependant="
          + dependant);
    }
    myDifference = lag;
    myHardness = hardness;
  }

  @Override
  public Task getDependant() {
    return myDependant;
  }

  @Override
  public Task getDependee() {
    return myDependee;
  }

  @Override
  public boolean equals(Object obj) {
    boolean result = obj instanceof TaskDependency;
    if (result) {
      TaskDependency rvalue = (TaskDependency) obj;
      result = myDependant.equals(rvalue.getDependant()) && myDependee.equals(rvalue.getDependee());
    }
    return result;
  }

  @Override
  public int hashCode() {
    return 7 * myDependant.hashCode() + 9 * myDependee.hashCode();
  }

  @Override
  public void setDifference(int difference) {
    myDifference = difference;
  }

  @Override
  public int getDifference() {
    return myDifference;
  }

  @Override
  public Hardness getHardness() {
    return myHardness;
  }

  @Override
  public void setHardness(Hardness hardness) {
    myHardness = hardness;
  }

  @Override
  public String toString() {
    return myDependee + "->" + myDependant;
  }

  public TaskDependency getImpl() {
    return this;
  }
}
