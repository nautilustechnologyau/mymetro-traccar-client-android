/*
 * Copyright (C) 2010 Paul Watts (paulcwatts@gmail.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package au.mymetro.operator.oba.io.elements;

import android.location.Location;

import java.io.Serializable;

public final class ObaTripStatusElement implements ObaTripStatus, Serializable {

    protected static final ObaTripStatusElement EMPTY_OBJECT = new ObaTripStatusElement();

    private long serviceDate;

    private boolean predicted;

    private long scheduleDeviation;

    private String vehicleId;

    private String closestStop;

    private long closestStopTimeOffset;

    private Position position;

    private String activeTripId;

    private Double distanceAlongTrip;

    private Double scheduledDistanceAlongTrip;

    private Double totalDistanceAlongTrip;

    private Double orientation;

    private String nextStop;

    private long nextStopTimeOffset;

    private String phase;

    private String status;

    private Long lastUpdateTime;

    private Position lastKnownLocation;

    private Long lastLocationUpdateTime;

    private Double lastKnownOrientation;

    private int blockTripSequence;

    private String occupancyStatus;

    private boolean airConditioned;

    private boolean wheelchairAccessible;

    private float speed;

    private double odometer;

    private float bearing;

    ObaTripStatusElement() {
        serviceDate = 0;
        predicted = false;
        scheduleDeviation = 0;
        vehicleId = "";
        closestStop = "";
        closestStopTimeOffset = 0;
        position = null;
        activeTripId = null;
        distanceAlongTrip = null;
        scheduledDistanceAlongTrip = null;
        totalDistanceAlongTrip = null;
        orientation = null;
        nextStop = null;
        nextStopTimeOffset = 0;
        phase = null;
        status = null;
        lastUpdateTime = null;
        lastKnownLocation = null;
        lastLocationUpdateTime = null;
        lastKnownOrientation = null;
        blockTripSequence = 0;
        occupancyStatus = "";
        airConditioned = false;
        wheelchairAccessible = false;
        speed = 0;
        odometer = 0;
        bearing = 0;
    }

    @Override
    public long getServiceDate() {
        return serviceDate;
    }

    @Override
    public boolean isPredicted() {
        return predicted;
    }

    @Override
    public long getScheduleDeviation() {
        return scheduleDeviation;
    }

    @Override
    public String getVehicleId() {
        return vehicleId;
    }

    @Override
    public String getClosestStop() {
        return closestStop;
    }

    @Override
    public long getClosestStopTimeOffset() {
        return closestStopTimeOffset;
    }

    @Override
    public Location getPosition() {
        return (position != null) ? position.getLocation() : null;
    }

    @Override
    public String getActiveTripId() {
        return activeTripId;
    }

    @Override
    public Double getDistanceAlongTrip() {
        return distanceAlongTrip;
    }

    @Override
    public Double getScheduledDistanceAlongTrip() {
        return scheduledDistanceAlongTrip;
    }

    @Override
    public Double getTotalDistanceAlongTrip() {
        return totalDistanceAlongTrip;
    }

    @Override
    public Double getOrientation() {
        return orientation;
    }

    @Override
    public String getNextStop() {
        return nextStop;
    }

    @Override
    public Long getNextStopTimeOffset() {
        return nextStopTimeOffset;
    }

    @Override
    public String getPhase() {
        return phase;
    }

    /**
     * @return The status modifiers for the trip, defined by {@link Status}. Can be null.
     */
    @Override
    public Status getStatus() {
        return Status.fromString(status);
    }

    @Override
    public long getLastUpdateTime() {
        return lastUpdateTime;
    }

    @Override
    public Location getLastKnownLocation() {
        return (lastKnownLocation != null) ? lastKnownLocation.getLocation() : null;
    }

    @Override
    public long getLastLocationUpdateTime() {
        return lastLocationUpdateTime;
    }

    @Override
    public Double getLastKnownOrientation() {
        return lastKnownOrientation;
    }

    @Override
    public int getBlockTripSequence() {
        return blockTripSequence;
    }

    /**
     * @return the current realtime occupancy of this vehicle, or null if the occupancy is unknown
     */
    @Override
    public Occupancy getOccupancyStatus() {
        return Occupancy.fromString(occupancyStatus);
    }

    @Override
    public boolean getAirConditioned() {
        return airConditioned;
    }

    @Override
    public boolean getWheelchairAccessible() {
        return wheelchairAccessible;
    }

    @Override
    public float getSpeed() {
        return speed;
    }

    @Override
    public double getOdometer() {
        return odometer;
    }

    @Override
    public float getBearing() {
        return bearing;
    }

    @Override
    public void setServiceDate(long serviceDate) {
        this.serviceDate = serviceDate;
    }

    @Override
    public void setPredicted(boolean predicted) {
        this.predicted = predicted;
    }

    @Override
    public void setScheduleDeviation(long scheduleDeviation) {
        this.scheduleDeviation = scheduleDeviation;
    }

    @Override
    public void setVehicleId(String vehicleId) {
        this.vehicleId = vehicleId;
    }

    @Override
    public void setClosestStop(String closestStop) {
        this.closestStop = closestStop;
    }

    @Override
    public void setClosestStopTimeOffset(long closestStopTimeOffset) {
        this.closestStopTimeOffset = closestStopTimeOffset;
    }

    @Override
    public void setPosition(Position position) {
        this.position = position;
    }

    @Override
    public void setActiveTripId(String activeTripId) {
        this.activeTripId = activeTripId;
    }

    @Override
    public void setDistanceAlongTrip(Double distanceAlongTrip) {
        this.distanceAlongTrip = distanceAlongTrip;
    }

    @Override
    public void setScheduledDistanceAlongTrip(Double scheduledDistanceAlongTrip) {
        this.scheduledDistanceAlongTrip = scheduledDistanceAlongTrip;
    }

    @Override
    public void setTotalDistanceAlongTrip(Double totalDistanceAlongTrip) {
        this.totalDistanceAlongTrip = totalDistanceAlongTrip;
    }

    @Override
    public void setOrientation(Double orientation) {
        this.orientation = orientation;
    }

    @Override
    public void setNextStop(String nextStop) {
        this.nextStop = nextStop;
    }

    @Override
    public void setNextStopTimeOffset(long nextStopTimeOffset) {
        this.nextStopTimeOffset = nextStopTimeOffset;
    }

    @Override
    public void setPhase(String phase) {
        this.phase = phase;
    }

    @Override
    public void setStatus(String status) {
        this.status = status;
    }

    @Override
    public void setLastUpdateTime(Long lastUpdateTime) {
        this.lastUpdateTime = lastUpdateTime;
    }

    @Override
    public void setLastKnownLocation(Position lastKnownLocation) {
        this.lastKnownLocation = lastKnownLocation;
    }

    @Override
    public void setLastLocationUpdateTime(Long lastLocationUpdateTime) {
        this.lastLocationUpdateTime = lastLocationUpdateTime;
    }

    @Override
    public void setLastKnownOrientation(Double lastKnownOrientation) {
        this.lastKnownOrientation = lastKnownOrientation;
    }

    @Override
    public void setBlockTripSequence(int blockTripSequence) {
        this.blockTripSequence = blockTripSequence;
    }

    @Override
    public void setOccupancyStatus(String occupancyStatus) {
        this.occupancyStatus = occupancyStatus;
    }

    @Override
    public void setAirConditioned(boolean airConditioned) {
        this.airConditioned = airConditioned;
    }

    @Override
    public void setWheelchairAccessible(boolean wheelchairAccessible) {
        this.wheelchairAccessible = wheelchairAccessible;
    }

    @Override
    public void setSpeed(float speed) {
        this.speed = speed;
    }

    @Override
    public void setOdometer(double odometer) {
        this.odometer = odometer;
    }

    @Override
    public void setBearing(float bearing) {
        this.bearing = bearing;
    }
}
