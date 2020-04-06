package com.senarios.checksandcards.Dialogs;

import android.content.DialogInterface;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.DialogFragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import com.senarios.checksandcards.R;


/**
 * Created by Fadi on 5/11/2014.
 */
public class ImageDialog extends DialogFragment implements View.OnClickListener {

    private Bitmap bmp;
    private String title;
    private String routing;
    private String account;
    private String cheque;
    private TextView routingEdit;
    private TextView accountEdit;
    private TextView chequeEdit;


    public ImageDialog(){
    }
    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        getDialog().getWindow().requestFeature(Window.FEATURE_NO_TITLE);
    }
    public static ImageDialog New(){
        return new ImageDialog();
    }

    public ImageDialog addBitmap(Bitmap bmp) {
        if (bmp != null)
            this.bmp = bmp;
        return this;
    }

    public ImageDialog addTitle(String routing, String account, String cheque) {
        this.routing = routing;
        this.account = account;
        this.cheque = cheque;
        return this;
    }

    public ImageDialog addTitle(String routing) {
        this.routing = routing;
//        this.account = account;
//        this.cheque = cheque;
        return this;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.image_dialog, null);
        ImageView imageView =  view.findViewById(R.id.image_dialog_imageView);
        accountEdit =  view.findViewById(R.id.account);
        routingEdit =  view.findViewById(R.id.routing);
        chequeEdit =  view.findViewById(R.id.cheque);

        Button button = (Button) view.findViewById(R.id.retry);
        button.setOnClickListener(this);
        if (bmp != null)
            imageView.setImageBitmap(bmp);

        routingEdit.setText(routing);
        accountEdit.setText(account);
        chequeEdit.setText(cheque);
        setCancelable(false);
        return view;
    }

    @Override
    public void onDismiss(DialogInterface dialog) {
        super.onDismiss(dialog);
    }

    @Override
    public void onClick(View v) {
        this.dismiss();
//        Intent intent = new Intent();
//        intent.putExtra("routing",routingEdit.getText().toString());
//        intent.putExtra("account",accountEdit.getText().toString());
//        intent.putExtra("cehque",chequeEdit.getText().toString());
//        intent.putExtra("micr",bmp);
//        getActivity().setResult(Activity.RESULT_OK,intent);
//        System.gc();
//        getActivity().finish();
    }


}