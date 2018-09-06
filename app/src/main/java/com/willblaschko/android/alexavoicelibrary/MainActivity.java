package com.willblaschko.android.alexavoicelibrary;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.TextView;

import com.liufeismart.test.AlexaUtil;

public class MainActivity extends AppCompatActivity {

    private AlexaUtil mAlexaUtil;

    private TextView tv_hello;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        tv_hello = (TextView) this.findViewById(R.id.tv_hello);
        tv_hello.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mAlexaUtil.startRecog();
            }
        });
        mAlexaUtil = new AlexaUtil(this);
        mAlexaUtil.startRecog();

    }

    @Override
    protected void onStop() {
        super.onStop();
//        if(audioPlayer != null){
//            audioPlayer.stop();
//        }
        mAlexaUtil.stop();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mAlexaUtil.destroy();

    }


}
