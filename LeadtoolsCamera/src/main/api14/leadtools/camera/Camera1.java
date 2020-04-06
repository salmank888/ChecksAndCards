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

import android.annotation.SuppressLint;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.os.Build;
import android.os.Handler;
import android.support.v4.util.SparseArrayCompat;
import android.util.Log;
import android.view.SurfaceHolder;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;

@SuppressWarnings("deprecation")
class Camera1
        extends CameraViewImpl
{

    private static final int INVALID_CAMERA_ID = -1;

    private static final SparseArrayCompat<String> FLASH_MODES = new SparseArrayCompat<>();

    static
    {
        FLASH_MODES.put(Constants.FLASH_OFF, Camera.Parameters.FLASH_MODE_OFF);
        FLASH_MODES.put(Constants.FLASH_ON, Camera.Parameters.FLASH_MODE_ON);
        FLASH_MODES.put(Constants.FLASH_TORCH, Camera.Parameters.FLASH_MODE_TORCH);
        FLASH_MODES.put(Constants.FLASH_AUTO, Camera.Parameters.FLASH_MODE_AUTO);
        FLASH_MODES.put(Constants.FLASH_RED_EYE, Camera.Parameters.FLASH_MODE_RED_EYE);
    }

    private int mCameraId;

    private Camera mCamera;

    private Camera.Parameters mCameraParameters;

    private final Camera.CameraInfo mCameraInfo = new Camera.CameraInfo();

    private final SizeMap mPreviewSizes = new SizeMap();

    private final SizeMap mPictureSizes = new SizeMap();

    private AspectRatio mAspectRatio;

    private boolean mShowingPreview;

    private boolean mAutoFocus;

    private int mFacing;

    private int mFlash;

    private float mMaxPreviewSizeMP = Constants.DEFAULT_MAX_PREVIEW_SIZE_MP;

    private int mMaxPreviewImages = Constants.DEFAULT_MAX_PREVIEW_IMAGES;

    private int mCaptureMode = Constants.CONTINUOUS;

    private static final String TAG = "Camera1";

    private boolean mHasPreviewedFrame;

    private Camera.PreviewCallback mPreviewCallback = new Camera.PreviewCallback()
    {
        @Override
        public void onPreviewFrame(byte[] bytes, Camera camera)
        {
            mHasPreviewedFrame = true;
            try
            {
                if (camera == null) return;
                Camera.Parameters parameters = camera.getParameters();
                Camera.Size previewSize = parameters.getPreviewSize();

                mCallback.onPreviewFrame(new LeadSize(mPreview.getView()
                                                              .getMeasuredWidth(),
                                                      mPreview.getView()
                                                              .getMeasuredHeight(), bytes,
                                                      mPreview.getWidth(), mPreview.getHeight()));
            }
            catch (Exception ex)
            {
                Log.e(TAG, ex.getMessage());
            }
        }
    };

    Camera1(Callback callback, PreviewImpl preview)
    {
        super(callback, preview);
        mCameraSensorOrientation = 90;
        preview.setCallback(new PreviewImpl.Callback()
        {
            @Override
            public void onSurfaceChanged()
            {
                if (mCamera != null)
                {
                    setUpPreview();
                    adjustCameraParameters();
                    if (mQueuedDisplayOrientation != null)
                    {
                        setDisplayOrientation(mQueuedDisplayOrientation);
                        mQueuedDisplayOrientation = null;
                    }
                    setPreviewCallback();
                }
            }
        });
    }

    @Override
    void start()
    {
        chooseCamera();
        openCamera();
        if (mPreview.isReady())
        {
            setUpPreview();
        }
        mShowingPreview = true;
        if (mCaptureMode == Constants.CONTINUOUS)
            setPreviewCallback();
        mCamera.startPreview();

        // Below is a dirty fix for an unrecoverable event on some devices where
        // the preview callback is never called. Lack of memory nor workflow seem to be
        // the cause of the issue. Debugger is not helpful.
        // Offending devices: Samsung Galaxy Grand Prime, Moto E2 2015
        new Handler().postDelayed(new Runnable()
        {
            @Override
            public void run()
            {
                if (!mHasPreviewedFrame && mCaptureMode == Constants.CONTINUOUS)
                {
                    Log.e(TAG, "Camera preview callback not called. Recovering...");
                    stop();
                    start();
                }
            }
        }, 3000);
    }

    private void setPreviewCallback()
    {
        mCamera.setPreviewCallback(mPreviewCallback);
    }

    @Override
    void stop()
    {
        if (mCamera != null)
        {
            mCamera.stopPreview();
        }
        mShowingPreview = false;
        mHasPreviewedFrame = false;
        releaseCamera();
    }

    @SuppressLint("NewApi") // Suppresses Camera#setPreviewTexture
    private void setUpPreview()
    {
        try
        {
            if (mPreview.getOutputClass() == SurfaceHolder.class)
            {
                final boolean needsToStopPreview = mShowingPreview &&
                        Build.VERSION.SDK_INT < Build.VERSION_CODES.ICE_CREAM_SANDWICH;
                if (needsToStopPreview)
                {
                    mCamera.stopPreview();
                }
                mCamera.setPreviewDisplay(mPreview.getSurfaceHolder());
                if (needsToStopPreview)
                {
                    mCamera.startPreview();
                }
            }
            else
            {
                mCamera.setPreviewTexture((SurfaceTexture)mPreview.getSurfaceTexture());
            }
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }
    }

    @Override
    boolean isCameraOpened()
    {
        return mCamera != null;
    }

    @Override
    void setFacing(int facing)
    {
        if (mFacing == facing)
        {
            return;
        }
        mFacing = facing;
        if (isCameraOpened())
        {
            stop();
            start();
        }
    }

    @Override
    int getFacing()
    {
        return mFacing;
    }

    @Override
    Set<AspectRatio> getSupportedAspectRatios()
    {
        return mPreviewSizes.ratios();
    }

    @Override
    void setAspectRatio(AspectRatio ratio)
    {
        if (mAspectRatio == null || !isCameraOpened())
        {
            // Handle this later when camera is opened
            mAspectRatio = ratio;
        }
        else if (!mAspectRatio.equals(ratio))
        {
            final Set<Size> sizes = mPreviewSizes.sizes(ratio);
            if (sizes == null)
            {
                throw new UnsupportedOperationException(ratio + " is not supported");
            }
            else
            {
                mAspectRatio = ratio;
                adjustCameraParameters();
            }
        }
    }

    @Override
    AspectRatio getAspectRatio()
    {
        return mAspectRatio;
    }

    @Override
    void setMaxPreviewSizeMP(float maxPreviewSizeMP)
    {
        if (maxPreviewSizeMP > 0.0f)
        {
            mMaxPreviewSizeMP = maxPreviewSizeMP;
        }
        if (isCameraOpened())
            adjustCameraParameters();
    }

    @Override
    float getMaxPreviewSizeMP()
    {
        return mMaxPreviewSizeMP;
    }

    @Override
    void setMaxPreviewImages(int maxPreviewImages)
    {
        // no-op, Camera1 images are tied to Java primitive byte arrays
        // on Dalvik/ART heap that are subject to GC, not native buffers
        // that need to be deallocated manually
    }

    @Override
    int getMaxPreviewImages()
    {
        return mMaxPreviewImages;
    }


    @Override
    void setAutoFocus(boolean autoFocus)
    {
        if (mAutoFocus == autoFocus)
        {
            return;
        }
        if (setAutoFocusInternal(autoFocus))
        {
            mCamera.setParameters(mCameraParameters);
        }
    }

    @Override
    boolean getAutoFocus()
    {
        if (!isCameraOpened())
        {
            return mAutoFocus;
        }
        String focusMode = mCameraParameters.getFocusMode();
        return focusMode != null && focusMode.contains("continuous");
    }

    @Override
    void setFlash(int flash)
    {
        if (flash == mFlash)
        {
            return;
        }
        if (setFlashInternal(flash))
        {
            mCamera.setParameters(mCameraParameters);
        }
    }

    @Override
    int getFlash()
    {
        return mFlash;
    }

    @Override
    void takePicture()
    {
        if (!isCameraOpened())
        {
            throw new IllegalStateException(
                    "Camera is not ready. Call start() before takePicture().");
        }
        if (getAutoFocus())
        {
            mCamera.cancelAutoFocus();
            mCamera.autoFocus(new Camera.AutoFocusCallback()
            {
                @Override
                public void onAutoFocus(boolean success, Camera camera)
                {
                    takePictureInternal();
                }
            });
        }
        else
        {
            takePictureInternal();
        }
    }

    private void takePictureInternal()
    {
        mCamera.takePicture(null, null, null, new Camera.PictureCallback()
        {
            @Override
            public void onPictureTaken(byte[] data, Camera camera)
            {
                mCallback.onPictureTaken(data);
                if (mCamera != null)
                {
                    mCamera.startPreview();
                }
            }
        });
    }

    @Override
    void setDisplayOrientation(int displayOrientation)
    {
        mDisplayOrientation = displayOrientation;
        if (isCameraOpened())
        {
            int cameraRotation = calcCameraRotation(displayOrientation);
            mCameraParameters.setRotation(cameraRotation);
            mCamera.setParameters(mCameraParameters);
            if (mShowingPreview)
            {
                mCamera.stopPreview();
            }
            mCamera.setDisplayOrientation(cameraRotation);
            if (mShowingPreview)
            {
                mCamera.startPreview();
            }
        }
    }

    @Override
    void queueDisplayOrientationChange(int displayOrientation)
    {
        mQueuedDisplayOrientation = displayOrientation;
    }

    @Override
    void setCaptureMode(int captureMode)
    {
        int oldCaptureMode = mCaptureMode;
        mCaptureMode = captureMode;
        if (isCameraOpened() && mCaptureMode != oldCaptureMode)
        {
            stop();
            start();
        }
    }

    @Override
    int getCaptureMode()
    {
        return mCaptureMode;
    }

    @Override
    boolean hasFlash()
    {
        if (mCameraParameters != null)
        {
            List<String> flashModes = mCameraParameters.getSupportedFlashModes();
            return flashModes.contains(Camera.Parameters.FLASH_MODE_AUTO) ||
                    flashModes.contains(Camera.Parameters.FLASH_MODE_ON) ||
                    flashModes.contains(Camera.Parameters.FLASH_MODE_RED_EYE) ||
                    flashModes.contains(Camera.Parameters.FLASH_MODE_TORCH);
        }
        return true;
    }

    @Override
    boolean hasMultipleCameras()
    {
        return Camera.getNumberOfCameras() > 1;
    }

    /**
     * This rewrites {@link #mCameraId} and {@link #mCameraInfo}.
     */
    private void chooseCamera()
    {
        for (int i = 0, count = Camera.getNumberOfCameras(); i < count; i++)
        {
            Camera.getCameraInfo(i, mCameraInfo);
            if (mCameraInfo.facing == mFacing)
            {
                mCameraId = i;
                return;
            }
        }
        mCameraId = INVALID_CAMERA_ID;
    }

    private void openCamera()
    {
        if (mCamera != null)
        {
            releaseCamera();
        }
        mCamera = Camera.open(mCameraId);
        mCameraParameters = mCamera.getParameters();
        // Supported preview sizes
        mPreviewSizes.clear();
        for (Camera.Size size : mCameraParameters.getSupportedPreviewSizes())
        {
            mPreviewSizes.add(new Size(size.width, size.height));
        }
        // Supported picture sizes;
        mPictureSizes.clear();
        for (Camera.Size size : mCameraParameters.getSupportedPictureSizes())
        {
            mPictureSizes.add(new Size(size.width, size.height));
        }
        // AspectRatio
        if (mAspectRatio == null)
        {
            mAspectRatio = Constants.DEFAULT_ASPECT_RATIO;
        }
        adjustCameraParameters();
        mCamera.setDisplayOrientation(calcCameraRotation(mDisplayOrientation));
        mCallback.onCameraOpened();
    }

    private AspectRatio chooseAspectRatio()
    {
        AspectRatio r = null;
        for (AspectRatio ratio : mPreviewSizes.ratios())
        {
            r = ratio;
            if (ratio.equals(Constants.DEFAULT_ASPECT_RATIO))
            {
                return ratio;
            }
        }
        return r;
    }

    private void adjustCameraParameters()
    {
        SortedSet<Size> previewSizes = mPreviewSizes.sizes(mAspectRatio);
        if (previewSizes == null)
        { // Not supported
            mAspectRatio = chooseAspectRatio();
            previewSizes = mPreviewSizes.sizes(mAspectRatio);
        }
        Size previewSize = chooseOptimalSize(previewSizes);
        SortedSet<Size> pictureSizes = mPictureSizes.sizes(mAspectRatio);
        Size pictureSize = null;
        if (pictureSizes != null)
        {
            pictureSize = pictureSizes.last();
        }
        else
        {
            Set<AspectRatio> ratios = mPictureSizes.ratios();
            pictureSize = findSizeWithSimilarAspectRatio(mAspectRatio, ratios, mPictureSizes);
        }

        // Largest picture size in this ratio
        if (mShowingPreview)
        {
            mCamera.stopPreview();
        }
        mCameraParameters.setPreviewSize(previewSize.getWidth(), previewSize.getHeight());
        mCameraParameters.setPictureSize(pictureSize.getWidth(), pictureSize.getHeight());
        mCameraParameters.setRotation(calcCameraRotation(mDisplayOrientation));
        setAutoFocusInternal(mAutoFocus);
        setFlashInternal(mFlash);
        mCamera.setParameters(mCameraParameters);
        if (mShowingPreview)
        {
            mCamera.startPreview();
        }

    }

    @SuppressWarnings("SuspiciousNameCombination")
    private Size chooseOptimalSize(SortedSet<Size> sizes)
    {
        if (!mPreview.isReady())
        { // Not yet laid out
            return sizes.first(); // Return the smallest size
        }
        int desiredWidth;
        int desiredHeight;
        final int surfaceWidth = mPreview.getWidth();
        final int surfaceHeight = mPreview.getHeight();
        if (mDisplayOrientation == 90 || mDisplayOrientation == 270)
        {
            desiredWidth = surfaceHeight;
            desiredHeight = surfaceWidth;
        }
        else
        {
            desiredWidth = surfaceWidth;
            desiredHeight = surfaceHeight;
        }

        Size best = null;
        for (Size size : sizes)
        { // Iterate from small to large
            if (CameraView.MPFromResolution(size.getWidth(), size.getHeight()) <= mMaxPreviewSizeMP)
            {
                best = size;
            }
        }

        if (best != null)
            return best;

        Size result = null;
        for (Size size : sizes)
        { // Iterate from small to large
            if (desiredWidth <= size.getWidth() && desiredHeight <= size.getHeight())
            {
                return size;
            }
            result = size;
        }
        return result;
    }

    private void releaseCamera()
    {
        if (mCamera != null)
        {
            mCamera.setPreviewCallback(null);
            // Not needed
//         SurfaceHolder holder = mPreview.getSurfaceHolder();
//         if (holder != null) {
//            holder.removeCallback(mPreview.getSurfaceHolderCallback());
//         }
            mCamera.release();
            mCamera = null;
            mCallback.onCameraClosed();
        }
    }

    private int calcCameraRotation(int rotation)
    {
        if (mCameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_FRONT)
        {
            return (360 - (mCameraInfo.orientation + rotation) % 360) % 360;
        }
        else
        {  // back-facing
            return (mCameraInfo.orientation - rotation + 360) % 360;
        }
    }

    /**
     * @return {@code true} if {@link #mCameraParameters} was modified.
     */
    private boolean setAutoFocusInternal(boolean autoFocus)
    {
        mAutoFocus = autoFocus;
        if (isCameraOpened())
        {
            final List<String> modes = mCameraParameters.getSupportedFocusModes();
            if (autoFocus && modes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE))
            {
                mCameraParameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
            }
            else if (modes.contains(Camera.Parameters.FOCUS_MODE_FIXED))
            {
                mCameraParameters.setFocusMode(Camera.Parameters.FOCUS_MODE_FIXED);
            }
            else if (modes.contains(Camera.Parameters.FOCUS_MODE_INFINITY))
            {
                mCameraParameters.setFocusMode(Camera.Parameters.FOCUS_MODE_INFINITY);
            }
            else
            {
                mCameraParameters.setFocusMode(modes.get(0));
            }
            return true;
        }
        else
        {
            return false;
        }
    }

    /**
     * @return {@code true} if {@link #mCameraParameters} was modified.
     */
    private boolean setFlashInternal(int flash)
    {
        if (isCameraOpened())
        {
            List<String> modes = mCameraParameters.getSupportedFlashModes();
            String mode = FLASH_MODES.get(flash);
            if (modes != null && modes.contains(mode))
            {
                mCameraParameters.setFlashMode(mode);
                mFlash = flash;
                return true;
            }
            String currentMode = FLASH_MODES.get(mFlash);
            if (modes == null || !modes.contains(currentMode))
            {
                mCameraParameters.setFlashMode(Camera.Parameters.FLASH_MODE_OFF);
                mFlash = Constants.FLASH_OFF;
                return true;
            }
            return false;
        }
        else
        {
            mFlash = flash;
            return false;
        }
    }

}
