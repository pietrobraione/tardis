/*
GanttProject is an opensource project management tool.
Copyright (C) 2002-2010 Alexandre Thomas, Dmitry Barashev

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

/**
 * Project task definition
 *
 * @author bard
 */
public interface Task extends MutableTask {
  /** Available task priorities */
  public enum Priority {
    LOWEST("3"), LOW("0"), NORMAL("1"), HIGH("2"), HIGHEST("4");

    private final String myPersistentValue;

    private Priority(String persistentValue) {
      myPersistentValue = persistentValue;
    }

    /**
     * @return the Priority value for the given integer value, or
     *         DEFAULT_PRIORITY if unknown
     */
    public static Priority getPriority(int value) {
      for (Task.Priority p : Task.Priority.values()) {
        if (p.ordinal() == value) {
          return p;
        }
      }
      return DEFAULT_PRIORITY;
    }

    public String getPersistentValue() {
      return myPersistentValue;
    }

    /** @return the priority as a lower-case String */
    public String getLowerString() {
      return this.toString().toLowerCase();
    }

    /** @return the key to get the I18n value for the priority */
    public String getI18nKey() {
      return "priority." + getLowerString();
    }

    /**
     * @return the path to the icon representing the priority
     */
    public String getIconPath() {
      return "/icons/task_" + getLowerString() + ".gif";
    }

    public static Priority fromPersistentValue(String priority) {
      for (Priority p : values()) {
        if (p.getPersistentValue().equals(priority)) {
          return p;
        }
      }
      return Priority.NORMAL;
    }
  }

  /** Default priority (for new tasks) */
  public static final Priority DEFAULT_PRIORITY = Priority.NORMAL;

  public static interface Cost {
    BigDecimal getValue();
    BigDecimal getManualValue();
    BigDecimal getCalculatedValue();
    void setValue(BigDecimal value);
    boolean isCalculated();
    void setCalculated(boolean calculated);
  }

  int getTaskID();

  String getName();

  boolean isMilestone();

  Priority getPriority();

  int getCompletionPercentage();

  /**
   * @return a color representing this Task (could be a custom color, milestone
   *         color, super task color or default color)
   */
  boolean getExpand();

  //
  TaskManager getManager();

  Task unpluggedClone();

  boolean isCritical();

  void applyThirdDateConstraint();

  int getThirdDateConstraint();

  void setThirdDateConstraint(int dateConstraint);

  boolean isProjectTask();
}
