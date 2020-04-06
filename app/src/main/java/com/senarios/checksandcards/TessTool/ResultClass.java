package com.senarios.checksandcards.TessTool;

import com.googlecode.leptonica.android.Pix;

/**
 * Created by smmehdi12 on 4/22/2016.
 */
public class ResultClass {
    public String rzlt;
    public Pix pix;

    ResultClass(String s, Pix p)
    {
        rzlt=s;
        pix=p;
    }
}
