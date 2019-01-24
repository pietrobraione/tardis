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

/**
 * Represents a airpace fix, airport, or navaid.
 * This is an immutable data structure
 */
public class Fix extends Point2D {

    /**
     * The fix id, assumes fixes have unique ids
     */
    private String id;

    /**
     * Constructs a Fix with the given name and coordinates.
     */
    public Fix (String id, double lat, double lon) {
        super(lat, lon);
        this.id = id;
    }

    /**
     * Constructs a Fix with the given name and coordinates.
     */
    public Fix (String id, Point2D location) {
        this(id, location.getLatitude(), location.getLongitude());
    }

    /**
     * Returns the id of this fix.
     */
    public String getId() {
        return this.id;
    }

    /**
     * Return a hash code for this object
     */
    public int hashCode() {
        return id.hashCode();
    }

    /**
     * Return true is o is a Fix and has the same id as this
     */
    public boolean equals(Object o) {
//MODI BEGIN
    	if (o == null) {
    		return false;
    	}
    	if (this == o) {
    		return true;
    	}
    	if (! (o instanceof Fix)) {
    		return false;
    	}
    	Fix f = (Fix) o;
    	//we inject here the implicit assumption that, if two fixes have same id, 
    	//then they have same lat-lon. Also, we simplify id comparison and replace
    	//it with identity (easier than stubbing String.equals(), and with similar 
    	//effect)
    	return (f.id == this.id && f.getLatitude() == this.getLatitude() && f.getLongitude() == this.getLongitude());
    	
        //return o != null &&
        //       o instanceof Fix &&
        //       ((Fix)o).id.equals(this.id);
//MODI END
    }

    /**
     * Return a String representation of this Fix
     */
    public String toString() {
        return id;
    }
}
