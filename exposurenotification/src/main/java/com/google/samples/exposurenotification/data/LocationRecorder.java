package com.google.samples.exposurenotification.data;

import com.google.protobuf.DescriptorProtos.SourceCodeInfo.Location;
import com.google.samples.exposurenotification.ble.utils.Constants;
import com.uber.h3core.H3Core;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class LocationRecorder {

    // save gps coordinates as global variables
    public static double currentLongitude = 0.0;
    public static double currentLatitude = 0.0;
    public static int resolution = 11;
    public static Random randomer;

    private static H3Core h3Core;

    public static List<LocationGPS> locations = new ArrayList<LocationGPS>() {
        {

            // CHRIS TODO: Add 20 more locations. Far away from each other and existing.

            // North America: East -> West
            add(new LocationGPS(40.6790790822764, -73.94328276647438, "Brooklyn, NY"));
            add(new LocationGPS(38.8453582628378, -77.08906445267223, "Washington DC"));
            add(new LocationGPS(25.94437783681225, -80.48723249955351, "Miami FL"));
            add(new LocationGPS(33.82312494080125, -84.28630138886852, "Atlanta GA"));
            add(new LocationGPS(40.52363510681139, -80.02230778309253, "Pittsburgh PA"));
            add(new LocationGPS(42.305406112474124, -71.13627562305504, "Boston MA"));
            add(new LocationGPS(39.958662952364286, -83.02591278157979, "Columbus, OH"));
            add(new LocationGPS(41.546616577415826, -81.48703844388235, "Cleveland OH"));
            add(new LocationGPS(43.71196497806132, -79.48986764120345, "Toronto Canada"));
            add(new LocationGPS(39.772273100303195, -86.11821200937975, "Indianapolis IN"));
            add(new LocationGPS(42.11298718504672, -87.42880806055483, "Chicago IL"));
            add(new LocationGPS(42.46241438457984, -83.07444684508627, "Detroit MI"));
            add(new LocationGPS(32.93307252589097, -96.62036349144697, "Dallas TX"));
            add(new LocationGPS(39.98037561764584, -104.85634255252943, "Denver CO"));
            add(new LocationGPS(37.975152463700645, -122.20744026431807, "San Francisco CA"));
            add(new LocationGPS(34.05657722877406, -118.26756855561736, "Los Angeles CA"));
            add(new LocationGPS(47.681611205824936, -122.23079475292553, "Seattle WA"));
            add(new LocationGPS(19.54966411069956, -155.6462127033766, "Hawaii"));
            add(new LocationGPS(39.100105, -94.5781416, "Kansas City MO"));
            add(new LocationGPS(40.7596198, -111.8867975, "Salt Lake City"));
            add(new LocationGPS(43.6166163, -116.200886, "Boise ID"));
            add(new LocationGPS(64.4459613, -149.680909, "Alaska"));
            add(new LocationGPS(18.2214149, -66.4132818, "Puerto Rico"));
            add(new LocationGPS(36.1672559, -115.148516, "Las Vegas NV"));
            add(new LocationGPS(34.5708167, -105.993007, "New Mexico"));
            add(new LocationGPS(45.6794293, -111.044047, "Montana"));

            // International
            add(new LocationGPS(49.27209300141755, 2.5514736091573713, "Paris France"));
            add(new LocationGPS(51.924673444258765, 0.05284238225123087, "London UK"));
            add(new LocationGPS(40.915428867835026, -3.627532029100828, "Madrid Spain"));
            add(new LocationGPS(52.8596091542215, 13.802074938472245, "Berlin Germany"));
            add(new LocationGPS(39.99317345140204, 116.23804154478624, "Beijing China"));
            add(new LocationGPS(31.35112453165643, 121.46776378993354, "Shanghai China"));
            add(new LocationGPS(23.400591038749184, 112.66873347299111, "Guangzhou China"));
            add(new LocationGPS(28.396169511941988, 77.12941489222258, "New Delhi India"));
            add(new LocationGPS(37.95881151692409, 127.15258704974916, "Seoul Korea"));
            add(new LocationGPS(36.070472217740374, 139.831295195883, "Tokyo Japan"));
            add(new LocationGPS(-34.07800590053609, 151.14206034122398, "Sydney AU"));
            add(new LocationGPS(-23.29520954337703, -43.768480190993095, "Rio Brazil"));
            add(new LocationGPS(-24.7761086, 134.755, "Australia"));
            add(new LocationGPS(49.4871968, 31.2718321, "Ukraine"));
            add(new LocationGPS(37.9839412, 23.7283052, "Athens Greece"));
            add(new LocationGPS(38.9743901, 1.4197463, "Ibiza"));
            add(new LocationGPS(50.6402809, 4.6667145, "Belgium"));
            add(new LocationGPS(64.9841821, -18.1059013, "Iceland"));
            add(new LocationGPS(55.670249, 10.3333283, "Denmark"));
            add(new LocationGPS(42.6384261, 12.674297, "Italy"));
            add(new LocationGPS(26.2540493, 29.2675469, "Egypt"));
            add(new LocationGPS(-31.7613365, -71.3187697, "Chile"));
            add(new LocationGPS(23.6585116, -102.0077097, "Mexico"));
            add(new LocationGPS(-28.8166236, 24.991639, "South Africa"));

        }
    };

    // locations in Brooklyn, NY, used for resolution sensitivity test
    public static List<LocationGPS> locations_NY = new ArrayList<LocationGPS>() {
        {
            add(new LocationGPS(40.6926552, -73.9429616, "Brooklyn NY - Point 1"));
            add(new LocationGPS(40.7220642, -73.9450216, "Brooklyn NY - Point 2"));
            add(new LocationGPS(40.7171202, -73.9560079, "Brooklyn NY - Point 3"));
            add(new LocationGPS(40.6989025, -73.9776372, "Brooklyn NY - Point 4"));
            add(new LocationGPS(40.6882297, -73.9714574, "Brooklyn NY - Point 5"));
            add(new LocationGPS(40.6809401, -73.9237356, "Brooklyn NY - Point 6"));
            add(new LocationGPS(40.6822419, -73.9738607, "Brooklyn NY - Point 7"));
            add(new LocationGPS(40.6692229, -73.9460515, "Brooklyn NY - Point 8"));
            add(new LocationGPS(40.6535968, -73.9728307, "Brooklyn NY - Point 9"));
            add(new LocationGPS(40.6364038, -73.9463949, "Brooklyn NY - Point 10"));
            add(new LocationGPS(40.6418748, -74.0023565, "Brooklyn NY - Point 11"));
            add(new LocationGPS(40.6559409, -73.9374685, "Brooklyn NY - Point 12"));
            add(new LocationGPS(40.6765138, -73.9779806, "Brooklyn NY - Point 13"));
            add(new LocationGPS(40.684585, -73.9896535, "Brooklyn NY - Point 14"));
            add(new LocationGPS(40.6452613, -73.9865636, "Brooklyn NY - Point 15"));
            add(new LocationGPS(40.6351011, -73.9920568, "Brooklyn NY - Point 16"));
            add(new LocationGPS(40.6681813, -73.9652776, "Brooklyn NY - Point 17"));
            add(new LocationGPS(40.683804, -73.9560079, "Brooklyn NY - Point 18"));
            add(new LocationGPS(40.6270239, -74.0095663, "Brooklyn NY - Point 19"));
            add(new LocationGPS(40.6267634, -73.9769506, "Brooklyn NY - Point 20"));
            add(new LocationGPS(40.6351011, -73.9340352, "Brooklyn NY - Point 21"));
            add(new LocationGPS(40.6202488, -73.952918, "Brooklyn NY - Point 22"));
            add(new LocationGPS(40.6309324, -73.9937734, "Brooklyn NY - Point 23"));
            add(new LocationGPS(40.6197276, -74.0109395, "Brooklyn NY - Point 24"));
            add(new LocationGPS(40.6387485, -74.0311956, "Brooklyn NY - Point 25"));
            add(new LocationGPS(40.6283268, -74.0287923, "Brooklyn NY - Point 26"));
            add(new LocationGPS(40.6189458, -73.99755, "Brooklyn NY - Point 27"));
            add(new LocationGPS(40.6218123, -73.9845037, "Brooklyn NY - Point 28"));
            add(new LocationGPS(40.6134729, -73.9693975, "Brooklyn NY - Point 29"));
            add(new LocationGPS(40.6851057, -73.9693975, "Brooklyn NY - Point 30"));
            add(new LocationGPS(40.6942171, -73.9621877, "Brooklyn NY - Point 31"));
            add(new LocationGPS(40.6903124, -73.9772939, "Brooklyn NY - Point 32"));
            add(new LocationGPS(40.6825022, -73.9707708, "Brooklyn NY - Point 33"));
            add(new LocationGPS(40.680159, -73.983817, "Brooklyn NY - Point 34"));
            add(new LocationGPS(40.7225845, -73.9515447, "Brooklyn NY - Point 35"));
            add(new LocationGPS(40.6283268, -73.942275, "Brooklyn NY - Point 36"));
            add(new LocationGPS(40.6567223, -73.9223623, "Brooklyn NY - Point 37"));
            add(new LocationGPS(40.6744308, -73.9134359, "Brooklyn NY - Point 38"));
            add(new LocationGPS(40.6825022, -73.9398717, "Brooklyn NY - Point 39"));
            add(new LocationGPS(40.67391, -73.9302587, "Brooklyn NY - Point 40"));
            add(new LocationGPS(40.6611498, -73.9481115, "Brooklyn NY - Point 41"));
            add(new LocationGPS(40.6548991, -73.952918, "Brooklyn NY - Point 42"));
            add(new LocationGPS(40.6436983, -73.9611577, "Brooklyn NY - Point 43"));
            add(new LocationGPS(40.6400512, -73.9673376, "Brooklyn NY - Point 44"));
            add(new LocationGPS(40.6410932, -73.9817571, "Brooklyn NY - Point 45"));
            add(new LocationGPS(40.6509921, -73.9381551, "Brooklyn NY - Point 46"));
            add(new LocationGPS(40.6702645, -73.9343786, "Brooklyn NY - Point 47"));
            add(new LocationGPS(40.6970804, -73.9288854, "Brooklyn NY - Point 48"));
            add(new LocationGPS(40.7098337, -73.9367818, "Brooklyn NY - Point 49"));
            add(new LocationGPS(40.71686, -73.9402151, "Brooklyn NY - Point 50"));
            add(new LocationGPS(40.7051491, -73.9457082, "Brooklyn NY - Point 51"));
            add(new LocationGPS(40.7038478, -73.951888, "Brooklyn NY - Point 52"));
            add(new LocationGPS(40.709053, -73.9244222, "Brooklyn NY - Point 53"));
            add(new LocationGPS(40.6705249, -73.9831304, "Brooklyn NY - Point 54"));
            add(new LocationGPS(40.680159, -73.9958333, "Brooklyn NY - Point 55"));
            add(new LocationGPS(40.6970804, -73.9522314, "Brooklyn NY - Point 56"));
            add(new LocationGPS(40.6746912, -73.9570379, "Brooklyn NY - Point 57"));
            add(new LocationGPS(40.6351011, -73.964591, "Brooklyn NY - Point 58"));
            add(new LocationGPS(40.6450008, -73.9360952, "Brooklyn NY - Point 59"));
            add(new LocationGPS(40.6517735, -73.9288854, "Brooklyn NY - Point 60"));
            add(new LocationGPS(40.614776, -73.9508581, "Brooklyn NY - Point 61"));
            add(new LocationGPS(40.7074914, -73.9378118, "Brooklyn NY - Point 62"));
            add(new LocationGPS(40.6189458, -73.99755, "Brooklyn NY - Point 63"));
            add(new LocationGPS(40.6163397, -73.9989232, "Brooklyn NY - Point 64"));
            add(new LocationGPS(40.6043505, -73.9879369, "Brooklyn NY - Point 65"));
            add(new LocationGPS(40.6033079, -73.9755773, "Brooklyn NY - Point 66"));
            add(new LocationGPS(40.6038292, -73.962531, "Brooklyn NY - Point 67"));
            add(new LocationGPS(40.6079996, -73.9481115, "Brooklyn NY - Point 68"));
            add(new LocationGPS(40.6199882, -73.9336919, "Brooklyn NY - Point 69"));
            add(new LocationGPS(40.6158185, -73.9391851, "Brooklyn NY - Point 70"));
            add(new LocationGPS(40.6103454, -73.9350652, "Brooklyn NY - Point 71"));
            add(new LocationGPS(40.5952269, -73.9786672, "Brooklyn NY - Point 72"));
            add(new LocationGPS(40.6137336, -74.0095663, "Brooklyn NY - Point 73"));
            add(new LocationGPS(40.6124304, -74.0246725, "Brooklyn NY - Point 74"));
            add(new LocationGPS(40.6421353, -74.0129995, "Brooklyn NY - Point 75"));
            add(new LocationGPS(40.5965303, -73.962531, "Brooklyn NY - Point 76"));
            add(new LocationGPS(40.5905342, -73.9556646, "Brooklyn NY - Point 77"));
            add(new LocationGPS(40.5986158, -73.9896535, "Brooklyn NY - Point 78"));
            add(new LocationGPS(40.6111273, -74.0088796, "Brooklyn NY - Point 79"));
            add(new LocationGPS(40.6298901, -73.9666509, "Brooklyn NY - Point 80"));
            add(new LocationGPS(40.5988765, -73.943305, "Brooklyn NY - Point 81"));
            add(new LocationGPS(40.6632333, -73.9687108, "Brooklyn NY - Point 82"));
            add(new LocationGPS(40.6629729, -73.9766073, "Brooklyn NY - Point 83"));
            add(new LocationGPS(40.6603685, -73.9676809, "Brooklyn NY - Point 84"));
            add(new LocationGPS(40.6608894, -73.9790105, "Brooklyn NY - Point 85"));
            add(new LocationGPS(40.6653167, -73.9594411, "Brooklyn NY - Point 86"));
            add(new LocationGPS(40.6559409, -73.964591, "Brooklyn NY - Point 87"));
            add(new LocationGPS(40.6710457, -73.973174, "Brooklyn NY - Point 88"));
            add(new LocationGPS(40.6533363, -73.9680242, "Brooklyn NY - Point 89"));
            add(new LocationGPS(40.6489082, -73.9536046, "Brooklyn NY - Point 90"));
            add(new LocationGPS(40.6225941, -73.974204, "Brooklyn NY - Point 91"));
            add(new LocationGPS(40.5894914, -73.9683675, "Brooklyn NY - Point 92"));
            add(new LocationGPS(40.5905342, -73.9395284, "Brooklyn NY - Point 93"));
            add(new LocationGPS(40.6319746, -73.9247655, "Brooklyn NY - Point 94"));
            add(new LocationGPS(40.6653167, -73.9312887, "Brooklyn NY - Point 95"));
            add(new LocationGPS(40.6715665, -73.9216756, "Brooklyn NY - Point 96"));
            add(new LocationGPS(40.6830229, -73.9491415, "Brooklyn NY - Point 97"));
            add(new LocationGPS(40.6934362, -73.9522314, "Brooklyn NY - Point 98"));
            add(new LocationGPS(40.7179008, -73.9522314, "Brooklyn NY - Point 99"));
            add(new LocationGPS(40.7152986, -73.9477682, "Brooklyn NY - Point 100"));
        }
    };

    // use 5 fixed data points for user's trace
    public static List<LocationGPS> locations_user_trace = new ArrayList<LocationGPS>(){
        {
            add(new LocationGPS(40.6603685, -73.9676809, "Brooklyn NY - Point 84"));
            add(new LocationGPS(40.6608894, -73.9790105, "Brooklyn NY - Point 85"));
            add(new LocationGPS(40.6653167, -73.9594411, "Brooklyn NY - Point 86"));
            add(new LocationGPS(40.6559409, -73.964591, "Brooklyn NY - Point 87"));
            add(new LocationGPS(40.6533363, -73.9680242, "Brooklyn NY - Point 89"));
        }
    };

    public static LocationGPS getNextRandomLocation() {
        if (randomer == null)
            randomer = new Random(Constants.seed); // init randomer with fixed seed

        return locations.get(randomer.nextInt(locations.size()));
    }

    public static LocationGPS getNextRandomLocation_NY() {
        if (randomer == null)
            randomer = new Random(Constants.seed); // init randomer with fixed seed

        return locations_NY.get(randomer.nextInt(locations_NY.size()));
    }

    public static List<Long> getH3Neighbors(long h3, int k) {
        if (h3Core == null) {
            h3Core = H3Core.newSystemInstance();
        }
        return h3Core.kRing(h3, k);
    }

    public static long getH3Parent(long h3, int res) {
        if (h3Core == null) {
            h3Core = H3Core.newSystemInstance();
        }
        return h3Core.h3ToParent(h3, res);
    }

    public static long getH3() {
        if (h3Core == null) {
            h3Core = H3Core.newSystemInstance();
        }

        return h3Core.geoToH3(currentLatitude, currentLongitude, resolution);
    }

    public static String getH3(double longitude, double latitude, int resolution) {
        if (h3Core == null) {
            h3Core = H3Core.newSystemInstance();
        }

        return h3Core.geoToH3Address(latitude, longitude, resolution);
    }

    public static long getH3Long(double longitude, double latitude, int resolution) {
        if (h3Core == null) {
            h3Core = H3Core.newSystemInstance();
        }

        return h3Core.geoToH3(latitude, longitude, resolution);
    }


    public static class LocationGPS {
        public double longitude;
        public double latitude;
        public String name;

        public LocationGPS(double lat, double lon, String n) {
            longitude = lon;
            latitude = lat;
            name = n;
        }

        @Override
        public String toString() {
//            return "Latitude: " + latitude + " Longitude: " + longitude;
            return name;
        }

        @Override
        public boolean equals(Object loc) {
            if (loc instanceof LocationGPS)
                return this.toString().equals(loc.toString());

            return false;
        }
    }

}
