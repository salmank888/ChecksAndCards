package com.senarios.chequescanlibrary;

import android.app.Activity;
import android.content.Intent;

import androidx.appcompat.app.AppCompatActivity;

public class GetMicrDetails
        extends AppCompatActivity
{

    public static final int CHECK_SCAN_REQUEST_CODE = 169;

    public static void with(Activity context)
    {
        context.startActivityForResult(new Intent("micrCheckActivity"),
                                       CHECK_SCAN_REQUEST_CODE);
    }
}
