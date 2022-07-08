package com.google.samples.ensnippets;

import android.Manifest.permission;
import android.content.Context;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.samples.exposurenotification.ble.advertising.BleAdvertisementGenerator;
import com.google.samples.exposurenotification.ble.advertising.BleAdvertiser;
import com.google.samples.exposurenotification.ble.advertising.RollingProximityIdManager;
import com.google.samples.exposurenotification.ble.utils.Constants;
import com.google.samples.exposurenotification.crypto.CryptoException;
import com.google.samples.exposurenotification.data.BatteryUtil;
import com.google.samples.exposurenotification.data.DataUtil;
import com.google.samples.exposurenotification.data.DayNumber;
import com.google.samples.exposurenotification.data.FileUtil;
import com.google.samples.exposurenotification.data.LocationRecorder;
import com.google.samples.exposurenotification.data.LocationRecorder.LocationGPS;
import com.google.samples.exposurenotification.data.RollingProximityId;
import com.google.samples.exposurenotification.data.TemporaryExposureKeySupport;
import com.google.samples.exposurenotification.data.generator.DateTimeGenerator;
import com.google.samples.exposurenotification.nearby.AdvertisementPacket;
import com.google.samples.exposurenotification.nearby.TemporaryExposureKey;
import com.google.samples.exposurenotification.matching.MatchingJni;


import org.jetbrains.annotations.Nullable;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale.Category;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;

import kotlin.jvm.internal.Intrinsics;

public final class MainActivity extends AppCompatActivity {

    public final int locationUpdateInterval = 60000; // in milliseconds
    public Context mContext;

    public BleAdvertisementGenerator advertiseGenerator;
    public BleAdvertiser bleAdvertiser;
    public RollingProximityIdManager proximityIdManager;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.setContentView(R.layout.activity_main);
        mContext = this.getBaseContext();
        TextView textView = (TextView) this.findViewById(R.id.sample_text);
        Intrinsics.checkExpressionValueIsNotNull(textView, "sample_text");
        textView.setText("GAEN+ Evaluation");

        //        if (ActivityCompat.checkSelfPermission(this, permission.BLUETOOTH_ADMIN) != PackageManager.PERMISSION_GRANTED
//        && ActivityCompat.checkSelfPermission(this, permission.BLUETOOTH) != PackageManager.PERMISSION_GRANTED) {
//
//        }

        requestPermissions(new String[]{permission.BLUETOOTH_ADMIN}, 1);
        requestPermissions(new String[]{permission.BLUETOOTH}, 2);
        requestPermissions(new String[]{permission.WRITE_EXTERNAL_STORAGE}, 3);

        // // // // // // // // // // // // // // // // // // // // //
        // GAEN+
        // Un comment the various experiments and rerun the app
        // View the console or logs on the phone in /Download/log.txt
        // // // // // // // // // // // // // // // // // // // // //


        // // // // // // // // // // // // // // // // // // // // //
        // Experiment: Overhead
        // // // // // // // // // // // // // // // // // // // // //
//        OverheadTest();

        // // // // // // // // // // // // // // // // // // // // //
        // Experiment: Matching Test using JNI
        // // // // // // // // // // // // // // // // // // // // //
//        try {
//            BatteryUtil.startRecordingBatteryPercentage(mContext);
//            checkingTestJni();
//        } catch (JSONException e){
//            e.printStackTrace();
//        }
        // // // // // // // // // // // // // // // // // // // // //
        // Experiment: Resolution
        // // // // // // // // // // // // // // // // // // // // //
        resolutionTest();
    }


    public void OverheadTest() {

        // use a fixed Tek for overhead test
        TemporaryExposureKey tek = TemporaryExposureKeySupport.getMaxKey(new DayNumber(0));
        proximityIdManager = new RollingProximityIdManager(tek);
        advertiseGenerator = new BleAdvertisementGenerator(proximityIdManager);
        bleAdvertiser = new BleAdvertiser();

        // start recording battery status
        BatteryUtil.startRecordingBatteryPercentage(mContext);

        if (Constants.GAENPLUS) {
            // GAEN+
            // update location per minute
            Timer timer = new Timer();
            timer.scheduleAtFixedRate(new TimerTask() {
                @Override
                public void run() {
                    // update location
                    LocationManager lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
                    Location loc = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                    LocationRecorder.currentLatitude = loc.getLatitude();
                    LocationRecorder.currentLongitude = loc.getLongitude();

                    Log.v("LocationUpdate",
                            "latitude: " + LocationRecorder.currentLatitude + " longitude: " + LocationRecorder.currentLongitude);

                    // change broadcast settings when location updated
                    try {
                        AdvertisementPacket advertisementPacket =
                                advertiseGenerator.generatePacket(0); // TODO fixed interval
                        bleAdvertiser.startAdvertising(advertisementPacket); // start broadcasting
                    } catch (CryptoException e) {
                        e.printStackTrace();
                    }
                }
            }, 0, locationUpdateInterval);

        }
        else {
            // original GAEN
            try {
                AdvertisementPacket advertisementPacket = advertiseGenerator.generatePacket(0); // TODO fixed interval
                bleAdvertiser.startAdvertising(advertisementPacket); // start broadcasting
            } catch (CryptoException e) {
                e.printStackTrace();
            }
        }
    }

    public void checkingTestJni() throws JSONException{
        FileUtil.writeLog("", false);

        // generate date time intervals
        DateTimeGenerator dateTimeGenerator = new DateTimeGenerator();

        // config
        int totalPeople = 100; // total # of teks to be generated
        int receiverLoc = 5;

        // generate TEKs
        long startTime, endTime;
        startTime = System.currentTimeMillis();
        int seed = Constants.seed; // use fixed random seed
        Map<Integer, List<TemporaryExposureKey>> teks =
                DataUtil.tekRandomGenerator(totalPeople, seed, dateTimeGenerator.dailyTimes);
        List<TemporaryExposureKey> tekList = new ArrayList<>();
        for (List<TemporaryExposureKey> tl: teks.values()) {
            tekList.addAll(tl);
        }

        // json results
        JSONArray jsonResults = new JSONArray();

        // generate RPIs received by the user
        List<RollingProximityId> rpis = new ArrayList<>();
        for (TemporaryExposureKey tek: tekList) {
            // create json result
            JSONObject currentJsonResult = new JSONObject();
//            currentJsonResult.put("TEK", DataUtil.byteArrayToHex(tek.getKeyData()));

            // generate RPIK
//            LocationGPS loc = LocationRecorder.getNextRandomLocation(); // TODO switch to NY locs
            LocationGPS loc = LocationRecorder.getNextRandomLocation_NY();
            LocationRecorder.currentLatitude = loc.latitude;
            LocationRecorder.currentLongitude = loc.longitude;
            proximityIdManager = new RollingProximityIdManager(tek);
            advertiseGenerator = new BleAdvertisementGenerator(proximityIdManager);
            byte[] rpik = proximityIdManager.rpiGenerator.rpik;
//            currentJsonResult.put("RPIK", DataUtil.byteArrayToHex(rpik));
//            currentJsonResult.put("Location", loc.toString());

            List<String> currentRpis = new ArrayList<>();

            long tekDate = tek.date;
            int timeIndex = dateTimeGenerator.dailyTimes.indexOf(tekDate);
            int currentIntervalIndex = timeIndex * 144;
            for (int i=0; i<144; ++i) {
                // generate RPI for each time interval
                RollingProximityId rpi =
                        advertiseGenerator.getRPI(dateTimeGenerator.timeIntervals.get(currentIntervalIndex).intValue());
                rpi.locationGPS = loc; // set location of RPI
                rpis.add(rpi);
                currentRpis.add(DataUtil.byteArrayToHex(rpi.get()));
                currentIntervalIndex += 1;
            }
//            currentJsonResult.put("RPIs", currentRpis);
//            jsonResults.put(currentJsonResult);
        }

        // calculate data generation time
        long dataGenerationTime = System.currentTimeMillis() - startTime; // in milliseconds
        log("Data generation time = " + dataGenerationTime / 1000 + "s");
        log(rpis.size() + " RPIs generated");

        // write json results
//        FileUtil.writeJSONResults(jsonResults);


        // on the receiver's side, assuming the he has been to 5 random locations
        log("The receiver has been to:");
        List<LocationGPS> receiverLocations = new ArrayList<>();
        for (int i=0; i<receiverLoc; ++i) {
//            LocationGPS loc = LocationRecorder.getNextRandomLocation(); // TODO switch to NY locs
            LocationGPS loc = LocationRecorder.locations_user_trace.get(i);
            receiverLocations.add(loc);
            log(loc.toString());
        }

        // generate received RPIs
        List<RollingProximityId> receivedRpis = new ArrayList<>();
        double percentage = 0.03;
        int totalReceivedRpi = (int) Math.floor(rpis.size() * percentage);
        Random random = new Random(seed); // use fixed seed
        List<Integer> randomIndexList = new ArrayList<>();

        log("Selecting " + percentage*100 + "% of RPIs for receiver...");
        for (int i=0; i<totalReceivedRpi; ++i) {
            int randomIndex = random.nextInt(rpis.size());
            while (randomIndexList.contains(randomIndex)) {
                randomIndex = random.nextInt(rpis.size());
            }
            randomIndexList.add(randomIndex); // make sure no duplicate
            receivedRpis.add(rpis.get(randomIndex));
        }
        log("Selection done, start finding matched RPIs");

        List<RollingProximityId> matchedRPIs = new ArrayList<>();

        // start matching
        // TODO use JNI library
        log("Start Matching using JNI interfaces");
        startTime = System.currentTimeMillis();
        MatchingJni matchingJni = new MatchingJni(mContext, receivedRpis);
        String [] matchedResult = matchingJni.matching_teks(tekList, receiverLocations, Constants.GAENPLUS);

        double matchingTime = ((double)(System.currentTimeMillis() - startTime)) / 1000;
        log("Matching Completed, elapsed time: " + matchingTime);


        // evaluate the results
        int matchCount = 0;
        int shouldMatch = 0;
        int correctMatch = 0;

        // TODO need to consider boundary case...
        Set<Long> locToCheck = new HashSet<>();
        for (int i=0; i<receiverLocations.size(); ++i) {
            // record h3 at resolution res
            long h3Long = LocationRecorder.getH3Long(receiverLocations.get(i).longitude, receiverLocations.get(i).latitude, LocationRecorder.resolution);
            // get neighbor and parent h3 index
            List<Long> neighbors = LocationRecorder.getH3Neighbors(h3Long, 1);
            long parent = LocationRecorder.getH3Parent(h3Long, LocationRecorder.resolution-1); // get parent at resolution
            locToCheck.add(parent);

            for (long nb: neighbors) {
                // get neighbor's parent index at res-1
                long nbParentIndex = LocationRecorder.getH3Parent(nb, LocationRecorder.resolution-1);
                if (nbParentIndex != parent) {
                    // is a boundary cell
                    locToCheck.add(nbParentIndex);
                }
            }
        }

        // building a hash table for the received RPIs for efficient look up
        Map<String, RollingProximityId> rpiMap = new HashMap<>();
        Map<String, RollingProximityId> shouldMatchRpiMap = new HashMap<>();
        for (RollingProximityId rpid: receivedRpis) {
            if (receiverLocations.contains(rpid.locationGPS)) {
                ++shouldMatch;
                shouldMatchRpiMap.put(rpid.toHexString(), rpid);
            }
            else {
                // TODO need to consider boundary case...

            }
            rpiMap.put(rpid.toHexString(), rpid);
        }

        List<RollingProximityId> matchedRPI = new ArrayList<>();
        for (int i=0; i<matchedResult.length; ++i) {
            if (!matchedResult[i].equals("")) {
                String currentRPIStr = matchedResult[i].toLowerCase();
                ++matchCount;
                RollingProximityId currentRPI = rpiMap.get(currentRPIStr);
                matchedRPI.add(currentRPI);
                if (shouldMatchRpiMap.containsKey(currentRPIStr)) {
                    correctMatch++;
                }
            }
            else
                break;
        }
        double fp = ((double)(matchCount - correctMatch)) / receivedRpis.size();
        double fn = ((double)(shouldMatch - correctMatch)) / receivedRpis.size();
        log("Total RPI: " + receivedRpis.size() + " Total Matched RPI: " + matchCount);
        log("Correct Match: " + correctMatch + "\tFalse positive:" + fp + "\tFalse negative:" + fn);
        log("" + ((double)(receivedRpis.size() - correctMatch)) / receivedRpis.size());
        System.out.println();
    }


    public void checkingTest() throws JSONException {

        FileUtil.writeLog("", false);

        // generate date time intervals
        DateTimeGenerator dateTimeGenerator = new DateTimeGenerator();

        // generate TEKs
        long startTime, endTime;
        startTime = System.currentTimeMillis();
        int totalPeople = 100; // total # of teks to be generated
        int seed = Constants.seed; // use fixed random seed
        Map<Integer, List<TemporaryExposureKey>> teks =
                DataUtil.tekRandomGenerator(totalPeople, seed, dateTimeGenerator.dailyTimes);
        List<TemporaryExposureKey> tekList = new ArrayList<>();
        for (List<TemporaryExposureKey> tl: teks.values()) {
            tekList.addAll(tl);
        }

        // json results
        JSONArray jsonResults = new JSONArray();

        // generate RPIs received by the user
        List<RollingProximityId> rpis = new ArrayList<>();
        for (TemporaryExposureKey tek: tekList) {
            // create json result
            JSONObject currentJsonResult = new JSONObject();
            currentJsonResult.put("TEK", DataUtil.byteArrayToHex(tek.getKeyData()));

            // generate RPIK
            LocationGPS loc = LocationRecorder.getNextRandomLocation(); // TODO switch to NY locs
//            LocationGPS loc = LocationRecorder.getNextRandomLocation_NY();
            LocationRecorder.currentLatitude = loc.latitude;
            LocationRecorder.currentLongitude = loc.longitude;
            proximityIdManager = new RollingProximityIdManager(tek);
            advertiseGenerator = new BleAdvertisementGenerator(proximityIdManager);
            byte[] rpik = proximityIdManager.rpiGenerator.rpik;
            currentJsonResult.put("RPIK", DataUtil.byteArrayToHex(rpik));
            currentJsonResult.put("Location", loc.toString());

            List<String> currentRpis = new ArrayList<>();

            long tekDate = tek.date;
            int timeIndex = dateTimeGenerator.dailyTimes.indexOf(tekDate);
            int currentIntervalIndex = timeIndex * 144;
            for (int i=0; i<144; ++i) {
                // generate RPI for each time interval
                RollingProximityId rpi =
                        advertiseGenerator.getRPI(dateTimeGenerator.timeIntervals.get(currentIntervalIndex).intValue());
                rpi.locationGPS = loc; // set location of RPI
                rpis.add(rpi);
                currentRpis.add(DataUtil.byteArrayToHex(rpi.get()));
                currentIntervalIndex += 1;
            }
            currentJsonResult.put("RPIs", currentRpis);
            jsonResults.put(currentJsonResult);
        }


        // calculate data generation time
        long dataGenerationTime = System.currentTimeMillis() - startTime; // in milliseconds
        log("Data generation time = " + dataGenerationTime / 1000 + "s");
        log(rpis.size() + " RPIs generated");

        // write json results
        FileUtil.writeJSONResults(jsonResults);

        // on the receiver's side, assuming the he has been to 5 random locations
        log("The receiver has been to:");
        List<LocationGPS> receiverLocations = new ArrayList<>();
        for (int i=0; i<5; ++i) {
            LocationGPS loc = LocationRecorder.getNextRandomLocation(); // TODO switch to NY locs
//            LocationGPS loc = LocationRecorder.locations_user_trace.get(i);
            receiverLocations.add(loc);
            log(loc.toString());
        }

        // generate received RPIs
        List<RollingProximityId> receivedRpis = new ArrayList<>();
        double percentage = 0.03;
        int totalReceivedRpi = (int) Math.floor(rpis.size() * percentage);
        Random random = new Random(seed); // use fixed seed
        List<Integer> randomIndexList = new ArrayList<>();

        log("Selecting " + percentage*100 + "% of RPIs for receiver...");
        for (int i=0; i<totalReceivedRpi; ++i) {
            int randomIndex = random.nextInt(rpis.size());
            while (randomIndexList.contains(randomIndex)) {
                randomIndex = random.nextInt(rpis.size());
            }
            randomIndexList.add(randomIndex); // make sure no duplicate
            receivedRpis.add(rpis.get(randomIndex));
        }
        log("Selection done, start finding matched RPIs");

        List<RollingProximityId> matchedRPIs = new ArrayList<>();

        // start matching
        int errorRpiNum = 0;
        int correctRpiNum = 0;
        startTime = System.currentTimeMillis();
        for (TemporaryExposureKey tek: tekList) {
            // calculate RPIs based on the teks
            if (Constants.GAENPLUS) {
                for (LocationGPS locationGPS : receiverLocations) {
                    // derive RPI
                    LocationRecorder.currentLatitude = locationGPS.latitude;
                    LocationRecorder.currentLongitude = locationGPS.longitude;
                    proximityIdManager = new RollingProximityIdManager(tek);
                    advertiseGenerator = new BleAdvertisementGenerator(proximityIdManager);

                    long tekDate = tek.date;
                    int timeIndex = dateTimeGenerator.dailyTimes.indexOf(tekDate);
                    int intervalStartIndex = timeIndex * 144;
                    // only need to loop through the intervals of the specific date
                    for (int i=0; i<144; ++i) {
                        int interval =
                                dateTimeGenerator.timeIntervals.get(intervalStartIndex + i).intValue();
                        RollingProximityId calculatedRPI = advertiseGenerator.getRPI((int) interval);
                        // check if rpi exists in the received rpi list
                        if (receivedRpis.contains(calculatedRPI)) {
                            RollingProximityId matchedRpi =
                                    receivedRpis.get(receivedRpis.indexOf(calculatedRPI));
                            log("Find matching RPI: " + matchedRpi.toString() + " " +
                                    "from: " + matchedRpi.locationGPS.toString());

                            if (!matchedRpi.locationGPS.equals(locationGPS))
                                errorRpiNum++;
                            else
                                correctRpiNum++;

                            matchedRPIs.add(matchedRpi);
                            receivedRpis.remove(matchedRpi); // remove matched RPI to save time
                        }
                    }
                }
            }
            else {
                // without GAEN+, all the teks should match
                proximityIdManager = new RollingProximityIdManager(tek);
                advertiseGenerator = new BleAdvertisementGenerator(proximityIdManager);
                long tekDate = tek.date;
                int timeIndex = dateTimeGenerator.dailyTimes.indexOf(tekDate);
                int intervalStartIndex = timeIndex * 144;
                // only need to loop through the intervals of the specific date
                for (int i=0; i<144; ++i) {
                    long interval = dateTimeGenerator.timeIntervals.get(intervalStartIndex + i);
                    RollingProximityId calculatedRPI = advertiseGenerator.getRPI((int) interval);
                    // check if rpi exists in the received rpi list
                    if (receivedRpis.contains(calculatedRPI)) {
                        RollingProximityId matchedRpi =
                                receivedRpis.get(receivedRpis.indexOf(calculatedRPI));
                        log("Find matching RPI: " + matchedRpi.toString() + " " +
                                "from: " + matchedRpi.locationGPS.toString());
                        matchedRPIs.add(matchedRpi);

                        if (!receiverLocations.contains(matchedRpi.locationGPS))
                            errorRpiNum++;
                        else
                            correctRpiNum++;

                        receivedRpis.remove(matchedRpi); // remove matched RPI to save time
                    }
                }
            }
        }
        // calculate time spent for the matching process
        long matchTime = System.currentTimeMillis() - startTime; // in ms
        log("Total matched RPI: " + matchedRPIs.size() + " out of " + rpis.size() * percentage);
        log("Total time for matching: " + matchTime / 1000 + "s");
        log("Correct RPI number: " + correctRpiNum);
        log("Mismatched RPI number: " + errorRpiNum);
        log("Unmatched RPI number: " + receivedRpis.size());
    }

    public static void resolutionTest() {
        int resolution = 10;
        HashMap<String, List<Integer>> map = new HashMap<>();
        for (LocationGPS loc: LocationRecorder.locations_NY) {
            String locIndex = LocationRecorder.getH3(loc.longitude, loc.latitude, resolution);
            int index = LocationRecorder.locations_NY.indexOf(loc);
            System.out.println(index + "\t" + locIndex);
            if (map.containsKey(locIndex))
                map.get(locIndex).add(index);
            else {
                List<Integer> tmp = new ArrayList<>();
                tmp.add(index);
                map.put(locIndex, tmp);
            }
        }

        int numKeys = map.keySet().size();
        System.out.println("numkeys: " + numKeys);
    }

    public static void log(String s) {
        String TAG = "MatchingTest";
        Log.i(TAG, s);
        FileUtil.writeLog(s, true);
    }



}

// email
/**
 * §  For each TEK in allTEK (1400)
 * §    for each location in userH3Locations (5)
 * §        generate RPIK with location
 * §        for each interval in allTimes (144)
 * §            generate RPI for this RPIK and this time
 * §            Check if there is a match
 *              Remove RPI from recieved RPI
 *
 *          # TEK * #LOC * #INTERVALS * #RPI
 **/

/**
 * For each Tek
 *      For each location
 *          generate RPIK
 *
 *
 * For each RPI
 *      For each RPIK
 *          For each intervals
 *              break
 *
 *    #TEK * #LOC + #RPI * #RPIK * #INTERVAL
 *
 *
 * For each RPI in receivedRPIs
 *  For each Tek in all TEKs
 *      For locations
 *          For intervals
 *              Calculated an RPI for the Tek
 *              Find if there is a match
 *              break, and check the next received RPI
 *
 *      #RPI * #TEK * #LOC * #INTERVALs
 **/





