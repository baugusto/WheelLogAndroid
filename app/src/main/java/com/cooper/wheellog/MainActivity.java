package com.cooper.wheellog;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Fragment;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.IntentSender;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Typeface;
import android.graphics.drawable.AnimationDrawable;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.IBinder;
import android.os.Bundle;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.design.widget.Snackbar;
import android.support.v4.content.FileProvider;
import android.support.v4.view.GravityCompat;
import android.support.v4.view.ViewPager;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.preference.PreferenceManager;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.os.Handler;
import android.view.View;
import android.view.WindowManager;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextClock;
import android.widget.TextView;
import android.widget.Toast;

import com.cooper.wheellog.utils.Constants;
import com.cooper.wheellog.utils.Constants.WHEEL_TYPE;
import com.cooper.wheellog.utils.Constants.ALARM_TYPE;
import com.cooper.wheellog.utils.HttpClient;
import com.cooper.wheellog.utils.SettingsUtil;
import com.cooper.wheellog.views.WheelView;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.AxisBase;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.IAxisValueFormatter;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.drive.Drive;
import com.loopj.android.http.JsonHttpResponseHandler;
import com.loopj.android.http.RequestParams;
import com.viewpagerindicator.LinePageIndicator;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;

import id.zelory.compressor.Compressor;
import io.flic.lib.FlicAppNotInstalledException;
import io.flic.lib.FlicBroadcastReceiverFlags;
import io.flic.lib.FlicButton;
import io.flic.lib.FlicManager;
import io.flic.lib.FlicManagerInitializedCallback;
import permissions.dispatcher.NeedsPermission;
import permissions.dispatcher.OnPermissionDenied;
import permissions.dispatcher.RuntimePermissions;
import timber.log.Timber;

import static com.cooper.wheellog.utils.MathsUtil.kmToMiles;

@RuntimePermissions
public class MainActivity extends AppCompatActivity implements GoogleApiClient.OnConnectionFailedListener {

    Menu mMenu;
    MenuItem miSearch;
    MenuItem miWheel;
    MenuItem miWatch;
    MenuItem miLogging;
    MenuItem miSpeech;

    TextView tvSpeed;
    TextView tvTemperature;
	TextView tvTemperature2;
	TextView tvAngle;
	TextView tvRoll;
    TextView tvCurrent;
    TextView tvPower;
    TextView tvVoltage;
    TextView tvBattery;
    TextView tvFanStatus;
    TextView tvTopSpeed;
	TextView tvAverageSpeed;
	TextView tvAverageRidingSpeed;
    TextView tvDistance;
	TextView tvWheelDistance;
	TextView tvUserDistance;
    TextView tvModel;
    TextView tvName;
    TextView tvVersion;
    TextView tvSerial;
    TextView tvTotalDistance;
    TextView tvRideTime;
	TextView tvRidingTime;
    TextView tvMode;
    TextView tvLivemapStatus;

    ImageButton ibLivemapStartFinish;
    ImageButton ibLivemapPause;
    ImageButton ibLivemapShare;
    ImageButton ibLivemapPhoto;

    ImageView ivUploadProgressWarning;
    ImageView ivLivemapError;
    ImageView ivGPSError;

    LineChart chart1;

    WebView wvEucWorld;
    WheelView wheelView;

    private LivemapService.LivemapStatus lastLivemapStatus = LivemapService.LivemapStatus.DISCONNECTED;
    private BluetoothLeService mBluetoothLeService;
    private BluetoothAdapter mBluetoothAdapter;
    private String mDeviceAddress;
    private int mConnectionState = BluetoothLeService.STATE_DISCONNECTED;
    private boolean doubleBackToExitPressedOnce = false;
    private Snackbar snackbar;
    int viewPagerPage = 0;
    private ArrayList<String> xAxis_labels = new ArrayList<>();
    private boolean use_mph = false;
    private GoogleApiClient mGoogleApiClient;
    private DrawerLayout mDrawer;
    private String mImagePath = "";
    private int imageUploadCount = 0;
    private WheelLogJSInterface jsInterface;

    protected static final int RESULT_DEVICE_SCAN_REQUEST = 20;
    protected static final int RESULT_REQUEST_ENABLE_BT = 30;
    protected static final int REQUEST_CODE_RESOLUTION = 40;
    protected static final int REQUEST_IMAGE_CAPTURE = 50;

    private final ServiceConnection mServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            mBluetoothLeService = ((BluetoothLeService.LocalBinder) service).getService();
            if (!mBluetoothLeService.initialize()) {
                Timber.e( getResources().getString(R.string.error_bluetooth_not_initialised));
                Toast.makeText(MainActivity.this, R.string.error_bluetooth_not_initialised, Toast.LENGTH_SHORT).show();
                finish();
            }

            if (BluetoothLeService.getConnectionState() == BluetoothLeService.STATE_DISCONNECTED &&
                    mDeviceAddress != null && !mDeviceAddress.isEmpty()) {
                mBluetoothLeService.setDeviceAddress(mDeviceAddress);
                toggleConnectToWheel();
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mBluetoothLeService = null;
            finish();
        }
    };

    private final BroadcastReceiver mBluetoothUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {

            switch (intent.getAction()) {
                case Constants.ACTION_BLUETOOTH_CONNECTION_STATE:
                    int connectionState = intent.getIntExtra(Constants.INTENT_EXTRA_CONNECTION_STATE, BluetoothLeService.STATE_DISCONNECTED);
                    Timber.i("Bluetooth state = %d", connectionState);
                    setConnectionState(connectionState);
                    break;
                case Constants.ACTION_WHEEL_DATA_AVAILABLE:
                    if (WheelData.getInstance().getWheelType() == WHEEL_TYPE.KINGSONG) {
                        if (WheelData.getInstance().getName().isEmpty())
                            sendBroadcast(new Intent(Constants.ACTION_REQUEST_KINGSONG_NAME_DATA));
                        else if (WheelData.getInstance().getSerial().isEmpty())
                            sendBroadcast(new Intent(Constants.ACTION_REQUEST_KINGSONG_SERIAL_DATA));
                    }
					if (intent.hasExtra(Constants.INTENT_EXTRA_WHEEL_SETTINGS)) {
						setWheelPreferences();						
					}
                    updateScreen(intent.hasExtra(Constants.INTENT_EXTRA_GRAPH_UPDATE_AVILABLE));
                    break;
                case Constants.ACTION_PEBBLE_SERVICE_TOGGLED:
                    setMenuIconStates();
                    break;
                case Constants.ACTION_SPEECH_SERVICE_TOGGLED:
                    setMenuIconStates();
                    break;
				//case Constants.ACTION_WHEEL_SETTING_CHANGED:
				//	if (intent.hasExtra(Constants.INTENT_EXTRA_WHEEL_REFRESH)) {
				//		setWheelPreferences();
				//	} 
				//	break;
                case Constants.ACTION_LOGGING_SERVICE_TOGGLED:
                    boolean running = intent.getBooleanExtra(Constants.INTENT_EXTRA_IS_RUNNING, false);
                    if (intent.hasExtra(Constants.INTENT_EXTRA_LOGGING_FILE_LOCATION)) {
                        String filepath = intent.getStringExtra(Constants.INTENT_EXTRA_LOGGING_FILE_LOCATION);
                        if (running)
                            showSnackBar(getResources().getString(R.string.started_logging, filepath), 5000);
                    }

                    setMenuIconStates();
                    break;
                case Constants.ACTION_PREFERENCE_CHANGED:
                    loadPreferences();
                    break;
				case Constants.ACTION_WHEEL_TYPE_RECOGNIZED:
					//System.out.println("WheelRecognizedMain");
                    String wheel_type = intent.getStringExtra(Constants.INTENT_EXTRA_WHEEL_TYPE);
                    //showSnackBar(getResources().getString(R.string.wheel_type_recognized, wheel_type), 5000);
					//((PreferencesFragment) getPreferencesFragment()).show_main_menu();
					break;
				case Constants.ACTION_ALARM_TRIGGERED:					
					int alarmType = ((ALARM_TYPE) intent.getSerializableExtra(Constants.INTENT_EXTRA_ALARM_TYPE)).getValue();
					if (alarmType == 0 ) {
						showSnackBar(getResources().getString(R.string.alarm_text_speed), 3000);						
					}
					if (alarmType == 1 ) {
						showSnackBar(getResources().getString(R.string.alarm_text_current), 3000);						
					}
					if (alarmType == 2 ) {
						showSnackBar(getResources().getString(R.string.alarm_text_temperature), 3000);						
					}
					break;
                case Constants.ACTION_LIVEMAP_STATUS:
                    updateLivemapUI();
                    break;
                case Constants.ACTION_LIVEMAP_LOCATION_UPDATED:
                    updateScreen(true);
                    break;
            }
        }
    };

    private void setConnectionState(int connectionState) {

        switch (connectionState) {
            case BluetoothLeService.STATE_CONNECTED:
                configureDisplay(WheelData.getInstance().getWheelType());
                if (mDeviceAddress != null && !mDeviceAddress.isEmpty())
                    SettingsUtil.setLastAddress(getApplicationContext(), mDeviceAddress);
                hideSnackBar();
                break;
            case BluetoothLeService.STATE_CONNECTING:
                if (mConnectionState == BluetoothLeService.STATE_CONNECTING)
                    showSnackBar(R.string.bluetooth_direct_connect_failed);
                else {
                    if (mBluetoothLeService.getDisconnectTime() != null) {
                        showSnackBar(
                                getString(R.string.connection_lost_at,
                                        new SimpleDateFormat("HH:mm:ss", Locale.US).format(mBluetoothLeService.getDisconnectTime())),
                                Snackbar.LENGTH_INDEFINITE);
                    }
                }
                break;
            case BluetoothLeService.STATE_DISCONNECTED:
                break;
        }
        mConnectionState = connectionState;
        setMenuIconStates();
    }

	private void setWheelPreferences() {
		Timber.i("SetWheelPreferences");
		((PreferencesFragment) getPreferencesFragment()).refreshWheelSettings(WheelData.getInstance().getWheelLight(), 
																				WheelData.getInstance().getWheelLed(), 
																				WheelData.getInstance().getWheelHandleButton(), 
																				WheelData.getInstance().getWheelMaxSpeed(), 
																				WheelData.getInstance().getSpeakerVolume(),
																				WheelData.getInstance().getPedalsPosition());
	}
	
    private void setMenuIconStates() {
        if (mMenu == null)
            return;

        if (mDeviceAddress == null || mDeviceAddress.isEmpty()) {
            miWheel.setEnabled(false);
            miWheel.getIcon().setAlpha(64);
        } else {
            miWheel.setEnabled(true);
            miWheel.getIcon().setAlpha(255);
        }

        switch (mConnectionState) {
            case BluetoothLeService.STATE_CONNECTED:
                miWheel.setIcon(R.drawable.ic_action_wheel_orange);
                miWheel.setTitle(R.string.disconnect_from_wheel);
                miSearch.setEnabled(false);
                miSearch.getIcon().setAlpha(64);
                break;
            case BluetoothLeService.STATE_CONNECTING:
                miWheel.setIcon(R.drawable.anim_wheel_icon);
                miWheel.setTitle(R.string.disconnect_from_wheel);
                ((AnimationDrawable) miWheel.getIcon()).start();
                miSearch.setEnabled(false);
                miSearch.getIcon().setAlpha(64);
                break;
            case BluetoothLeService.STATE_DISCONNECTED:
                miWheel.setIcon(R.drawable.ic_action_wheel_white);
                miWheel.setTitle(R.string.connect_to_wheel);
                miSearch.setEnabled(true);
                miSearch.getIcon().setAlpha(255);
                break;
        }

        if (PebbleService.isInstanceCreated()) {
            miWatch.setIcon(R.drawable.ic_action_watch_orange);
        } else {
            miWatch.setIcon(R.drawable.ic_action_watch_white);
        }

        if (LoggingService.isInstanceCreated()) {
            miLogging.setTitle(R.string.stop_data_service);
            miLogging.setIcon(R.drawable.ic_action_logging_orange);
        } else {
            miLogging.setTitle(R.string.start_data_service);
            miLogging.setIcon(R.drawable.ic_action_logging_white);
        }

        if (SpeechService.isInstanceCreated()) {
            miSpeech.setTitle(R.string.stop_speech_service);
            miSpeech.setIcon(R.drawable.ic_action_speech_orange);
        } else {
            miSpeech.setTitle(R.string.start_speech_service);
            miSpeech.setIcon(R.drawable.ic_action_speech_white);
        }

    }
    
    private void configureDisplay(WHEEL_TYPE wheelType) {
        TextView tvWaitText = (TextView) findViewById(R.id.tvWaitText);
        TextView tvTitleSpeed = (TextView) findViewById(R.id.tvTitleSpeed);
        TextView tvTitleMaxSpeed = (TextView) findViewById(R.id.tvTitleTopSpeed);
		TextView tvTitleAverageSpeed = (TextView) findViewById(R.id.tvTitleAverageSpeed);
		TextView tvTitleAverageRidingSpeed = (TextView) findViewById(R.id.tvTitleAverageRidingSpeed);
        TextView tvTitleBattery = (TextView) findViewById(R.id.tvTitleBattery);
        TextView tvTitleDistance = (TextView) findViewById(R.id.tvTitleDistance);
		TextView tvTitleWheelDistance = (TextView) findViewById(R.id.tvTitleWheelDistance);
		TextView tvTitleUserDistance = (TextView) findViewById(R.id.tvTitleUserDistance);
        TextView tvTitleRideTime = (TextView) findViewById(R.id.tvTitleRideTime);
		TextView tvTitleRidingTime = (TextView) findViewById(R.id.tvTitleRidingTime);
        TextView tvTitleVoltage = (TextView) findViewById(R.id.tvTitleVoltage);
        TextView tvTitleCurrent = (TextView) findViewById(R.id.tvTitleCurrent);
        TextView tvTitlePower = (TextView) findViewById(R.id.tvTitlePower);
        TextView tvTitleTemperature = (TextView) findViewById(R.id.tvTitleTemperature);
		TextView tvTitleTemperature2 = (TextView) findViewById(R.id.tvTitleTemperature2);
		TextView tvTitleAngle = (TextView) findViewById(R.id.tvTitleAngle);
		TextView tvTitleRoll = (TextView) findViewById(R.id.tvTitleRoll);
        TextView tvTitleFanStatus = (TextView) findViewById(R.id.tvTitleFanStatus);
        TextView tvTitleMode = (TextView) findViewById(R.id.tvTitleMode);
        TextView tvTitleTotalDistance = (TextView) findViewById(R.id.tvTitleTotalDistance);
        TextView tvTitleName = (TextView) findViewById(R.id.tvTitleName);
        TextView tvTitleModel = (TextView) findViewById(R.id.tvTitleModel);
        TextView tvTitleVersion = (TextView) findViewById(R.id.tvTitleVersion);
        TextView tvTitleSerial = (TextView) findViewById(R.id.tvTitleSerial);

        switch (wheelType) {
            case KINGSONG:
                tvWaitText.setVisibility(View.GONE);
                tvTitleSpeed.setVisibility(View.VISIBLE);
                tvSpeed.setVisibility(View.VISIBLE);
                tvTitleMaxSpeed.setVisibility(View.VISIBLE);
                tvTopSpeed.setVisibility(View.VISIBLE);
				tvTitleAverageSpeed.setVisibility(View.VISIBLE);
                tvAverageSpeed.setVisibility(View.VISIBLE);
				tvTitleAverageRidingSpeed.setVisibility(View.VISIBLE);
                tvAverageRidingSpeed.setVisibility(View.VISIBLE);
                tvTitleBattery.setVisibility(View.VISIBLE);
                tvBattery.setVisibility(View.VISIBLE);
                tvTitleDistance.setVisibility(View.VISIBLE);
                tvDistance.setVisibility(View.VISIBLE);
				tvTitleWheelDistance.setVisibility(View.VISIBLE);
                tvWheelDistance.setVisibility(View.VISIBLE);
				tvTitleUserDistance.setVisibility(View.VISIBLE);
                tvUserDistance.setVisibility(View.VISIBLE);
                tvTitleRideTime.setVisibility(View.VISIBLE);
                tvRideTime.setVisibility(View.VISIBLE);
				tvTitleRidingTime.setVisibility(View.VISIBLE);
                tvRidingTime.setVisibility(View.VISIBLE);
                tvTitleVoltage.setVisibility(View.VISIBLE);
                tvVoltage.setVisibility(View.VISIBLE);
                tvTitleCurrent.setVisibility(View.VISIBLE);
                tvCurrent.setVisibility(View.VISIBLE);
                tvTitlePower.setVisibility(View.VISIBLE);
                tvPower.setVisibility(View.VISIBLE);
                tvTitleTemperature.setVisibility(View.VISIBLE);
                tvTemperature.setVisibility(View.VISIBLE);
                tvTitleTemperature2.setVisibility(View.VISIBLE);
                tvTemperature2.setVisibility(View.VISIBLE);
                tvTitleFanStatus.setVisibility(View.VISIBLE);
                tvFanStatus.setVisibility(View.VISIBLE);
                tvTitleMode.setVisibility(View.VISIBLE);
                tvMode.setVisibility(View.VISIBLE);
                tvTitleTotalDistance.setVisibility(View.VISIBLE);
                tvTotalDistance.setVisibility(View.VISIBLE);
                tvTitleName.setVisibility(View.VISIBLE);
                tvName.setVisibility(View.VISIBLE);
                tvTitleModel.setVisibility(View.VISIBLE);
                tvModel.setVisibility(View.VISIBLE);
                tvTitleVersion.setVisibility(View.VISIBLE);
                tvVersion.setVisibility(View.VISIBLE);
                tvTitleSerial.setVisibility(View.VISIBLE);
                tvSerial.setVisibility(View.VISIBLE);
                break;
            case GOTWAY:
                tvWaitText.setVisibility(View.GONE);
                tvTitleSpeed.setVisibility(View.VISIBLE);
                tvSpeed.setVisibility(View.VISIBLE);
                tvTitleMaxSpeed.setVisibility(View.VISIBLE);
                tvTopSpeed.setVisibility(View.VISIBLE);
				tvTitleAverageSpeed.setVisibility(View.VISIBLE);
                tvAverageSpeed.setVisibility(View.VISIBLE);
				tvTitleAverageRidingSpeed.setVisibility(View.VISIBLE);
                tvAverageRidingSpeed.setVisibility(View.VISIBLE);
                tvTitleBattery.setVisibility(View.VISIBLE);
                tvBattery.setVisibility(View.VISIBLE);
                tvTitleDistance.setVisibility(View.VISIBLE);
                tvDistance.setVisibility(View.VISIBLE);
				tvTitleWheelDistance.setVisibility(View.VISIBLE);
                tvWheelDistance.setVisibility(View.VISIBLE);
				tvTitleUserDistance.setVisibility(View.VISIBLE);
                tvUserDistance.setVisibility(View.VISIBLE);
                tvTitleRideTime.setVisibility(View.VISIBLE);
                tvRideTime.setVisibility(View.VISIBLE);
				tvTitleRidingTime.setVisibility(View.VISIBLE);
                tvRidingTime.setVisibility(View.VISIBLE);
                tvTitleVoltage.setVisibility(View.VISIBLE);
                tvVoltage.setVisibility(View.VISIBLE);
                tvTitleCurrent.setVisibility(View.VISIBLE);
                tvCurrent.setVisibility(View.VISIBLE);
                tvTitlePower.setVisibility(View.VISIBLE);
                tvPower.setVisibility(View.VISIBLE);
                tvTitleTemperature.setVisibility(View.VISIBLE);
                tvTemperature.setVisibility(View.VISIBLE);
                tvTitleTotalDistance.setVisibility(View.VISIBLE);
                tvTotalDistance.setVisibility(View.VISIBLE);
                break;
            case INMOTION:
                tvWaitText.setVisibility(View.GONE);
                tvTitleSpeed.setVisibility(View.VISIBLE);
                tvSpeed.setVisibility(View.VISIBLE);
                tvTitleMaxSpeed.setVisibility(View.VISIBLE);
                tvTopSpeed.setVisibility(View.VISIBLE);
				tvTitleAverageSpeed.setVisibility(View.VISIBLE);
                tvAverageSpeed.setVisibility(View.VISIBLE);
				tvTitleAverageRidingSpeed.setVisibility(View.VISIBLE);
                tvAverageRidingSpeed.setVisibility(View.VISIBLE);
                tvTitleBattery.setVisibility(View.VISIBLE);
                tvBattery.setVisibility(View.VISIBLE);
                tvTitleDistance.setVisibility(View.VISIBLE);
                tvDistance.setVisibility(View.VISIBLE);
				tvTitleUserDistance.setVisibility(View.VISIBLE);
                tvUserDistance.setVisibility(View.VISIBLE);
                tvTitleRideTime.setVisibility(View.VISIBLE);
                tvRideTime.setVisibility(View.VISIBLE);
				tvTitleRidingTime.setVisibility(View.VISIBLE);
                tvRidingTime.setVisibility(View.VISIBLE);
                tvTitleVoltage.setVisibility(View.VISIBLE);
                tvVoltage.setVisibility(View.VISIBLE);
                tvTitleCurrent.setVisibility(View.VISIBLE);
                tvCurrent.setVisibility(View.VISIBLE);
                tvTitlePower.setVisibility(View.VISIBLE);
                tvPower.setVisibility(View.VISIBLE);
                tvTitleTemperature.setVisibility(View.VISIBLE);
                tvTemperature.setVisibility(View.VISIBLE);
				tvTitleTemperature2.setVisibility(View.VISIBLE);
                tvTemperature2.setVisibility(View.VISIBLE);
				tvTitleMode.setVisibility(View.VISIBLE);
                tvMode.setVisibility(View.VISIBLE);
				tvTitleAngle.setVisibility(View.VISIBLE);
                tvAngle.setVisibility(View.VISIBLE);
				tvTitleRoll.setVisibility(View.VISIBLE);
                tvRoll.setVisibility(View.VISIBLE);
                tvTitleTotalDistance.setVisibility(View.VISIBLE);
                tvTotalDistance.setVisibility(View.VISIBLE);
				tvTitleModel.setVisibility(View.VISIBLE);
                tvModel.setVisibility(View.VISIBLE);
                tvTitleVersion.setVisibility(View.VISIBLE);
                tvVersion.setVisibility(View.VISIBLE);
                tvTitleSerial.setVisibility(View.VISIBLE);
                tvSerial.setVisibility(View.VISIBLE);
                break;

            case NINEBOT_Z:
                tvWaitText.setVisibility(View.GONE);
                tvTitleSpeed.setVisibility(View.VISIBLE);
                tvSpeed.setVisibility(View.VISIBLE);
                tvTitleMaxSpeed.setVisibility(View.VISIBLE);
                tvTopSpeed.setVisibility(View.VISIBLE);
                tvTitleAverageSpeed.setVisibility(View.VISIBLE);
                tvAverageSpeed.setVisibility(View.VISIBLE);
                tvTitleAverageRidingSpeed.setVisibility(View.VISIBLE);
                tvAverageRidingSpeed.setVisibility(View.VISIBLE);
                tvTitleBattery.setVisibility(View.VISIBLE);
                tvBattery.setVisibility(View.VISIBLE);
                tvTitleDistance.setVisibility(View.VISIBLE);
                tvDistance.setVisibility(View.VISIBLE);
                tvTitleUserDistance.setVisibility(View.VISIBLE);
                tvUserDistance.setVisibility(View.VISIBLE);
                tvTitleRideTime.setVisibility(View.VISIBLE);
                tvRideTime.setVisibility(View.VISIBLE);
                tvTitleRidingTime.setVisibility(View.VISIBLE);
                tvRidingTime.setVisibility(View.VISIBLE);
                tvTitleVoltage.setVisibility(View.VISIBLE);
                tvVoltage.setVisibility(View.VISIBLE);
                tvTitleCurrent.setVisibility(View.VISIBLE);
                tvCurrent.setVisibility(View.VISIBLE);
                tvTitlePower.setVisibility(View.VISIBLE);
                tvPower.setVisibility(View.VISIBLE);
                tvTitleTemperature.setVisibility(View.VISIBLE);
                tvTemperature.setVisibility(View.VISIBLE);
                tvTitleTemperature2.setVisibility(View.GONE);
                tvTemperature2.setVisibility(View.GONE);
                tvTitleMode.setVisibility(View.GONE);
                tvMode.setVisibility(View.GONE);
                tvTitleAngle.setVisibility(View.GONE);
                tvAngle.setVisibility(View.GONE);
                tvTitleRoll.setVisibility(View.GONE);
                tvRoll.setVisibility(View.GONE);
                tvTitleTotalDistance.setVisibility(View.VISIBLE);
                tvTotalDistance.setVisibility(View.VISIBLE);
                tvTitleModel.setVisibility(View.VISIBLE);
                tvModel.setVisibility(View.VISIBLE);
                tvTitleVersion.setVisibility(View.VISIBLE);
                tvVersion.setVisibility(View.VISIBLE);
                tvTitleSerial.setVisibility(View.VISIBLE);
                tvSerial.setVisibility(View.VISIBLE);
                break;
            default:
                tvWaitText.setVisibility(View.VISIBLE);
                tvTitleSpeed.setVisibility(View.GONE);
                tvSpeed.setVisibility(View.GONE);
                tvTitleMaxSpeed.setVisibility(View.GONE);
                tvTopSpeed.setVisibility(View.GONE);
				tvTitleAverageSpeed.setVisibility(View.GONE);
                tvAverageSpeed.setVisibility(View.GONE);
				tvTitleAverageRidingSpeed.setVisibility(View.GONE);
                tvAverageRidingSpeed.setVisibility(View.GONE);
                tvTitleBattery.setVisibility(View.GONE);
                tvBattery.setVisibility(View.GONE);
                tvTitleDistance.setVisibility(View.GONE);
                tvDistance.setVisibility(View.GONE);
				tvTitleWheelDistance.setVisibility(View.GONE);
                tvWheelDistance.setVisibility(View.GONE);
				tvTitleUserDistance.setVisibility(View.GONE);
                tvUserDistance.setVisibility(View.GONE);
                tvTitleRideTime.setVisibility(View.GONE);
                tvRideTime.setVisibility(View.GONE);
				tvTitleRidingTime.setVisibility(View.GONE);
                tvRidingTime.setVisibility(View.GONE);
                tvTitleVoltage.setVisibility(View.GONE);
                tvVoltage.setVisibility(View.GONE);
                tvTitleCurrent.setVisibility(View.GONE);
                tvCurrent.setVisibility(View.GONE);
                tvTitlePower.setVisibility(View.GONE);
                tvPower.setVisibility(View.GONE);
                tvTitleTemperature.setVisibility(View.GONE);
                tvTemperature.setVisibility(View.GONE);
				tvTitleTemperature2.setVisibility(View.GONE);
                tvTemperature2.setVisibility(View.GONE);
				tvTitleAngle.setVisibility(View.GONE);
                tvAngle.setVisibility(View.GONE);
				tvTitleRoll.setVisibility(View.GONE);
                tvRoll.setVisibility(View.GONE);
                tvTitleFanStatus.setVisibility(View.GONE);
                tvFanStatus.setVisibility(View.GONE);
                tvTitleMode.setVisibility(View.GONE);
                tvMode.setVisibility(View.GONE);
                tvTitleTotalDistance.setVisibility(View.GONE);
                tvTotalDistance.setVisibility(View.GONE);
                tvTitleName.setVisibility(View.GONE);
                tvName.setVisibility(View.GONE);
                tvTitleModel.setVisibility(View.GONE);
                tvModel.setVisibility(View.GONE);
                tvTitleVersion.setVisibility(View.GONE);
                tvVersion.setVisibility(View.GONE);
                tvTitleSerial.setVisibility(View.GONE);
                tvSerial.setVisibility(View.GONE);
                break;
        }
    }

    private void updateScreen(boolean updateGraph) {
        switch (viewPagerPage) {
            case 0: // GUI View
                updateWheelView();
                break;
            case 1: // Text View
                if (use_mph) {
                    tvSpeed.setText(String.format(Locale.US, "%.1f mph", kmToMiles(WheelData.getInstance().getSpeedDouble())));
                    tvTopSpeed.setText(String.format(Locale.US, "%.1f mph", kmToMiles(WheelData.getInstance().getTopSpeedDouble())));
					tvAverageSpeed.setText(String.format(Locale.US, "%.1f mph", kmToMiles(WheelData.getInstance().getAverageSpeedDouble())));
					tvAverageRidingSpeed.setText(String.format(Locale.US, "%.1f mph", kmToMiles(WheelData.getInstance().getAverageRidingSpeedDouble())));
                    tvDistance.setText(String.format(Locale.US, "%.2f mi", kmToMiles(WheelData.getInstance().getDistanceDouble())));
					tvWheelDistance.setText(String.format(Locale.US, "%.2f mi", kmToMiles(WheelData.getInstance().getWheelDistanceDouble())));
					tvUserDistance.setText(String.format(Locale.US, "%.2f mi", kmToMiles(WheelData.getInstance().getUserDistanceDouble())));
                    tvTotalDistance.setText(String.format(Locale.US, "%.2f mi", kmToMiles(WheelData.getInstance().getTotalDistanceDouble())));
                } else {
                    tvSpeed.setText(String.format(Locale.US, "%.1f km/h", WheelData.getInstance().getSpeedDouble()));
                    tvTopSpeed.setText(String.format(Locale.US, "%.1f km/h", WheelData.getInstance().getTopSpeedDouble()));
					tvAverageSpeed.setText(String.format(Locale.US, "%.1f km/h", WheelData.getInstance().getAverageSpeedDouble()));
					tvAverageRidingSpeed.setText(String.format(Locale.US, "%.1f km/h", WheelData.getInstance().getAverageRidingSpeedDouble()));
                    tvDistance.setText(String.format(Locale.US, "%.3f km", WheelData.getInstance().getDistanceDouble()));
					tvWheelDistance.setText(String.format(Locale.US, "%.3f km", WheelData.getInstance().getWheelDistanceDouble()));
					tvUserDistance.setText(String.format(Locale.US, "%.3f km", WheelData.getInstance().getUserDistanceDouble()));
                    tvTotalDistance.setText(String.format(Locale.US, "%.3f km", WheelData.getInstance().getTotalDistanceDouble()));
                }

                tvVoltage.setText(String.format(Locale.US, "%.2fV", WheelData.getInstance().getVoltageDouble()));
                tvTemperature.setText(String.format(Locale.US, "%d°C", WheelData.getInstance().getTemperature()));
				tvTemperature2.setText(String.format(Locale.US, "%d°C", WheelData.getInstance().getTemperature2()));
				tvAngle.setText(String.format(Locale.US, "%.2f°", WheelData.getInstance().getAngle()));
				tvRoll.setText(String.format(Locale.US, "%.2f°", WheelData.getInstance().getRoll()));
                tvCurrent.setText(String.format(Locale.US, "%.2fA", WheelData.getInstance().getCurrentDouble()));
                tvPower.setText(String.format(Locale.US, "%.2fW", WheelData.getInstance().getPowerDouble()));
                tvBattery.setText(String.format(Locale.US, "%d%%", WheelData.getInstance().getBatteryLevel()));
                tvFanStatus.setText(WheelData.getInstance().getFanStatus() == 0 ? getString(R.string.fan_off) : getString(R.string.fan_on));
                tvVersion.setText(String.format(Locale.US, "%s", WheelData.getInstance().getVersion()));
                tvName.setText(WheelData.getInstance().getName());
                tvModel.setText(WheelData.getInstance().getModel());
                tvSerial.setText(WheelData.getInstance().getSerial());
                tvRideTime.setText(WheelData.getInstance().getRideTimeString());
				tvRidingTime.setText(WheelData.getInstance().getRidingTimeString());
                tvMode.setText(WheelData.getInstance().getModeStr());
                break;
            case 2: // Graph  View
                if (updateGraph) {
                    xAxis_labels = WheelData.getInstance().getXAxis();

                    if (xAxis_labels.size() > 0) {

                        LineDataSet dataSetSpeed;
                        LineDataSet dataSetCurrent;

                        if (chart1.getData() == null) {
                            dataSetSpeed = new LineDataSet(null, getString(R.string.speed));
                            dataSetCurrent = new LineDataSet(null, getString(R.string.current));
                            dataSetSpeed.setLineWidth(2);
                            dataSetCurrent.setLineWidth(2);
                            dataSetSpeed.setAxisDependency(YAxis.AxisDependency.LEFT);
                            dataSetCurrent.setAxisDependency(YAxis.AxisDependency.RIGHT);
                            dataSetSpeed.setMode(LineDataSet.Mode.CUBIC_BEZIER);
                            dataSetCurrent.setMode(LineDataSet.Mode.CUBIC_BEZIER);
                            dataSetSpeed.setColor(getResources().getColor(android.R.color.white));
                            dataSetCurrent.setColor(getResources().getColor(R.color.accent));
                            dataSetSpeed.setDrawCircles(false);
                            dataSetCurrent.setDrawCircles(false);
                            dataSetSpeed.setDrawValues(false);
                            dataSetCurrent.setDrawValues(false);
                            LineData chart1_lineData = new LineData();
                            chart1_lineData.addDataSet(dataSetCurrent);
                            chart1_lineData.addDataSet(dataSetSpeed);
                            chart1.setData(chart1_lineData);
                            findViewById(R.id.leftAxisLabel).setVisibility(View.VISIBLE);
                            findViewById(R.id.rightAxisLabel).setVisibility(View.VISIBLE);
                        } else {
                            dataSetSpeed = (LineDataSet) chart1.getData().getDataSetByLabel(getString(R.string.speed), true);
                            dataSetCurrent = (LineDataSet) chart1.getData().getDataSetByLabel(getString(R.string.current), true);
                        }

                        dataSetSpeed.clear();
                        dataSetCurrent.clear();

                        ArrayList<Float> currentAxis = new ArrayList<>(WheelData.getInstance().getCurrentAxis());
                        ArrayList<Float> speedAxis = new ArrayList<>(WheelData.getInstance().getSpeedAxis());

                        for (Float d : currentAxis) {
                            float value = 0;
                            if (d != null)
                                value = d;

                            dataSetCurrent.addEntry(new Entry(dataSetCurrent.getEntryCount(), value));
                        }

                        for (Float d : speedAxis)
                        {
                            float value = 0;

                            if (d != null)
                                value = d;

                            if (use_mph)
                                dataSetSpeed.addEntry(new Entry(dataSetSpeed.getEntryCount(), kmToMiles(value)));
                            else
                                dataSetSpeed.addEntry(new Entry(dataSetSpeed.getEntryCount(), value));
                        }

                        dataSetCurrent.notifyDataSetChanged();
                        dataSetSpeed.notifyDataSetChanged();
                        chart1.getData().notifyDataChanged();
                        chart1.notifyDataSetChanged();
                        chart1.invalidate();
                        break;
                    }
                }
                break;
            case 3: // Livemap View
                break;
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        updateScreen(true);
    }

    @Override @SuppressLint("SetJavaScriptEnabled")
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        WheelData.initiate(this);

        FlicManager.setAppCredentials("1aa3a357-feb8-4da2-bbe6-f69d418420c9", "cac07edc-18bf-47d5-9e6e-169c41cb00bf", "WheelLog");

        getFragmentManager().beginTransaction()
                .replace(R.id.settings_frame, getPreferencesFragment(), Constants.PREFERENCES_FRAGMENT_TAG)
                .commit();

        ViewPageAdapter adapter = new ViewPageAdapter(this);
        ViewPager pager = (ViewPager) findViewById(R.id.pager);
        pager.setAdapter(adapter);
        pager.setOffscreenPageLimit(4);

        LinePageIndicator titleIndicator = (LinePageIndicator)findViewById(R.id.indicator);
        titleIndicator.setViewPager(pager);
        pager.addOnPageChangeListener(pageChangeListener);

        mDeviceAddress = SettingsUtil.getLastAddress(getApplicationContext());
        final Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        tvSpeed = (TextView) findViewById(R.id.tvSpeed);
        tvCurrent = (TextView) findViewById(R.id.tvCurrent);
        tvPower = (TextView) findViewById(R.id.tvPower);
        tvTemperature = (TextView) findViewById(R.id.tvTemperature);
		tvTemperature2 = (TextView) findViewById(R.id.tvTemperature2);
		tvAngle = (TextView) findViewById(R.id.tvAngle);
		tvRoll = (TextView) findViewById(R.id.tvRoll);
        tvVoltage = (TextView) findViewById(R.id.tvVoltage);
        tvBattery = (TextView) findViewById(R.id.tvBattery);
        tvFanStatus = (TextView) findViewById(R.id.tvFanStatus);
        tvTopSpeed = (TextView) findViewById(R.id.tvTopSpeed);
		tvAverageSpeed = (TextView) findViewById(R.id.tvAverageSpeed);
		tvAverageRidingSpeed = (TextView) findViewById(R.id.tvAverageRidingSpeed);
        tvDistance = (TextView) findViewById(R.id.tvDistance);
		tvWheelDistance = (TextView) findViewById(R.id.tvWheelDistance);
		tvUserDistance = (TextView) findViewById(R.id.tvUserDistance);
        tvTotalDistance = (TextView) findViewById(R.id.tvTotalDistance);
        tvModel = (TextView) findViewById(R.id.tvModel);
        tvName = (TextView) findViewById(R.id.tvName);
        tvVersion = (TextView) findViewById(R.id.tvVersion);
        tvSerial = (TextView) findViewById(R.id.tvSerial);
        tvRideTime = (TextView) findViewById(R.id.tvRideTime);
		tvRidingTime = (TextView) findViewById(R.id.tvRidingTime);
        tvMode = (TextView) findViewById(R.id.tvMode);
        wheelView = (WheelView) findViewById(R.id.wheelView);
        mDrawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        tvLivemapStatus = findViewById(R.id.tvLivemapStatus);
        ibLivemapStartFinish = findViewById(R.id.ibLivemapStartFinish);
        ibLivemapPause = findViewById(R.id.ibLivemapPause);
        ibLivemapPhoto = findViewById(R.id.ibLivemapPhoto);
        ibLivemapShare = findViewById(R.id.ibLivemapShare);
        ivUploadProgressWarning = findViewById(R.id.ivLivemapUploadProgressWarning);
        ivLivemapError = findViewById(R.id.ivLivemapError);
        ivGPSError = findViewById(R.id.ivLivemapGPSError);

        jsInterface = new WheelLogJSInterface(this);

        wvEucWorld = findViewById(R.id.wvEucWorld);
        wvEucWorld.setWebViewClient(new WebViewClient() {
            public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                wvEucWorld.loadData(Constants.EMPTY_HTML, "text/html", "UTF-8");
            }
        });
        wvEucWorld.getSettings().setJavaScriptEnabled(true);
        wvEucWorld.addJavascriptInterface(jsInterface, "WheelLog");
        wvEucWorld.loadData(Constants.EMPTY_HTML, "text/html", "UTF-8");

        mDrawer.addDrawerListener(new DrawerLayout.DrawerListener() {
            @Override
            public void onDrawerSlide(View drawerView, float slideOffset) {
            }

            @Override
            public void onDrawerOpened(View drawerView) {
            }

            @Override
            public void onDrawerClosed(View drawerView) {
                ((PreferencesFragment) getPreferencesFragment()).show_main_menu();
            }

            @Override
            public void onDrawerStateChanged(int newState) {
            }
        });

        ibLivemapStartFinish.setOnClickListener(new ImageButton.OnClickListener() {
            public void onClick(View v)  {
            if (LivemapService.getStatus() == LivemapService.LivemapStatus.DISCONNECTED)
                tvLivemapStatus.setText(getString(R.string.livemap_connecting));
            else
                tvLivemapStatus.setText(getString(R.string.livemap_disconnecting));
            setBtnState(ibLivemapStartFinish, false, false);
            setBtnState(ibLivemapPause, false, false);
            MainActivityPermissionsDispatcher.toggleLivemapServiceWithCheck(MainActivity.this);
            }
        });

        ibLivemapPause.setOnClickListener(new ImageButton.OnClickListener() {
            public void onClick(View v)  {
                setBtnState(ibLivemapPause, false, false);
                if (LivemapService.getStatus() == LivemapService.LivemapStatus.STARTED)
                    sendBroadcast(new Intent(Constants.ACTION_LIVEMAP_PAUSE));
                else
                    sendBroadcast(new Intent(Constants.ACTION_LIVEMAP_RESUME));
            }
        });

        ibLivemapPhoto.setOnClickListener(new ImageButton.OnClickListener() {
            public void onClick(View v)  {
                MainActivityPermissionsDispatcher.imageCaptureWithCheck(MainActivity.this);
            }
        });

        ibLivemapShare.setOnClickListener(new ImageButton.OnClickListener() {
            public void onClick(View v)  {
                shareLivemapUrl();
            }
        });

        Typeface tf = Typeface.create("sans-serif-condensed", Typeface.NORMAL);
        TextClock textClock = (TextClock) findViewById(R.id.textClock);
        TextView tvWaitText = (TextView) findViewById(R.id.tvWaitText);
        textClock.setTypeface(tf);
        tvWaitText.setTypeface(tf);
        tvLivemapStatus.setTypeface(tf);

        chart1 = (LineChart) findViewById(R.id.chart);
        chart1.setDrawGridBackground(false);
        chart1.getDescription().setEnabled(false);
        chart1.setHardwareAccelerationEnabled(true);
        chart1.setHighlightPerTapEnabled(false);
        chart1.setHighlightPerDragEnabled(false);
        chart1.getLegend().setTextColor(getResources().getColor(android.R.color.white));
        chart1.setNoDataTextColor(getResources().getColor(android.R.color.white));
        chart1.setNoDataText(getResources().getString(R.string.waiting_for_wheel));
        chart1.setNoDataTextTypeface(tf);

        YAxis leftAxis = chart1.getAxisLeft();
        YAxis rightAxis = chart1.getAxisRight();
        leftAxis.setAxisMinValue(0f);
        rightAxis.setAxisMinValue(0f);
        leftAxis.setDrawGridLines(false);
        rightAxis.setDrawGridLines(false);
        leftAxis.setTextColor(getResources().getColor(android.R.color.white));
        rightAxis.setTextColor(getResources().getColor(android.R.color.white));

        XAxis xAxis = chart1.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setTextColor(getResources().getColor(android.R.color.white));
        xAxis.setValueFormatter(chartAxisValueFormatter);

        loadPreferences();

        if (SettingsUtil.isFirstRun(this)) {
            new Handler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    mDrawer.openDrawer(GravityCompat.START, true);
                }}, 1000);
        }

        // Use this check to determine whether BLE is supported on the device.  Then you can
        // selectively disable BLE-related features.
        /*
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(this, R.string.ble_not_supported, Toast.LENGTH_SHORT).show();
            finish();
        }
        */

        // Initializes a Bluetooth adapter.  For API level 18 and above, get a reference to
        // BluetoothAdapter through BluetoothManager.
        BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        if (bluetoothManager != null) mBluetoothAdapter = bluetoothManager.getAdapter();

        // Checks if Bluetooth is supported on the device.
        if (mBluetoothAdapter == null) {
            Toast.makeText(this, R.string.error_bluetooth_not_supported, Toast.LENGTH_SHORT).show();
            //finish();
        } else if (!mBluetoothAdapter.isEnabled()) {
            // Ensures Bluetooth is enabled on the device.  If Bluetooth is not currently enabled,
            // fire an intent to display a dialog asking the user to grant permission to enable it.
            if (!mBluetoothAdapter.isEnabled())
                startActivityForResult(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE), RESULT_REQUEST_ENABLE_BT);
        } else {
            startBluetoothService();
        }

        // Enable voice messages on startup
        if (SettingsUtil.isSpeechEnabledOnStartup(this))
            startSpeechService();

        // Check for updates
        checkForUpdatesOnStartup();
    }

    @Override
    public void onResume() {
        super.onResume();

        if (SettingsUtil.isAutoUploadEnabled(this))
            getGoogleApiClient().connect();

        if (mBluetoothLeService != null &&
                mConnectionState != BluetoothLeService.getConnectionState())
            setConnectionState(BluetoothLeService.getConnectionState());

        if (WheelData.getInstance().getWheelType() != WHEEL_TYPE.Unknown)
            configureDisplay(WheelData.getInstance().getWheelType());

        registerReceiver(mBluetoothUpdateReceiver, makeIntentFilter());
        updateScreen(true);
        updateLivemapUI();
    }

    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        setMenuIconStates();
    }

    @Override
    public void onPause() {
        super.onPause();
        unregisterReceiver(mBluetoothUpdateReceiver);
		//cationUtil.getInstance().unregisterReceiver();
        if (SettingsUtil.isAutoUploadEnabled(this))
            getGoogleApiClient().disconnect();
    }

    @Override
    protected void onDestroy() {
        stopLivemapService();
        stopPebbleService();
        stopLoggingService();
        stopSpeechService();
        WheelData.getInstance().full_reset(false);
        WheelData.getInstance().destroy();
        if (mBluetoothLeService != null) {
            unbindService(mServiceConnection);
            stopService(new Intent(getApplicationContext(), BluetoothLeService.class));
            mBluetoothLeService = null;
        }
        super.onDestroy();
        //
        new Timer().scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                // Wait until livemap finish gracefully
                if (LivemapService.getStatus() == LivemapService.LivemapStatus.DISCONNECTED)
                    android.os.Process.killProcess(android.os.Process.myPid());
            }
        }, 0, 100);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        mMenu = menu;
        miSearch = mMenu.findItem(R.id.miSearch);
        miWheel = mMenu.findItem(R.id.miWheel);
        miWatch = mMenu.findItem(R.id.miWatch);
        miLogging = mMenu.findItem(R.id.miLogging);
        miSpeech = mMenu.findItem(R.id.miSpeech);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.miSearch:
                MainActivityPermissionsDispatcher.startScanActivityWithCheck(this);
                return true;
            case R.id.miWheel:
                toggleConnectToWheel();
                return true;
            case R.id.miLogging:
                MainActivityPermissionsDispatcher.toggleLoggingServiceWithCheck(this);
                return true;
            case R.id.miWatch:
                togglePebbleService();
                return true;
            case R.id.miSpeech:
                MainActivityPermissionsDispatcher.toggleSpeechServiceWithCheck(this);
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    public boolean onKeyUp(int keyCode, KeyEvent event) {
        View settings_layout = findViewById(R.id.settings_layout);
        switch (keyCode) {
            case KeyEvent.KEYCODE_MENU:
                if (mDrawer.isDrawerOpen(settings_layout)) {
                    mDrawer.closeDrawers();
                } else {
                    mDrawer.openDrawer(GravityCompat.START, true);
                }
                return true;
            case KeyEvent.KEYCODE_BACK:
                if (mDrawer.isDrawerOpen(settings_layout)) {
                    if (((PreferencesFragment) getPreferencesFragment()).is_main_menu())
                        mDrawer.closeDrawer(GravityCompat.START, true);
					else
                    if (((PreferencesFragment) getPreferencesFragment()).is_speech_messages_menu())
                        ((PreferencesFragment) getPreferencesFragment()).show_speech_menu();
                    else
					    ((PreferencesFragment) getPreferencesFragment()).show_main_menu();
                } else {
                    if (doubleBackToExitPressedOnce) {
                        finish();
                        return true;
                    }

                    doubleBackToExitPressedOnce = true;
                    showSnackBar(R.string.back_to_exit);

                    new Handler().postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            doubleBackToExitPressedOnce = false;
                        }
                    }, 2000);
                }
                return true;
            default:
                return super.onKeyDown(keyCode, event);
        }
    }

    ViewPager.SimpleOnPageChangeListener pageChangeListener = new ViewPager.SimpleOnPageChangeListener(){
        @Override
        public void onPageSelected(int position) {
            super.onPageSelected(position);
            viewPagerPage = position;
            updateScreen(true);
        }
    };

    private void loadPreferences() {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
        setMainActivityLockBehaviour();
        use_mph = sharedPreferences.getBoolean(getString(R.string.use_mi), false);
        int max_speed = sharedPreferences.getInt(getString(R.string.max_speed), 30) * 10;
        wheelView.setMaxSpeed(max_speed);
        wheelView.setUseMPH(use_mph);
        wheelView.invalidate();

        boolean alarms_enabled = sharedPreferences.getBoolean(getString(R.string.alarms_enabled), false);
		boolean use_ratio = sharedPreferences.getBoolean(getString(R.string.use_ratio), false);
		WheelData.getInstance().setUseRatio(use_ratio);

        int gotway_voltage = Integer.parseInt(sharedPreferences.getString(getString(R.string.gotway_voltage), "0"));
        WheelData.getInstance().setGotwayVoltage(gotway_voltage);

        //boolean gotway_84v = sharedPreferences.getBoolean(getString(R.string.gotway_84v), false);
        //WheelData.getInstance().setGotway84V(gotway_84v);
		WheelData.getInstance().setAlarmsEnabled(alarms_enabled);

        if (alarms_enabled) {
            int alarm1Speed = sharedPreferences.getInt(getString(R.string.alarm_1_speed), 0);
            int alarm2Speed = sharedPreferences.getInt(getString(R.string.alarm_2_speed), 0);
            int alarm3Speed = sharedPreferences.getInt(getString(R.string.alarm_3_speed), 0);
            int alarm1Battery = sharedPreferences.getInt(getString(R.string.alarm_1_battery), 0);
            int alarm2Battery = sharedPreferences.getInt(getString(R.string.alarm_2_battery), 0);
            int alarm3Battery = sharedPreferences.getInt(getString(R.string.alarm_3_battery), 0);
            int current_peak_alarm = sharedPreferences.getInt(getString(R.string.alarm_current), 0);
            int current_sustained_alarm = sharedPreferences.getInt(getString(R.string.alarm_current_sustained), 0);
			int temperature_alarm = sharedPreferences.getInt(getString(R.string.alarm_temperature), 0);
            boolean disablePhoneVibrate = sharedPreferences.getBoolean(getString(R.string.disable_phone_vibrate), false);

            WheelData.getInstance().setPreferences(
                    alarm1Speed, alarm1Battery,
                    alarm2Speed, alarm2Battery,
                    alarm3Speed, alarm3Battery,
                    current_peak_alarm, current_sustained_alarm, temperature_alarm, disablePhoneVibrate);
            wheelView.setWarningSpeed(alarm1Speed);
        } else
            wheelView.setWarningSpeed(0);

        boolean auto_log = sharedPreferences.getBoolean(getString(R.string.auto_log), false);
        boolean log_location = sharedPreferences.getBoolean(getString(R.string.log_location_data), false);
        boolean auto_upload = sharedPreferences.getBoolean(getString(R.string.auto_upload), false);

        if (auto_log)
            MainActivityPermissionsDispatcher.acquireStoragePermissionWithCheck(this);

        if (log_location)
            MainActivityPermissionsDispatcher.acquireLocationPermissionWithCheck(this);

        if (auto_upload)
            getGoogleApiClient().connect();

        updateScreen(true);
    }

    @NeedsPermission({Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE})
    void acquireStoragePermission() {}

    @NeedsPermission(Manifest.permission.ACCESS_FINE_LOCATION)
    void acquireLocationPermission() {}

    @OnPermissionDenied({Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE})
    void storagePermissionDenied() {
        SettingsUtil.setAutoLog(this, false);
        ((PreferencesFragment) getPreferencesFragment()).refreshVolatileSettings();
    }

    @OnPermissionDenied(Manifest.permission.ACCESS_FINE_LOCATION)
    void locationPermissionDenied() {
        SettingsUtil.setLogLocationEnabled(this, false);
        ((PreferencesFragment) getPreferencesFragment()).refreshVolatileSettings();
    }

    private void showSnackBar(int msg) { showSnackBar(getString(msg)); }
    private void showSnackBar(String msg) { showSnackBar(msg, 2000); }
    private void showSnackBar(String msg, int timeout) {
        if (snackbar == null) {
            View mainView = findViewById(R.id.main_view);
            snackbar = Snackbar
                    .make(mainView, "", Snackbar.LENGTH_LONG);
            snackbar.getView().setBackgroundResource(R.color.primary_dark);
            snackbar.setAction(android.R.string.ok, new View.OnClickListener() {
                @Override
                public void onClick(View view) {}
            });
        }
        snackbar.setDuration(timeout);
        snackbar.setText(msg);
        snackbar.show();
    }

    private void hideSnackBar() {
        if (snackbar == null)
            return;

        snackbar.dismiss();
    }

    private void stopLoggingService() {
        if (LoggingService.isInstanceCreated())
            toggleLoggingService();
    }

    @NeedsPermission({Manifest.permission.READ_EXTERNAL_STORAGE,Manifest.permission.WRITE_EXTERNAL_STORAGE})
    void toggleLoggingService() {
        Intent dataLoggerServiceIntent = new Intent(getApplicationContext(), LoggingService.class);

        if (LoggingService.isInstanceCreated())
            stopService(dataLoggerServiceIntent);
        else
            startService(dataLoggerServiceIntent);
    }

    private void stopPebbleService() {
        if (PebbleService.isInstanceCreated())
            togglePebbleService();
    }
    private void togglePebbleService() {
        Intent pebbleServiceIntent = new Intent(getApplicationContext(), PebbleService.class);
        if (PebbleService.isInstanceCreated())
            stopService(pebbleServiceIntent);
        else
            startService(pebbleServiceIntent);
    }

    private void startSpeechService() {
        if (!SpeechService.isInstanceCreated())
            toggleSpeechService();
    }
    private void stopSpeechService() {
        if (SpeechService.isInstanceCreated())
            toggleSpeechService();
    }

    @NeedsPermission({Manifest.permission.BLUETOOTH})
    void toggleSpeechService() {
        Intent speechServiceIntent = new Intent(getApplicationContext(), SpeechService.class);
        if (SpeechService.isInstanceCreated())
            stopService(speechServiceIntent);
        else
            startService(speechServiceIntent);
    }

    private void stopLivemapService() {
        if (LivemapService.isInstanceCreated())
            toggleLivemapService();
    }

    @NeedsPermission(Manifest.permission.ACCESS_FINE_LOCATION)
    void toggleLivemapService() {
        Intent livemapServiceIntent = new Intent(getApplicationContext(), LivemapService.class);
        if (LivemapService.isInstanceCreated())
            stopService(livemapServiceIntent);
        else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(livemapServiceIntent);
            }
            else {
                startService(livemapServiceIntent);
            }
        }
    }

    private void startBluetoothService() {
        Intent bluetoothServiceIntent = new Intent(getApplicationContext(), BluetoothLeService.class);
        startService(bluetoothServiceIntent);
        bindService(bluetoothServiceIntent, mServiceConnection, BIND_AUTO_CREATE);
    }

    private void toggleConnectToWheel() {
        sendBroadcast(new Intent(Constants.ACTION_REQUEST_CONNECTION_TOGGLE));
    }

    @NeedsPermission(Manifest.permission.ACCESS_FINE_LOCATION)
    void startScanActivity() {
        startActivityForResult(new Intent(MainActivity.this, ScanActivity.class), RESULT_DEVICE_SCAN_REQUEST);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        // NOTE: delegate the permission handling to generated method
        MainActivityPermissionsDispatcher.onRequestPermissionsResult(this, requestCode, grantResults);
    }

    @Override
    protected void onActivityResult(final int requestCode, final int resultCode, final Intent data) {
        Timber.i("onActivityResult");
        try {
            FlicManager.getInstance(this, new FlicManagerInitializedCallback() {
                @Override
                public void onInitialized(FlicManager manager) {
                    FlicButton button = manager.completeGrabButton(requestCode, resultCode, data);
                    if (button != null) {
                        button.registerListenForBroadcast(FlicBroadcastReceiverFlags.CLICK_OR_DOUBLE_CLICK_OR_HOLD);
                        Toast.makeText(MainActivity.this, R.string.flic_button_grabbed, Toast.LENGTH_LONG).show();
                    } else {
                        Toast.makeText(MainActivity.this, R.string.flic_button_not_grabbed, Toast.LENGTH_LONG).show();
                    }
                }
            });
        }
        catch (Exception e) {
            e.printStackTrace();
        }
        switch (requestCode) {
            case RESULT_DEVICE_SCAN_REQUEST:
                if (resultCode == RESULT_OK) {
                    mDeviceAddress = data.getStringExtra("MAC");
                    Timber.i("Device selected = %s", mDeviceAddress);
                    String mDeviceName = data.getStringExtra("NAME");
                    Timber.i("Device selected = %s", mDeviceName);
                    mBluetoothLeService.setDeviceAddress(mDeviceAddress);
                    WheelData.getInstance().full_reset(SettingsUtil.getDontResetData(this));
                    WheelData.getInstance().setBtName(mDeviceName);
                    updateScreen(true);
                    setMenuIconStates();
                    mBluetoothLeService.close();
                    toggleConnectToWheel();
                }
                break;
            case RESULT_REQUEST_ENABLE_BT:
                if (mBluetoothAdapter.isEnabled())
                    startBluetoothService();
                else {
                    Toast.makeText(this, R.string.bluetooth_required, Toast.LENGTH_LONG).show();
                    finish();
                }
                break;
            case REQUEST_CODE_RESOLUTION:
                if (resultCode == RESULT_OK)
                    getGoogleApiClient().connect();
                else {
                    SettingsUtil.setAutoUploadEnabled(this, false);
                    ((PreferencesFragment) getPreferencesFragment()).refreshVolatileSettings();
                }
                break;
            case REQUEST_IMAGE_CAPTURE:
                if ((resultCode == RESULT_OK) && (!mImagePath.isEmpty()) && (LivemapService.isInstanceCreated())) {
                    uploadImageToEucWorld();
                }
                break;
        }
    }

    private IntentFilter makeIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(Constants.ACTION_BLUETOOTH_CONNECTION_STATE);
        intentFilter.addAction(Constants.ACTION_WHEEL_DATA_AVAILABLE);
        intentFilter.addAction(Constants.ACTION_LOGGING_SERVICE_TOGGLED);
        intentFilter.addAction(Constants.ACTION_PEBBLE_SERVICE_TOGGLED);
        intentFilter.addAction(Constants.ACTION_SPEECH_SERVICE_TOGGLED);
        intentFilter.addAction(Constants.ACTION_LIVEMAP_SERVICE_TOGGLED);
        intentFilter.addAction(Constants.ACTION_LIVEMAP_STATUS);
        intentFilter.addAction(Constants.ACTION_LIVEMAP_LOCATION_UPDATED);
        intentFilter.addAction(Constants.ACTION_PREFERENCE_CHANGED);
		intentFilter.addAction(Constants.ACTION_WHEEL_SETTING_CHANGED);
		intentFilter.addAction(Constants.ACTION_WHEEL_TYPE_RECOGNIZED);	
		intentFilter.addAction(Constants.ACTION_ALARM_TRIGGERED);			
        return intentFilter;
    }

    IAxisValueFormatter chartAxisValueFormatter = new IAxisValueFormatter() {
        @Override
        public String getFormattedValue(float value, AxisBase axis) {
            if (value < xAxis_labels.size())
                return xAxis_labels.get((int) value);
            else
                return "";
        }
        // we don't draw numbers, so no decimal digits needed
        @Override
        public int getDecimalDigits() {  return 0; }
    };

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
        Timber.i("GoogleApiClient connection failed: %s", connectionResult.toString());
        if (!connectionResult.hasResolution()) {
            // show the localized error dialog.
            GoogleApiAvailability.getInstance().getErrorDialog(this, connectionResult.getErrorCode(), 0).show();
            SettingsUtil.setAutoUploadEnabled(this, false);
            ((PreferencesFragment) getPreferencesFragment()).refreshVolatileSettings();
            return;
        }
        try {
            connectionResult.startResolutionForResult(this, REQUEST_CODE_RESOLUTION);
        } catch (IntentSender.SendIntentException e) {
            Timber.e("Exception while starting resolution activity");
        }
    }

    public GoogleApiClient getGoogleApiClient() {
        if (mGoogleApiClient == null) {
            mGoogleApiClient = new GoogleApiClient.Builder(this)
                    .addApi(Drive.API)
                    .addScope(Drive.SCOPE_FILE)
                    .addOnConnectionFailedListener(this)
                    .build();
        }
        return mGoogleApiClient;
    }

    private Fragment getPreferencesFragment() {
        Fragment frag = getFragmentManager().findFragmentByTag(Constants.PREFERENCES_FRAGMENT_TAG);

        if (frag == null)
            frag = new PreferencesFragment();

        return frag;
    }

    private void shareLivemapUrl() {
        if (!LivemapService.getUrl().equals("")) {
            Intent share = new Intent(android.content.Intent.ACTION_SEND);
            share.setType("text/plain");
            share.putExtra(Intent.EXTRA_SUBJECT, R.string.link_livemap_subject);
            share.putExtra(Intent.EXTRA_TEXT, LivemapService.getUrl());

            Intent view = new Intent(Intent.ACTION_VIEW);
            view.setData(Uri.parse(LivemapService.getUrl()));

            Intent chooserIntent = Intent.createChooser(share, getString(R.string.share_livemap));
            chooserIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS, new Intent[]{view});
            startActivity(chooserIntent);
        }
    }

    private void setBtnState(ImageButton btn, boolean down, boolean enabled) {
        btn.setClickable(enabled);
        if (btn == ibLivemapStartFinish)
            ibLivemapStartFinish.setImageResource(down ? R.drawable.ic_btn_finish : R.drawable.ic_btn_start);
        if (enabled)
            btn.setBackgroundColor(down ? getResources().getColor(R.color.accent) : getResources().getColor(R.color.wheelview_arc_dim));
        else
            btn.setBackgroundColor(getResources().getColor(R.color.disabled));
    }

    private File createImageFile() {
        String filename = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date()) + ".jpg";
        File path = new File(Environment.getExternalStorageDirectory(), Constants.PICTURE_FOLDER_NAME);
        path.mkdirs();
        File image = new File(path, filename);
        mImagePath = image.getAbsolutePath();
        return image;
    }

    @NeedsPermission({Manifest.permission.READ_EXTERNAL_STORAGE,Manifest.permission.WRITE_EXTERNAL_STORAGE})
    public void imageCapture() {
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (intent.resolveActivity(getPackageManager()) != null) {
            File imageFile = createImageFile();
            if (imageFile != null) {
                Uri photoURI = FileProvider.getUriForFile(this, "com.cooper.wheellog.fileprovider", imageFile);
                intent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI);
                if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.LOLLIPOP) {
                    intent.setClipData(ClipData.newRawUri("", photoURI));
                    intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
                }
                startActivityForResult(intent, REQUEST_IMAGE_CAPTURE);
            }
        }
    }

    private void setMainActivityLockBehaviour() {
        if (SettingsUtil.getShowWhenLocked(this))
            this.getWindow().addFlags(WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
        else
            this.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
    }

    private void updateLivemapUI() {
        // GPS signal
        if (LivemapService.isConnected() && !LivemapService.getLivemapGPS())
            ivGPSError.setVisibility(View.VISIBLE);
        else
            ivGPSError.setVisibility(View.GONE);
        // Image upload notification
        if (imageUploadCount > 0)
            ivUploadProgressWarning.setVisibility(View.VISIBLE);
        else
            ivUploadProgressWarning.setVisibility(View.GONE);
        // Live map status notification
        switch (LivemapService.getLivemapError()) {
            case -1:
                ivLivemapError.setVisibility(View.GONE);
                break;
            case 0:
                ivLivemapError.setVisibility(View.GONE);
                break;
            case 1:
                ivLivemapError.setImageResource(R.drawable.ic_warning);
                ivLivemapError.setVisibility(View.VISIBLE);
                break;
            case 2:
                ivLivemapError.setImageResource(R.drawable.ic_error);
                ivLivemapError.setVisibility(View.VISIBLE);
                break;
        }
        // Buttons and text
        if (LivemapService.isInstanceCreated()) {
            switch (LivemapService.getStatus()) {
                case DISCONNECTED:
                    if (lastLivemapStatus != LivemapService.LivemapStatus.DISCONNECTED)
                        wvEucWorld.reload();

                    tvLivemapStatus.setText(getString(R.string.livemap_offline));
                    setBtnState(ibLivemapStartFinish, false, !SettingsUtil.getLivemapApiKey(this).equals(""));
                    setBtnState(ibLivemapPause, false, false);
                    setBtnState(ibLivemapPhoto, false, false);
                    setBtnState(ibLivemapShare, false, false);
                    break;
                case CONNECTING:
                    tvLivemapStatus.setText(getString(R.string.livemap_connecting));
                    setBtnState(ibLivemapStartFinish, false, false);
                    setBtnState(ibLivemapPause, false, false);
                    setBtnState(ibLivemapPhoto, false, false);
                    setBtnState(ibLivemapShare, false, false);
                    break;
                case WAITING_FOR_GPS:
                    tvLivemapStatus.setText(getString(R.string.livemap_gps_wait));
                    setBtnState(ibLivemapStartFinish, true, true);
                    setBtnState(ibLivemapPause, false, false);
                    setBtnState(ibLivemapPhoto, false, false);
                    setBtnState(ibLivemapShare, false, false);
                    break;
                case STARTED:
                    if (lastLivemapStatus != LivemapService.LivemapStatus.STARTED)
                        wvEucWorld.reload();

                    tvLivemapStatus.setText(getString(R.string.livemap_live));
                    setBtnState(ibLivemapStartFinish, true, true);
                    setBtnState(ibLivemapPause, false, true);
                    setBtnState(ibLivemapPhoto, false, true);
                    setBtnState(ibLivemapShare, false, true);
                    break;
                case PAUSING:
                case RESUMING:
                    tvLivemapStatus.setText(getString(R.string.livemap_live));
                    setBtnState(ibLivemapStartFinish, true, true);
                    setBtnState(ibLivemapPause, true, false);
                    setBtnState(ibLivemapPhoto, false, true);
                    setBtnState(ibLivemapShare, false, true);
                    break;
                case PAUSED:
                    tvLivemapStatus.setText(getString(R.string.livemap_live));
                    setBtnState(ibLivemapStartFinish, true, true);
                    setBtnState(ibLivemapPause, true, true);
                    setBtnState(ibLivemapPhoto, false, true);
                    setBtnState(ibLivemapShare, false, true);
                    break;
                case DISCONNECTING:
                    tvLivemapStatus.setText(getString(R.string.livemap_disconnecting));
                    setBtnState(ibLivemapStartFinish, true, false);
                    setBtnState(ibLivemapPause, false, false);
                    setBtnState(ibLivemapPhoto, false, false);
                    setBtnState(ibLivemapShare, false, false);
                    break;
            }
            lastLivemapStatus = LivemapService.getStatus();
        }
        else {
            lastLivemapStatus = LivemapService.LivemapStatus.DISCONNECTED;
            tvLivemapStatus.setText(getString(R.string.livemap_offline));
            setBtnState(ibLivemapStartFinish, false, !SettingsUtil.getLivemapApiKey(this).equals(""));
            setBtnState(ibLivemapPause, false, false);
            setBtnState(ibLivemapPhoto, false, false);
            setBtnState(ibLivemapShare, false, false);
        }
    }

    private void loadEucWorldApp() {
        if (!SettingsUtil.getLivemapApiKey(this).equals(""))
            wvEucWorld.loadUrl(Constants.getEucWorldUrl()+"/app?k="+SettingsUtil.getLivemapApiKey(this));
        else
            wvEucWorld.loadUrl(Constants.getEucWorldUrl()+"/app");
    }

    private void uploadImageToEucWorld() {
        final RequestParams requestParams = new RequestParams();

        // Set image location
        requestParams.put("llt", String.format(Locale.US, "%.7f", LivemapService.getInstance().getLatitude()));
        requestParams.put("lln", String.format(Locale.US, "%.7f", LivemapService.getInstance().getLongitude()));
        float[] latLon = new float[2];
        try {
            final ExifInterface exifInterface = new ExifInterface(mImagePath);
            if (exifInterface.getLatLong(latLon)) {
                // Update image location with EXIF values
                requestParams.put("llt", String.format(Locale.US, "%.7f", latLon[0]));
                requestParams.put("lln", String.format(Locale.US, "%.7f", latLon[1]));
            }
        }
        catch (IOException e) { }

        // Get source image size
        BitmapFactory.Options bopts = new BitmapFactory.Options();
        bopts.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(mImagePath, bopts);
        int width = bopts.outWidth;
        int height = bopts.outHeight;

        // Limit image size and quality
        final File img;
        try {
            // Compressor doesn't scale image if you define both setMaxWidth() and setMaxHeight()
            if (width > height)
                img = new Compressor(this)
                        .setMaxWidth(2000)
                        .setQuality(75)
                        .setCompressFormat(Bitmap.CompressFormat.JPEG)
                        .compressToFile(new File(mImagePath));
            else
                img = new Compressor(this)
                        .setMaxHeight(2000)
                        .setQuality(75)
                        .setCompressFormat(Bitmap.CompressFormat.JPEG)
                        .compressToFile(new File(mImagePath));
        }
        catch (IOException e) {
            Toast.makeText(this, R.string.livemap_image_preparation_error, Toast.LENGTH_LONG).show();
            return;
        }

        try {
            requestParams.put("image", img);
            requestParams.put("a", SettingsUtil.getLivemapApiKey(this));
            requestParams.put("k", LivemapService.getInstance().getTourKey());
            requestParams.put("dt", new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.US).format(new Date()));
            requestParams.put("ldt", new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.US).format(new Date(LivemapService.getInstance().getLocationTime())));
            Toast.makeText(getApplicationContext(), R.string.livemap_image_uploading, Toast.LENGTH_LONG).show();
            imageUploadCount += 1;
            HttpClient.post(Constants.getEucWorldUrl() + "/api/tour/upload", requestParams, new JsonHttpResponseHandler() {
                @Override
                public void onSuccess(int statusCode, cz.msebera.android.httpclient.Header[] headers, JSONObject response) {
                    imageUploadCount -= 1;
                    img.delete();
                    try {
                        int error = response.getInt("error");
                        if (error == 0)
                            Toast.makeText(getApplicationContext(), R.string.livemap_image_uploaded, Toast.LENGTH_LONG).show();
                        else
                            Toast.makeText(getApplicationContext(), R.string.livemap_image_upload_error, Toast.LENGTH_LONG).show();
                    }
                    catch (JSONException e) {
                        e.printStackTrace();
                    }
                    updateLivemapUI();
                }
                @Override
                public void onFailure (int statusCode, cz.msebera.android.httpclient.Header[] headers, Throwable throwable, JSONObject errorResponse) {
                    imageUploadCount -= 1;
                    img.delete();
                    Toast.makeText(getApplicationContext(), R.string.livemap_image_upload_error, Toast.LENGTH_LONG).show();
                    updateLivemapUI();
                }
                @Override
                public void onFailure (int statusCode, cz.msebera.android.httpclient.Header[] headers, String responseString, Throwable throwable) {
                    imageUploadCount -= 1;
                    img.delete();
                    Toast.makeText(getApplicationContext(), R.string.livemap_image_upload_error, Toast.LENGTH_LONG).show();
                    updateLivemapUI();
                }
            });
        }
        catch(FileNotFoundException e) { }
    }

    private void checkForUpdatesOnStartup() {
        final RequestParams requestParams = new RequestParams();
        String apiKey = SettingsUtil.getLivemapApiKey(this);
        requestParams.put("resource", "app");
        if (!apiKey.equals("")) requestParams.put("k", apiKey);
        HttpClient.post(Constants.getEucWorldUrl() + "/api/get", requestParams, new JsonHttpResponseHandler() {
            @Override
            public void onSuccess(int statusCode, cz.msebera.android.httpclient.Header[] headers, JSONObject response) {
                try {
                    int error = response.getInt("error");
                    if (error == 0) {
                        jsInterface.setAppLatestVersionName(response.getJSONObject("data").getString("appLatestVersionName"));
                        jsInterface.setAppLatestVersionCode(response.getJSONObject("data").getInt("appLatestVersionCode"));
                        jsInterface.setAppLatestDownloadUrl(response.getJSONObject("data").getString("appLatestDownloadUrl"));
                        loadEucWorldApp();

                        if (jsInterface.getAppLatestVersionCode() > BuildConfig.VERSION_CODE) {
                            Snackbar snackbar = Snackbar.make(findViewById(R.id.main_view), getString(R.string.update_available, jsInterface.getAppLatestVersionName()), Snackbar.LENGTH_LONG);
                            snackbar.setAction(R.string.update_available_download, new View.OnClickListener() {
                                @Override
                                public void onClick(View view) {
                                    Intent i = new Intent(Intent.ACTION_VIEW);
                                    i.setData(Uri.parse(jsInterface.getAppLatestDownloadUrl()));
                                    startActivity(i);
                                }
                            });
                            snackbar.getView().setBackgroundResource(R.color.primary_dark);
                            snackbar.setDuration(5000);
                            snackbar.show();
                        }
                    }
                }
                catch (JSONException e) {
                    e.printStackTrace();
                }
            }
            @Override
            public void onFailure (int statusCode, cz.msebera.android.httpclient.Header[] headers, Throwable throwable, JSONObject errorResponse) { }
            @Override
            public void onFailure (int statusCode, cz.msebera.android.httpclient.Header[] headers, String responseString, Throwable throwable) { }
        });
    }

    private void updateWheelView() {
        if (WheelData.getInstance().isConnected()) {
            wheelView.setWheel();
            wheelView.setSpeed(WheelData.getInstance().getSpeed());
            wheelView.setBattery(WheelData.getInstance().getBatteryLevel());
            wheelView.setTemperature(WheelData.getInstance().getTemperature());
            wheelView.setRideTime(WheelData.getInstance().getRideTimeString());
            wheelView.setRidingTime(WheelData.getInstance().getRidingTimeString());
            wheelView.setTopSpeed(WheelData.getInstance().getTopSpeedDouble());
            wheelView.setDistance(WheelData.getInstance().getDistanceDouble());
            wheelView.setTotalDistance(WheelData.getInstance().getTotalDistanceDouble());
            wheelView.setVoltage(WheelData.getInstance().getVoltageDouble());
            wheelView.setCurrent(WheelData.getInstance().getCurrentDouble());
            wheelView.setAverageSpeed(WheelData.getInstance().getAverageSpeedDouble());
            wheelView.setAverageRidingSpeed(WheelData.getInstance().getAverageRidingSpeedDouble());
        }
        else
        if (LivemapService.getLivemapGPS()) {
            wheelView.setGPS();
            wheelView.setSpeed((int)(LivemapService.getSpeed() * 10));
            wheelView.setRideTime(LivemapService.getRideTimeString());
            wheelView.setRidingTime(LivemapService.getRidingTimeString());
            wheelView.setTopSpeed(LivemapService.getTopSpeed());
            wheelView.setDistance(LivemapService.getDistance());
            wheelView.setAverageSpeed(LivemapService.getAverageSpeed());
            wheelView.setAverageRidingSpeed(LivemapService.getAverageRidingSpeed());
        }
    }

    public void grabFlicButton() {
        try {
            FlicManager.getInstance(this, new FlicManagerInitializedCallback() {
                @Override
                public void onInitialized(FlicManager manager) {
                    manager.initiateGrabButton(MainActivity.this);
                }
            });
        }
        catch (FlicAppNotInstalledException err) {
            Toast.makeText(this, "Flic App is not installed", Toast.LENGTH_SHORT).show();
        }
    }

    private String getFlicKey(String name) {
        int id = getResources().getIdentifier(name, "string", getPackageName());
        return (id > 0) ? getResources().getString(id)+"." : "";
    }

}