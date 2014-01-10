package ca.mahram.android.sample;

import android.app.Activity;
import android.os.Bundle;

import ca.mahram.android.TouchImageView;


public class TouchImageViewActivity extends Activity {
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        TouchImageView img = (TouchImageView) findViewById(R.id.img);
        img.setMaxZoom(4);
    }
}