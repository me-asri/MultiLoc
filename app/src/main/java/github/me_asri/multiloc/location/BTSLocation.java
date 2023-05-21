package github.me_asri.multiloc.location;

import android.Manifest;
import android.content.Context;
import android.os.Build;
import android.os.CancellationSignal;
import android.telephony.CellIdentityLte;
import android.telephony.CellInfo;
import android.telephony.CellInfoLte;
import android.telephony.TelephonyManager;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresPermission;

import java.util.List;
import java.util.function.BiConsumer;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import retrofit2.http.GET;
import retrofit2.http.Query;

public class BTSLocation {
    private static final String API_URL = "https://opencellid.org/ajax/";

    private final OpenCellIDService service;

    public BTSLocation() {
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(API_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        service = retrofit.create(OpenCellIDService.class);
    }

    @RequiresPermission(Manifest.permission.ACCESS_FINE_LOCATION)
    public void getLocation(Context context, CancellationSignal cancelSignal, BiConsumer<Result, Throwable> callback) {
        TelephonyManager tm = context.getSystemService(TelephonyManager.class);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tm.requestCellInfoUpdate(context.getMainExecutor(), new TelephonyManager.CellInfoCallback() {
                @Override
                public void onCellInfo(@NonNull List<CellInfo> cellInfoList) {
                    CellInfo cell = cellInfoList.stream()
                            .findFirst().orElse(null);
                    if (cell == null) {
                        // No cells found
                        callback.accept(null, new NoCellsException());
                        return;
                    }

                    Call<Result> serviceCall = getBTSLocation(cell, callback);
                    if (cancelSignal != null && serviceCall != null) {
                        cancelSignal.setOnCancelListener(serviceCall::cancel);
                    }
                }
            });
        } else {
            CellInfo cellInfo = tm.getAllCellInfo().stream()
                    .findFirst().orElse(null);
            if (cellInfo == null) {
                callback.accept(null, new NoCellsException());
                return;
            }

            Call<Result> serviceCall = getBTSLocation(cellInfo, callback);
            if (cancelSignal != null && serviceCall != null) {
                cancelSignal.setOnCancelListener(serviceCall::cancel);
            }
        }
    }

    private Call<Result> getBTSLocation(CellInfo cellInfo, BiConsumer<Result, Throwable> callback) {
        Call<Result> serviceCall;
        if (cellInfo instanceof CellInfoLte) {
            CellIdentityLte identity = ((CellInfoLte) cellInfo).getCellIdentity();
            serviceCall = service.getCellLocation(identity.getMcc(), identity.getMnc(), identity.getTac(), identity.getCi());
        } else {
            callback.accept(null, new UnknownCellTypeException());
            return null;
        }

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

        return serviceCall;
    }

    public static class Result {
        public final double lon;
        public final double lat;
        public final int range;

        public Result(String lon, String lat, String range) {
            this.lon = Double.parseDouble(lon);
            this.lat = Double.parseDouble(lat);
            this.range = Integer.parseInt(range);
        }
    }

    private interface OpenCellIDService {
        @GET("searchCell.php")
        Call<Result> getCellLocation(@Query("mcc") int mcc, @Query("mnc") int mnc, @Query("lac") int lac, @Query("cell_id") int ci);
    }

    public static class BTSException extends RuntimeException {
    }

    public static class NoCellsException extends BTSException {
    }

    public static class UnknownCellTypeException extends BTSException {
    }
}
