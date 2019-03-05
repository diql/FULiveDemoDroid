package com.faceunity.fulivedemo;

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;

public class NewBeautyActivity extends AppCompatActivity {

    private FUBeautyFragment mFragment;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.layout_empty);

        String tag = FUBeautyFragment.TAG;
        mFragment = (FUBeautyFragment) getSupportFragmentManager().findFragmentByTag(tag);
        if (mFragment == null) {
            mFragment = FUBeautyFragment.newInstance();
            getSupportFragmentManager().beginTransaction()
                    .add(R.id.fl_fragment_stub, mFragment, tag)
                    .commit();
        }
    }
}
