/*
 TSAFE Prototype: A decision support tool for air traffic controllers
 Copyright (C) 2003  Gregory D. Dennis

 This program is free software; you can redistribute it and/or
 modify it under the terms of the GNU General Public License
 as published by the Free Software Foundation; either version 2
 of the License, or (at your option) any later version.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with this program; if not, write to the Free Software
 Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */

package tsafe;

import java.util.*;

import common.LinkedList;

/**
 * Represents a trajectory, or 4-Dimensional path
 */
public class Trajectory {
    
    /**
     * List of all the points in the trajectory
     */
    private List points;

    /**
     * Construct an empty trajectory
     */
    public Trajectory() {
        points = new LinkedList();
    }

    /**
     * Construct a trajectory that is a copy of another
     */
    public Trajectory(Trajectory t) {
        this.points = new LinkedList(t.points);
    }

    /**
     * Add a point to the trajectory
     */
    public void addPoint(Point4D p) {
        points.add(p);
    }

    /**
     * Returns true if the trajectory is empty
     */
    public boolean isEmpty() {
        return points.isEmpty();
    }

    /**
     * Returns the first point in the trajectory
     *
     *@throws NoSuchElementException if the trajectory is empty
     */
    public Point4D firstPoint() {
        if (isEmpty()) throw new NoSuchElementException("trajectory is empty");
        return (Point4D)points.get(0);
    }

    /**
     * Returns the last point in the trajectory
     *
     *@throws NoSuchElementException if the trajectory is empty
     */
    public Point4D lastPoint() {
        if (isEmpty()) throw new NoSuchElementException("trajectory is empty");
        return (Point4D)points.get(points.size() - 1);
    }
    
    /**
     * Return an unmodifiable list of points in the trajectory
     */
    public List pointList() {
        return Collections.unmodifiableList(points);
    }

    /**
     * Return an iterator over the points in the trajectory
     */
    public Iterator pointIterator() {
        return points.iterator();
    }

    /**
     * Return a String representation of this trajectory
     */
    public String toString() {
        StringBuffer sb = new StringBuffer("[");
        Iterator pointIter = points.iterator();

        while(pointIter.hasNext()) {
            sb.append(pointIter.next());
            if (pointIter.hasNext()) {
                sb.append(",");
            }
        }

        sb.append("]");
        return sb.toString();
    }
}