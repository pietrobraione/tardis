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
public interface MutableTask {
  void setName(String name);

  void setMilestone(boolean isMilestone);

  void setPriority(Task.Priority priority);

  void setCompletionPercentage(int percentage);

  // void setStartFixed(boolean isFixed);

  // void setFinishFixed(boolean isFixed);

  void setExpand(boolean expand);

  /**
   * Sets the task as critical or not. The method is used be TaskManager after
   * having run a CriticalPathAlgorithm to set the critical tasks. When painted,
   * the tasks are rendered as critical using Task.isCritical(). So, a task is
   * set as critical only if critical path is displayed.
   * 
   * @param critical
   *          <code>true</code> if this is critical, <code>false</code>
   *          otherwise.
   */
  void setCritical(boolean critical);

  void setProjectTask(boolean projectTask);
}
