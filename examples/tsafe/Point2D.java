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
 * A 2-Dimensional point with latitude and longitude components
 * This data structure is immutable
 */
public class Point2D {
//MODI BEGIN
    //private
	public
//MODI END
	double lat, lon;

    public Point2D(double lat, double lon) {
        this.lat = lat;
        this.lon = lon;
    }

    public double getLatitude()  {return lat;}
    public double getLongitude() {return lon;}
}
