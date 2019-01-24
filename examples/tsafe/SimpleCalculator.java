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
 * Simple way of doing lat/lon to x,y conversion
 */
public class SimpleCalculator extends EngineCalculator {

    /**
     * Conversion constants
     */
    private static final double EARTH_RADIUS = 6371000.0;
    private static final double RADIANS_PER_DEGREE = Math.PI / 180.0;
    private static final double METERS_PER_LAT = EARTH_RADIUS * RADIANS_PER_DEGREE;
    private static final double METERS_PER_LON_AT_EQUATOR = METERS_PER_LAT;

    /**
     * The origin in lat/lon
     */
//MODI BEGIN
    //private
//MODI END
    double minLat, minLon;

    /**
     * Construct a simple calculator
     */
    public SimpleCalculator(LatLonBounds bounds) {
        this.minLat = bounds.minLat;
        this.minLon = bounds.minLon;
    }

    /**
     * Return the meters per degree longitude at the given degree latitude
     */
    private double metersPerLonAt(double lat) {
        return METERS_PER_LON_AT_EQUATOR * Math.cos(lat * RADIANS_PER_DEGREE);
    }

    /**
     * Implementation of EngineCalculator method
     */
    public PointXY toXY(double lat, double lon) {
//MODI BEGIN
        return new PointXY(toX(lat, lon), toY(lat, lon));
        //return new PointXY((lon - minLon) * metersPerLonAt(lat), (lat - minLat) * METERS_PER_LAT);
//MODI END
    }

    /**
     * Implementation of EngineCalculator method
     */
    public Point2D toLL(double x, double y) {
//MODI BEGIN
        return new Point2D(toLat(x, y), toLon(x, y));
        //double lat = (y / METERS_PER_LAT) + minLat;
        //double lon = (x / metersPerLonAt(lat)) + minLon;
        //return new Point2D(lat, lon);
//MODI END
    }

//MODI BEGIN
    private double toX(double lat, double lon) {
    	return (lon - minLon) * metersPerLonAt(lat);
    }
    
    private double toY(double lat, double lon) {
    	return (lat - minLat) * METERS_PER_LAT;
    }
    
    private double toLat(double x, double y) {
    	return (y / METERS_PER_LAT) + minLat;
    }
    
    private double toLon(double x, double y) {
    	return (x / metersPerLonAt(toLat(x, y))) + minLon;
    }
//MODI END
    
}
