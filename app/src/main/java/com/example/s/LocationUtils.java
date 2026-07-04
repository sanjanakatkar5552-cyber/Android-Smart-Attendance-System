package com.example.s;

import android.location.Location;

public class LocationUtils {

    public static boolean isWithinRadius(
            double studentLat,
            double studentLng,
            double classLat,
            double classLng,
            float allowedRadiusMeters
    ) {
        float[] result = new float[1];
        Location.distanceBetween(
                studentLat, studentLng,
                classLat, classLng,
                result
        );
        return result[0] <= allowedRadiusMeters;
    }
}
