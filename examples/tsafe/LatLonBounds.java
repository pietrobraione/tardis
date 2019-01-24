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

/**
 * Represents an area bounded by lat/lon coordinates
 * This data structure is immutable
 */
public class LatLonBounds {

    /**
     * Lat/lon bounds coordinates
     */
    public final double minLat, minLon, maxLat, maxLon;    

    /**
     * Construct a lat/lon boundary
     */
    public LatLonBounds(double minLat, double minLon, double maxLat, double maxLon) {
        this.minLat = minLat;
        this.minLon = minLon;
        this.maxLat = maxLat;
        this.maxLon = maxLon;
    }

    /**
     * Returns true is p lies within these bounds; false otherwise
     */
    public boolean contains(Point2D p) {
        return contains(p.getLatitude(), p.getLongitude());
    }

    /**
     * Returns true is the point (lat, lon) lies within these bounds; false otherwise
     */
    public boolean contains(double lat, double lon) {
        return (lat > minLat && lat < maxLat) &&
               (lon > minLon && lon < maxLon);
    }

    public String toString() {
        return "Latitude: "  + minLat + "-" + maxLat + " " +
        "Longitude: " + minLon + "-" + maxLon;
    }
}