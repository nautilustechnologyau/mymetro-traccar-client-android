package au.mymetro.operator;

import android.location.Location;
import android.os.AsyncTask;
import android.util.Log;

import com.google.type.Date;

import org.onebusaway.geospatial.model.XYPoint;
import org.onebusaway.transit_data_federation.impl.shapes.PointAndIndex;
import org.onebusaway.transit_data_federation.impl.shapes.ShapePointsLibrary;

import java.util.ArrayList;
import java.util.List;

import au.mymetro.operator.oba.io.elements.ObaStop;
import au.mymetro.operator.oba.io.elements.ObaTripSchedule;
import au.mymetro.operator.oba.io.elements.ObaTripStatus;
import au.mymetro.operator.oba.io.elements.ObaTripStatusElement;
import au.mymetro.operator.oba.io.request.ObaTripDetailsResponse;
import au.mymetro.operator.oba.util.LocationUtils;

public class TripStatusUtils {
    public static String TAG = "TripStatusUtils";
    private int mCurrentStopIndex = -1;
    private int mNextStopIndex = -1;
    private final List<XYPoint> mPoints;
    private double[] mShapePointDistances;
    private ObaTripDetailsResponse mResponse;
    private ObaStop mCurrentStop;
    private ObaStop mNextStop;
    private final List<ObaStop> mStops;
    private final List<ObaTripSchedule.StopTime> mStopTimes;
    private ShapePointsLibrary mSharePointLibrary;
    private TripStatusComputedListener mTripStatusComputedListener;
    private CloseToStopListener mCloseToStopListener;
    private boolean mTripEnded = false;
    private double mMinDistanceToDetermineTripEnd = 10;
    private double mMinDistanceToDetermineCloseStop = 20;
    private long mLastDistanceSampleTime = 0;

    public double getSpeed() {
        return mSpeed;
    }

    private double mSpeed = 0;
    final double THRESHOLD = .0001;

    public int getDistanceToNextStop() {
        return Math.round(mDistanceToNextStop);
    }

    private float mDistanceToNextStop = 0;
    private int mCloseNotificationSentForStopIndex = -1;
    //private ShapePoints mShapePoints;
    //private List<XYPoint> mProjectedShapePoints;
    //private UTMProjection mProjection;

    private AsyncTask<?, ?, ?> computeTask;

    public interface TripStatusComputedListener {
        void onTripStatusComputed(TripStatusUtils tripStatus);
    }

    public interface CloseToStopListener {
        void onCloseToStop(ObaStop stop, ObaTripSchedule.StopTime stopTime, PointAndIndex pointAndIndex);
    }

    public TripStatusUtils(ObaStop currentStop, ObaTripDetailsResponse response, TripStatusComputedListener tripStatusComputedListener, CloseToStopListener closeToStopListener) {
        mSharePointLibrary = new ShapePointsLibrary();
        mSharePointLibrary.setLocalMinimumThreshold(500);
        mPoints = new ArrayList<>();
        mStops = new ArrayList<>();
        mStopTimes = new ArrayList<>();

        mResponse = response;
        mCurrentStop = currentStop;
        mNextStop = currentStop;

        mTripStatusComputedListener = tripStatusComputedListener;
        mCloseToStopListener = closeToStopListener;

        if (response == null) {
            mTripEnded = true;
            return;
        }

        if (currentStop == null) {
            mTripEnded = true;
            return;
        }

        ObaTripSchedule tripSchedule = response.getSchedule();
        if (tripSchedule == null) {
            mTripEnded = true;
            return;
        }

        ObaTripSchedule.StopTime[] stopTimes = tripSchedule.getStopTimes();
        if (stopTimes == null) {
            mTripEnded = true;
            return;
        }

        int index = 0;
        for (ObaTripSchedule.StopTime stopTime : stopTimes) {
            ObaStop stop = response.getStop(stopTime.getStopId());
            if (stop != null) {
                mPoints.add(p(stop.getLongitude(), stop.getLatitude()));
                mStops.add(stop);
                mStopTimes.add(stopTime);
                // get current stop index
                if (mCurrentStopIndex == -1 && stop.getId().equals(currentStop.getId())) {
                    mCurrentStopIndex = index;
                }
                index++;
            }
        }

        if (mPoints.isEmpty() || mPoints.size() < 2) {
            mTripEnded = true;
            return;
        }

        //mShapePoints = createShapePoints(mPoints);

        //mProjection = UTMLibrary.getProjectionForPoint(
        //        mShapePoints.getLats()[0], mShapePoints.getLons()[0]);

        //mProjectedShapePoints = mSharePointLibrary.getProjectedShapePoints(
        //        mShapePoints, mProjection);

        mShapePointDistances = getShapePointDistances(); //  getShapePointDistances(mPoints);
        if (mShapePointDistances.length < 2) {
            mTripEnded = true;
            return;
        }

        if (mCurrentStopIndex == -1) {
            //mCurrentStopIndex = 0;
            mTripEnded = true;
            return;
        }

        mNextStopIndex = mCurrentStopIndex;

        /*if (mCurrentStopIndex < mStops.size() - 1) {
            mNextStopIndex = mCurrentStopIndex + 1;
        } else {
            mNextStopIndex = mCurrentStopIndex;
        }*/

        Log.d("TripStatusUtils", "Stop count: " + mStops.size());
        Log.d("TripStatusUtils", "mCurrentStopIndex: " + mCurrentStopIndex);
        computeTripStatus(mCurrentStop);
    }

    private void computeTripStatus(XYPoint targetPoint, Location location) {
        if (mTripEnded) {
            return;
        }

        /*if (mCurrentStopIndex == mStops.size() - 1) {
            mCurrentStop = mStops.get(mCurrentStopIndex);
            mNextStopIndex = mCurrentStopIndex;
            mNextStop = mCurrentStop;
            return;
        }

        if (mStops.size() < 2) {
            mCurrentStopIndex = 0;
            mCurrentStop = mStops.get(0);
            mNextStopIndex = mCurrentStopIndex;
            mNextStop = mCurrentStop;
            return;
        }

        if (mCurrentStopIndex >= mStopTimes.size() - 1) {
            mNextStopIndex = mCurrentStopIndex;
            mNextStop = mCurrentStop;
            return;
        }*/

        // current stop is the last stop. the trip is end
        /*if (mCurrentStopIndex >= mPoints.size() - 1) {
            mNextStopIndex = mCurrentStopIndex;
            mTripEnded = true;
            return;
        }*/

        /*if (mCurrentStopIndex + 1 >= mStops.size() - 1) {
            mCurrentStopIndex = mStops.size() - 1;
        }*/

        // Log.d("TripStatusUtils", "Searching from mCurrentStopIndex: " + mCurrentStopIndex);
        // Log.d("TripStatusUtils", "mNextStopIndex before search: " + mNextStopIndex);
        List<PointAndIndex> result = mSharePointLibrary.computePotentialAssignments(mPoints,
                mShapePointDistances, targetPoint, mCurrentStopIndex, mStops.size());
        PointAndIndex pointIndex = null;
        if (result == null || result.isEmpty()) {
            // next stop is too far? set next stop manually
            //mCurrentStopIndex++;
            //mNextStopIndex++;
            return;

            // try with index 0
            //if (mCurrentStopIndex != 0) {
//                result = mSharePointLibrary.computePotentialAssignments(mPoints,
//                        mShapePointDistances, targetPoint, 0, mPoints.size());
//
                // we have reached to trip end?
//                if (result == null || result.isEmpty()) {
//                    mTripEnded = true;
//                    return;
//                }
            //} else {
            //    return;
            //}

            /*if (mNextStopIndex >= mStops.size() - 1) {
                mNextStopIndex = mStops.size() - 1;
            }

            mCurrentStopIndex = mNextStopIndex - 1;*/
        } else {
            pointIndex = result.get(0);
            // Log.d("TripStatusUtils", "Search result index: " + pointIndex.index);
            if (mNextStopIndex != pointIndex.index + 1) {
                mNextStopIndex = pointIndex.index + 1;
                mDistanceToNextStop = 0;
                mLastDistanceSampleTime = 0;
                Log.d("TripStatusUtils", "mNextStopIndex changed: " + mNextStopIndex);
                Log.d("TripStatusUtils", "mCurrentStopIndex: " + mCurrentStopIndex);
            }

            if (mCurrentStopIndex != pointIndex.index) {
                mCurrentStopIndex = pointIndex.index;
                Log.d("TripStatusUtils", "mCurrentStopIndex changed: " + mCurrentStopIndex);
            }
        }

        if (mNextStopIndex >= mStops.size() - 1) {
            mNextStopIndex = mStops.size() - 1;
        }

        if (mCurrentStopIndex >= mStops.size() - 1) {
            mCurrentStopIndex = mStops.size() - 1;
        }
        /*if (mNextStopIndex - 1 >= 0) {
            mCurrentStopIndex = mNextStopIndex - 1;
        } else {
            mCurrentStopIndex = mNextStopIndex;
        }*/

        mResponse.getStatus().setPredicted(true);
        mResponse.getStatus().setNextStop(getNextStop().getName());
        mResponse.getStatus().setClosestStop(mStops.get(pointIndex.index).getName());
        mResponse.getStatus().setLastKnownLocation(ObaTripStatus.Position.getInstance(targetPoint.getY(), targetPoint.getX()));
        mResponse.getStatus().setDistanceAlongTrip(pointIndex.distanceAlongShape);
        mResponse.getStatus().setLastUpdateTime(System.currentTimeMillis() / 1000);
        if (location != null) {
            mResponse.getStatus().setLastLocationUpdateTime(location.getTime());
            if (location.hasBearing()) {
                mResponse.getStatus().setBearing(location.getBearing());
            }

            if (location.hasSpeed()) {
                mResponse.getStatus().setSpeed(location.getSpeed());
            } else {
                mResponse.getStatus().setSpeed(0);
            }
        }

        // get the distance
        float distance = calculateDistance(targetPoint, mPoints.get(mNextStopIndex));
        if (mDistanceToNextStop <= 0) {
            mDistanceToNextStop = distance;
            mLastDistanceSampleTime = System.currentTimeMillis();
        }

        if ((mDistanceToNextStop - distance) > THRESHOLD) {
            float distanceTraveled = mDistanceToNextStop - distance; // meters
            Log.d(TAG, "Distance traveled (m): " + distanceTraveled);
            long timeElapsed = System.currentTimeMillis() - mLastDistanceSampleTime; // milliseconds
            Log.d(TAG, "Time elapsed (ms): " + timeElapsed);
            double speedInSec = (distanceTraveled / timeElapsed); // km/sec
            mSpeed = Math.round(Math.abs(speedInSec * 3600));
            Log.d(TAG, "Calculated speed kmh: " + mSpeed);

            if (mResponse.getStatus().getSpeed() == 0) {
                mResponse.getStatus().setSpeed((float) mSpeed);
            }

            // predicted arrival time
            long serviceDate = getTripStatus().getServiceDate();
            long currentTimeInSec = new java.util.Date().getTime()/1000;
            long predictedDurationOfArrivalInSec = (long)((distance / speedInSec)/1000);
            long scheduledArrivalTimeInSec = serviceDate/1000 + mStopTimes.get(mNextStopIndex).getArrivalTime();
            long predictedArrivalTimeInSec = currentTimeInSec + predictedDurationOfArrivalInSec;
            long deviation = predictedArrivalTimeInSec - scheduledArrivalTimeInSec;
            mResponse.getStatus().setScheduleDeviation(deviation);
            Log.d(TAG, "Schedule time (s): " + scheduledArrivalTimeInSec);
            Log.d(TAG, "Predicted arrival time (s): " + predictedArrivalTimeInSec);
            Log.d(TAG, "Deviation (s): " + deviation);
            mDistanceToNextStop = distance;
            mLastDistanceSampleTime = System.currentTimeMillis();
        } else {
            mSpeed = 0.0;
        }

        if (mNextStopIndex == mStops.size() - 1) {
            // check distance from last stop
            if (mDistanceToNextStop <= mMinDistanceToDetermineTripEnd) {
                mTripEnded = true;
            }
        }

        //Log.d("TripStatusUtils", "Distance from target: " + mDistanceToNextStop);

        if (mDistanceToNextStop <= mMinDistanceToDetermineCloseStop && mCloseNotificationSentForStopIndex != mNextStopIndex) {
            if (mCloseToStopListener != null) {
                mCloseNotificationSentForStopIndex = mNextStopIndex;
                mCloseToStopListener.onCloseToStop(mStops.get(mNextStopIndex), mStopTimes.get(mNextStopIndex), pointIndex);
            }
        }

        //Log.d("TripStatusProcessor", "Next stop index: " + mNextStopIndex);
        //Log.d("TripStatusProcessor", "Next stop name: " + mStops.get(mNextStopIndex).getName());

        // mNextStop = mStops.get(mNextStopIndex);

        /*if (mNextStopIndex - mCurrentStopIndex > 1) {
            mCurrentStopIndex = mNextStopIndex - 1;
            mCurrentStop = mStops.get(mCurrentStopIndex);
        }*/
    }

    public void computeTripStatus(Location location) {
        if (location == null) {
            return;
        }
        computeInBackground(location);
    }

    public void computeTripStatus(ObaStop stop) {
        if (stop == null) {
            return;
        }

        computeInBackground(p(stop.getLongitude(), stop.getLatitude()));
    }

    public void computeInBackground(XYPoint point) {
        if (computeTask != null) {
            if (computeTask.getStatus() == AsyncTask.Status.PENDING || computeTask.getStatus() == AsyncTask.Status.FINISHED) {
                computeTask.cancel(true);
            } else {
                return;
            }
        }

        computeTask = new StatusComputeTask(this, point);
        computeTask.execute();
    }

    public void computeInBackground(Location location) {
        if (computeTask != null) {
            if (computeTask.getStatus() == AsyncTask.Status.PENDING || computeTask.getStatus() == AsyncTask.Status.FINISHED) {
                computeTask.cancel(true);
            } else {
                return;
            }
        }

        computeTask = new StatusComputeTask(this, location);
        computeTask.execute();
    }

    public ObaStop getCurrentStop() {
        if (mTripEnded) {
            return mStops.get(mStops.size() - 1);
        } else {
            return mStops.get(mCurrentStopIndex);
        }
    }

    public ObaStop getNextStop() {
        if (mTripEnded) {
            return mStops.get(mStops.size() - 1);
        } else {
            return mStops.get(mNextStopIndex);
        }
    }

    public ObaTripSchedule.StopTime getNextStopTime() {
        if (mTripEnded) {
            return mStopTimes.get(mStopTimes.size() - 1);
        } else {
            return mStopTimes.get(mNextStopIndex);
        }
    }

    public ObaTripSchedule.StopTime getCurrentStopTime() {
        if (mTripEnded) {
            return mStopTimes.get(mStopTimes.size() - 1);
        } else {
            return mStopTimes.get(mCurrentStopIndex);
        }
    }

    public ObaTripStatus getTripStatus() {
        return mResponse.getStatus();
    }

    public ObaTripDetailsResponse getResponse() {
        return mResponse;
    }

    public boolean hasTripEnded() {
        return mTripEnded;
    }

    /*private double[] getShapePointDistances(List<XYPoint> points) {
        double[] distances = new double[points.size()];
        double accumulatedDistance = 0;
        for (int i = 0; i < points.size(); i++) {
            XYPoint point = points.get(i);
            if (i > 0)
                accumulatedDistance += point.getDistance(points.get(i - 1));
            distances[i] = accumulatedDistance;
        }
        return distances;
    }*/

    /*private ShapePoints createShapePoints(List<XYPoint> points) {
        ShapePoints shapePoints = new ShapePoints();

        double[] lats = new double[points.size()];
        double[] lons = new double[points.size()];

        int i = 0;
        for (XYPoint point : points) {
            lons[i] = point.getX();
            lats[i] = point.getY();
        }

        shapePoints.setLons(lons);
        shapePoints.setLats(lats);

        return shapePoints;
    }*/

    private double[] getShapePointDistances() {
        double[] distances = new double[mStopTimes.size()];
        int i = 0;
        for (ObaTripSchedule.StopTime stopTime : mStopTimes) {
            distances[i] = stopTime.getDistanceAlongTrip();
            i++;
        }

        return distances;
    }


    private XYPoint p(double x, double y) {
        return new XYPoint(x, y);
    }

    private float calculateDistance(XYPoint p1, XYPoint p2) {
        Location loc1 = new Location("dummy");
        loc1.setLongitude(p1.getX());
        loc1.setLatitude(p1.getY());

        Location loc2 = new Location("dummy");
        loc2.setLongitude(p2.getX());
        loc2.setLatitude(p2.getY());

        return loc1.distanceTo(loc2);
    }

    private class StatusComputeTask extends AsyncTask<Object, Void, TripStatusUtils> {
        private TripStatusUtils mTripStatus;
        private XYPoint mPoint;
        private Location mLocation;

        public StatusComputeTask(TripStatusUtils tripStatus, XYPoint point) {
            mTripStatus = tripStatus;
            mPoint = point;
        }

        public StatusComputeTask(TripStatusUtils tripStatus, Location location) {
            mTripStatus = tripStatus;
            mLocation = location;
            mPoint = p(location.getLongitude(), location.getLatitude());
        }

        @Override
        protected TripStatusUtils doInBackground(Object... params) {
            mTripStatus.computeTripStatus(mPoint, mLocation);
            return mTripStatus;
        }

        @Override
        protected void onPostExecute(TripStatusUtils result) {
            if (result.mTripStatusComputedListener != null) {
                result.mTripStatusComputedListener.onTripStatusComputed(result);
            }
        }
    }
}
