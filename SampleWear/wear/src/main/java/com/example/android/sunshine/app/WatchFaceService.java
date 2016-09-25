package com.example.android.sunshine.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.WindowInsets;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataItem;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.Wearable;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

/**
 * Created by ousma on 9/24/2016.
 */

public class WatchFaceService extends CanvasWatchFaceService {

    private static final String LOG_TAG = WatchFaceService.class.getSimpleName();

    private static final Typeface NORMAL_TYPEFACE = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL);

    private static final long INTERACTIVE_UPDATE_RATE_MS = TimeUnit.SECONDS.toMillis(1);

    private static final int MSG_UPDATE_TIME = 0;

    @Override
    public CanvasWatchFaceService.Engine onCreateEngine() {
        return new Engine();
    }

    private class Engine extends CanvasWatchFaceService.Engine implements DataApi.DataListener, GoogleApiClient.ConnectionCallbacks,GoogleApiClient.OnConnectionFailedListener {
        boolean mRegisteredTimeZoneReceiver = false;
        Paint mBackgroundPaint;
        Paint mTextPaint;
        boolean mAmbient;
        Calendar mCalendar;
        private GoogleApiClient mGoogleApiClient;

        private String mHighTemp = "0°C";
        private String mLowTemp = " 0°C";

        private int mWeatherId;
        Paint mLightTextPaint;
        private SimpleDateFormat mDayOfWeekFormat;

        Date mDate;

        private final  Handler mUpdateTimeHandler = new Handler(){
            @Override
            public void handleMessage(Message message) {
                switch (message.what){
                    case MSG_UPDATE_TIME:
                        invalidate();
                        if(shouldTimerBeRunning()){
                            long timeMs = System.currentTimeMillis();
                            long delayMs = INTERACTIVE_UPDATE_RATE_MS
                                    - (timeMs % INTERACTIVE_UPDATE_RATE_MS);
                            mUpdateTimeHandler
                                    .sendEmptyMessageDelayed(MSG_UPDATE_TIME, delayMs);
                        }
                        break;
                }
            }
        };

        final BroadcastReceiver mTimeZoneReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                mCalendar.setTimeZone(TimeZone.getDefault());
                invalidate();
            }
        };
        float mXOffset;
        float mYOffset;

        boolean mLowBitAmbient;

        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);

            mGoogleApiClient = new GoogleApiClient.Builder(WatchFaceService.this)
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this)
                    .addApi(Wearable.API)
                    .build();
            mGoogleApiClient.connect();

            setWatchFaceStyle(new WatchFaceStyle.Builder(WatchFaceService.this)
                    .setCardPeekMode(WatchFaceStyle.PEEK_MODE_VARIABLE)
                    .setBackgroundVisibility(WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE)
                    .setShowSystemUiTime(false)
                    .setAcceptsTapEvents(true)
                    .build());
            Resources resources = WatchFaceService.this.getResources();
            mYOffset = resources.getDimension(R.dimen.digital_watch_y_offset);

            mBackgroundPaint = new Paint();
            mBackgroundPaint.setColor(resources.getColor(R.color.background));

            mTextPaint = new Paint();
            mTextPaint = createTextPaint(resources.getColor(R.color.digital_text));

            mLightTextPaint = new Paint();
            mLightTextPaint = createTextPaint(resources.getColor(R.color.light_text));

            mCalendar = Calendar.getInstance();

            mDate = new Date();
        }

        private Paint createTextPaint(int textColor) {
            Paint paint = new Paint();
            paint.setColor(textColor);
            paint.setTypeface(NORMAL_TYPEFACE);
            paint.setAntiAlias(true);
            return paint;
        }

        @Override
        public void onDestroy() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            super.onDestroy();
        }

        private boolean shouldTimerBeRunning() {
            return isVisible() && !isInAmbientMode();
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);
            if(visible){
                registerReceiver();

                mCalendar.setTimeZone(TimeZone.getDefault());
                invalidate();
            }else {
                unregisterReceiver();
                if (mGoogleApiClient != null && mGoogleApiClient.isConnected()) {
                    mGoogleApiClient.disconnect();
                }
            }
        }

        private void registerReceiver() {
            if (mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = true;
            IntentFilter filter = new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED);
            WatchFaceService.this.registerReceiver(mTimeZoneReceiver, filter);
        }

        private void unregisterReceiver() {
            if (!mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = false;
            WatchFaceService.this.unregisterReceiver(mTimeZoneReceiver);
        }

        @Override
        public void onApplyWindowInsets(WindowInsets insets) {
            super.onApplyWindowInsets(insets);

            // Load resources that have alternate values for round watches.
            Resources resources = WatchFaceService.this.getResources();
            boolean isRound = insets.isRound();
            mXOffset = resources.getDimension(isRound
                    ? R.dimen.digital_x_offset_round : R.dimen.digital_watch_x_offset);
            float textSize = resources.getDimension(isRound
                    ? R.dimen.digital_watch_text_size_round : R.dimen.digital_watch_text_size);
            float dateSize = resources.getDimension(isRound
                    ? R.dimen.digital_watch_date_size_round : R.dimen.digital_watch_date_size);

            mTextPaint.setTextSize(textSize);
            mLightTextPaint.setTextSize(dateSize);
        }

        @Override
        public void onPropertiesChanged(Bundle properties) {
            super.onPropertiesChanged(properties);
            mLowBitAmbient = properties.getBoolean(PROPERTY_LOW_BIT_AMBIENT, false);
        }

        @Override
        public void onTimeTick() {
            super.onTimeTick();
            invalidate();
        }

        @Override
        public void onAmbientModeChanged(boolean inAmbientMode) {
            super.onAmbientModeChanged(inAmbientMode);
            if (mAmbient != inAmbientMode) {
                mAmbient = inAmbientMode;
                if (mLowBitAmbient) {
                    mTextPaint.setAntiAlias(!inAmbientMode);
                }
                invalidate();
            }

            updateTimer();
        }

        private void updateTimer() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            if (shouldTimerBeRunning()) {
                mUpdateTimeHandler.sendEmptyMessage(MSG_UPDATE_TIME);
            }
        }

        @Override
        public void onTapCommand(int tapType, int x, int y, long eventTime) {
            switch (tapType) {
                case TAP_TYPE_TOUCH:
                    break;
                case TAP_TYPE_TOUCH_CANCEL:
                    break;
                case TAP_TYPE_TAP:
                    Toast.makeText(getApplicationContext(), R.string.message, Toast.LENGTH_SHORT)
                            .show();
                    break;
            }
            invalidate();
        }

        @Override
        public void onDraw(Canvas canvas, Rect bounds) {
            // Draw the background.
            if (isInAmbientMode()) {
                canvas.drawColor(Color.BLACK);
            } else {
                canvas.drawRect(0, 0, bounds.width(), bounds.height(), mBackgroundPaint);
            }

            // Draw H:MM in ambient mode or H:MM:SS in interactive mode.
            long now = System.currentTimeMillis();
            mCalendar.setTimeInMillis(now);
            String text = String.format("%d:%02d", mCalendar.get(Calendar.HOUR),mCalendar.get(Calendar.MINUTE));

            canvas.drawText(text, mXOffset+40, mYOffset, mTextPaint);

            float mLineOffset = getResources().getDimension(R.dimen.line_height);
            if (mWeatherId == 0) {
                mWeatherId = 800;
            }
            Bitmap weatherIcon = BitmapFactory.decodeResource(getResources(), Utility.getIconResourceForWeatherCondition(mWeatherId));
            int scale = 60;
            Bitmap weather = Bitmap.createScaledBitmap(weatherIcon, scale, scale, true);

            mDayOfWeekFormat = new SimpleDateFormat("EEE, dd MMMM");
            String date = mDayOfWeekFormat.format(mCalendar.getTime());

            canvas.drawText(date, bounds.centerX() - (mLightTextPaint.measureText(date)/2),
                    mYOffset + mLineOffset, mLightTextPaint);


            Log.d("data received :", "\nHigh: " + mHighTemp + " Low: " + mLowTemp + " ID: " + mWeatherId);

            if (!mAmbient) {
                canvas.drawBitmap(weather, mXOffset+5, mYOffset + mLineOffset, mTextPaint);
                canvas.drawText(mHighTemp, mXOffset+70, mYOffset + 2 * mLineOffset + 10, mLightTextPaint);
                canvas.drawText(mLowTemp, mXOffset+90 + mLightTextPaint.measureText(mHighTemp),mYOffset + 2 * mLineOffset + 10, mLightTextPaint);
            }

        }

        @Override
        public void onConnected(@Nullable Bundle bundle) {
            Log.d(LOG_TAG, "Google Api client CONNECTED");
            Wearable.DataApi.addListener(mGoogleApiClient, this);
        }

        @Override
        public void onConnectionSuspended(int reason) {
            Log.d(LOG_TAG, "Google Api Client Connection SUSPENDED "+reason);
        }

        @Override
        public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
            Log.d(LOG_TAG, "Google Api client Connection FAILED "+connectionResult);
        }

        @Override
        public void onDataChanged(DataEventBuffer dataEventBuffer) {
            Log.d(LOG_TAG, "inside onDataChanged ");
            for (DataEvent dataEvent : dataEventBuffer) {
                if (dataEvent.getType() == DataEvent.TYPE_CHANGED) {
                    DataItem dataItem = dataEvent.getDataItem();
                    if (dataItem.getUri().getPath().equals("/wearable")) {
                        DataMap dataMap = DataMapItem.fromDataItem(dataItem).getDataMap();
                        mHighTemp = dataMap.getString("high");
                        mLowTemp = dataMap.getString("low");
                        mWeatherId = dataMap.getInt("id");

                        mHighTemp = String.format("%3s",String.valueOf(mHighTemp)) + "°C";
                        mLowTemp = String.format("%3s",String.valueOf(mLowTemp)) + "°C";

                        Log.d("WATCH_DATA", "High: " + mHighTemp + " Low: " + mLowTemp + " ID: " + mWeatherId);
                        invalidate();
                    }
                }
            }
        }
    }

}
