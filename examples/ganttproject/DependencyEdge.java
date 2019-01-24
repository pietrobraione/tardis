package ganttproject;

import java.util.Date;

import com.google.common.collect.Range;

public interface DependencyEdge {
    /**
     * @return dst node start date constraint
     */
    Range<Date> getStartRange();

    /**
     * @return dst node end date constraint
     */
    Range<Date> getEndRange();

    /**
     * @return this dependency target node
     */
    Node getDst();

    /**
     * @return this dependency source node
     */
    Node getSrc();

    boolean isWeak();
  }
