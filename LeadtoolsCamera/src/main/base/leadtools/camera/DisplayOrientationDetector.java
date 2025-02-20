/*
 * Copyright (C) 2016 The Android Open Source Project
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

/*
 * NOTICE:
 * This source file has been modified from its original version by LEAD Technologies, Inc.
 */

package leadtools.camera;

import android.content.Context;
import android.content.res.Configuration;
import android.util.SparseIntArray;
import android.view.Display;
import android.view.OrientationEventListener;
import android.view.Surface;
import android.view.WindowManager;


/**
 * Monitors the value returned from {@link Display#getRotation()}.
 */
abstract class DisplayOrientationDetector {

   private final OrientationEventListener mOrientationEventListener;

   /**
    * Mapping from Surface.Rotation_n to degrees.
    */
   private static final SparseIntArray DISPLAY_ORIENTATIONS = new SparseIntArray();

   static {
      DISPLAY_ORIENTATIONS.put(Surface.ROTATION_0, 0);
      DISPLAY_ORIENTATIONS.put(Surface.ROTATION_90, 90);
      DISPLAY_ORIENTATIONS.put(Surface.ROTATION_180, 180);
      DISPLAY_ORIENTATIONS.put(Surface.ROTATION_270, 270);
   }

   private Display mDisplay;

   private int mLastKnownDisplayOrientation = 0;

   private Context mContext;

   public DisplayOrientationDetector(Context context) {
      mContext = context;
      mOrientationEventListener = new OrientationEventListener(mContext) {

         /** This is either Surface.Rotation_0, _90, _180, _270, or -1 (invalid). */
         private int mLastKnownRotation = -1;

         @Override
         public void onOrientationChanged(int orientation) {
            if (orientation == OrientationEventListener.ORIENTATION_UNKNOWN ||
                  mDisplay == null) {
               return;
            }
            final int rotation = mDisplay.getRotation();
            if (mLastKnownRotation != rotation) {
               mLastKnownRotation = rotation;
               dispatchOnDisplayOrientationChanged(DISPLAY_ORIENTATIONS.get(rotation));
            }
         }
      };
   }

   public void enable(Display display) {
      mDisplay = display;
      mOrientationEventListener.enable();
      // Immediately dispatch the first callback
      dispatchOnDisplayOrientationChanged(DISPLAY_ORIENTATIONS.get(display.getRotation()));
   }

   public void disable() {
      mOrientationEventListener.disable();
      mDisplay = null;
   }

   public int getLastKnownDisplayOrientation() {
      return mLastKnownDisplayOrientation;
   }

   private void dispatchOnDisplayOrientationChanged(int displayOrientation) {
      mLastKnownDisplayOrientation = displayOrientation;
      onDisplayOrientationChanged(displayOrientation);
   }

   public int getDeviceDefaultOrientation() {

      WindowManager windowManager =  (WindowManager) mContext.getSystemService(Context.WINDOW_SERVICE);

      Configuration config = mContext.getResources().getConfiguration();

      int rotation = windowManager.getDefaultDisplay().getRotation();

      if ( ((rotation == Surface.ROTATION_0 || rotation == Surface.ROTATION_180) &&
            config.orientation == Configuration.ORIENTATION_LANDSCAPE)
            || ((rotation == Surface.ROTATION_90 || rotation == Surface.ROTATION_270) &&
            config.orientation == Configuration.ORIENTATION_PORTRAIT)) {
         return Configuration.ORIENTATION_LANDSCAPE;
      } else {
         return Configuration.ORIENTATION_PORTRAIT;
      }
   }


   /**
    * Called when display orientation is changed.
    *
    * @param displayOrientation One of 0, 90, 180, and 270.
    */
   public abstract void onDisplayOrientationChanged(int displayOrientation);

}
