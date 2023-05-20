/*
 * Copyright 2023 Nautilus Technology
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package au.mymetro.operator;

import android.location.Location;
import android.os.AsyncTask;
import android.util.Log;

import org.onebusaway.geospatial.model.XYPoint;
import org.onebusaway.transit_data_federation.impl.shapes.PointAndIndex;
import org.onebusaway.transit_data_federation.impl.shapes.ShapePointsLibrary;

import java.util.ArrayList;
import java.util.List;

import au.mymetro.operator.oba.io.elements.ObaStop;
import au.mymetro.operator.oba.io.elements.ObaTripSchedule;
import au.mymetro.operator.oba.io.elements.ObaTripStatus;
import au.mymetro.operator.oba.io.request.ObaTripDetailsResponse;

public class TripStatusUtils {
    public static String TAG = "TripStatusUtils";

    private static final double MIN_DISTANCE_TO_DETERMINE_TRIP_END = 10;
    private static final double MIN_DISTANCE_TO_DETERMINE_CLOSE_STOP = 20;
    private static final double DISTANCE_THRESHOLD = 0.0001;
    private static final double SPEED_THRESHOLD = 0.278;

    private int mCurrentStopIndex = -1;
    private int mNextStopIndex = -1;
    private final List<XYPoint> mPoints;
    private double[] mShapePointDistances;
    private final ObaTripDetailsResponse mResponse;
    private ObaStop mCurrentStop;
    private ObaStop mNextStop;
    private final List<ObaStop> mStops;
    private final List<ObaTripSchedule.StopTime> mStopTimes;
    private final ShapePointsLibrary mShapePointLibrary;
    private TripStatusComputedListener mTripStatusComputedListener;
    private CloseToStopListener mCloseToStopListener;
    private boolean mTripEnded = false;
    private long mLastDistanceSampleTime = 0;
    private long mLastIdleTimestamp = 0;
    private double mSpeed = 0;
    private float mDistanceToNextStop = 0;
    private int mCloseNotificationSentForStopIndex = -1;
    private double mTotalTripLength = 0;
    private double mTripDistanceTravelled = 0;
    private boolean mStatusChanged = false;
    private Location mLastKnownLocation;

    private AsyncTask<?, ?, ?> mComputeTask;

    public interface TripStatusComputedListener {
        void onTripStatusComputed(TripStatusUtils tripStatus);
    }

    public interface CloseToStopListener {
        void onCloseToStop(ObaStop stop, ObaTripSchedule.StopTime stopTime, PointAndIndex pointAndIndex);
    }

    public TripStatusUtils(ObaStop currentStop, ObaTripDetailsResponse response, TripStatusComputedListener tripStatusComputedListener, CloseToStopListener closeToStopListener) {
        mShapePointLibrary = new ShapePointsLibrary();
        mShapePointLibrary.setLocalMinimumThreshold(500);
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

        mShapePointDistances = getShapePointDistances(); //  getShapePointDistances(mPoints);
        if (mShapePointDistances.length < 2) {
            mTripEnded = true;
            return;
        }

        if (mCurrentStopIndex == -1) {
            mTripEnded = true;
            return;
        }

        mNextStopIndex = mCurrentStopIndex;

        computeTripStatus(mCurrentStop);
    }

    public void destroy() {
        if (mComputeTask != null) {
            mComputeTask.cancel(true);
            mComputeTask = null;
        }
        mCloseToStopListener = null;
        mTripStatusComputedListener = null;
    }

    private void updateTripStatus(Location location) {
        if (mTripEnded) {
            return;
        }

        if (location == null) {
            return;
        }

        XYPoint targetPoint = p(location.getLongitude(), location.getLatitude());;

        List<PointAndIndex> result = mShapePointLibrary.computePotentialAssignments(mPoints,
                mShapePointDistances, targetPoint, mCurrentStopIndex, mStops.size());
        PointAndIndex pointIndex = null;
        if (result == null || result.isEmpty()) {
            return;
        } else {
            pointIndex = result.get(0);
            if (mNextStopIndex != pointIndex.index + 1) {
                mNextStopIndex = pointIndex.index + 1;
                mDistanceToNextStop = (float) getNextStopDistance(pointIndex.index);
                mLastDistanceSampleTime = 0;
                mLastIdleTimestamp = 0;
            }

            if (mCurrentStopIndex != pointIndex.index) {
                mCurrentStopIndex = pointIndex.index;
            }
        }

        if (mNextStopIndex >= mStops.size() - 1) {
            mNextStopIndex = mStops.size() - 1;
            mDistanceToNextStop = 0;
        }

        if (mCurrentStopIndex >= mStops.size() - 1) {
            mCurrentStopIndex = mStops.size() - 1;
        }

        mResponse.getStatus().setPredicted(true);
        mResponse.getStatus().setNextStop(getNextStop().getName());
        mResponse.getStatus().setClosestStop(mStops.get(pointIndex.index).getName());
        mResponse.getStatus().setLastKnownLocation(ObaTripStatus.Position.getInstance(targetPoint.getY(), targetPoint.getX()));
        mResponse.getStatus().setDistanceAlongTrip(pointIndex.distanceAlongShape);
        mResponse.getStatus().setLastUpdateTime(System.currentTimeMillis() / 1000);
        mResponse.getStatus().setLastLocationUpdateTime(location.getTime());
        if (location.hasBearing()) {
            mResponse.getStatus().setBearing(location.getBearing());
        }

        // get the distance
        long now = new java.util.Date().getTime();
        long currentTimeInSec = now/1000;
        float distance = calculateDistance(targetPoint, mPoints.get(mNextStopIndex));
        long serviceDate = getTripStatus().getServiceDate();

        // distance delta
        double distanceDelta = Math.abs(mDistanceToNextStop - distance); // meter
        long timeDelta = now - mLastDistanceSampleTime <= 0 ? now : mLastDistanceSampleTime; // ms
        mTripDistanceTravelled = calculateTripDistanceTravelled(distance);
        mLastDistanceSampleTime = now;

        // calculate speed
        double speedInSec = 0; // m/sec
        if (location.hasSpeed()) {
            mSpeed = (location.getSpeed() / 1000) * 3600.00;
            mResponse.getStatus().setSpeed(location.getSpeed());
            speedInSec = location.getSpeed();
        } else {
            if (distanceDelta > 0 && timeDelta > 0) {
                speedInSec = (distanceDelta / timeDelta) / 1000; // m/sec
                mSpeed = Math.round(Math.abs(speedInSec / 1000 * 3600.0));
            } else {
                mSpeed = 0;
                speedInSec = 0;
            }
            mResponse.getStatus().setSpeed((float) mSpeed);
        }

        /*if (mDistanceToNextStop <= 0) {
            long predictedDurationOfArrivalInSec = (long)((distance / 0.0167)/1000);
            long scheduledArrivalTimeInSec = serviceDate/1000 + mStopTimes.get(mNextStopIndex).getArrivalTime();
            long predictedArrivalTimeInSec = currentTimeInSec + predictedDurationOfArrivalInSec;
            long deviation = predictedArrivalTimeInSec - scheduledArrivalTimeInSec;
            mResponse.getStatus().setScheduleDeviation(deviation);
        }*/

        // arrival time prediction
        mDistanceToNextStop = distance;
        if (distanceDelta >= DISTANCE_THRESHOLD && speedInSec >= SPEED_THRESHOLD) {
            // predicted arrival time
            Log.d(TAG, "distance + " + distance);
            Log.d(TAG, "speedInSec + " + speedInSec);
            long predictedDurationOfArrivalInSec = (long)(distance / speedInSec);
            Log.d(TAG, "predictedDurationOfArrivalInSec + " + predictedDurationOfArrivalInSec);
            long scheduledArrivalTimeInSec = serviceDate/1000 + mStopTimes.get(mNextStopIndex).getArrivalTime();
            Log.d(TAG, "scheduledArrivalTimeInSec + " + scheduledArrivalTimeInSec);
            long predictedArrivalTimeInSec = currentTimeInSec + Math.abs(predictedDurationOfArrivalInSec);
            Log.d(TAG, "predictedArrivalTimeInSec + " + predictedArrivalTimeInSec);
            long deviation = predictedArrivalTimeInSec - scheduledArrivalTimeInSec; // negative means earlier
            Log.d(TAG, "deviation + " + deviation);

            mResponse.getStatus().setScheduleDeviation(deviation);

            mLastDistanceSampleTime = now;

            mLastIdleTimestamp = 0;
        } else {
            // this block is to handle idle time waiting on a s top or traffic
            // mSpeed = 0.0;
            if (mLastIdleTimestamp == 0) {
                mLastIdleTimestamp = now;
            }
            if (distanceDelta >= DISTANCE_THRESHOLD) {
                long predictedDurationOfArrivalInSec = (long)((distance / 0.0167)/1000);
                long scheduledArrivalTimeInSec = serviceDate/1000 + mStopTimes.get(mNextStopIndex).getArrivalTime();
                long predictedArrivalTimeInSec = currentTimeInSec + predictedDurationOfArrivalInSec;
                long deviation = predictedArrivalTimeInSec - scheduledArrivalTimeInSec;
                mResponse.getStatus().setScheduleDeviation(deviation);
            } else {
                timeDelta = now - mLastIdleTimestamp;

                if (timeDelta > 0) {
                    long lastDeviation = mResponse.getStatus().getScheduleDeviation();
                    //Log.d(TAG, "Previous Deviation: " + lastDeviation);
                    long deviation = (long) Math.round(lastDeviation + timeDelta / 1000.00);
                    //Log.d(TAG, "Time delta: " + timeDelta / 1000);
                    mResponse.getStatus().setScheduleDeviation(deviation);
                    mLastIdleTimestamp = now;
                    Log.d(TAG, "New Deviation: " + deviation);
                }
            }
        }

        if (mNextStopIndex == mStops.size() - 1) {
            // check distance from last stop
            if (mDistanceToNextStop <= MIN_DISTANCE_TO_DETERMINE_TRIP_END) {
                mTripEnded = true;
            }
        }

        if (mDistanceToNextStop <= MIN_DISTANCE_TO_DETERMINE_CLOSE_STOP && mCloseNotificationSentForStopIndex != mNextStopIndex) {
            if (mCloseToStopListener != null) {
                mCloseNotificationSentForStopIndex = mNextStopIndex;
                mCloseToStopListener.onCloseToStop(mStops.get(mNextStopIndex), mStopTimes.get(mNextStopIndex), pointIndex);
            }
        }
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

        Location location = new Location("LatLong");
        location.setLongitude(stop.getLongitude());
        location.setLatitude(stop.getLatitude());
        computeInBackground(location);
    }

    public void computeInBackground(Location location) {
        if (mComputeTask != null) {
            if (mComputeTask.getStatus() == AsyncTask.Status.PENDING || mComputeTask.getStatus() == AsyncTask.Status.FINISHED) {
                mComputeTask.cancel(true);
            } else {
                return;
            }
        }

        mComputeTask = new StatusComputeTask(this, location);
        mComputeTask.execute();
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

    public int getDistanceToNextStop() {
        return Math.round(mDistanceToNextStop);
    }

    public double getSpeed() {
        return mSpeed;
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

    public double getTotalTripLength() {
        return mTotalTripLength;
    }

    public double getDistanceTravelled() {
        return mTripDistanceTravelled;
    }

    private double[] getShapePointDistances() {
        double[] distances = new double[mStopTimes.size()];
        int i = 0;
        for (ObaTripSchedule.StopTime stopTime : mStopTimes) {
            distances[i] = stopTime.getDistanceAlongTrip();
            //mTotalTripLength += stopTime.getDistanceAlongTrip();
            i++;
        }

        mTotalTripLength = mStopTimes.get(mStopTimes.size() - 1).getDistanceAlongTrip();

        return distances;
    }

    private double calculateTripDistanceTravelled(double distanceTravelledToNextStop) {
        return mStopTimes.get(mNextStopIndex - 1).getDistanceAlongTrip() + distanceTravelledToNextStop;
    }

    private double getNextStopDistance(int mCurrentStopIndex) {
        if (mCurrentStopIndex >= mStopTimes.size() - 2) {
            return 0;
        }

        ObaTripSchedule.StopTime currentStop = mStopTimes.get(mCurrentStopIndex);
        ObaTripSchedule.StopTime nextStop = mStopTimes.get(mCurrentStopIndex + 1);
        return nextStop.getDistanceAlongTrip() - currentStop.getDistanceAlongTrip();
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
        private final TripStatusUtils mTripStatus;
        private Location mLocation;


        public StatusComputeTask(TripStatusUtils tripStatus, Location location) {
            mTripStatus = tripStatus;
            mLocation = location;
        }

        @Override
        protected TripStatusUtils doInBackground(Object... params) {
            //mTripStatus.computeTripStatus(mLocation);
            mTripStatus.updateTripStatus(mLocation);
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
