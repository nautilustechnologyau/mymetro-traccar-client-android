package au.mymetro.operator.oba.util;

import android.content.ContentValues;

import au.mymetro.operator.app.Application;
import au.mymetro.operator.oba.io.elements.ObaStop;
import au.mymetro.operator.oba.provider.ObaContract;

/**
 * Created by azizmb9494 on 2/20/16.
 */
public class DBUtil {
    public static void addToDB(ObaStop stop) {
        String name = UIUtils.formatDisplayText(stop.getName());

        // Update the database
        ContentValues values = new ContentValues();
        values.put(ObaContract.Stops.CODE, stop.getStopCode());
        values.put(ObaContract.Stops.NAME, name);
        values.put(ObaContract.Stops.DIRECTION, stop.getDirection());
        values.put(ObaContract.Stops.LATITUDE, stop.getLatitude());
        values.put(ObaContract.Stops.LONGITUDE, stop.getLongitude());
        if (Application.get().getCurrentRegion() != null) {
            values.put(ObaContract.Stops.REGION_ID, Application.get().getCurrentRegion().getId());
        }
        ObaContract.Stops.insertOrUpdate(stop.getId(), values, true);
    }
}
