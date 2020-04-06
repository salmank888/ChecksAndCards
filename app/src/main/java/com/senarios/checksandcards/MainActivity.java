package com.senarios.checksandcards;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.support.annotation.NonNull;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;

import leadtools.camera.CameraView;
import leadtools.camera.LeadSize;

public class MainActivity
        extends AppCompatActivity
{


    // Requests codes: to use for open images from gallery or files, capturing images, or live capture
    // Also used to check for Permissions
    private static final int IMAGE_GALLERY = 0x0001;
    private static final int IMAGE_CAPTURE = 0x0002;
    //    private static final int IMAGE_FILE = 0x0003;
    private static final int IMAGE_LIVE_CAPTURE = 0x0004;
    private static final int CAMERA_RW_REQUEST = 0x0005;
    private Context mContext;
    private boolean mIsWorking;
    private boolean mShouldStartCapture = true;
    private OverlayView mOverlayView;
    private CameraView mCameraView;
    private LeadRect mMicrReadAreaBounds;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mContext = this;
        if (!DeviceUtils.checkCapturePermission(this, CAMERA_RW_REQUEST))
            return;

        init();
    }

    private void init()
    {
        mCameraView = (CameraView)findViewById(R.id.cameraView);
        if (mCameraView != null)
            mCameraView.addCallback(mCallback);

        mOverlayView = (OverlayView)findViewById(R.id.overlayView);
        ShadowedScrollView sv = (ShadowedScrollView)findViewById(R.id.bottomScrollView);
        sv.setShadowViews(findViewById(R.id.bottomShadowLeft),
                          findViewById(R.id.bottomShadowRight));
    }

    @Override
    protected void onResume()
    {
        super.onResume();
        if (mShouldStartCapture &&
                ContextCompat.checkSelfPermission(this,
                                                  Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(this,
                                                  Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(this,
                                                  Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED)
        {

            startLiveCapture();
            mShouldStartCapture = true;
        }
    }

    @Override
    protected void onPause()
    {
        super.onPause();
        if (mCameraView != null && mCameraView.isCameraOpened())
        {
            stopLiveCapture();
        }
    }

    @Override
    public void onBackPressed()
    {
        super.onBackPressed();

        if (mCameraView != null && mCameraView.isCameraOpened())
            mCameraView.stop();
    }

    private void onSelectImageSrc(int code)
    {

        // Stop live capture if working
        boolean isLiveCaptureWorking = mCameraView.isCameraOpened();
        if (isLiveCaptureWorking)
            stopLiveCapture();

        if (code == IMAGE_LIVE_CAPTURE)
        {// If it's working; do nothing (This is to stop Live capture when the user tap on the live capture button while the live capture mode is running)
            if (isLiveCaptureWorking)
                return;
            if (!DeviceUtils.hasCamera(this))
            {
                Toast.makeText(mContext, "There Is No Camera Available", Toast.LENGTH_SHORT)
                     .show();
                return;
            }
            if (!DeviceUtils.checkCameraPermission(this, IMAGE_LIVE_CAPTURE))
                return;

            startLiveCapture();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults)
    {
        boolean allGranted = true;
        for (int result : grantResults)
        {
            if (result == PackageManager.PERMISSION_DENIED)
            {
                allGranted = false;
                break;
            }
        }
        if (grantResults.length == 0)
            return;

        if (requestCode == CAMERA_RW_REQUEST)
        {
            if (!allGranted)
            {
                // Cannot copy or read the OCR Runtime to the storage; show an error message and close the demo
                Toast.makeText(mContext, "Permissions Not grated", Toast.LENGTH_SHORT)
                     .show();
            }
            else
            {
                init();
            }
        }
        else
        {// Check if permission granted
            if (allGranted)
                onSelectImageSrc(requestCode);
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    public void onSelectImage(View v)
    {
        // Start\Stop live capture
        if (v.getId() == R.id.btn_image_live_capture)
        {
            onSelectImageSrc(IMAGE_LIVE_CAPTURE);
        }
    }

    private void startLiveCapture()
    {
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        // Clear Image
//        mImageViewer.setImage(null);

        // Start live capture
        mCameraView.setVisibility(View.VISIBLE);
        mCameraView.start();


//        setMicrExtractMode();
//        mContinueCapture = true;
        mIsWorking = false;
    }

    private void stopLiveCapture()
    {
//        mContinueCapture = false;

        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        // Clear Image
//        mImageViewer.setImage(null);

        // Save showProgress value to be used in 'onStopCapture'

        mCameraView.stop();
    }

    private CameraView.Callback mCallback = new CameraView.Callback()
    {
        @Override
        public void onCameraOpened(CameraView cameraView)
        {
//        super.onCameraOpened(cameraView);
        }

        @Override
        public void onCameraClosed(CameraView cameraView)
        {
            if (mOverlayView != null)
                mOverlayView.setVisibility(View.INVISIBLE);
            mCameraView.setVisibility(View.INVISIBLE);
        }

        @Override
        public void onPictureTaken(CameraView cameraView, byte[] data)
        {
//        super.onPictureTaken(cameraView, data);
        }

        @Override
        public void onPreviewFrame(CameraView cameraView, LeadSize surfaceSize)
        {
            YuvImage yuvimage = new YuvImage(surfaceSize.getJpegData(), ImageFormat.NV21,
                                             surfaceSize.getWidth(), surfaceSize.getHeight(), null);


            mMicrReadAreaBounds = mOverlayView.updateArea(mCameraView.getWidth(),
                                                          mCameraView.getHeight(), surfaceSize,
                                                          yuvimage.getWidth(),
                                                          yuvimage.getHeight());
//         // show overlay view
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            yuvimage.compressToJpeg(
                    new Rect(mMicrReadAreaBounds.getLeft(), mMicrReadAreaBounds.getTop(),
                             mMicrReadAreaBounds.getRight(), mMicrReadAreaBounds.getBottom()), 100,
                    baos);
            byte[] jdata = baos.toByteArray();

            Bitmap bmp = BitmapFactory.decodeByteArray(jdata, 0, jdata.length);



            mOverlayView.setVisibility(View.VISIBLE);
            mOverlayView.invalidate();
            Toast.makeText(mContext, "image Count" + count++, Toast.LENGTH_SHORT)
                 .show();
        }
    };
    private int count = 0;
}
