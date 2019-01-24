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
 * Hold the parameters for all the engine algorithms
 */
public class EngineParameters {

    public EngineParameters() {}

    // CONFORMANCE MONITOR (CM) PARAMETERS
    private static final boolean DEFAULT_CM_LATERAL_WEIGHT_ON  = false;
    private static final boolean DEFAULT_CM_VERTICAL_WEIGHT_ON = false;
    private static final boolean DEFAULT_CM_ANGULAR_WEIGHT_ON  = false;
    private static final boolean DEFAULT_CM_SPEED_WEIGHT_ON    = false;
    public boolean cmLateralWeightOn  = DEFAULT_CM_LATERAL_WEIGHT_ON;
    public boolean cmVerticalWeightOn = DEFAULT_CM_VERTICAL_WEIGHT_ON;
    public boolean cmAngularWeightOn  = DEFAULT_CM_ANGULAR_WEIGHT_ON;
    public boolean cmSpeedWeightOn    = DEFAULT_CM_SPEED_WEIGHT_ON;
    
    private static final double DEFAULT_CM_LATERAL_THRESHOLD  = 18520.0;
    private static final double DEFAULT_CM_VERTICAL_THRESHOLD = 609.7561;
    private static final double DEFAULT_CM_ANGULAR_THRESHOLD  = 0.5;
    private static final double DEFAULT_CM_SPEED_THRESHOLD    = 100;
    public double cmLateralThreshold  = DEFAULT_CM_LATERAL_THRESHOLD;
    public double cmVerticalThreshold = DEFAULT_CM_VERTICAL_THRESHOLD;
    public double cmAngularThreshold  = DEFAULT_CM_ANGULAR_THRESHOLD;
    public double cmSpeedThreshold    = DEFAULT_CM_SPEED_THRESHOLD;

    private static final double DEFAULT_CM_RESIDUAL_THRESHOLD = 1.0;
    public double cmResidualThreshold = DEFAULT_CM_RESIDUAL_THRESHOLD;

    // TRAJECTORY SYNTHESIZER (TS) PARAMETERS
    private static final long DEFAULT_TS_TIME_HORIZON = 3 * 60 * 1000;
    public long tsTimeHorizon = DEFAULT_TS_TIME_HORIZON;
//MODI BEGIN
    //to help EvoSuite
    public void setCmLateralWeightOn(boolean val) {
    	this.cmLateralWeightOn = val;
    }
    public void setCmVerticalWeightOn(boolean val) {
    	this.cmVerticalWeightOn = val;
    }
    public void setCmAngularWeightOn(boolean val) {
    	this.cmAngularWeightOn = val;
    }
    public void setCmSpeedWeightOn(boolean val) {
    	this.cmSpeedWeightOn = val;
    }
    public void setCmLateralThreshold(double val) {
    	this.cmLateralThreshold = val;
    }
    public void setCmVerticalThreshold(double val) {
    	this.cmVerticalThreshold = val;
    }
    public void setCmAngularThreshold(double val) {
    	this.cmAngularThreshold = val;
    }
    public void setCmSpeedThreshold(double val) {
    	this.cmSpeedThreshold = val;
    }
    public void setCmResidualThreshold(double val) {
    	this.cmResidualThreshold = val;
    }
    public void setTsTimeHorizon(long val) {
    	this.tsTimeHorizon = val;
    }
//MODI END
}
