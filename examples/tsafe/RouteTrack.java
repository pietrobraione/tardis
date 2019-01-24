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
 * Represents a flight's track along its route
 */
public class RouteTrack extends FlightTrack {

    private Fix prevFix, nextFix;
    
    public RouteTrack(Fix prevFix, Fix nextFix, double latitude, double longitude,
                      double altitude, /*MODI long*/ double time, double speed, double heading) {
        super(latitude, longitude, altitude, time, speed, heading);
        this.prevFix = prevFix;
        this.nextFix = nextFix;
    }
    
    public RouteTrack(Fix prevFix, Fix nextFix, FlightTrack ft) {
        super(ft.getLatitude(), ft.getLongitude(), ft.getAltitude(),
              ft.getTime(), ft.getSpeed(), ft.getHeading());
        this.prevFix = prevFix;
        this.nextFix = nextFix;
    }
    
    public Fix getPrevFix() {
        return prevFix;
    }
    
    public Fix getNextFix() {
        return nextFix;
    }
}