// *************************************************************
// Copyright (c) 1991-2019 LEAD Technologies, Inc.              
// All Rights Reserved.                                         
// *************************************************************
package com.senarios.checksandcards;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;

/**
 * The DeviceUtils helper used to:
 * <ul>
 * <li>Check if the device has a camera or external storage, and request permission if needed (request permissions needed for API 23+,
 * the request permission result handled in 'Activity.onRequestPermissionsResult').</li>
 * <li>Capture an image, or pick images from gallery and external storage.</li>
 * </ul>
 */
public class DeviceUtils
{
   /**
    * Determines whether this device has a camera.
    *
    * @param context The Context object
    * @return 'True' if the device has a camera, otherwise 'False'
    */
   public static boolean hasCamera(Context context) {
      PackageManager manager = context.getPackageManager();
      boolean hasCamera = manager.hasSystemFeature(PackageManager.FEATURE_CAMERA);

      // "PackageManager.FEATURE_CAMERA_FRONT" supported in API 9+
      if (!hasCamera && Build.VERSION.SDK_INT >= 9)
         hasCamera = manager.hasSystemFeature(PackageManager.FEATURE_CAMERA_FRONT);

      return hasCamera;
   }

   /**
    * Determines whether the device have a storage with read\write access.
    *
    * @return 'True' if the device has an accessible read\write storage, otherwise 'False'
    */
   public static boolean isMediaMounted() {
      return Environment.getExternalStorageState().compareToIgnoreCase(Environment.MEDIA_MOUNTED) == 0;
   }

   /**
    * Get the external storage directory path
    *
    * @return The external storage directory path
    */
   public static String getExternalStorageDirectory() {
      String path = Environment.getExternalStorageDirectory().getPath();
      if (!path.endsWith("/"))
         path += "/";

      return path;
   }

   /**
    * Checking the requested permissions, and requesting permissions for non granted permissions.
    *
    * @param activity    The target activity.
    * @param permissions The requested permissions.
    * @param requestCode Application specific request code (To use when calling 'Activity.onRequestPermissionsResult')
    * @return 'True' if the permission granted, otherwise false(And send a request permission).
    * @since ANDROID API >= 23
    */
   public static boolean requestPermission(Activity activity, String[] permissions, int requestCode) {
      if (Build.VERSION.SDK_INT < 23)
         return true;

      List<String> requestPermissions = new ArrayList<String>();
      // Check each permission
      for (String permission : permissions) {
         if (ContextCompat.checkSelfPermission(activity, permission) == PackageManager.PERMISSION_DENIED) {
            requestPermissions.add(permission);
         }
      }

      // All granted
      if (requestPermissions.size() == 0)
         return true;

      ActivityCompat.requestPermissions(activity, requestPermissions.toArray(new String[requestPermissions.size()]), requestCode);
      return false;
   }

   /**
    * Checking the device storage read permission (And send a request if permission not granted).
    *
    * @param activity    The target activity.
    * @param requestCode Application specific request code (To use when calling 'Activity.onRequestPermissionsResult')
    * @return 'True' if the permission granted, otherwise false(And send a request permission).
    * @since ANDROID API >= 23
    */
   @SuppressWarnings({"unused", "RedundantIfStatement"})
   public static boolean checkReadStoragePermission(Activity activity, int requestCode) {
      if (Build.VERSION.SDK_INT < 23)
         return true;
      else
         return requestPermission(activity, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, requestCode);
   }

   /**
    * Checking the device storage write permission (And send a request if permission not granted).
    *
    * @param activity    The target activity.
    * @param requestCode Application specific request code (To use when calling 'Activity.onRequestPermissionsResult')
    * @return 'True' if the permission granted, otherwise false(And send a request permission).
    * @since ANDROID API >= 23
    */
   @SuppressWarnings({"unused", "RedundantIfStatement"})
   public static boolean checkWriteStoragePermission(Activity activity, int requestCode) {
      if (Build.VERSION.SDK_INT < 23)
         return true;
      else
         return requestPermission(activity, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, requestCode);
   }

   /**
    * Checking the device storage read\write permission (And send a request if permission not granted).
    *
    * @param activity    The target activity.
    * @param requestCode Application specific request code (To use when calling 'Activity.onRequestPermissionsResult')
    * @return 'True' if the permission granted, otherwise false(And send a request permission).
    * @since ANDROID API >= 23
    */
   @SuppressWarnings("RedundantIfStatement")
   public static boolean checkRWStoragePermission(Activity activity, int requestCode) {
      if (Build.VERSION.SDK_INT < 23)
         return true;
      else
         return requestPermission(activity, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE}, requestCode);
   }

   /**
    * Checking the device camera permission (And send a request if permission not granted).
    *
    * @param activity    The target activity.
    * @param requestCode Application specific request code (To use when calling 'Activity.onRequestPermissionsResult')
    * @return 'True' if the permission granted, otherwise false(And send a request permission).
    * @since ANDROID API >= 23
    */
   public static boolean checkCapturePermission(Activity activity, int requestCode) {
      if (Build.VERSION.SDK_INT < 23)
         return true;

      // Read\Write external storage permissions needed to save the captured image to storage
      return requestPermission(activity,
              new String[]{Manifest.permission.READ_EXTERNAL_STORAGE,
                      Manifest.permission.WRITE_EXTERNAL_STORAGE,
                      Manifest.permission.CAMERA},
              requestCode);
   }

   /**
    * Checking the device camera permission (And send a request if permission not granted).
    *
    * @param activity    The target activity.
    * @param requestCode Application specific request code (To use when calling 'Activity.onRequestPermissionsResult')
    * @return 'True' if the permission granted, otherwise false(And send a request permission).
    * @since ANDROID API >= 23
    */
   public static boolean checkCameraPermission(Activity activity, int requestCode) {
      if (Build.VERSION.SDK_INT < 23)
         return true;

      return requestPermission(activity,
              new String[]{Manifest.permission.CAMERA},
              requestCode);
   }

   /**
    * Checking the device internet connection permission.
    *
    * @param activity    The target activity.
    * @param requestCode Application specific request code (To use when calling 'Activity.onRequestPermissionsResult')
    * @return 'True' if the permission granted, otherwise false(And send a request permission).
    * @since ANDROID API >= 23
    */
   @SuppressWarnings("unused")
   public static boolean checkInternetPermission(Activity activity, int requestCode) {
      if (Build.VERSION.SDK_INT < 23)
         return true;

      // Read\Write external storage permissions needed to save the captured image to storage
      return requestPermission(activity,
              new String[]{Manifest.permission.ACCESS_NETWORK_STATE,
                      Manifest.permission.INTERNET},
              requestCode);
   }

   /**
    * Helper method to pick images from gallery (Starts an 'ACTION_PICK' images activity; the result should be handled in 'activity.onActivityResult')
    *
    * @param activity    The target activity.
    * @param requestCode Application specific request code (To use when the gallery 'Activity.onActivityResult').
    */
   public static void pickImageFromGallery(Activity activity, int requestCode) {
      if (!checkReadStoragePermission(activity, requestCode))
         return;

      Intent gallery = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
      gallery.setType("image/*");
      activity.startActivityForResult(gallery, requestCode);
   }


}
