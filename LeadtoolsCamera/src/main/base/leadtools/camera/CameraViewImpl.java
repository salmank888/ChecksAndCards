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

import android.view.View;

import java.util.Set;
import java.util.SortedSet;

abstract class CameraViewImpl {

   protected final Callback mCallback;

   protected final PreviewImpl mPreview;

   protected Integer mQueuedDisplayOrientation;

   protected int mCameraSensorOrientation;

   protected int mDisplayOrientation;

   CameraViewImpl(Callback callback, PreviewImpl preview) {
      mCallback = callback;
      mPreview = preview;
   }

   View getView() {
      return mPreview.getView();
   }

   int getPreviewBufferRotation() {
      return mCameraSensorOrientation - mDisplayOrientation;
   }

   // Looks for closest aspect ratio to baseline in ratios, then sees if picture sizes exist in that AspectRatio
   // If they do, takes the largest of those available. If they don't, restart recursively at next closest AspectRatio
   Size findSizeWithSimilarAspectRatio(AspectRatio baseline, Set<AspectRatio> ratios, SizeMap pictureSizes){
      float baselineRatio = (float)baseline.getX() / (float)baseline.getY();
      float bestDiff = Float.MAX_VALUE;
      AspectRatio closest = null;
      for(AspectRatio test : ratios){
         float testRatio = (float)test.getX() / (float)test.getY();
         float diff = Math.abs(baselineRatio - testRatio);
         if(diff < bestDiff){
            bestDiff = diff;
            closest = test;
         }
      }
      SortedSet<Size> potentialSizes = pictureSizes.sizes(closest);
      if(potentialSizes != null){
         return potentialSizes.last();
      }
      else{
         boolean res = ratios.remove(closest);
         if(res){
            return findSizeWithSimilarAspectRatio(baseline, ratios, pictureSizes);
         }
         else{
            // execution should never enter this code path, but this is a safety measure for a potential
            // infinite loop if ratios.remove fails and returns false
            return null;
         }
      }
   }

   abstract void start();

   abstract void stop();

   abstract boolean isCameraOpened();

   abstract void setFacing(int facing);

   abstract int getFacing();

   abstract Set<AspectRatio> getSupportedAspectRatios();

   abstract void setAspectRatio(AspectRatio ratio);

   abstract AspectRatio getAspectRatio();

   abstract void setAutoFocus(boolean autoFocus);

   abstract boolean getAutoFocus();

   abstract void setFlash(int flash);

   abstract int getFlash();

   abstract boolean hasFlash();

   abstract void setMaxPreviewSizeMP(float maxPreviewSizeMP);

   abstract float getMaxPreviewSizeMP();

   abstract void setMaxPreviewImages(int maxPreviewImages);

   abstract int getMaxPreviewImages();

   abstract void setCaptureMode(int captureMode);

   abstract int getCaptureMode();

   abstract void takePicture();

   abstract void setDisplayOrientation(int displayOrientation);

   abstract void queueDisplayOrientationChange(int displayOrientation);

   abstract boolean hasMultipleCameras();

   interface Callback {

      void onCameraOpened();

      void onCameraClosed();

      //Always JPEG
      void onPictureTaken(byte[] data);

      void onPreviewFrame(LeadSize surfaceSize);

   }

}
