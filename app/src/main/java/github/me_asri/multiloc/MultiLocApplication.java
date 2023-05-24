package github.me_asri.multiloc;

import android.app.Application;

import com.google.android.material.color.DynamicColors;

public class MultiLocApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();

        DynamicColors.applyToActivitiesIfAvailable(this);
    }
}
