package github.me_asri.multiloc.location;

import android.os.CancellationSignal;

import androidx.annotation.NonNull;

import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

import okhttp3.OkHttpClient;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import retrofit2.http.GET;

public class IPLocation {
    private static final String API_URL = "http://ip-api.com/json/";

    private final APIService mService;

    public IPLocation(long timeoutMillis) {
        OkHttpClient httpClient = new OkHttpClient.Builder()
                .readTimeout(timeoutMillis, TimeUnit.MILLISECONDS)
                .connectTimeout(timeoutMillis, TimeUnit.MILLISECONDS)
                .build();

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(API_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .client(httpClient)
                .build();

        mService = retrofit.create(APIService.class);
    }

    public void getLocation(CancellationSignal cancelSignal, BiConsumer<Result, Throwable> callback) {
        Call<Result> serviceCall = mService.getLocation();
        serviceCall.enqueue(new Callback<Result>() {
            @Override
            public void onResponse(@NonNull Call<Result> call, @NonNull Response<Result> response) {
                Result result = response.body();
                callback.accept(result, null);
            }

            @Override
            public void onFailure(@NonNull Call<Result> call, @NonNull Throwable t) {
                if (!call.isCanceled()) {
                    callback.accept(null, t);
                }
            }
        });

        if (cancelSignal != null) {
            cancelSignal.setOnCancelListener(serviceCall::cancel);
        }
    }

    public static class Result {
        public final String status;
        public final String country;
        public final String countryCode;
        public final String region;
        public final String regionName;
        public final String city;
        public final String zip;
        public final double lat;
        public final double lon;
        public final String timezone;
        public final String isp;
        public final String org;
        public final String as;
        public final String query;

        public Result(String status, String country, String countryCode, String region, String regionName, String city, String zip, double lat, double lon, String timezone, String isp, String org, String as, String query) {
            this.status = status;
            this.country = country;
            this.countryCode = countryCode;
            this.region = region;
            this.regionName = regionName;
            this.city = city;
            this.zip = zip;
            this.lat = lat;
            this.lon = lon;
            this.timezone = timezone;
            this.isp = isp;
            this.org = org;
            this.as = as;
            this.query = query;
        }
    }

    private interface APIService {
        @GET("/json")
        Call<Result> getLocation();
    }
}
