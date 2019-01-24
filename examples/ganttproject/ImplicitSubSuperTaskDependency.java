package ganttproject;

import java.util.Date;

import com.google.common.base.Objects;
import com.google.common.collect.Range;

/**
 * Implicit dependency between a subtask and a supertask. It puts constraints
 * on supertask bounds: it should start not later than subtask starts and
 * should start not earlier than subtask ends
 */
public class ImplicitSubSuperTaskDependency implements DependencyEdge {

  private final Node mySubTask;
  private final Node mySuperTask;
  private Range<Date> myStartRange;
  private Range<Date> myEndRange;

  public ImplicitSubSuperTaskDependency(Node subTask, Node superTask) {
    mySubTask = subTask;
    mySuperTask = superTask;
  }

  @Override
  public Range<Date> getStartRange() {
    return myStartRange;
  }

  @Override
  public Range<Date> getEndRange() {
    return myEndRange;
  }

  @Override
  public Node getDst() {
    return mySuperTask;
  }

  @Override
  public Node getSrc() {
    return mySubTask;
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(mySubTask, mySuperTask);
  }

  @Override
  public boolean equals(Object obj) {
    if (obj instanceof ImplicitSubSuperTaskDependency == false) {
      return false;
    }
    ImplicitSubSuperTaskDependency that = (ImplicitSubSuperTaskDependency) obj;
    return this.mySubTask.myTask.equals(that.mySubTask.myTask) && this.mySuperTask.myTask.equals(that.mySuperTask.myTask);
  }

  @Override
  public boolean isWeak() {
    return false;
  }

  @Override
  public String toString() {
    return mySubTask.toString() + " is a subtask of " + mySuperTask.toString();
  }
}
