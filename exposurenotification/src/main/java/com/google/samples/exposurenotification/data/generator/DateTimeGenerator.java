package com.google.samples.exposurenotification.data.generator;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class DateTimeGenerator {

    public List<Long> dailyTimes = new ArrayList<>();
    public List<Long> timeIntervals = new ArrayList<>();


    public DateTimeGenerator() {
        SimpleDateFormat formatter = new SimpleDateFormat("dd-MMM-yyyy HH:mm:ss");
        try {
            // create 14 days time
            dailyTimes.add(formatter.parse("01-Sep-2021 00:00:00").getTime());
            dailyTimes.add(formatter.parse("02-Sep-2021 00:00:00").getTime());
            dailyTimes.add(formatter.parse("03-Sep-2021 00:00:00").getTime());
            dailyTimes.add(formatter.parse("04-Sep-2021 00:00:00").getTime());
            dailyTimes.add(formatter.parse("05-Sep-2021 00:00:00").getTime());
            dailyTimes.add(formatter.parse("06-Sep-2021 00:00:00").getTime());
            dailyTimes.add(formatter.parse("07-Sep-2021 00:00:00").getTime());
            dailyTimes.add(formatter.parse("08-Sep-2021 00:00:00").getTime());
            dailyTimes.add(formatter.parse("09-Sep-2021 00:00:00").getTime());
            dailyTimes.add(formatter.parse("10-Sep-2021 00:00:00").getTime());
            dailyTimes.add(formatter.parse("11-Sep-2021 00:00:00").getTime());
            dailyTimes.add(formatter.parse("12-Sep-2021 00:00:00").getTime());
            dailyTimes.add(formatter.parse("13-Sep-2021 00:00:00").getTime());
            dailyTimes.add(formatter.parse("14-Sep-2021 00:00:00").getTime());

            // create time intervals
            for (long dayTime: dailyTimes) {
                for (int i=0; i<144; ++i) {
                    // 600s interval, timestamps are in milliseconds
                    timeIntervals.add((dayTime + (600 * 1000) * i) / TimeUnit.MINUTES.toMillis(10));
                }
            }

        } catch (ParseException | NullPointerException e) {
            e.printStackTrace();
        }
    }

}
