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
 * A flight's position, speed, and heading information
 * This is an immutable datatype
 */
public class FlightTrack {

    /**
     * Track variables
     */
//MODI BEGIN
    //private
	public
//MODI END
    double lat, lon, alt, speed, heading;
    private /*MODI long*/ double time;

    /**
     * Construct a flight track with the given parameters
     */
    public FlightTrack(double lat, double lon, double alt, /*MODI long*/ double time, double speed, double heading) {
        this.lat = lat;
        this.lon = lon;
        this.alt = alt;
        this.time = time;
        this.speed     = speed;
        this.heading   = heading;
    }

    /** Return the latitude */
    public double getLatitude()  {
        return lat;
    }

    /** Return the longitude */    
    public double getLongitude() {
        return lon;
    }

    /** Return the altitude */    
    public double getAltitude()  {
        return alt;
    }

    /** Return the time */
    public /*MODI long*/ double getTime() {
        return time;
    }

    /** Return the track speed */
    public double getSpeed() {
        return speed;
    }

    /** Return the track heading */
    public double getHeading() {
        return heading;
    }

    /**
     * Return a Point4D rep of this track
     */
    public Point4D asPoint4D() {
        return new Point4D(lat, lon, alt, time);
    }
}