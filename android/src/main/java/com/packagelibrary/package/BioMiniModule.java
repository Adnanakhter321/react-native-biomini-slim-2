package com.biomini.adnan;

import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.Promise;
import android.content.Context;
import com.facebook.react.bridge.LifecycleEventListener;
import android.content.IntentFilter;
import android.hardware.usb.UsbManager;
import android.hardware.usb.UsbDevice;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Intent;
import android.util.Log;
import java.util.HashMap;
import java.util.Iterator;
import com.facebook.react.bridge.WritableNativeArray;
import com.facebook.react.bridge.WritableNativeMap;
import com.facebook.react.bridge.WritableMap;
import com.biominiseries.BioMiniFactory;
import com.biominiseries.IBioMiniDevice;
import com.biominiseries.CaptureResponder;
import com.biominiseries.util.Logger;
import android.os.SystemClock;
import androidx.viewpager2.widget.ViewPager2;
import com.facebook.react.bridge.Arguments;
import android.graphics.Bitmap;
import java.util.Map;
import android.util.Base64;
import java.io.ByteArrayOutputStream;
import com.facebook.react.modules.core.DeviceEventManagerModule;
import android.content.pm.PackageManager;



public class BioMiniModule extends ReactContextBaseJavaModule implements LifecycleEventListener {
    private static final String ACTION_USB_PERMISSION = "com.android.example.USB_PERMISSION";
    private static final String TAG = "BioMini";
    private UsbManager mUsbManager;
    private PendingIntent mPermissionIntent;
    private UsbDevice mUsbDevice;
    private IBioMiniDevice mCurrentDevice;
    private BioMiniFactory mBioMiniFactory;
    private final ReactApplicationContext reactContext;
    private ViewPager2 mViewPager;
    private Context mContext;
    private IBioMiniDevice.TemplateData mTemplateData;
    private IBioMiniDevice.CaptureOption mCaptureOption = new IBioMiniDevice.CaptureOption();


    public BioMiniModule(ReactApplicationContext reactContext) {
        super(reactContext);
        this.reactContext = reactContext;
        this.mContext = reactContext; 
        this.mUsbManager = (UsbManager) reactContext.getSystemService(Context.USB_SERVICE);
        this.mPermissionIntent = PendingIntent.getBroadcast(
                reactContext,
                0,
                new Intent(ACTION_USB_PERMISSION),
                PendingIntent.FLAG_IMMUTABLE
        );

        if(javaHasUsbHostFeature()){
            registerUSBReceiver();
            // registerUSBPermissionReceiver();
            initUsbListener();
        }
    }

    @Override
    public String getName() {
        return "BioMiniModule";
    }

    @Override
    public void onHostResume() {
        // App is in the foreground
    }

    @Override
    public void onHostPause() {
        // App goes to the background
    }

    @ReactMethod
    public void abortCapturing(Promise promise) {
        try {
            // Your provided native code logic
            int result = mCurrentDevice.abortCapturing();
            int nRetryCount = 0;

            // Check if capture was aborted
            if (result == 0) {
                // Aborting was successful
                if (mCaptureOption.captureFuntion != IBioMiniDevice.CaptureFuntion.NONE) {
                    // setLogInTextView(mCaptureOption.captureFuntion.name() + " is aborted.");
                }
                mCaptureOption.captureFuntion = IBioMiniDevice.CaptureFuntion.NONE;

                // Resolve the promise with true for success
                promise.resolve(true);
            } else {
                // Abort capturing failed, return false
                promise.resolve(false);
            }
        } catch (Exception e) {
            // If an error occurs, reject the promise with an error message
            promise.reject("ERROR_ABORTING", e);
        }
    }


    private void registerUSBReceiver() {
        BroadcastReceiver usbReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(action)) {
                    mUsbDevice = null;
                    mCurrentDevice = null;
                    sendEvent("USBConnected", "USB device connected");
                } else if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {
                    sendEvent("USBDisconnected", "USB device disconnected");
                }
            }
        };

        IntentFilter filter = new IntentFilter();
        filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        reactContext.registerReceiver(usbReceiver, filter);
    }

    @ReactMethod
    public void hasUsbHostFeature(Promise promise) {
        boolean response = mContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_USB_HOST);
        promise.resolve(response);
    }
    public boolean javaHasUsbHostFeature() {
        return mContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_USB_HOST);
    }

    // Method to get the name of the current USB device
    @ReactMethod
    public void getCurrentDeviceName(Promise promise) {
        if (mCurrentDevice != null && mCurrentDevice.getDeviceInfo() != null) {
            // String deviceName = mCurrentDevice.getDeviceInfo().deviceName;
            String deviceName = mCurrentDevice.getDeviceInfo().deviceName;
            if (deviceName != null) {
                // Resolve the promise with the device name
                promise.resolve(deviceName);
            } else {
                // If the device name is null, reject the promise
                promise.reject("DEVICE_NAME_NULL", "null");
            }
        } else {
            // If no device is connected, reject the promise
            promise.resolve("null");
        }
    }

    // Call this method to request permission for the USB device
    private void requestUsbPermission(UsbDevice device) {
        PendingIntent permissionIntent = PendingIntent.getBroadcast(reactContext, 0, new Intent(ACTION_USB_PERMISSION), 0);
        UsbManager usbManager = (UsbManager) reactContext.getSystemService(Context.USB_SERVICE);
        usbManager.requestPermission(device, permissionIntent);
    }

    // This method sends events to the React Native side
    private void sendEvent(String eventName, String eventData) {
        WritableMap params = Arguments.createMap();
        params.putString("message", eventData);

        reactContext.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
                .emit(eventName, params);
    }


    @Override
    public void onHostDestroy() {
        try {
            // Ensure the device stops capturing before cleanup
            if (mCurrentDevice != null && mCurrentDevice.isCapturing()) {
                doAbortCapture();
                while (mCurrentDevice.isCapturing()) {
                    SystemClock.sleep(10); // Wait for the capture to stop
                }
            }

            // Cleanup BioMiniFactory and remove the USB device
            if (mBioMiniFactory != null) {
                if (mUsbDevice != null) {
                    mBioMiniFactory.removeDevice(mUsbDevice);
                }
                mBioMiniFactory.close();
            }

            // Unregister broadcast receiver to prevent memory leaks
            mContext.unregisterReceiver(mUsbReceiver);
            
            // Clear device references
            mUsbDevice = null;
            mCurrentDevice = null;

        } catch (Exception e) {
            Logger.e("Error during cleanup on destroy: " + e.getMessage());
        }
    }
    // Inside your existing BioMiniModule class

    @ReactMethod
    public void doSingleCapture(Promise promise) {
        Logger.d("START!");

        // Reset template data
        mTemplateData = null;
        // Set capture options
        mCaptureOption.captureFuntion = IBioMiniDevice.CaptureFuntion.CAPTURE_SINGLE;
        mCaptureOption.extractParam.captureTemplate = true;
        mCaptureOption.extractParam.maxTemplateSize = IBioMiniDevice.MaxTemplateSize.MAX_TEMPLATE_1024;
        mCaptureOption.frameRate = IBioMiniDevice.FrameRate.LOW;
        Logger.d("START!");

        if (mCurrentDevice != null) {
            // Setting the template type to ISO19794_2 directly after capturing
            int tmp_type = IBioMiniDevice.TemplateType.ISO19794_2.value();
            mCurrentDevice.setParameter(new IBioMiniDevice.Parameter(IBioMiniDevice.ParameterType.TEMPLATE_TYPE, tmp_type));
        }

        if (mCurrentDevice != null) {
            try{
                boolean result = mCurrentDevice.captureSingle(
                    mCaptureOption,
                    new CaptureResponder() {
                        @Override
                        public boolean onCaptureEx(Object context, IBioMiniDevice.CaptureOption option, final Bitmap capturedImage, IBioMiniDevice.TemplateData capturedTemplate, IBioMiniDevice.FingerState fingerState) {

                            Logger.d("START! : " + mCaptureOption.captureFuntion.toString());

                            if (capturedTemplate != null) {
                                Logger.d("TemplateData is not null!");
                                mTemplateData = capturedTemplate;

                                // // Convert template data to Base64 string
                                // String base64Template = encodeToBase64(mTemplateData.data);
                                // Logger.d("Fingerprint Template Base64: " + base64Template);
                                // Log.d("BioMini", base64Template);

                                // Create a WritableMap to send both template and base64 to React Native
                                WritableMap resultMap = Arguments.createMap();
                                resultMap.putString("template", encodeToBase64(mTemplateData.data));  // Template as Base64

                                ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                                capturedImage.compress(Bitmap.CompressFormat.PNG, 100, byteArrayOutputStream);
                                byte[] byteArray = byteArrayOutputStream.toByteArray();
                                String encodedImage = Base64.encodeToString(byteArray, Base64.DEFAULT);

                                // Make sure to add the correct data URI prefix for Base64 images
                                String base64Image = "data:image/png;base64," + encodedImage;
                                resultMap.putString("image", base64Image);
                                

                                // Resolve the promise with the resultMap
                                promise.resolve(resultMap);
                            } else {
                                // Reject the promise if no template was captured
                                promise.reject("TEMPLATE_ERROR", "No template data captured");
                            }

                            return super.onCaptureEx(context, option, capturedImage, capturedTemplate, fingerState);
                        }
                    },
                    true
                );
            }
            catch (Exception e) {
            //    if (!result) {
            //         promise.reject("CAPTURE_FAILED", "Failed to start capture");
            //    }
                promise.reject("ERROR_CREATING_DEVICE", e.getMessage());
            }
            // If the capture did not start successfully, reject the promise
           
        } else {
            // If no device is connected, reject the promise
            promise.reject("NO_DEVICE", "No device connected");
        }
    }

    // Helper function to convert byte array to Base64
    private String encodeToBase64(byte[] data) {
        return android.util.Base64.encodeToString(data, android.util.Base64.NO_WRAP);
    }

    @ReactMethod
    public void createBioMiniDevice(Promise promise) {
        Logger.d("START!");

        if (mUsbDevice == null) {
            promise.reject("DEVICE_NOT_CONNECTED", "Biomini Device not connected");
            return;
        }

        try {
            if (mBioMiniFactory != null) {
                mBioMiniFactory.close();
            }

            mBioMiniFactory = new BioMiniFactory(mContext, mUsbManager) {
                @Override
                public void onDeviceChange(DeviceChangeEvent event, Object dev) {
                    Logger.d("onDeviceChange: " + event);
                }
            };

            boolean result = mBioMiniFactory.addDevice(mUsbDevice);

            if (result) {
                mCurrentDevice = mBioMiniFactory.getDevice(0);
                if (mCurrentDevice != null) {
                    HashMap<String, String> deviceInfoMap = new HashMap<>();
                    deviceInfoMap.put("deviceName", mCurrentDevice.getDeviceInfo().deviceName);
                    deviceInfoMap.put("deviceSN", mCurrentDevice.getDeviceInfo().deviceSN);
                    deviceInfoMap.put("versionFW", mCurrentDevice.getDeviceInfo().versionFW);
                    deviceInfoMap.put("sdkVersionInfo", mBioMiniFactory.getSdkVersionInfo());

                    WritableMap writableMap = Arguments.createMap();
                    for (Map.Entry<String, String> entry : deviceInfoMap.entrySet()) {
                        writableMap.putString(entry.getKey(), entry.getValue());
                    }

                    promise.resolve(writableMap);
                } else {
                    promise.reject("DEVICE_NOT_FOUND", "Current device not found");
                }
            } else {
                promise.reject("DEVICE_ADDITION_FAILED", "Failed to add device");
            }
        } catch (Exception e) {
            promise.reject("ERROR_CREATING_DEVICE", e.getMessage());
        }
    }

    @ReactMethod
    public void initUsbListener() {
        Logger.d("Starting USB listener!");
        IntentFilter permissionFilter = new IntentFilter(ACTION_USB_PERMISSION);
        reactContext.registerReceiver(mUsbReceiver, permissionFilter);
        IntentFilter attachFilter = new IntentFilter(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        reactContext.registerReceiver(mUsbReceiver, attachFilter);
        IntentFilter detachFilter = new IntentFilter(UsbManager.ACTION_USB_DEVICE_DETACHED);
        reactContext.registerReceiver(mUsbReceiver, detachFilter);
    }

    @ReactMethod
    public boolean isUsbDeviceConnected() {
        HashMap<String, UsbDevice> deviceList = mUsbManager.getDeviceList();
        return !deviceList.isEmpty(); // Returns true if there are connected devices
    }

    @ReactMethod
    public void requestPermissionUsb(final Promise promise) {
        if (promise == null) {
            Log.e(TAG, "Promise object is null");
            return;
        }

        if(isUsbDeviceConnected()){
            HashMap<String, UsbDevice> deviceList = mUsbManager.getDeviceList();
            if (deviceList.isEmpty()) {
                promise.reject("NoDeviceFound", "No USB devices found");
                return;
            }

            Iterator<UsbDevice> deviceIterator = deviceList.values().iterator();
            if (deviceIterator.hasNext()) {
                mUsbDevice = deviceIterator.next();
                mUsbManager.requestPermission(mUsbDevice, mPermissionIntent);
                promise.resolve(true);
            } else {
                promise.reject("DeviceError", "Failed to find a suitable USB device");
            }
        }
    }


    @ReactMethod
    public void removeDevice(Promise promise) {
        try {
            if (mCurrentDevice != null && mCurrentDevice.isCapturing()) {
                doAbortCapture();
                while (mCurrentDevice.isCapturing()) {
                    SystemClock.sleep(10);
                }
            }

            if (mBioMiniFactory != null) {
                if (mUsbDevice != null) {
                    mBioMiniFactory.removeDevice(mUsbDevice);
                }
                mBioMiniFactory.close();
                mContext.unregisterReceiver(mUsbReceiver);
                mUsbDevice = null;
                mCurrentDevice = null;
            }

            promise.resolve("Success: Device Removed");
        } catch (Exception e) {
            promise.reject("Error", e.getMessage());
        }
    }

    private void removeDevice() {
        if (mCurrentDevice != null) {
            Logger.d("Device disconnected.");
            mCurrentDevice = null;
        }
    }

    private void doAbortCapture() {
        if (mCurrentDevice != null) {
            Logger.d("Aborting capture...");
            // mCurrentDevice.abort();
        }
    }

    private final BroadcastReceiver mUsbReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            switch (action) {
                case ACTION_USB_PERMISSION:
                    Logger.d("ACTION_USB_PERMISSION");
                    boolean hasUsbPermission = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false);
                    if (hasUsbPermission && mUsbDevice != null) {
                        Logger.d(mUsbDevice.getDeviceName() + " acquired USB permission.");
                    } else {
                        Logger.d("USB permission is not granted!");
                    }
                    break;
                case UsbManager.ACTION_USB_DEVICE_ATTACHED:
                    Logger.d("ACTION_USB_DEVICE_ATTACHED");
                    addDeviceToUsbDeviceList();
                    break;
                case UsbManager.ACTION_USB_DEVICE_DETACHED:
                    Logger.d("ACTION_USB_DEVICE_DETACHED");
                    removeDevice();
                    break;
                default:
                    break;
            }
        }
    };

    private void addDeviceToUsbDeviceList() {
        Logger.d("Adding device to USB device list...");
    }
}
