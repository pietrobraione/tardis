package ganttproject;

import java.util.Date;

import com.google.common.base.Objects;
import com.google.common.collect.Range;


/**
 * Implicit dependency which is inherited by subtasks when explicit dependency to their supertask
 * is added to the graph.
 */
public class ImplicitInheritedDependency implements DependencyEdge {
  final DependencyEdge myExplicitDep;
  private final Node mySrc;
  private final Node myDst;

  public ImplicitInheritedDependency(DependencyEdge explicitIncoming, Node supertaskNode, Node subtaskNode) {
    myExplicitDep = explicitIncoming;
    mySrc = explicitIncoming.getSrc();
    myDst = subtaskNode;
  }

  @Override
  public Range<Date> getStartRange() {
    return myExplicitDep.getStartRange();
  }

  @Override
  public Range<Date> getEndRange() {
    return myExplicitDep.getEndRange();
  }

  @Override
  public Node getDst() {
    return myDst;
  }

  @Override
  public Node getSrc() {
    return mySrc;
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(mySrc.myTask, myDst.myTask);
  }

  @Override
  public boolean equals(Object obj) {
    if (obj instanceof ImplicitInheritedDependency == false) {
      return false;
    }
    ImplicitInheritedDependency that = (ImplicitInheritedDependency) obj;
    return this.mySrc.myTask.equals(that.mySrc.myTask) && this.myDst.myTask.equals(that.myDst.myTask);
  }

  @Override
  public boolean isWeak() {
    return true;
  }

  @Override
  public String toString() {
    return "Dependency inherited from supertask:" + myExplicitDep.toString();
  }
}


