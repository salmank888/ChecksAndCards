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

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.os.Build;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;

@TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
class TextureViewPreview extends PreviewImpl {

   private TextureView mTextureView;

   private int mDisplayOrientation;

   private Context mContext;

   private ViewGroup mParent;

   TextureView.SurfaceTextureListener mSurfaceTextureListener = new TextureView.SurfaceTextureListener(){
      @Override
      public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
         setSize(width, height);
         configureTransform();
         dispatchSurfaceChanged();
      }

      @Override
      public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
         setSize(width, height);
         configureTransform();
         dispatchSurfaceChanged();
      }


      /*
         NOTE: Certain Samsung devices do not implement this method correctly.

         After returning true, they DO NOT free the SurfaceTexture. If capture
         is restarted, the SurfaceTexture will already be locked

       */
      @Override
      public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
         setSize(0, 0);
         return true;
      }

      @Override
      public void onSurfaceTextureUpdated(SurfaceTexture surface) {
      }
   };

   TextureViewPreview(Context context, ViewGroup parent) {
      mContext = context;
      mParent = parent;
      rebuildView();
   }

   void rebuildView(){
      mTextureView = null;
      mParent.removeAllViews();
      View view = View.inflate(mContext, R.layout.texture_view, mParent);
      mTextureView = (TextureView) view.findViewById(R.id.texture_view);
      mTextureView.setSurfaceTextureListener(mSurfaceTextureListener);
   }


   @TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH_MR1)
   @Override
   void setBufferSize(int width, int height) {
      // This method is called only from Camera2.
      mTextureView.getSurfaceTexture().setDefaultBufferSize(width, height);
   }

   @Override
   Surface getSurface() {
      return new Surface(mTextureView.getSurfaceTexture());
   }

   @Override
   SurfaceTexture getSurfaceTexture() {
      return mTextureView.getSurfaceTexture();
   }

   @Override
   View getView() {
      return mTextureView;
   }

   @Override
   Class getOutputClass() {
      return SurfaceTexture.class;
   }

   @Override
   void setDisplayOrientation(int displayOrientation) {
      mDisplayOrientation = displayOrientation;
      configureTransform();
   }

   @Override
   boolean isReady() {
      return mTextureView.getSurfaceTexture() != null;
   }

   /**
    * Configures the transform matrix for TextureView based on {@link #mDisplayOrientation} and
    * the surface size.
    */
   private void configureTransform() {
      Matrix matrix = new Matrix();
      if (mDisplayOrientation % 180 == 90) {
         final int width = getWidth();
         final int height = getHeight();
         // Rotate the camera preview when the screen is landscape.
         matrix.setPolyToPoly(
               new float[]{
                     0.f, 0.f, // top left
                     width, 0.f, // top right
                     0.f, height, // bottom left
                     width, height, // bottom right
               }, 0,
               mDisplayOrientation == 90 ?
                     // Clockwise
                     new float[]{
                           0.f, height, // top left
                           0.f, 0.f, // top right
                           width, height, // bottom left
                           width, 0.f, // bottom right
                     }
                     : // mDisplayOrientation == 270
                     // Counter-clockwise
                     new float[]{
                           width, 0.f, // top left
                           width, height, // top right
                           0.f, 0.f, // bottom left
                           0.f, height, // bottom right
                     }, 0,
               4);
      }
      mTextureView.setTransform(matrix);
   }

}
