package ca.mahram.android.sample;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import java.util.Locale;

import ca.mahram.android.TouchImageView;


public class TouchImageViewActivity extends Activity implements TouchImageView.FlingListener{
    private static final String LOGTAG = "TouchImageViewSample";

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        TouchImageView img = (TouchImageView) findViewById(R.id.img);

        if (img.getFlingBehaviour() == TouchImageView.FlingBehaviour.LISTENER)
            img.setFlingListener(this);

        img.setMaxZoom(4);
    }

    @Override
    public boolean onFlingRight(float distance, float velocity) {
        toastFling ("Right", distance, velocity);
        return true;
    }

    @Override
    public boolean onFlingLeft(float distance, float velocity) {
        toastFling ("Left", distance, velocity);
        return true;
    }

    @Override
    public boolean onFlingUp(float distance, float velocity) {
        toastFling ("Up", distance, velocity);
        return true;
    }

    @Override
    public boolean onFlingDown(float distance, float velocity) {
        toastFling ("Down", distance, velocity);
        return true;
    }

    private void toastFling (final String direction, final float distance, final float velocity) {
        Toast.makeText(this, direction, Toast.LENGTH_SHORT).show();
        Log.d(LOGTAG, String.format(Locale.ENGLISH, "%f pixels %s @ %f px/s", distance, direction, velocity));
    }
}