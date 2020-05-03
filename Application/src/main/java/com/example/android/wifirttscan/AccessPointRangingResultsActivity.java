/*
 * Copyright (C) 2018 Google Inc. All Rights Reserved.
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
package com.example.android.wifirttscan;

import android.Manifest.permission;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.wifi.ScanResult;
import android.net.wifi.rtt.RangingRequest;
import android.net.wifi.rtt.RangingResult;
import android.net.wifi.rtt.RangingResultCallback;
import android.net.wifi.rtt.WifiRttManager;
import android.os.Bundle;
import android.os.Handler;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.appcompat.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
/**
 Code Reference: https://github.com/android/connectivity-samples/tree/master/WifiRttScan
 Edited Googles example RTT-Application to meet the requirements of this app
 **/
public class AccessPointRangingResultsActivity extends AppCompatActivity {
    private static final String TAG = "APRRActivity";

    public static final String SCAN_RESULT_EXTRA =
            "com.example.android.wifirttscan.extra.SCAN_RESULT";
    public static final String SCAN_RESULT_EXTRA_1 =
            "com.example.android.wifirttscan.extra.SCAN_RESULT_1";

    private static final int SAMPLE_SIZE_DEFAULT = 50;
    private static final int MILLISECONDS_DELAY_BEFORE_NEW_RANGING_REQUEST_DEFAULT = 200;

    // UI Elements.
    private TextView mSsidTextView;
    private TextView mBssidTextView;

    private TextView mRangeTextView;
    private TextView mRangeSDTextView;
    private TextView mSuccessRatioTextView;

    private TextView _mRangeTextView;
    private TextView _mRangeSDTextView;

    private TextView trackingTextView;

    private EditText mSampleSizeEditText;
    private EditText mMillisecondsDelayBeforeNewRangingRequestEditText;

    // Non UI variables.
    private ScanResult mScanResult;
    private ScanResult _mScanResult;
    List<ScanResult> scanResultList = new ArrayList<>();
    private String mMAC;
    private String _mMAC;

    private int mNumberOfRangeRequests;
    private int mNumberOfSuccessfulRangeRequests;

    private int mMillisecondsDelayBeforeNewRangingRequest;

    // Max sample size to calculate average for
    // 1. Distance to device (getDistanceMm) over time
    // 2. Standard deviation of the measured distance to the device (getDistanceStdDevMm) over time
    // Note: A RangeRequest result already consists of the average of 7 readings from a burst,
    // so the average in (1) is the average of these averages.
    private int mSampleSize;

    // Used to loop over a list of distances to calculate averages (ensures data structure never
    // get larger than sample size).
    private int mStatisticRangeHistoryEndIndex;
    private ArrayList<Integer> mStatisticRangeHistory;
    private ArrayList<Integer> _mStatisticRangeHistory;

    // Used to loop over a list of the standard deviation of the measured distance to calculate
    // averages  (ensures data structure never get larger than sample size).
    private int mStatisticRangeSDHistoryEndIndex;
    private ArrayList<Integer> mStatisticRangeSDHistory;
    private ArrayList<Integer> _mStatisticRangeSDHistory;

    private WifiRttManager mWifiRttManager;
    private RttRangingResultCallback mRttRangingResultCallback;

    // Triggers additional RangingRequests with delay (mMillisecondsDelayBeforeNewRangingRequest).
    final Handler mRangeRequestDelayHandler = new Handler();


    boolean tracking = false;
    int lastDistance;
    int _lastDistance;
    int distanceChange;
    int _distanceChange;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_access_point_ranging_results);

        // Initializes UI elements.

        mRangeTextView = findViewById(R.id.range_value);
        _mRangeTextView = findViewById(R.id._range_value);
        mRangeSDTextView = findViewById(R.id.range_sd_value);
        _mRangeSDTextView = findViewById(R.id._range_sd_value);

        mSuccessRatioTextView = findViewById(R.id.success_ratio_value);

        trackingTextView = findViewById(R.id.crossed_street_text);

        mSampleSizeEditText = findViewById(R.id.stats_window_size_edit_value);
        mSampleSizeEditText.setText(SAMPLE_SIZE_DEFAULT + "");

        mMillisecondsDelayBeforeNewRangingRequestEditText =
                findViewById(R.id.ranging_period_edit_value);
        mMillisecondsDelayBeforeNewRangingRequestEditText.setText(
                MILLISECONDS_DELAY_BEFORE_NEW_RANGING_REQUEST_DEFAULT + "");

        // Retrieve ScanResult from Intent.
        Intent intent = getIntent();
        mScanResult = intent.getParcelableExtra(SCAN_RESULT_EXTRA);
        _mScanResult = intent.getParcelableExtra(SCAN_RESULT_EXTRA_1);

        scanResultList.add(mScanResult);
        scanResultList.add(_mScanResult);

        if (mScanResult == null) {
            finish();
        }

        mMAC = mScanResult.BSSID;
        _mMAC = _mScanResult.BSSID;


        mWifiRttManager = (WifiRttManager) getSystemService(Context.WIFI_RTT_RANGING_SERVICE);
        mRttRangingResultCallback = new RttRangingResultCallback();

        // Used to store range (distance) and rangeSd (standard deviation of the measured distance)
        // history to calculate averages.
        mStatisticRangeHistory = new ArrayList<>();
        mStatisticRangeSDHistory = new ArrayList<>();
        _mStatisticRangeHistory = new ArrayList<>();
        _mStatisticRangeSDHistory = new ArrayList<>();

        resetData();

        startRangingRequest();
    }

    private void resetData() {
        mSampleSize = Integer.parseInt(mSampleSizeEditText.getText().toString());

        mMillisecondsDelayBeforeNewRangingRequest =
                Integer.parseInt(
                        mMillisecondsDelayBeforeNewRangingRequestEditText.getText().toString());

        mNumberOfSuccessfulRangeRequests = 0;
        mNumberOfRangeRequests = 0;

        mStatisticRangeHistoryEndIndex = 0;
        mStatisticRangeHistory.clear();
        _mStatisticRangeHistory.clear();

        mStatisticRangeSDHistoryEndIndex = 0;
        mStatisticRangeSDHistory.clear();
        _mStatisticRangeSDHistory.clear();
    }

    private void startRangingRequest() {
        // Permission for fine location should already be granted via MainActivity (you can't get
        // to this class unless you already have permission. If they get to this class, then disable
        // fine location permission, we kick them back to main activity.
        if (ActivityCompat.checkSelfPermission(this, permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            finish();
        }

        mNumberOfRangeRequests++;

        RangingRequest rangingRequest =
                new RangingRequest.Builder().addAccessPoints(scanResultList).build();

        mWifiRttManager.startRanging(
                rangingRequest, getApplication().getMainExecutor(), mRttRangingResultCallback);
    }


    // Adds distance to history. If larger than sample size value, loops back over and replaces the
    // oldest distance record in the list.
    private void addDistanceToHistory(int distance, int _distance) {

        if (_mStatisticRangeHistory.size() >= mSampleSize && mStatisticRangeHistory.size() >= mSampleSize) {

            if (mStatisticRangeHistoryEndIndex >= mSampleSize) {
                mStatisticRangeHistoryEndIndex = 0;
            }

            mStatisticRangeHistory.set(mStatisticRangeHistoryEndIndex, distance);
            _mStatisticRangeHistory.set(mStatisticRangeHistoryEndIndex, _distance);
            mStatisticRangeHistoryEndIndex++;

        } else {
            mStatisticRangeHistory.add(distance);
            _mStatisticRangeHistory.add(_distance);
        }

        if(_distance < 2000 || distance < 2000 && !tracking){
            tracking = true;
            trackingTextView.setText("STARTED TRACKING");
        }
        if(tracking && lastDistance != 0 && _lastDistance != 0){
            calculateDistanceChanges(distance, lastDistance, _distance, _lastDistance);
        }
        lastDistance = distance;
        _lastDistance = _distance;
        if((distanceChange > 1000 && _distanceChange < -1000) || (_distanceChange > 1000 && distanceChange < -1000)){
            Intent returnIntent = new Intent();
            returnIntent.putExtra("result", "CROSSED THE STREET");
            setResult(Activity.RESULT_OK, returnIntent);
            finish();
            distanceChange = 0;
            _distanceChange = 0;
            tracking = false;
        }
    }

    public void calculateDistanceChanges(int distance, int lastDistance, int _distance, int _lastDistance){
        if(distance > 6000 && _distance > 6000){
            distanceChange = 0;
            _distanceChange = 0;
            tracking = false;
            Intent returnIntent = new Intent();
            returnIntent.putExtra("result", "DID NOT CROSS THE STREET");
            setResult(Activity.RESULT_OK, returnIntent);
            finish();
            return;
        }
        distanceChange += lastDistance - distance;
        _distanceChange += _lastDistance - _distance;
    }


    private void addStandardDeviationOfDistanceToHistory(int distanceSd, int _distanceSd) {

        if (_mStatisticRangeSDHistory.size() >= mSampleSize && mStatisticRangeSDHistory.size() >= mSampleSize) {

            if (mStatisticRangeSDHistoryEndIndex >= mSampleSize) {
                mStatisticRangeSDHistoryEndIndex = 0;
            }

            mStatisticRangeSDHistory.set(mStatisticRangeSDHistoryEndIndex, distanceSd);
            _mStatisticRangeSDHistory.set(mStatisticRangeSDHistoryEndIndex, _distanceSd);
            mStatisticRangeSDHistoryEndIndex++;

        } else {
            mStatisticRangeSDHistory.add(distanceSd);
            _mStatisticRangeSDHistory.add(_distanceSd);
        }
    }

    public void onResetButtonClick(View view) {
        resetData();
    }

    // Class that handles callbacks for all RangingRequests and issues new RangingRequests.
    private class RttRangingResultCallback extends RangingResultCallback {

        private void queueNextRangingRequest() {
            mRangeRequestDelayHandler.postDelayed(
                    new Runnable() {
                        @Override
                        public void run() {
                            startRangingRequest();
                        }
                    },
                    mMillisecondsDelayBeforeNewRangingRequest);
        }

        @Override
        public void onRangingFailure(int code) {
            Log.d(TAG, "onRangingFailure() code: " + code);
            queueNextRangingRequest();
        }

        @Override
        public void onRangingResults(@NonNull List<RangingResult> list) {
            Log.d(TAG, "onRangingResults(): " + list);

            if (list.size() > 1) {

                RangingResult rangingResult = list.get(0);
                RangingResult _rangingResult = list.get(1);

                if (mMAC.equals(rangingResult.getMacAddress().toString()) && _mMAC.equals(_rangingResult.getMacAddress().toString())) {

                    if (rangingResult.getStatus() == RangingResult.STATUS_SUCCESS && _rangingResult.getStatus() == RangingResult.STATUS_SUCCESS) {

                        mNumberOfSuccessfulRangeRequests++;

                        mRangeTextView.setText((rangingResult.getDistanceMm() / 1000f) + "");
                        _mRangeTextView.setText((_rangingResult.getDistanceMm() / 1000f) + "");
                        addDistanceToHistory(rangingResult.getDistanceMm(), _rangingResult.getDistanceMm());

                        mRangeSDTextView.setText(
                                (rangingResult.getDistanceStdDevMm() / 1000f) + "");
                        _mRangeSDTextView.setText(
                                (_rangingResult.getDistanceStdDevMm() / 1000f) + "");
                        addStandardDeviationOfDistanceToHistory(rangingResult.getDistanceStdDevMm(), _rangingResult.getDistanceStdDevMm());



                        float successRatio =
                                ((float) mNumberOfSuccessfulRangeRequests
                                                / (float) mNumberOfRangeRequests)
                                        * 100;
                        mSuccessRatioTextView.setText(successRatio + "%");


                    } else if (rangingResult.getStatus()
                            == RangingResult.STATUS_RESPONDER_DOES_NOT_SUPPORT_IEEE80211MC) {
                        Log.d(TAG, "RangingResult failed (AP doesn't support IEEE80211 MC.");

                    } else {
                        Log.d(TAG, "RangingResult failed.");
                    }

                } else {
                    Toast.makeText(
                                    getApplicationContext(),
                                    R.string
                                            .mac_mismatch_message_activity_access_point_ranging_results,
                                    Toast.LENGTH_LONG)
                            .show();
                }
            }

            queueNextRangingRequest();
        }
    }
}
