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
 * Represents a route, or 2-dimensional series of fixes
 */
public class Route {

    /**
     * List of all the fixes in the route
     */
//MODI BEGIN
    public
    //private 
    List fixes;
//MODI END

    /**
     * Construct an empty route
     */
    public Route () {
        fixes = new LinkedList();
    }
    
    /**
     * Construct a route that is a copy of another
     */
    public Route(Route r) {
        this.fixes = new LinkedList(r.fixes);
    }

    /**
     * Add a fix to the route
     */
    public void addFix(Fix f) {
        fixes.add(f);
    }

    /**
     * Returns true if the route is empty
     */
    public boolean isEmpty() {
        return fixes.isEmpty();
    }

    /**
     * Returns the first fix in the route
     *
     *@throws NoSuchElementException if the route is empty
     */
    public Fix firstFix() {
        if (isEmpty()) throw new NoSuchElementException("route is empty");
        return (Fix)fixes.get(0);
    }

    /**
     * Returns the last fix in the route
     *
     *@throws NoSuchElementException if the route is empty
     */
    public Fix lastFix() {
        if (isEmpty()) throw new NoSuchElementException("route is empty");
        return (Fix)fixes.get(fixes.size() - 1);
    }

    /**
     * Return an unmodifiable list of fixes in the route
     */
    public List fixList() {
        return Collections.unmodifiableList(fixes);
    }

    /**
     * Return an iterator over the fixes in the route
     */
    public Iterator fixIterator() {
        return fixes.iterator();
    }

    /**
     * Return a String representation of this route
     */
    public String toString() {
        StringBuffer sb = new StringBuffer();
        Iterator fixIter = fixes.iterator();

        while(fixIter.hasNext()) {
            sb.append(fixIter.next());
            if (fixIter.hasNext()) {
                sb.append(".");
            }
        }

        return sb.toString();
    }
}