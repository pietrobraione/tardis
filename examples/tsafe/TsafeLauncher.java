package tsafe;

import static jbse.meta.Analysis.fail;
import static jbse.meta.Analysis.ignore;

import org.junit.Assert;
import org.junit.Assume;

/**
 * Drivers for TS_R_3 property.
 * 
 * @author Pietro Braione
 *
 */
public class TsafeLauncher {	
	//Methods to test
	
	/**
	 * This checks the property TS_R_3, see <a href="https://doi.org/10.1145/2491411.2491433">Braione et al. 2013</a>.
	 * 
	 * @param trajSynth a {@link TrajectorySynthesizer}.
	 * @param routeTrack a {@link RouteTrack}.
	 * @param route a {@link Route}.
	 */
	public void checkTrajectorySynthesis(TrajectorySynthesizer trajSynth, RouteTrack routeTrack, Route route) {
		//assumes preconditions
		assumePreconditions(trajSynth, routeTrack, route);
		
		//invokes target method
		final Trajectory traj = trajSynth.getRouteTrajectory(routeTrack, route);

		//checks assertion
		if (!trajEndsCorrectly(traj, trajSynth, route)) {
			fail();
		}
	}

	//Private methods
	
	private void assume(boolean b) {
		if (!b) {
			throw new RuntimeException("Assumption violated");
		}
	}

	private void assumePreconditions(TrajectorySynthesizer trajSynth, RouteTrack routeTrack, Route route) {
		assume(trajSynth.params.tsTimeHorizon > 0);
		assume(routeTrack.getSpeed() > 0);
		assume(collinear(trajSynth, routeTrack.getPrevFix(), routeTrack.getNextFix(), new Point2D(routeTrack.getLatitude(), routeTrack.getLongitude())));
		assume(inRectangle(trajSynth, routeTrack.getPrevFix(), routeTrack.getNextFix(), new Point2D(routeTrack.getLatitude(), routeTrack.getLongitude())));
	}
	
	private boolean trajEndsCorrectly(Trajectory traj, TrajectorySynthesizer trajSynth, Route route) {
		Point4D lastPoint = traj.lastPoint();
		final boolean trajTravelsMaxTime = lastPoint.getTime() == traj.firstPoint().getTime() + trajSynth.params.tsTimeHorizon;
		final boolean trajReachesLastFix;
		if (trajTravelsMaxTime) {
			trajReachesLastFix = false;
		} else if (route.isEmpty()) {
			trajReachesLastFix = false;
		} else {
			Fix lastFix = route.lastFix();
			trajReachesLastFix = lastFix.getLatitude() == lastPoint.getLatitude() && lastFix.getLongitude() == lastPoint.getLongitude();
		}
		return (trajTravelsMaxTime || trajReachesLastFix); 
	}
	
	/**
	 * Checks whether three {@link Point2D}s are collinear according 
	 * to {@link #trajSynth}'s linear approximation, within an error. 
	 * 
	 * @param first a {@link Point2D}.
	 * @param second a {@link Point2D}.
	 * @param third a {@link Point2D}.
	 * @return {@code true} iff its parameters are on a same straight line.
	 */
	private static final double EPSILON_COLLINEAR = 1E4; //100 x 100
	private boolean collinear(TrajectorySynthesizer trajSynth, Point2D first, Point2D second, Point2D third) {
		PointXY firstXY = trajSynth.calc.toXY(first);
		PointXY secondXY = trajSynth.calc.toXY(second);
		PointXY thirdXY = trajSynth.calc.toXY(third);
		double diff = firstXY.getX() * (secondXY.getY() - thirdXY.getY())
		           +  secondXY.getX() * (thirdXY.getY() - firstXY.getY())
		           +  thirdXY.getX() * (firstXY.getY() - secondXY.getY());
		return (-EPSILON_COLLINEAR <= diff && diff <= EPSILON_COLLINEAR);
	}
	
	/**
	 * Checks that a point is in a rectangle according 
	 * to {@link #trajSynth}'s linear approximation, within an error.
	 * 
	 * @param firstCorner a {@link Point2D}, a corner of the rectangle.
	 * @param secondCorner a {@link Point2D}, the corner of the rectangle
	 *        opposite to {@code firstCorner}.
	 * @param toCheck another {@link Point2D}.
	 * @return {@code true} iff {@code toCheck} is in the rectangle
	 *         (according to {@link #trajSynth}'s linear approximation)
	 *         with opposite vertices {@code firstCorner} and {@code secondCorner}.
	 */
	private boolean inRectangle(TrajectorySynthesizer trajSynth, Point2D firstCorner, Point2D secondCorner, Point2D toCheck) {
		final PointXY firstCornerXY = trajSynth.calc.toXY(firstCorner);
		final PointXY secondCornerXY = trajSynth.calc.toXY(secondCorner);
		final PointXY toCheckXY = trajSynth.calc.toXY(toCheck);
		return inInterval(firstCornerXY.getX(), secondCornerXY.getX(), toCheckXY.getX()) &&
				inInterval(firstCornerXY.getY(), secondCornerXY.getY(), toCheckXY.getY());
	}

	/**
	 * Checks that a value is in an interval.
	 * 
	 * @param first One of the bounds of the interval.
	 * @param second The other bound of the interval. It is NOT required that {@code first <= second}.
	 * @param toCheck The value to be checked.
	 * @return {@code true} iff {@code toCheck} belongs to the interval {@code first..second}
	 *         or {@code second..first}, depending on which is the greater.
	 */
	private boolean inInterval(double first, double second, double toCheck) {
		if (first <= toCheck) {
			return (toCheck <= second);
		} else { // toCheck < first
			return (second <= toCheck);
		}
	}	
}