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

import tsafe.Point2D;
import tsafe.PointXY;

/**
 * Calculates distances, angles, and converts lat/long coordinates to x,y coordinates
 */
abstract public class EngineCalculator {

    // ABSTRACT CONVERSION METHODS
    abstract public PointXY toXY(double lat, double lon);
    abstract public Point2D toLL(double x, double y);

    // NON-ABSTRACT CONVERSION METHODS
    public PointXY toXY(Point2D p) {
        return toXY(p.getLatitude(), p.getLongitude());
    }

    public Point2D toLL(PointXY p) {
        return toLL(p.getX(), p.getY());
    }

    // DISTANCES BETWEEN LAT/LON COORDINATES
    public double distanceLL(double lat1, double lon1, double lat2, double lon2) {
        return distanceXY(toXY(lat1, lon1), toXY(lat2, lon2));
    }

    public double distanceLL(double lat1, double lon1, Point2D p2) {
        return distanceLL(lat1, lon1, p2.getLatitude(), p2.getLongitude());
    }

    public double distanceLL(Point2D p1, Point2D p2) {
        return distanceLL(p1.getLatitude(), p1.getLongitude(), p2.getLatitude(), p2.getLongitude());
    }

    // DISTANCES BETWEEN X,Y COORDINATES
    public double distanceXY(double x1, double y1, double x2, double y2) {
        return java.awt.geom.Point2D.distance(x1, y1, x2, y2);
    }

    public double distanceXY(double x1, double y1, PointXY p2) {
        return distanceXY(x1, y1, p2.getX(), p2.getY());
    }

    public double distanceXY(PointXY p1, PointXY p2) {
        return distanceXY(p1.getX(), p1.getY(), p2.getX(), p2.getY());
    }

    // ANGLE BETWEEN LAT/LON COORDINATES
    public double angleLL(double lat1, double lon1, double lat2, double lon2) {
        return angleXY(toXY(lat1, lon1), toXY(lat2, lon2));
    }

    public double angleLL(double lat1, double lon1, Point2D p2) {
        return angleLL(lat1, lon1, p2.getLatitude(), p2.getLongitude());
    }

    public double angleLL(Point2D p1, Point2D p2) {
        return angleLL(p1.getLatitude(), p1.getLongitude(), p2.getLatitude(), p2.getLongitude());
    }

    // ANGLE BETWEEN X,Y COORDINATES
    /* Returns an angle between 0 and 2PI */
    public double angleXY(double x1, double y1, double x2, double y2) {
        double x = x2 - x1;
        double y = y2 - y1;

//MODI BEGIN
        //if (x == 0 && y > 0) {
        if (x == 0) {
        	if (y > 0) {
//MODI END
            return Math.PI / 2;
//MODI BEGIN
        //} else if (x == 0 && y <= 0) {
            	} else { // y <= 0
//MODI END
            return Math.PI / -2;
//MODI BEGIN
            	}
//MODI END
        }
        else {
            double angle = Math.atan(y / x);

            if (x < 0) {
                angle += Math.PI;
            } else if (/* MODI x > 0 &&*/ y < 0) {
                angle += 2 * Math.PI;
            }

            return angle;
        }
    }

    public double angleXY(double x1, double y1, PointXY p2) {
        return angleXY(x1, y1, p2.getX(), p2.getY());
    }

    public double angleXY(PointXY p1, PointXY p2) {
        return angleXY(p1.getX(), p1.getY(), p2.getX(), p2.getY());
    }
}
