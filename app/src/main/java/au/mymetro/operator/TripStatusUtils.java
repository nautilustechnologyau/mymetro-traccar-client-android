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
    private static final double THRESHOLD = .001;

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

    private void computeTripStatus(XYPoint targetPoint, Location location) {
        if (mTripEnded) {
            return;
        }

        List<PointAndIndex> result = mShapePointLibrary.computePotentialAssignments(mPoints,
                mShapePointDistances, targetPoint, mCurrentStopIndex, mStops.size());
        PointAndIndex pointIndex = null;
        if (result == null || result.isEmpty()) {
            return;
        } else {
            pointIndex = result.get(0);
            if (mNextStopIndex != pointIndex.index + 1) {
                mNextStopIndex = pointIndex.index + 1;
                mDistanceToNextStop = 0;
                mLastDistanceSampleTime = 0;
                mLastIdleTimestamp = 0;
            }

            if (mCurrentStopIndex != pointIndex.index) {
                mCurrentStopIndex = pointIndex.index;
            }
        }

        if (mNextStopIndex >= mStops.size() - 1) {
            mNextStopIndex = mStops.size() - 1;
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
        long now = new java.util.Date().getTime();
        float distance = calculateDistance(targetPoint, mPoints.get(mNextStopIndex));
        if (mDistanceToNextStop <= 0) {
            mDistanceToNextStop = distance;
            mLastDistanceSampleTime = now;
        }

        double distanceDelta = mDistanceToNextStop - distance;
        long timeDelta = now - mLastDistanceSampleTime;

        if (distanceDelta >= THRESHOLD && timeDelta >= 1000) {
            long timeElapsed = now - mLastDistanceSampleTime; // milliseconds
            double speedInSec = (distanceDelta / timeElapsed); // km/sec
            mSpeed = Math.round(Math.abs(speedInSec * 3600));

            if (mResponse.getStatus().getSpeed() == 0) {
                mResponse.getStatus().setSpeed((float) mSpeed);
            }

            mTripDistanceTravelled = calculateTripDistanceTravelled(distance);

            // predicted arrival time
            long serviceDate = getTripStatus().getServiceDate();
            long currentTimeInSec = now/1000;
            long predictedDurationOfArrivalInSec = (long)((distance / speedInSec)/1000);
            long scheduledArrivalTimeInSec = serviceDate/1000 + mStopTimes.get(mNextStopIndex).getArrivalTime();
            long predictedArrivalTimeInSec = currentTimeInSec + predictedDurationOfArrivalInSec;
            long deviation = predictedArrivalTimeInSec - scheduledArrivalTimeInSec;
            mResponse.getStatus().setScheduleDeviation(deviation);
            mDistanceToNextStop = distance;
            mLastDistanceSampleTime = now;
        } else {
            // mSpeed = 0.0;
            if (mLastIdleTimestamp == 0) {
                mLastIdleTimestamp = now;
            }

            timeDelta = now - mLastIdleTimestamp;

            if (timeDelta >= 1000){
                mSpeed = 0.0;
                //long timeElapsed = now - mLastIdleTimestamp;
                //if (timeElapsed >= 1000) {
                    long lastDeviation = mResponse.getStatus().getScheduleDeviation();
                    Log.d(TAG, "Previous Deviation: " + lastDeviation);
                    long deviation = lastDeviation + timeDelta / 1000;
                    Log.d(TAG, "Last Time: " + timeDelta / 1000);
                    mResponse.getStatus().setScheduleDeviation(deviation);
                    mLastIdleTimestamp = now;
                    Log.d(TAG, "New Deviation: " + deviation);
                //}
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

        computeInBackground(p(stop.getLongitude(), stop.getLatitude()));
    }

    public void computeInBackground(XYPoint point) {
        if (mComputeTask != null) {
            if (mComputeTask.getStatus() == AsyncTask.Status.PENDING || mComputeTask.getStatus() == AsyncTask.Status.FINISHED) {
                mComputeTask.cancel(true);
            } else {
                return;
            }
        }

        mComputeTask = new StatusComputeTask(this, point);
        mComputeTask.execute();
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
        double distance = 0;
        //for (int i = 0; i < mNextStopIndex; i++) {
        //    distance += mStopTimes.get(i).getDistanceAlongTrip();
        //}

        distance = mStopTimes.get(mNextStopIndex - 1).getDistanceAlongTrip() + distanceTravelledToNextStop;
        return distance;
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
        private final XYPoint mPoint;
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
