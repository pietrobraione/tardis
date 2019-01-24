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

import java.awt.Color;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.logging.Level;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.math.BigDecimal;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLEncoder;

import com.google.common.collect.ImmutableList;


/**
 * @author bard
 */
public class TaskImpl implements Task {
  private final int myID;

  private final TaskManagerImpl myManager;

  private String myName;

  private String myWebLink = "";

  private boolean isMilestone;

  boolean isProjectTask;

  private Priority myPriority;

  private int myThirdDateConstraint;

  private int myCompletionPercentage;

  private boolean bExpand;

  // private final TaskDependencyCollection myDependencies = new
  // TaskDependencyCollectionImpl();
  private boolean myEventsEnabled;

  private boolean critical;

  private boolean isUnplugged = false;

  public final static int NONE = 0;

  public final static int EARLIESTBEGIN = 1;

  public TaskImpl(TaskManagerImpl taskManager, int taskID) {
    myManager = taskManager;
    myID = taskID;

    myPriority = DEFAULT_PRIORITY;
    bExpand = true;
  }

  public TaskImpl(TaskImpl copy, boolean isUnplugged) {
    this.isUnplugged = isUnplugged;
    myManager = copy.myManager;
    // Use a new (unique) ID for the cloned task
    myID = myManager.getAndIncrementId();
 
    myName = copy.myName;
    myWebLink = copy.myWebLink;
    isMilestone = copy.isMilestone;
    isProjectTask = copy.isProjectTask;
    myPriority = copy.myPriority;
    myThirdDateConstraint = copy.myThirdDateConstraint;
    myCompletionPercentage = copy.myCompletionPercentage;
    bExpand = copy.bExpand;
  }

  @Override
  public int getTaskID() {
    return myID;
  }

  @Override
  public Task unpluggedClone() {
    TaskImpl result = new TaskImpl(this, true);
    return result;
  }

  class MutatorException extends RuntimeException {
    public MutatorException(String msg) {
      super(msg);
    }
  }

  @Override
  public String getName() {
    return myName;
  }

  @Override
  public boolean isMilestone() {
    return isMilestone && Boolean.TRUE == myManager.isZeroMilestones();
  }

  public boolean isLegacyMilestone() {
    return isMilestone;
  }
  @Override
  public Priority getPriority() {
    return myPriority;
  }

  @Override
  public int getThirdDateConstraint() {
    return myThirdDateConstraint;
  }

  @Override
  public int getCompletionPercentage() {
    return myCompletionPercentage;
  }

  @Override
  public boolean getExpand() {
    return bExpand;
  }

  @Override
  public TaskManager getManager() {
    return myManager;
  }

  private static interface EventSender {
    void enable();

  }

  private class ProgressEventSender implements EventSender {
    private boolean myEnabled;

    @Override
    public void enable() {
      myEnabled = true;
    }
  }

  private class PropertiesEventSender implements EventSender {
    private boolean myEnabled;

    @Override
    public void enable() {
      myEnabled = true;
    }
  }

  private static class FieldChange {
    Object myFieldValue;
    Object myOldValue;

    EventSender myEventSender;

    void setValue(Object newValue) {
      myFieldValue = newValue;
      myEventSender.enable();
    }

    public void setOldValue(Object oldValue) {
      myOldValue = oldValue;
    }
  }

  @Override
  public void setName(String name) {
    myName = (name == null ? null : name.trim());
  }

  @Override
  public void setMilestone(boolean milestone) {
    isMilestone = milestone;
  }

  @Override
  public void setPriority(Priority priority) {
    myPriority = priority;
  }

  @Override
  public void setThirdDateConstraint(int thirdDateConstraint) {
    myThirdDateConstraint = thirdDateConstraint;
    // recalculateActivities();
  }


  @Override
  public void setCompletionPercentage(int percentage) {
    if (percentage != myCompletionPercentage) {
      myCompletionPercentage = percentage;
      EventSender progressEventSender = new ProgressEventSender();
      progressEventSender.enable();
    }
  }

  @Override
  public void setExpand(boolean expand) {
    bExpand = expand;
  }

  protected void enableEvents(boolean enabled) {
    myEventsEnabled = enabled;
  }

  protected boolean areEventsEnabled() {
    return myEventsEnabled && myManager.areEventsEnabled();
  }

  @Override
  public String toString() {
    return getName();
  }

  public boolean isUnplugged() {
    return this.isUnplugged;
  }

  @Override
  public void setCritical(boolean critical) {
    this.critical = critical;
  }

  @Override
  public boolean isCritical() {
    return this.critical;
  }

  // TODO: implementation of this method has no correlation with algorithms
  // recalculating schedules,
  // doesn't affect subtasks and supertasks. It is necessary to call this
  // method explicitly from other
  // parts of code to be sure that constraint fulfills
  @Override
  public void applyThirdDateConstraint() {
//    if (getThird() != null)
//      switch (getThirdDateConstraint()) {
//      case EARLIESTBEGIN:
//        if (getThird().after(getStart())) {
//          shift(myManager.getTimeUnitStack().createDuration(getDuration().getTimeUnit(), getStart().getTime(), getThird().getTime()));
//        }
//        break;
//      }
  }

  @Override
  public boolean isProjectTask() {
    return isProjectTask;
  }

  @Override
  public void setProjectTask(boolean projectTask) {
    isProjectTask = projectTask;
  }
}
