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
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.NonNull;
import android.support.annotation.RequiresApi;
import android.util.Log;
import android.util.SparseIntArray;
import android.view.Surface;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Set;
import java.util.SortedSet;

@TargetApi(Build.VERSION_CODES.LOLLIPOP)
class Camera2
        extends CameraViewImpl
{

    private static final String TAG = "Camera2";

    private static final SparseIntArray INTERNAL_FACINGS = new SparseIntArray();

    static
    {
        INTERNAL_FACINGS.put(Constants.FACING_BACK, CameraCharacteristics.LENS_FACING_BACK);
        INTERNAL_FACINGS.put(Constants.FACING_FRONT, CameraCharacteristics.LENS_FACING_FRONT);
    }

    private final CameraManager mCameraManager;

    private final CameraDevice.StateCallback mCameraDeviceCallback
            = new CameraDevice.StateCallback()
    {

        @Override
        public void onOpened(@NonNull CameraDevice camera)
        {
            mCameraDevice = camera;
            startCaptureSession();
            mCallback.onCameraOpened();
        }

        @Override
        public void onClosed(@NonNull CameraDevice camera)
        {
            mCallback.onCameraClosed();
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice camera)
        {
            mCameraDevice = null;
        }

        @Override
        public void onError(@NonNull CameraDevice camera, int error)
        {
            Log.e(TAG, "onError: " + camera.getId() + " (" + error + ")");
            //Workaround for some 5.0/5.1 devices
            //https://github.com/googlesamples/android-Camera2Video/issues/2
            if (!(mConfigFailureCount >= 2))
            {
                stop();
                mCallback.onCameraClosed();
                start();
                mCallback.onCameraOpened();
            }
        }

    };

    private final CameraCaptureSession.StateCallback mSessionCallback
            = new CameraCaptureSession.StateCallback()
    {

        @Override
        public void onConfigured(@NonNull CameraCaptureSession session)
        {
            if (mCameraDevice == null)
            {
                return;
            }
            mCaptureSession = session;
            updateAutoFocus();
            updateFlash();
            try
            {
                HandlerThread backgroundThread = new HandlerThread("CameraPreview");
                backgroundThread.start();
                mBackgroundHandler = new Handler(backgroundThread.getLooper());
                if (mCaptureMode == Constants.SINGLE)
                {
                    mCaptureSession.setRepeatingRequest(mPreviewRequestBuilder.build(),
                                                        mCaptureCallback, mBackgroundHandler);
                }
                else
                {
                    mCaptureSession.setRepeatingRequest(mPreviewRequestBuilder.build(),
                                                        null, mBackgroundHandler);
                }
            }
            catch (CameraAccessException e)
            {
                Log.e(TAG, "Failed to start camera preview because it couldn't access camera", e);
            }
            catch (IllegalStateException e)
            {
                Log.e(TAG, "Failed to start camera preview.", e);
            }
            mConfigFailureCount = 0;
        }

        @Override
        public void onConfigureFailed(@NonNull CameraCaptureSession session)
        {
            Log.e(TAG, "Failed to configure capture session.");
            mConfigFailureCount++;
        }

        @Override
        public void onClosed(@NonNull CameraCaptureSession session)
        {
            if (mCaptureSession != null && mCaptureSession.equals(session))
            {
                mCaptureSession = null;
            }
            if (mBackgroundHandler != null)
            {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2)
                {
                    mBackgroundHandler.getLooper()
                                      .quitSafely();
                }
                else
                {
                    mBackgroundHandler.getLooper()
                                      .quit();
                }
                mBackgroundHandler = null;
            }
        }

    };


    private PictureCaptureCallback mCaptureCallback = new PictureCaptureCallback()
    {

        @Override
        public void onPrecaptureRequired()
        {
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER,
                                       CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_START);
            setState(STATE_PRECAPTURE);
            try
            {
                mCaptureSession.capture(mPreviewRequestBuilder.build(), this, null);
                mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER,
                                           CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_IDLE);
            }
            catch (CameraAccessException e)
            {
                Log.e(TAG, "Failed to run precapture sequence.", e);
            }
        }

        @Override
        public void onReady()
        {
            captureStillPicture();
        }

    };

    private final ImageReader.OnImageAvailableListener mOnImageAvailableListener
            = new ImageReader.OnImageAvailableListener()
    {

        @Override
        public void onImageAvailable(ImageReader reader)
        {
            try (Image image = reader.acquireNextImage())
            {
                Image.Plane[] planes = image.getPlanes();
                if (planes.length > 0)
                {
                    ByteBuffer buffer = planes[0].getBuffer();
                    byte[] data = new byte[buffer.remaining()];
                    buffer.get(data);
                    mCallback.onPictureTaken(data);
                }
            }
        }

    };


    private String mCameraId;

    private CameraCharacteristics mCameraCharacteristics;

    private CameraDevice mCameraDevice;

    private CameraCaptureSession mCaptureSession;

    private CaptureRequest.Builder mPreviewRequestBuilder;

    private ImageReader mImageReaderSingle;

    private ImageReader mImageReaderContinuous;

    private final SizeMap mPreviewSizes = new SizeMap();

    private final SizeMap mPictureSizes = new SizeMap();

    private int mFacing;

    private AspectRatio mAspectRatio = Constants.DEFAULT_ASPECT_RATIO;

    private boolean mAutoFocus;

    private int mFlash;

    private float mMaxPreviewSizeMP = Constants.DEFAULT_MAX_PREVIEW_SIZE_MP;

    private int mMaxPreviewImages = Constants.DEFAULT_MAX_PREVIEW_IMAGES;

    private int mOpenImages = 0;

    private Handler mBackgroundHandler;

    private int mConfigFailureCount = 0;

    private int mCaptureMode = Constants.CONTINUOUS;

    Camera2(Callback callback, PreviewImpl preview, Context context)
    {
        super(callback, preview);
        mCameraManager = (CameraManager)context.getSystemService(Context.CAMERA_SERVICE);
        mPreview.setCallback(new PreviewImpl.Callback()
        {
            @Override
            public void onSurfaceChanged()
            {
                if (mQueuedDisplayOrientation != null)
                {
                    setDisplayOrientation(mQueuedDisplayOrientation);
                    mQueuedDisplayOrientation = null;
                }
                startCaptureSession();
            }
        });
    }

    @Override
    void start()
    {
        chooseCameraIdByFacing();
        collectCameraInfo();
        prepareImageReaders();
        startOpeningCamera();
    }

    @Override
    void stop()
    {

        if (mCaptureSession != null)
        {
            try
            {
                mCaptureSession.stopRepeating();
                mCaptureSession.abortCaptures();
            }
            catch (CameraAccessException ex)
            {
                Log.e(TAG, ex.getMessage());
            }
            mCaptureSession.close();
            mCaptureSession = null;
        }
        if (mCameraDevice != null)
        {
            mCameraDevice.close();
            mCameraDevice = null;
        }
        if (mImageReaderSingle != null)
        {
            mImageReaderSingle.close();
            mImageReaderSingle = null;
        }

        if (mImageReaderContinuous != null)
        {
            mImageReaderContinuous.close();
            mImageReaderContinuous = null;
        }

        if (mPreview instanceof TextureViewPreview && CameraView.isSamsungDevice())
        {
            ((TextureViewPreview)mPreview).rebuildView();
        }
    }

    @Override
    boolean isCameraOpened()
    {
        return mCameraDevice != null;
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
        chooseCameraIdByFacing();
        collectCameraInfo();
        if (ratio == null || ratio.equals(mAspectRatio) ||
                !mPreviewSizes.ratios()
                              .contains(ratio))
        {
            // TODO: Better error handling
            return;
        }
        mAspectRatio = ratio;
        if (mCaptureSession != null)
        {
            mCaptureSession.close();
            mCaptureSession = null;
            startCaptureSession();
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
        {
            stop();
            start();
        }
    }

    @Override
    float getMaxPreviewSizeMP()
    {
        return mMaxPreviewSizeMP;
    }

    @Override
    void setMaxPreviewImages(int maxPreviewImages)
    {
        if (maxPreviewImages < 2)
            return;
        mMaxPreviewImages = maxPreviewImages;
        if (isCameraOpened())
        {
            stop();
            start();
        }
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
        mAutoFocus = autoFocus;
        if (mPreviewRequestBuilder != null)
        {
            updateAutoFocus();
            if (mCaptureSession != null)
            {
                try
                {
                    mCaptureSession.setRepeatingRequest(mPreviewRequestBuilder.build(),
                                                        mCaptureCallback, mBackgroundHandler);
                }
                catch (CameraAccessException e)
                {
                    mAutoFocus = !mAutoFocus; // Revert
                }
            }
        }
    }

    @Override
    boolean getAutoFocus()
    {
        return mAutoFocus;
    }

    @Override
    void setFlash(int flash)
    {
        if (mFlash == flash)
        {
            return;
        }
        int saved = mFlash;
        mFlash = flash;
        if (mPreviewRequestBuilder != null)
        {
            updateFlash();
            if (mCaptureSession != null)
            {
                try
                {
                    mCaptureSession.setRepeatingRequest(mPreviewRequestBuilder.build(),
                                                        mCaptureCallback, mBackgroundHandler);
                }
                catch (CameraAccessException e)
                {
                    mFlash = saved; // Revert
                }
            }
        }
    }

    @Override
    int getFlash()
    {
        return mFlash;
    }

    @Override
    boolean hasFlash()
    {
        try
        {
            for (String cameraId : mCameraManager.getCameraIdList())
            {
                CameraCharacteristics chars = mCameraManager.getCameraCharacteristics(cameraId);
                Boolean res = chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
                if (res != null && res)
                    return true;
            }
            return false;
        }
        catch (CameraAccessException ex)
        {
            return false;
        }
    }

    @Override
    boolean hasMultipleCameras()
    {
        try
        {
            return mCameraManager.getCameraIdList().length > 1;
        }
        catch (CameraAccessException ex)
        {
            return false;
        }
    }

    @Override
    void takePicture()
    {
        if (mAutoFocus)
        {
            lockFocus();
        }
        else
        {
            captureStillPicture();
        }
    }

    @Override
    void queueDisplayOrientationChange(int displayOrientation)
    {
        mQueuedDisplayOrientation = displayOrientation;
    }

    @Override
    void setDisplayOrientation(int displayOrientation)
    {
        mDisplayOrientation = displayOrientation;
        mPreview.setDisplayOrientation(mDisplayOrientation);
    }

    /**
     * <p>Chooses a camera ID by the specified camera facing ({@link #mFacing}).</p>
     * <p>This rewrites {@link #mCameraId}, {@link #mCameraCharacteristics}, and optionally
     * {@link #mFacing}.</p>
     */
    private void chooseCameraIdByFacing()
    {
        try
        {
            int internalFacing = INTERNAL_FACINGS.get(mFacing);
            final String[] ids = mCameraManager.getCameraIdList();
            if (ids.length == 0)
            { // No camera
                throw new RuntimeException("No camera available.");
            }

            for (String id : ids)
            {
                CameraCharacteristics characteristics = mCameraManager.getCameraCharacteristics(id);
                Integer internal = characteristics.get(CameraCharacteristics.LENS_FACING);
                if (internal == null)
                {
                    throw new NullPointerException("Unexpected state: LENS_FACING null");
                }
                if (internal == internalFacing)
                {
                    mCameraId = id;
                    mCameraCharacteristics = characteristics;
                    return;
                }
            }
            // Not found
            mCameraId = ids[0];
            mCameraCharacteristics = mCameraManager.getCameraCharacteristics(mCameraId);
            Integer internal = mCameraCharacteristics.get(CameraCharacteristics.LENS_FACING);
            if (internal == null)
            {
                throw new NullPointerException("Unexpected state: LENS_FACING null");
            }
            for (int i = 0, count = INTERNAL_FACINGS.size(); i < count; i++)
            {
                if (INTERNAL_FACINGS.valueAt(i) == internal)
                {
                    mFacing = INTERNAL_FACINGS.keyAt(i);
                    return;
                }
            }
            // The operation can reach here when the only camera device is an external one.
            // We treat it as facing back.
            mFacing = Constants.FACING_BACK;
        }
        catch (CameraAccessException e)
        {
            throw new RuntimeException("Failed to get a list of camera devices", e);
        }
    }

    /**
     * <p>Collects some information from {@link #mCameraCharacteristics}.</p>
     * <p>This rewrites {@link #mPreviewSizes}, {@link #mPictureSizes}, and optionally,
     * {@link #mAspectRatio}.</p>
     */
    private void collectCameraInfo()
    {
        StreamConfigurationMap map = mCameraCharacteristics.get(
                CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        if (map == null)
        {
            throw new IllegalStateException("Failed to get configuration map: " + mCameraId);
        }
        mPreviewSizes.clear();
        for (android.util.Size size : map.getOutputSizes(mPreview.getOutputClass()))
        {
            mPreviewSizes.add(new Size(size.getWidth(), size.getHeight()));
        }
        mPictureSizes.clear();
        // try to get hi-res output sizes for Marshmallow and higher
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
        {
            android.util.Size[] outputSizes = map.getHighResolutionOutputSizes(ImageFormat.JPEG);
            if (outputSizes != null)
            {
                for (android.util.Size size : map.getHighResolutionOutputSizes(ImageFormat.JPEG))
                {
                    mPictureSizes.add(new Size(size.getWidth(), size.getHeight()));
                }
            }
        }
        // fallback camera sizes and lower than Marshmallow
        if (mPictureSizes.ratios()
                         .size() == 0)
        {
            for (android.util.Size size : map.getOutputSizes(ImageFormat.JPEG))
            {
                mPictureSizes.add(new Size(size.getWidth(), size.getHeight()));
            }
        }

        if (!mPreviewSizes.ratios()
                          .contains(mAspectRatio))
        {
            mAspectRatio = mPreviewSizes.ratios()
                                        .iterator()
                                        .next();
        }

        Integer sensorOrientation = mCameraCharacteristics.get(
                CameraCharacteristics.SENSOR_ORIENTATION);
        mCameraSensorOrientation = sensorOrientation == null ? 0 : sensorOrientation;
    }


    private void prepareImageReaders()
    {

        switch (mCaptureMode)
        {
        case Constants.SINGLE:
            SortedSet<Size> pictureSizes = mPictureSizes.sizes(mAspectRatio);
            Size largestPicture = null;
            if (pictureSizes != null)
            {
                largestPicture = pictureSizes.last();
            }
            else
            {
                Set<AspectRatio> ratios = mPictureSizes.ratios();
                largestPicture = findSizeWithSimilarAspectRatio(mAspectRatio, ratios,
                                                                mPictureSizes);
            }

            mImageReaderSingle = ImageReader.newInstance(largestPicture.getWidth(),
                                                         largestPicture.getHeight(),
                                                         ImageFormat.JPEG, /* maxImages */ 2);
            mImageReaderSingle.setOnImageAvailableListener(mOnImageAvailableListener, null);
            break;
        case Constants.CONTINUOUS:
            SortedSet<Size> previewSizes = mPreviewSizes.sizes(
                    mAspectRatio); // this can't be null -- setAspectRatio will return early if so
            Size bestPreview = null;
            Size largestPreview = null;
            for (Size size : previewSizes)
            {
                if (CameraView.MPFromResolution(size.getWidth(),
                                                size.getHeight()) <= mMaxPreviewSizeMP)
                {
                    bestPreview = size;
                }
            }

            if (bestPreview != null)
            {
                mImageReaderContinuous = ImageReader.newInstance(bestPreview.getWidth(),
                                                                 bestPreview.getHeight(),
                                                                 ImageFormat.YUV_420_888,
                                                                 mMaxPreviewImages);
            }
            else
            {
                largestPreview = previewSizes.last();
                mImageReaderContinuous = ImageReader.newInstance(largestPreview.getWidth(),
                                                                 largestPreview.getHeight(),
                                                                 ImageFormat.YUV_420_888,
                                                                 mMaxPreviewImages);
            }

            mImageReaderContinuous.setOnImageAvailableListener(
                    new ImageReader.OnImageAvailableListener()
                    {
                        @Override
                        public void onImageAvailable(ImageReader imageReader)
                        {
                            if (mOpenImages < imageReader.getMaxImages())
                            {
                                Image image = imageReader.acquireNextImage();
                                if (image != null)
                                {
                                    //converting to JPEG
                                    byte[] jpegData = ImageUtil.imageToByteArray(image);
                                    mCallback.onPreviewFrame(new LeadSize(mPreview.getView()
                                                                                  .getMeasuredWidth(),
                                                                          mPreview.getView()
                                                                                  .getMeasuredHeight(),
                                                                          jpegData,
                                                                          image.getWidth(),
                                                                          image.getHeight()));
                                    //write to file (for example ..some_path/frame.jpg)
//                        FileManager.writeFrame(FILE_NAME, jpegData);
                                    image.close();
                                    mOpenImages--;
                                }

//                     Image image = imageReader.acquireNextImage();
//                     Bitmap b = imageToBitmap(image,180);
//                     if (image != null) {
//                        mOpenImages++;
//                        final int width = image.getWidth();
//                        final int height = image.getHeight();
//                        Image.Plane[] planes = image.getPlanes();
//                        if (planes.length == 3) {
//                           ByteBuffer ybb = planes[0].getBuffer();
//                           ByteBuffer cbbb = planes[1].getBuffer();
//                           ByteBuffer crbb = planes[2].getBuffer();
//                           YUVImage.Builder builder = new YUVImage.Builder();
//                           builder.setWidth(width)
//                                 .setHeight(height)
//                                 .setFormat(RasterYUVFormat.YUV_420_888)
//                                 .setPlaneBytesPerLine(0, planes[0].getRowStride())
//                                 .setPlanePixelStride(0, planes[0].getPixelStride())
//                                 .setPlaneBytesPerLine(1, planes[1].getRowStride())
//                                 .setPlanePixelStride(1, planes[1].getPixelStride())
//                                 .setPlaneBytesPerLine(2, planes[2].getRowStride())
//                                 .setPlanePixelStride(2, planes[2].getPixelStride());
//                           if ((ybb.isDirect() && cbbb.isDirect() && crbb.isDirect())) {
//                              builder.setDirect(true, mYUVCallback, image)
//                                    .setPlaneByteBuffer(0, ybb)
//                                    .setPlaneByteBuffer(1, cbbb)
//                                    .setPlaneByteBuffer(2, crbb);
//                           } else {
//                              byte[] y = new byte[ybb.remaining()];
//                              byte[] cb = new byte[cbbb.remaining()];
//                              byte[] cr = new byte[crbb.remaining()];
//                              ybb.get(y);
//                              cbbb.get(cb);
//                              crbb.get(cr);
//                              ybb = null;
//                              cbbb = null;
//                              crbb = null;
//                              builder.setDirect(false, null, null)
//                                    .setPlaneData(0, y)
//                                    .setPlaneData(1, cb)
//                                    .setPlaneData(2, cr);
//                           }
//                           YUVImage yuvImage = builder.build();
//                           if (!yuvImage.isDirect()) {
//                              image.close();
//                              mOpenImages--;
//                           }
//                        }
//                     }
                            }
                        }
                    }, null);
            break;
        }
    }

    /**
     * <p>Starts opening a camera device.</p>
     * <p>The result will be processed in {@link #mCameraDeviceCallback}.</p>
     */
    private void startOpeningCamera()
    {
        try
        {
            mCameraManager.openCamera(mCameraId, mCameraDeviceCallback, null);
        }
        catch (CameraAccessException e)
        {
            throw new RuntimeException("Failed to open camera: " + mCameraId, e);
        }
    }

    /**
     * <p>Starts a capture session for camera preview.</p>
     * <p>This rewrites {@link #mPreviewRequestBuilder}.</p>
     * <p>The result will be continuously processed in {@link #mSessionCallback}.</p>
     */
    private void startCaptureSession()
    {
        if (!isCameraOpened() || !mPreview.isReady() ||
                (mCaptureMode == Constants.CONTINUOUS && mImageReaderContinuous == null) ||
                (mCaptureMode == Constants.SINGLE && mImageReaderSingle == null))
        {
            return;
        }
        Size previewSize = chooseOptimalSize();
        mPreview.setBufferSize(previewSize.getWidth(), previewSize.getHeight());
        Surface surface = mPreview.getSurface();
        try
        {
            mPreviewRequestBuilder = mCameraDevice.createCaptureRequest(
                    CameraDevice.TEMPLATE_PREVIEW);
            mPreviewRequestBuilder.addTarget(surface);
            switch (mCaptureMode)
            {
            case Constants.CONTINUOUS:
                mPreviewRequestBuilder.addTarget(mImageReaderContinuous.getSurface());
                mCameraDevice.createCaptureSession(
                        Arrays.asList(surface, mImageReaderContinuous.getSurface()),
                        mSessionCallback, null);
                break;
            case Constants.SINGLE:
                //mPreviewRequestBuilder.addTarget(mImageReaderSingle.getSurface());
                mCameraDevice.createCaptureSession(
                        Arrays.asList(surface, mImageReaderSingle.getSurface()), mSessionCallback,
                        null);
                break;
            }
        }
        catch (CameraAccessException e)
        {

            throw new RuntimeException("Failed to start camera session");
        }
    }

    /**
     * Chooses the optimal preview size based on {@link #mPreviewSizes} and the surface size.
     *
     * @return The picked size for camera preview.
     */
    private Size chooseOptimalSize()
    {
        int surfaceLonger, surfaceShorter;
        final int surfaceWidth = mPreview.getWidth();
        final int surfaceHeight = mPreview.getHeight();
        if (surfaceWidth < surfaceHeight)
        {
            surfaceLonger = surfaceHeight;
            surfaceShorter = surfaceWidth;
        }
        else
        {
            surfaceLonger = surfaceWidth;
            surfaceShorter = surfaceHeight;
        }
        SortedSet<Size> candidates = mPreviewSizes.sizes(mAspectRatio);
        // Pick the smallest of those big enough.
        for (Size size : candidates)
        {
            if (size.getWidth() >= surfaceLonger && size.getHeight() >= surfaceShorter)
            {
                return size;
            }
        }
        // If no size is big enough, pick the largest one.
        return candidates.last();
    }

    /**
     * Updates the internal state of auto-focus to {@link #mAutoFocus}.
     */
    private void updateAutoFocus()
    {
        if (mAutoFocus)
        {
            int[] modes = mCameraCharacteristics.get(
                    CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES);
            // Auto focus is not supported
            if (modes == null || modes.length == 0 ||
                    (modes.length == 1 && modes[0] == CameraCharacteristics.CONTROL_AF_MODE_OFF))
            {
                mAutoFocus = false;
                mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE,
                                           CaptureRequest.CONTROL_AF_MODE_OFF);
            }
            else
            {
                mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE,
                                           CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            }
        }
        else
        {
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE,
                                       CaptureRequest.CONTROL_AF_MODE_OFF);
        }
    }

    /**
     * Updates the internal state of flash to {@link #mFlash}.
     */
    private void updateFlash()
    {
        switch (mFlash)
        {
        case Constants.FLASH_OFF:
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE,
                                       CaptureRequest.CONTROL_AE_MODE_ON);
            mPreviewRequestBuilder.set(CaptureRequest.FLASH_MODE,
                                       CaptureRequest.FLASH_MODE_OFF);
            break;
        case Constants.FLASH_ON:
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE,
                                       CaptureRequest.CONTROL_AE_MODE_ON_ALWAYS_FLASH);
            mPreviewRequestBuilder.set(CaptureRequest.FLASH_MODE,
                                       CaptureRequest.FLASH_MODE_OFF);
            break;
        case Constants.FLASH_TORCH:
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE,
                                       CaptureRequest.CONTROL_AE_MODE_ON);
            mPreviewRequestBuilder.set(CaptureRequest.FLASH_MODE,
                                       CaptureRequest.FLASH_MODE_TORCH);
            break;
        case Constants.FLASH_AUTO:
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE,
                                       CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);
            mPreviewRequestBuilder.set(CaptureRequest.FLASH_MODE,
                                       CaptureRequest.FLASH_MODE_OFF);
            break;
        case Constants.FLASH_RED_EYE:
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE,
                                       CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH_REDEYE);
            mPreviewRequestBuilder.set(CaptureRequest.FLASH_MODE,
                                       CaptureRequest.FLASH_MODE_OFF);
            break;
        }
    }

    /**
     * Locks the focus as the first step for a still image capture.
     */
    private void lockFocus()
    {
        if (mCaptureSession == null)
            return;

        mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER,
                                   CaptureRequest.CONTROL_AF_TRIGGER_START);
        try
        {
            mCaptureCallback.setState(PictureCaptureCallback.STATE_LOCKING);
            mCaptureSession.capture(mPreviewRequestBuilder.build(), mCaptureCallback, null);
        }
        catch (CameraAccessException e)
        {
            Log.e(TAG, "Failed to lock focus.", e);
        }
    }

    /**
     * Captures a still picture.
     */
    private void captureStillPicture()
    {
        if (mCameraDevice == null)
            return;

        try
        {
            CaptureRequest.Builder captureRequestBuilder = mCameraDevice.createCaptureRequest(
                    CameraDevice.TEMPLATE_STILL_CAPTURE);
            captureRequestBuilder.addTarget(mImageReaderSingle.getSurface());
            captureRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE,
                                      mPreviewRequestBuilder.get(CaptureRequest.CONTROL_AF_MODE));
            switch (mFlash)
            {
            case Constants.FLASH_OFF:
                captureRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE,
                                          CaptureRequest.CONTROL_AE_MODE_ON);
                captureRequestBuilder.set(CaptureRequest.FLASH_MODE,
                                          CaptureRequest.FLASH_MODE_OFF);
                break;
            case Constants.FLASH_ON:
                captureRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE,
                                          CaptureRequest.CONTROL_AE_MODE_ON_ALWAYS_FLASH);
                break;
            case Constants.FLASH_TORCH:
                captureRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE,
                                          CaptureRequest.CONTROL_AE_MODE_ON);
                captureRequestBuilder.set(CaptureRequest.FLASH_MODE,
                                          CaptureRequest.FLASH_MODE_TORCH);
                break;
            case Constants.FLASH_AUTO:
                captureRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE,
                                          CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);
                break;
            case Constants.FLASH_RED_EYE:
                captureRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE,
                                          CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);
                break;
            }
            // Calculate JPEG orientation.
            @SuppressWarnings("ConstantConditions")
            int sensorOrientation = mCameraCharacteristics.get(
                    CameraCharacteristics.SENSOR_ORIENTATION);
            captureRequestBuilder.set(CaptureRequest.JPEG_ORIENTATION,
                                      (sensorOrientation +
                                              mDisplayOrientation * (mFacing == Constants.FACING_FRONT ? 1 : -1) +
                                              360) % 360);
            // Stop preview and capture a still picture.
            mCaptureSession.stopRepeating();
            mCaptureSession.capture(captureRequestBuilder.build(),
                                    new CameraCaptureSession.CaptureCallback()
                                    {
                                        @Override
                                        public void onCaptureCompleted(@NonNull CameraCaptureSession session,
                                                                       @NonNull CaptureRequest request,
                                                                       @NonNull TotalCaptureResult result)
                                        {
                                            unlockFocus();
                                        }
                                    }, null);
        }
        catch (CameraAccessException e)
        {
            Log.e(TAG, "Cannot capture a still picture.", e);
        }
    }

    int getCaptureMode()
    {
        return mCaptureMode;
    }

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

    /**
     * Unlocks the auto-focus and restart camera preview. This is supposed to be called after
     * capturing a still picture.
     */
    private void unlockFocus()
    {
        mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER,
                                   CaptureRequest.CONTROL_AF_TRIGGER_CANCEL);

        if (mCaptureSession != null)
        {
            try
            {
                mCaptureSession.capture(mPreviewRequestBuilder.build(), mCaptureCallback, null);
                updateAutoFocus();
                updateFlash();
                mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER,
                                           CaptureRequest.CONTROL_AF_TRIGGER_IDLE);
                mCaptureSession.setRepeatingRequest(mPreviewRequestBuilder.build(),
                                                    mCaptureCallback,
                                                    mBackgroundHandler);
                mCaptureCallback.setState(PictureCaptureCallback.STATE_PREVIEW);
            }
            catch (CameraAccessException e)
            {
                Log.e(TAG, "Failed to restart camera preview.", e);
            }
        }
    }

    /**
     * A {@link CameraCaptureSession.CaptureCallback} for capturing a still picture.
     */
    private static abstract class PictureCaptureCallback
            extends CameraCaptureSession.CaptureCallback
    {

        static final int STATE_PREVIEW = 0;
        static final int STATE_LOCKING = 1;
        static final int STATE_LOCKED = 2;
        static final int STATE_PRECAPTURE = 3;
        static final int STATE_WAITING = 4;
        static final int STATE_CAPTURING = 5;

        private int mState;

        void setState(int state)
        {
            mState = state;
        }

        @Override
        public void onCaptureProgressed(@NonNull CameraCaptureSession session,
                                        @NonNull CaptureRequest request,
                                        @NonNull CaptureResult partialResult)
        {
            process(partialResult);
        }

        @Override
        public void onCaptureCompleted(@NonNull CameraCaptureSession session,
                                       @NonNull CaptureRequest request,
                                       @NonNull TotalCaptureResult result)
        {
            process(result);
        }

        private void process(@NonNull CaptureResult result)
        {
            switch (mState)
            {
            case STATE_LOCKING:
            {
                Integer af = result.get(CaptureResult.CONTROL_AF_STATE);
                if (af == null)
                {
                    onReady();
                    break;
                }
                if (CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED == af ||
                        CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED == af)
                {
                    Integer ae = result.get(CaptureResult.CONTROL_AE_STATE);
                    if (ae == null || ae == CaptureResult.CONTROL_AE_STATE_CONVERGED)
                    {
                        setState(STATE_CAPTURING);
                        onReady();
                    }
                    else
                    {
                        onPrecaptureRequired();
                    }
                }
                break;
            }
            case STATE_PRECAPTURE:
            {
                Integer ae = result.get(CaptureResult.CONTROL_AE_STATE);
                if (ae == null ||
                        ae == CaptureResult.CONTROL_AE_STATE_PRECAPTURE ||
                        ae == CaptureRequest.CONTROL_AE_STATE_FLASH_REQUIRED)
                {
                    setState(STATE_WAITING);
                }
                break;
            }
            case STATE_WAITING:
            {
                Integer ae = result.get(CaptureResult.CONTROL_AE_STATE);
                if (ae == null || ae != CaptureResult.CONTROL_AE_STATE_PRECAPTURE)
                {
                    setState(STATE_CAPTURING);
                    onReady();
                }
                break;
            }
            }
        }

        /**
         * Called when it is ready to take a still picture.
         */
        public abstract void onReady();

        /**
         * Called when it is necessary to run the precapture sequence.
         */
        public abstract void onPrecaptureRequired();

    }

    public Bitmap imageToBitmap(Image image, float rotationDegrees)
    {

        byte[] nv21;
        ByteBuffer yBuffer = image.getPlanes()[0].getBuffer();
        ByteBuffer uBuffer = image.getPlanes()[1].getBuffer();
        ByteBuffer vBuffer = image.getPlanes()[2].getBuffer();

        int ySize = yBuffer.remaining();
        int uSize = uBuffer.remaining();
        int vSize = vBuffer.remaining();

        nv21 = new byte[ySize + uSize + vSize];


        //U and V are swapped
        yBuffer.get(nv21, 0, ySize);
        vBuffer.get(nv21, ySize, vSize);
        uBuffer.get(nv21, ySize + vSize, uSize);

//      return nv21;

        // NV21 is a plane of 8 bit Y values followed by interleaved  Cb Cr
//      ByteBuffer ib = ByteBuffer.allocate(image.getHeight() * image.getWidth() * 2);
//
//      ByteBuffer y = image.getPlanes()[0].getBuffer();
//      ByteBuffer cr = image.getPlanes()[1].getBuffer();
//      ByteBuffer cb = image.getPlanes()[2].getBuffer();
//      ib.put(y);
//      ib.put(cb);
//      ib.put(cr);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        YuvImage yuv = new YuvImage(nv21, ImageFormat.NV21, image.getWidth(), image.getHeight(),
                                    null);
        yuv.compressToJpeg(new Rect(0, 0, image.getWidth(), image.getHeight()), 100, out);
        byte[] imageBytes = out.toByteArray();

        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length, options);
        options.inJustDecodeBounds = false;
//      options.inSampleSize = calculateSampleSize(options.outWidth, options.outHeight, dstWidth,
//              dstHeight, scalingLogic);
        Bitmap unscaledBitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length,
                                                              options);

//      return unscaledBitmap;

        Bitmap bm = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);
        Bitmap bitmap = bm;

        // On android the camera rotation and the screen rotation
        // are off by 90 degrees, so if you are capturing an image
        // in "portrait" orientation, you'll need to rotate the image.
        if (rotationDegrees != 0)
        {
            Matrix matrix = new Matrix();
            matrix.postRotate(rotationDegrees);
            Bitmap scaledBitmap = Bitmap.createScaledBitmap(bm,
                                                            bm.getWidth(), bm.getHeight(), true);
            bitmap = Bitmap.createBitmap(scaledBitmap, 0, 0,
                                         scaledBitmap.getWidth(), scaledBitmap.getHeight(), matrix,
                                         true);
        }
        return bitmap;
    }

    static final class ImageUtil
    {

        @RequiresApi(api = Build.VERSION_CODES.KITKAT)
        public static byte[] imageToByteArray(Image image)
        {
            byte[] data = null;
            if (image.getFormat() == ImageFormat.JPEG)
            {
                Image.Plane[] planes = image.getPlanes();
                ByteBuffer buffer = planes[0].getBuffer();
                data = new byte[buffer.capacity()];
                buffer.get(data);
                return data;
            }
            else if (image.getFormat() == ImageFormat.YUV_420_888)
            {
                data = YUV_420_888toNV21(image);
            }
            return data;
        }

        @RequiresApi(api = Build.VERSION_CODES.KITKAT)
        private static byte[] YUV_420_888toNV21(Image image)
        {
            byte[] nv21;
            ByteBuffer yBuffer = image.getPlanes()[0].getBuffer();
            ByteBuffer uBuffer = image.getPlanes()[1].getBuffer();
            ByteBuffer vBuffer = image.getPlanes()[2].getBuffer();

            int ySize = yBuffer.remaining();
            int uSize = uBuffer.remaining();
            int vSize = vBuffer.remaining();

            nv21 = new byte[ySize + uSize + vSize];

            //U and V are swapped
            yBuffer.get(nv21, 0, ySize);
            vBuffer.get(nv21, ySize, vSize);
            uBuffer.get(nv21, ySize + vSize, uSize);

            return nv21;
        }

        private static byte[] NV21toJPEG(byte[] nv21, int width, int height)
        {
            YuvImage yuvimage = new YuvImage(nv21, ImageFormat.NV21, width, height, null);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            yuvimage.compressToJpeg(new Rect(0, 0, width, height), 100, baos);
            byte[] jdata = baos.toByteArray();

            // Convert to Bitmap
//         Bitmap bmp = BitmapFactory.decodeByteArray(jdata, 0, jdata.length);
//         System.out.println("Bitmap Name 3" + bmp);


            return jdata;
        }
    }
}


