package com.senarios.checksandcards;

import android.app.Activity;
import android.graphics.Bitmap;
import android.util.Log;

import com.senarios.checksandcards.Imaging.Tools;
import com.senarios.checksandcards.TessTool.ResultClass;
import com.senarios.checksandcards.TessTool.TessAsyncEngine;
import com.senarios.checksandcards.TessTool.TessEngine;


/**
 * Created by Fadi on 6/11/2014.
 */
public class FindResult {

    static final String TAG = "DBG_" + TessAsyncEngine.class.getName();

    private Bitmap bmp;

    private Activity context;

    public ResultClass doInBackground(Object... params) {

        try {

            if(params.length < 2) {
                Log.e(TAG, "Error passing parameter to execute - missing params");
                return null;
            }

            if(!(params[0] instanceof Activity) || !(params[1] instanceof Bitmap)) {
                Log.e(TAG, "Error passing parameter to execute(context, bitmap)");
                return null;
            }

            context = (Activity)params[0];

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

            ResultClass result = tessEngine.detectText(bmp);
            result.rzlt = result.rzlt.replace(" ", "");
            result.rzlt = result.rzlt.replace("\n","");
            //Log.d(TAG, result);
            return result;

        } catch (Exception ex) {
            Log.d(TAG, "Error: " + ex + "\n" + ex.getMessage());
        }

        return null;
    }

}
