// *************************************************************
// Copyright (c) 1991-2019 LEAD Technologies, Inc.              
// All Rights Reserved.                                         
// *************************************************************
package com.senarios.checksandcards;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.HorizontalScrollView;

/**
 *  HorizontalScrollView with dynamic shadows at ends to indicate room to scroll
 */
public class ShadowedScrollView
        extends HorizontalScrollView{

    private View mShadowLeftView;
    private View mShadowRightView;

    public ShadowedScrollView(Context context) {
        super(context);
    }

    public ShadowedScrollView(Context context, AttributeSet attrs) {
        super(context,attrs);
    }

    public ShadowedScrollView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    public void onScrollChanged(int l, int t, int oldl, int oldt){
        super.onScrollChanged(l, t, oldl, oldt);
        setShadowOpacities(l);
    }

    @Override
    public void onLayout(boolean changed, int left, int top, int right, int bottom){
        super.onLayout(changed, left, top, right, bottom);
        setShadowHeight();
        setShadowOpacities(0);
    }

    public void setShadowViews(View left, View right){
        this.mShadowLeftView = left;
        this.mShadowRightView = right;
        setShadowHeight();
        setShadowOpacities(0);
    }

    private void setShadowHeight(){
        if(mShadowLeftView != null && mShadowRightView != null){
            ViewGroup.LayoutParams paramsLeft = mShadowLeftView.getLayoutParams();
            ViewGroup.LayoutParams paramsRight = mShadowRightView.getLayoutParams();
            paramsLeft.height = getHeight();
            paramsRight.height = getHeight();
            mShadowLeftView.setLayoutParams(paramsLeft);
            mShadowLeftView.invalidate();
            mShadowRightView.setLayoutParams(paramsRight);
            mShadowRightView.invalidate();
        }
    }
    @SuppressWarnings("deprecation")
    private void setShadowOpacities(int l){
        if(getActivity() == null || mShadowLeftView == null || mShadowRightView == null)
            return;

        WindowManager windowManager = getActivity().getWindowManager();
        if(isScrollBarVisible()){
            int scrollMax = this.getChildAt(0).getMeasuredWidth()- windowManager.getDefaultDisplay().getWidth();
            double lineInSand = (double) scrollMax / 5.0;
            if(l <= lineInSand){
                int val = (int)((double) l / lineInSand * 255.0);
                //Must account for overscroll momentum which goes beyond scroll range
                mShadowLeftView.getBackground().setAlpha(val > 0 ? val : 0);
                mShadowRightView.getBackground().setAlpha(255);
            }
            if(l >= (scrollMax - lineInSand)){
                double adjustedL = l - (scrollMax - lineInSand);
                int val = 255 - (int)(adjustedL / lineInSand * 255.0);
                //Must account for overscroll momentum which goes beyond scroll range
                mShadowRightView.getBackground().setAlpha(val > 0 ? val : 0);
                mShadowLeftView.getBackground().setAlpha(255);
            }
        }
        else{
            mShadowLeftView.getBackground().setAlpha(0);
            mShadowRightView.getBackground().setAlpha(0);
        }
    }

    public boolean isScrollBarVisible(){
        return getActivity().getWindowManager().getDefaultDisplay().getWidth() - this.getChildAt(0).getMeasuredWidth() < 0;
    }

    @Nullable
    private Activity getActivity() {
        Context context = getContext();
        while (context instanceof ContextWrapper) {
            if (context instanceof Activity) {
                return (Activity)context;
            }
            context = ((ContextWrapper)context).getBaseContext();
        }
        return null;
    }
}
