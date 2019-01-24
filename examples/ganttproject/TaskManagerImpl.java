/*
 * Created on 05.07.2003
 *
 */
package ganttproject;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author bard
 */
public class TaskManagerImpl implements TaskManager {
  private AtomicInteger myMaxID = new AtomicInteger(0);

  private final Task myRoot;

  private final DependencyGraph myDependencyGraph = new DependencyGraph();

  private boolean areEventsEnabled = true;

  private static class TaskMap {
    private final Map<Integer, Task> myId2task = new HashMap<Integer, Task>();
    private boolean isModified = true;
    private Task[] myArray;
    private final TaskManagerImpl myManager;

    TaskMap(TaskManagerImpl taskManager) {
      myManager = taskManager;
    }

    void addTask(Task task) {
      myId2task.put(new Integer(task.getTaskID()), task);
      isModified = true;
    }

    Task getTask(int id) {
      return myId2task.get(new Integer(id));
    }

    public Task[] getTasks() {
      if (isModified) {
        myArray = myId2task.values().toArray(new Task[myId2task.size()]);
        isModified = false;
      }
      return myArray;
    }

    public void clear() {
      myId2task.clear();
      isModified = true;
    }

    public void removeTask(Task task) {
      myId2task.remove(new Integer(task.getTaskID()));
      isModified = true;
    }

    public int size() {
      return myId2task.size();
    }

    public boolean isEmpty() {
      return myId2task.isEmpty();
    }

    void setDirty() {
      isModified = true;
    }
  }

  private final TaskMap myTaskMap = new TaskMap(this);

  private Boolean isZeroMilestones = true;

  public TaskManagerImpl() {
    // clear();
    myRoot = createRootTask();
  }

  @Override
  public Task getTask(int taskId) {
    return myTaskMap.getTask(taskId);
  }

  @Override
  public Task getRootTask() {
    return myRoot;
  }

  @Override
  public Task[] getTasks() {
    return myTaskMap.getTasks();
  }

  private Task createRootTask() {
    Task root = new TaskImpl(this, -1);
    root.setExpand(true);
    root.setName("root");
    return root;
  }

  @Override
  public Task createTask() {
    return newTaskBuilder().build();
  }

  @Override
  public TaskBuilder newTaskBuilder() {
    return new TaskBuilder() {
      @Override
      public Task build() {

        if (myId == null || myTaskMap.getTask(myId) != null) {
          myId = getAndIncrementId();
        }

        TaskImpl task = myPrototype == null ?
            new TaskImpl(TaskManagerImpl.this, myId) : new TaskImpl((TaskImpl)myPrototype, false);

        String name = myName == null ? "_" + task.getTaskID() : myName;
        task.setName(name);


        if (isExpanded != null) {
          task.setExpand(isExpanded);
        }
        if (myCompletion != null) {
          task.setCompletionPercentage(myCompletion);
        }
        registerTask(task);



        if (isLegacyMilestone) {
          task.setMilestone(isLegacyMilestone);
        }
        return task;
      }
    };
  }

  int getAndIncrementId() {
    return myMaxID.getAndIncrement();
  }

  @Override
  public void registerTask(Task task) {
    int taskID = task.getTaskID();
    assert myTaskMap.getTask(taskID) == null : "There is a task that already has the ID " + taskID;
    myTaskMap.addTask(task);
    myMaxID.set(Math.max(taskID + 1, myMaxID.get()));
  }

  boolean isRegistered(TaskImpl task) {
    return myTaskMap.getTask(task.getTaskID()) != null;
  }

  @Override
  public int getTaskCount() {
    return myTaskMap.size();
  }

  @Override
  public int getProjectCompletion() {
    return myRoot.getCompletionPercentage();
  }


  @Override
  public TaskManager emptyClone() {
    TaskManagerImpl result = new TaskManagerImpl();
    return result;
  }

  public void setEventsEnabled(boolean enabled) {
    areEventsEnabled = enabled;
  }

  boolean areEventsEnabled() {
    return areEventsEnabled;
  }

  @Override
  public Boolean isZeroMilestones() {
    return isZeroMilestones;
  }

  @Override
  public DependencyGraph getDependencyGraph() {
    return myDependencyGraph;
  }
}
