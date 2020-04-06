package com.senarios.checksandcards.TessTool;

import android.app.Activity;
import android.graphics.Bitmap;
import android.os.AsyncTask;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;

import com.senarios.checksandcards.Dialogs.ImageDialog;
import com.senarios.checksandcards.Imaging.Tools;

/**
 * Created by Fadi on 6/11/2014.
 */
public class TessAsyncEngine extends AsyncTask<Object, Void, String> {

    static final String TAG = "DBG_" + TessAsyncEngine.class.getName();

    private Bitmap bmp;

    private AppCompatActivity context;


    @Override
    protected String doInBackground(Object... params) {

        try {

            if(params.length < 2) {
                Log.e(TAG, "Error passing parameter to execute - missing params");
                return null;
            }

            if(!(params[0] instanceof Activity) || !(params[1] instanceof Bitmap)) {
                Log.e(TAG, "Error passing parameter to execute(context, bitmap)");
                return null;
            }

            context = (AppCompatActivity) params[0];

            bmp = (Bitmap)params[1];

            if(context == null || bmp == null) {
                Log.e(TAG, "Error passed null parameter to execute(context, bitmap)");
                return null;
            }

            int rotate = 0;

            if(params.length == 3 && params[2]!= null && params[2] instanceof Integer){
                rotate = (Integer) params[2];
            }

            if(rotate >= -180 && rotate <= 180 && rotate != 0)
            {
                bmp = Tools.preRotateBitmap(bmp, rotate);
                Log.d(TAG, "Rotated OCR bitmap " + rotate + " degrees");
            }

            TessEngine tessEngine =  TessEngine.Generate(context);

            bmp = bmp.copy(Bitmap.Config.ARGB_8888, true);

            String result = "";

            Log.d(TAG, result);

            return result;

        } catch (Exception ex) {
            Log.d(TAG, "Error: " + ex + "\n" + ex.getMessage());
        }

        return null;
    }

    @Override
    protected void onPostExecute(String s) {

        if(s == null || bmp == null || context == null)
            return;
        if(s.length()<32){
            ImageDialog.New()
                    .addBitmap(bmp)
                    .show(context.getSupportFragmentManager(), TAG);
                    super.onPostExecute(s);
            return;
        }

        ImageDialog.New()
                .addBitmap(bmp)
                .show(context.getSupportFragmentManager(), TAG);

        super.onPostExecute(s);
    }
}
