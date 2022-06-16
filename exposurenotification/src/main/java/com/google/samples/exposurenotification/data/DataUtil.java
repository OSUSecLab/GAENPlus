package com.google.samples.exposurenotification.data;

import com.google.samples.exposurenotification.nearby.TemporaryExposureKey;

import org.joda.time.Instant;

import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DataUtil {

    public static byte [] convertDoubleToByteArray(double number) {
        ByteBuffer byteBuffer = ByteBuffer.allocate(Double.BYTES);
        byteBuffer.putDouble(number);
        return byteBuffer.array();
    }

    public static String byteArrayToHex(byte[] a) {
        StringBuilder sb = new StringBuilder(a.length * 2);
        for(byte b: a)
            sb.append(String.format("%02x", b));
        return sb.toString();
    }

    // randomly generate a list of teks
    // return value is a map, where the index is the person's index, the value is the list of teks
    public static Map<Integer, List<TemporaryExposureKey>> tekRandomGenerator(int totalPeople,
                                                                              int seed,
                                                                              List<Long> dailyTime) {

        SecureRandom secureRandom = new SecureRandom();
        secureRandom.setSeed(seed);
        byte[] b = new byte[16];

        Map<Integer, List<TemporaryExposureKey>> teks = new HashMap<>();

        for (int peopleIndex=0; peopleIndex<totalPeople; ++peopleIndex) {
            List<TemporaryExposureKey> currentPeoplesTeks = new ArrayList<>();
            for (long dt: dailyTime) {
                // create teks for each person
                secureRandom.nextBytes(b);
                TemporaryExposureKey tek =
                        TemporaryExposureKeySupport.getTek(new DayNumber(new Instant(dt)), b);
                tek.date = dt; // record date time for the TEK
                currentPeoplesTeks.add(tek);
            }
            // add teks to results
            teks.put(peopleIndex, currentPeoplesTeks);
        }

        return teks;
    }


}
