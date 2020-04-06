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
import android.content.res.TypedArray;
import android.os.Build;
import android.os.Parcel;
import android.os.Parcelable;
import android.support.annotation.IntDef;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.os.ParcelableCompat;
import android.support.v4.os.ParcelableCompatCreatorCallbacks;
import android.util.AttributeSet;
import android.widget.FrameLayout;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.Set;

public class CameraView
        extends FrameLayout
{

    /**
     * The camera device faces the opposite direction as the device's screen.
     */
    public static final int FACING_BACK = Constants.FACING_BACK;

    /**
     * The camera device faces the same direction as the device's screen.
     */
    public static final int FACING_FRONT = Constants.FACING_FRONT;


    /**
     * Direction the camera faces relative to device screen.
     */
    @IntDef({ FACING_BACK, FACING_FRONT })
    @Retention(RetentionPolicy.SOURCE)
    public @interface Facing
    {
    }


    /**
     * Flash will not be fired.
     */
    public static final int FLASH_OFF = Constants.FLASH_OFF;

    /**
     * Flash will always be fired during snapshot.
     */
    public static final int FLASH_ON = Constants.FLASH_ON;

    /**
     * Constant emission of light during preview, auto-focus and snapshot.
     */
    public static final int FLASH_TORCH = Constants.FLASH_TORCH;

    /**
     * Flash will be fired automatically when required.
     */
    public static final int FLASH_AUTO = Constants.FLASH_AUTO;

    /**
     * Flash will be fired in red-eye reduction mode.
     */
    public static final int FLASH_RED_EYE = Constants.FLASH_RED_EYE;

    public static final int CONTINUOUS = Constants.CONTINUOUS;

    public static final int SINGLE = Constants.SINGLE;


    /**
     * The mode for for the camera device's flash control
     */
    @IntDef({ FLASH_OFF, FLASH_ON, FLASH_TORCH, FLASH_AUTO, FLASH_RED_EYE })
    public @interface Flash
    {
    }


    private final CameraViewImpl mImpl;

    private final CallbackBridge mCallbacks;

    private boolean mAdjustViewBounds;

    private boolean mHasInvertedAspectRatio;

    private final DisplayOrientationDetector mDisplayOrientationDetector;

    public CameraView(Context context)
    {
        this(context, null);
    }

    public CameraView(Context context, AttributeSet attrs)
    {
        this(context, attrs, 0);
    }

    @SuppressWarnings("WrongConstant")
    public CameraView(Context context, AttributeSet attrs, int defStyleAttr)
    {
        super(context, attrs, defStyleAttr);

        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.CameraView, defStyleAttr,
                                                      R.style.Widget_CameraView);

        boolean forceLegacyCamera = a.getBoolean(R.styleable.CameraView_forceLegacyCamera, false);

        // Internal setup
        final PreviewImpl preview;
        if (Build.VERSION.SDK_INT < 14)
        {
            preview = new SurfaceViewPreview(context, this);
        }
        else
        {
            preview = new TextureViewPreview(context, this);
        }
        mCallbacks = new CallbackBridge();
        if (Build.VERSION.SDK_INT < 22 || forceLegacyCamera)
        {
            mImpl = new Camera1(mCallbacks, preview);
        }
        else
        {
            mImpl = new Camera2(mCallbacks, preview, context);
        }

        // Attributes
        mAdjustViewBounds = a.getBoolean(R.styleable.CameraView_android_adjustViewBounds, false);
        setFacing(a.getInt(R.styleable.CameraView_facing, FACING_BACK));
        String aspectRatio = a.getString(R.styleable.CameraView_aspectRatio);
        if (aspectRatio != null)
        {
            setAspectRatio(AspectRatio.parse(aspectRatio));
        }
        else
        {
            setAspectRatio(Constants.DEFAULT_ASPECT_RATIO);
        }
        setAutoFocus(a.getBoolean(R.styleable.CameraView_autoFocus, true));
        setFlash(a.getInt(R.styleable.CameraView_flash, Constants.FLASH_AUTO));
        setMaxPreviewImages(a.getInt(R.styleable.CameraView_maxPreviewImages,
                                     Constants.DEFAULT_MAX_PREVIEW_IMAGES));
        setMaxPreviewSizeMP(a.getFloat(R.styleable.CameraView_maxPreviewSizeMP,
                                       Constants.DEFAULT_MAX_PREVIEW_SIZE_MP));
        setCaptureMode(a.getInt(R.styleable.CameraView_captureMode, Constants.CONTINUOUS));
        a.recycle();
        // Display orientation detector
        mDisplayOrientationDetector = new DisplayOrientationDetector(context)
        {
            @Override
            public void onDisplayOrientationChanged(int displayOrientation)
            {
                //Queueing the orientation change fixes bug on Galaxy Nexus, potentially others
                if (mImpl.mPreview.isReady())
                    mImpl.setDisplayOrientation(displayOrientation);
                else
                    mImpl.queueDisplayOrientationChange(displayOrientation);
            }
        };
    }

    @Override
    protected void onAttachedToWindow()
    {
        super.onAttachedToWindow();
        mDisplayOrientationDetector.enable(ViewCompat2.getDisplay(this));
    }

    @Override
    protected void onDetachedFromWindow()
    {
        mDisplayOrientationDetector.disable();
        super.onDetachedFromWindow();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec)
    {
        // Handle android:adjustViewBounds
        if (mAdjustViewBounds)
        {
            if (!isCameraOpened())
            {
                mCallbacks.reserveRequestLayoutOnOpen();
                super.onMeasure(widthMeasureSpec, heightMeasureSpec);
                return;
            }
            final int widthMode = MeasureSpec.getMode(widthMeasureSpec);
            final int heightMode = MeasureSpec.getMode(heightMeasureSpec);
            if (widthMode == MeasureSpec.EXACTLY && heightMode != MeasureSpec.EXACTLY)
            {
                final AspectRatio ratio = getAspectRatio();
                assert ratio != null;
                int height = (int)(MeasureSpec.getSize(widthMeasureSpec) * ratio.toFloat());
                if (heightMode == MeasureSpec.AT_MOST)
                {
                    height = Math.min(height, MeasureSpec.getSize(heightMeasureSpec));
                }
                super.onMeasure(widthMeasureSpec,
                                MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
            }
            else if (widthMode != MeasureSpec.EXACTLY && heightMode == MeasureSpec.EXACTLY)
            {
                final AspectRatio ratio = getAspectRatio();
                assert ratio != null;
                int width = (int)(MeasureSpec.getSize(heightMeasureSpec) * ratio.toFloat());
                if (widthMode == MeasureSpec.AT_MOST)
                {
                    width = Math.min(width, MeasureSpec.getSize(widthMeasureSpec));
                }
                super.onMeasure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                                heightMeasureSpec);
            }
            else
            {
                super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            }
        }
        else
        {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        }
        // Measure the TextureView
        int width = getMeasuredWidth();
        int height = getMeasuredHeight();
        AspectRatio ratio = getAspectRatio();
        if ((mDisplayOrientationDetector.getDeviceDefaultOrientation() == Configuration.ORIENTATION_PORTRAIT &&
                mDisplayOrientationDetector.getLastKnownDisplayOrientation() % 180 == 0) ||
                (mDisplayOrientationDetector.getDeviceDefaultOrientation() == Configuration.ORIENTATION_LANDSCAPE &&
                        mDisplayOrientationDetector.getLastKnownDisplayOrientation() % 180 == 90))
        {
            ratio = ratio.inverse();
            mHasInvertedAspectRatio = true;
        }
        else
        {
            mHasInvertedAspectRatio = false;
        }
        assert ratio != null;
        if (height < width * ratio.getY() / ratio.getX())
        {
            mImpl.getView()
                 .measure(
                         MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                         MeasureSpec.makeMeasureSpec(width * ratio.getY() / ratio.getX(),
                                                     MeasureSpec.EXACTLY));
        }
        else
        {
            mImpl.getView()
                 .measure(
                         MeasureSpec.makeMeasureSpec(height * ratio.getX() / ratio.getY(),
                                                     MeasureSpec.EXACTLY),
                         MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
        }
    }

    @Override
    protected Parcelable onSaveInstanceState()
    {
        SavedState state = new SavedState(super.onSaveInstanceState());
        state.facing = getFacing();
        state.ratio = getAspectRatio();
        state.autoFocus = getAutoFocus();
        state.flash = getFlash();
        return state;
    }

    @Override
    protected void onRestoreInstanceState(Parcelable state)
    {
        if (!(state instanceof SavedState))
        {
            super.onRestoreInstanceState(state);
            return;
        }
        SavedState ss = (SavedState)state;
        super.onRestoreInstanceState(ss.getSuperState());
        setFacing(ss.facing);
        setAspectRatio(ss.ratio);
        setAutoFocus(ss.autoFocus);
        setFlash(ss.flash);
    }

    static boolean isSamsungDevice()
    {
        String strBrand = Build.BRAND;
        String strManufacturer = Build.MANUFACTURER;
        return strBrand != null && strManufacturer != null ? strBrand.compareToIgnoreCase(
                "Samsung") == 0 || strManufacturer.compareToIgnoreCase("Samsung") == 0 : false;
    }
    public void start() {
        mImpl.start();
    }
    public void stop()
    {
        mImpl.stop();
    }

    /**
     * @return {@code true} if the camera is opened.
     */
    public boolean isCameraOpened()
    {
        return mImpl.isCameraOpened();
    }

    /**
     * Add a new callback.
     *
     * @param callback The {@link Callback} to add.
     * @see #removeCallback(Callback)
     */
    public void addCallback(@NonNull Callback callback)
    {
        mCallbacks.add(callback);
    }

    /**
     * Remove a callback.
     *
     * @param callback The {@link Callback} to remove.
     * @see #addCallback(Callback)
     */
    public void removeCallback(@NonNull Callback callback)
    {
        mCallbacks.remove(callback);
    }

    /**
     * @param adjustViewBounds {@code true} if you want the CameraView to adjust its bounds to
     *                         preserve the aspect ratio of camera.
     * @see #getAdjustViewBounds()
     */
    public void setAdjustViewBounds(boolean adjustViewBounds)
    {
        if (mAdjustViewBounds != adjustViewBounds)
        {
            mAdjustViewBounds = adjustViewBounds;
            requestLayout();
        }
    }

    /**
     * @return True when this CameraView is adjusting its bounds to preserve the aspect ratio of
     * camera.
     * @see #setAdjustViewBounds(boolean)
     */
    public boolean getAdjustViewBounds()
    {
        return mAdjustViewBounds;
    }

    /**
     * @return True when this CameraView is using an inverted version of its aspect ratio.
     * ie. when the CameraView is taller that it is wide
     */
    public boolean hasInvertedAspectRatio()
    {
        return mHasInvertedAspectRatio;
    }

    /**
     * Chooses camera by the direction it faces.
     *
     * @param facing The camera facing. Must be either {@link #FACING_BACK} or
     *               {@link #FACING_FRONT}.
     */
    public void setFacing(@Facing int facing)
    {
        mImpl.setFacing(facing);
    }

    /**
     * Gets the direction that the current camera faces.
     *
     * @return The camera facing.
     */
    @Facing
    public int getFacing()
    {
        //noinspection WrongConstant
        return mImpl.getFacing();
    }

    /**
     * Gets all the aspect ratios supported by the current camera.
     */
    public Set<AspectRatio> getSupportedAspectRatios()
    {
        return mImpl.getSupportedAspectRatios();
    }

    /**
     * Sets the aspect ratio of camera.
     *
     * @param ratio The {@link AspectRatio} to be set.
     */
    public void setAspectRatio(@NonNull AspectRatio ratio)
    {
        mImpl.setAspectRatio(ratio);
    }

    /**
     * Gets the current aspect ratio of camera.
     *
     * @return The current {@link AspectRatio}. Can be {@code null} if no camera is opened yet.
     */
    @Nullable
    public AspectRatio getAspectRatio()
    {
        return mImpl.getAspectRatio();
    }

    /**
     * Enables or disables the continuous auto-focus mode. When the current camera doesn't support
     * auto-focus, calling this method will be ignored.
     *
     * @param autoFocus {@code true} to enable continuous auto-focus mode. {@code false} to
     *                  disable it.
     */
    public void setAutoFocus(boolean autoFocus)
    {
        mImpl.setAutoFocus(autoFocus);
    }

    /**
     * Returns whether the continuous auto-focus mode is enabled.
     *
     * @return {@code true} if the continuous auto-focus mode is enabled. {@code false} if it is
     * disabled, or if it is not supported by the current camera.
     */
    public boolean getAutoFocus()
    {
        return mImpl.getAutoFocus();
    }

    /**
     * Sets the flash mode.
     *
     * @param flash The desired flash mode.
     */
    public void setFlash(@Flash int flash)
    {
        mImpl.setFlash(flash);
    }

    /**
     * Gets the current flash mode.
     *
     * @return The current flash mode.
     */
    @Flash
    public int getFlash()
    {
        //noinspection WrongConstant
        return mImpl.getFlash();
    }

    /**
     * Set the max preview size in megapixels (MP). Might not be observed
     * if the a preview size under the max desired size is not
     * available at the current {@link AspectRatio}. Default value of 2.1 MP (roughly 1080p).
     *
     * @param maxPreviewSizeMP The desired max preview size.
     */
    public void setMaxPreviewSizeMP(float maxPreviewSizeMP)
    {
        mImpl.setMaxPreviewSizeMP(maxPreviewSizeMP);
    }

    /**
     * Gets the max preview size in megapixels (MP).
     *
     * @return The current max preview size in megapixels (MP)
     */
    public float getMaxPreviewSizeMP()
    {
        return mImpl.getMaxPreviewSizeMP();
    }

    /**
     * Sets the max preview images that can be allocated at any given time. This value cannot be lower
     * than 2 for performance purposes. Only applies to devices using {@link android.hardware.camera2}
     * (API >= 22). Typically, this value should increase if you are processing multiple images in parallel
     * and should match the number of threads that you are using.
     *
     * @param maxPreviewImages The maximum number of preview images that can be allocated at any time
     */
    public void setMaxPreviewImages(int maxPreviewImages)
    {
        mImpl.setMaxPreviewImages(maxPreviewImages);
    }

    public void setCaptureMode(int captureMode)
    {
        mImpl.setCaptureMode(captureMode);
    }

    public int getCaptureMode()
    {
        return mImpl.getCaptureMode();
    }

    /**
     * Gets the max number of preview images that can be allocated
     *
     * @return The current max number of preview images that can be allocated
     */
    public int getMaxPreviewImages()
    {
        return mImpl.getMaxPreviewImages();
    }

    public boolean hasMultipleCameras()
    {
        return mImpl.hasMultipleCameras();
    }

    public boolean hasFlash()
    {
        return mImpl.hasFlash();
    }

    /**
     * Converts a resolution to megapixels for {@link #setMaxPreviewSizeMP}
     *
     * @param width  resolution width
     * @param height resolution height
     * @return The size of the resolution in megapixels (MP)
     */
    public static float MPFromResolution(int width, int height)
    {
        return ((float)(width * height)) / 1000000.0f;
    }

    /**
     * Converts megapixels to total pixel count of a resolution
     *
     * @param mp A preview size in megapixels
     * @return The total pixel count of the given megapixel value
     */
    public static int resolutionFromMP(float mp)
    {
        return (int)(mp * 1000000);
    }

    /**
     * Take a picture. The result will be returned to
     * {@link Callback#onPictureTaken(CameraView, byte[])}.
     */
    public void takePicture()
    {
        mImpl.takePicture();
    }

    private class CallbackBridge
            implements CameraViewImpl.Callback
    {

        private final ArrayList<Callback> mCallbacks = new ArrayList<>();

        private boolean mRequestLayoutOnOpen;

        public void add(Callback callback)
        {
            mCallbacks.add(callback);
        }

        public void remove(Callback callback)
        {
            mCallbacks.remove(callback);
        }

        @Override
        public void onCameraOpened()
        {
            if (mRequestLayoutOnOpen)
            {
                mRequestLayoutOnOpen = false;
                requestLayout();
            }
            for (Callback callback : mCallbacks)
            {
                callback.onCameraOpened(CameraView.this);
            }
        }

        @Override
        public void onCameraClosed()
        {
            for (Callback callback : mCallbacks)
            {
                callback.onCameraClosed(CameraView.this);
            }
        }

        @Override
        public void onPictureTaken(byte[] data)
        {
            for (Callback callback : mCallbacks)
            {
                callback.onPictureTaken(CameraView.this, data);
            }
        }

        @Override
        public void onPreviewFrame(LeadSize surfaceSize)
        {
            for (Callback callback : mCallbacks)
            {
                callback.onPreviewFrame(CameraView.this, surfaceSize);
            }
        }

        public void reserveRequestLayoutOnOpen()
        {
            mRequestLayoutOnOpen = true;
        }
    }


    protected static class SavedState
            extends BaseSavedState
    {

        @Facing
        int facing;

        AspectRatio ratio;

        boolean autoFocus;

        @Flash
        int flash;

        @SuppressWarnings("WrongConstant")
        public SavedState(Parcel source, ClassLoader loader)
        {
            super(source);
            facing = source.readInt();
            ratio = source.readParcelable(loader);
            autoFocus = source.readByte() != 0;
            flash = source.readInt();
        }

        public SavedState(Parcelable superState)
        {
            super(superState);
        }

        @Override
        public void writeToParcel(Parcel out, int flags)
        {
            super.writeToParcel(out, flags);
            out.writeInt(facing);
            out.writeParcelable(ratio, 0);
            out.writeByte((byte)(autoFocus ? 1 : 0));
            out.writeInt(flash);
        }

        public static final Creator<SavedState> CREATOR
                = ParcelableCompat.newCreator(new ParcelableCompatCreatorCallbacks<SavedState>()
        {

            @Override
            public SavedState createFromParcel(Parcel in, ClassLoader loader)
            {
                return new SavedState(in, loader);
            }

            @Override
            public SavedState[] newArray(int size)
            {
                return new SavedState[size];
            }

        });

    }


    /**
     * Callback for monitoring events about {@link CameraView}.
     */
    @SuppressWarnings("UnusedParameters")
    public abstract static class Callback
    {

        /**
         * Called when camera is opened.
         *
         * @param cameraView The associated {@link CameraView}.
         */
        public void onCameraOpened(CameraView cameraView)
        {
        }

        /**
         * Called when camera is closed.
         *
         * @param cameraView The associated {@link CameraView}.
         */
        public void onCameraClosed(CameraView cameraView)
        {
        }

        /**
         * Called when a picture is taken.
         *
         * @param cameraView The associated {@link CameraView}.
         * @param data       JPEG buffers.
         */
        public void onPictureTaken(CameraView cameraView, byte[] data)
        {
        }

        /**
         * Called when a picture is taken.
         */
        public void onPreviewFrame(CameraView cameraView, LeadSize surfaceSize)
        {

        }
    }
}
