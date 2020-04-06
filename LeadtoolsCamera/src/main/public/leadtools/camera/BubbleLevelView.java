package leadtools.camera;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.util.AttributeSet;
import android.view.Surface;
import android.view.View;

public class BubbleLevelView extends View implements SensorEventListener {

   private boolean mIsRunning;
   private Paint mPaint;
   private SensorManager mSensorManager;
   private Sensor mRotationVectorSensor;
   private final float[] mRotationMatrix = new float[16];
   //Yaw, Pitch, Roll
   private final float[] mYPR = new float[3];
   private int mSurfaceOrientation = Surface.ROTATION_90;

   public BubbleLevelView(Context context){
      super(context);
      init(context, null, 0);
   }

   public BubbleLevelView(Context context, AttributeSet attrs){
      super(context, attrs);
      init(context, attrs, 0);
   }

   public BubbleLevelView(Context context, AttributeSet attrs, int defStyleAttr){
      super(context, attrs, defStyleAttr);
      init(context, attrs, defStyleAttr);
   }

   private void init(Context context, AttributeSet attrs, int defStyleAttr){
      mPaint = new Paint();
      mPaint.setColor(Color.WHITE);
      mPaint.setStrokeWidth(4);
      mPaint.setStyle(Paint.Style.STROKE);

      //init sensor manager
      mSensorManager = (SensorManager)context.getSystemService(Context.SENSOR_SERVICE);
      // find the rotation-vector sensor
      mRotationVectorSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
      // initialize the rotation matrix to identity
      mRotationMatrix[0] = 1;
      mRotationMatrix[4] = 1;
      mRotationMatrix[8] = 1;
      mRotationMatrix[12] = 1;
   }

   public boolean isRotationVectorSensorAvailable(){
      return mRotationVectorSensor != null;
   }

   @Override
   public void onDraw(Canvas canvas){
      int radius = (Math.min(getWidth(), getHeight()) / 2) - 10;
      canvas.drawCircle(getWidth() / 2, getHeight() / 2, radius, mPaint);
      canvas.drawLine(getWidth() / 2 - radius / 8f, getHeight() / 2, getWidth() / 2 + radius / 8f, getHeight() / 2, mPaint);
      canvas.drawLine(getWidth() / 2, getHeight() / 2 - radius / 8f, getWidth() / 2, getHeight() / 2 + radius / 8f, mPaint);
      if(mIsRunning){

         int xIndex;
         int yIndex;
         int xSign;
         int ySign;
         switch (mSurfaceOrientation){
            case Surface.ROTATION_0:
               xIndex = 2;
               yIndex = 1;
               xSign = -1;
               ySign = -1;
               break;
            case Surface.ROTATION_90:
               xIndex = 1;
               yIndex = 2;
               xSign = 1;
               ySign = -1;
               break;
            case Surface.ROTATION_180:
               xIndex = 2;
               yIndex = 1;
               xSign = 1;
               ySign = 1;
               break;
            case Surface.ROTATION_270:
               xIndex = 1;
               yIndex = 2;
               xSign = -1;
               ySign = 1;
               break;
            default:
               xIndex = 2;
               yIndex = 1;
               xSign = -1;
               ySign = -1;
               break;
         }

         canvas.drawCircle(getWidth() / 2 + xSign * ((float)radius * ((float) Math.toDegrees(mYPR[xIndex]) / 90f)),
               getHeight() / 2 - ySign * ((float)radius * ((float) Math.toDegrees(mYPR[yIndex]) / 90f)),
               radius / 8f,
               mPaint);

         invalidate();
      }
   }

   public void onAccuracyChanged(Sensor sensor, int accuracy) {}

   public void onSensorChanged(SensorEvent event) {
      // we received a sensor event. it is a good practice to check
      // that we received the proper event
      if (event.sensor.getType() == Sensor.TYPE_ROTATION_VECTOR) {
         // convert the rotation-vector to a 4x4 matrix.
         SensorManager.getRotationMatrixFromVector(mRotationMatrix , event.values);

         //Rotation matrix -> Euler angles (yaw, pitch, roll) in radians
         SensorManager.getOrientation(mRotationMatrix, mYPR);
      }
   }

   public void setOrientation(int orientation){
      mSurfaceOrientation = orientation;
   }

   public void start() {
      // enable our sensor when the activity is resumed, ask for
      // 10 ms updates.
      mSensorManager.registerListener(this, mRotationVectorSensor, 10000);
      mIsRunning = true;
   }
   public void stop() {
      // make sure to turn our sensor off when the activity is paused
      mSensorManager.unregisterListener(this);
      mIsRunning = false;
   }

   public boolean isRunning(){
      return mIsRunning;
   }

}
