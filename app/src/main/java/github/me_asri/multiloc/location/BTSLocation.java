package github.me_asri.multiloc.location;

import android.Manifest;
import android.content.Context;
import android.os.Build;
import android.os.CancellationSignal;
import android.telephony.CellIdentityGsm;
import android.telephony.CellIdentityLte;
import android.telephony.CellIdentityWcdma;
import android.telephony.CellInfo;
import android.telephony.CellInfoGsm;
import android.telephony.CellInfoLte;
import android.telephony.CellInfoWcdma;
import android.telephony.TelephonyManager;
import android.util.Log;

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
    private static final String TAG = BTSLocation.class.getName();
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
                            .filter(CellInfo::isRegistered)
                            .findFirst()
                            .orElse(null);
                    if (cell == null) {
                        // No cells found
                        callback.accept(null, new NoCellsException());
                        return;
                    }

                    Call<APIResult> serviceCall = getBTSLocation(cell, callback);
                    if (cancelSignal != null && serviceCall != null) {
                        cancelSignal.setOnCancelListener(serviceCall::cancel);
                    }
                }
            });
        } else {
            CellInfo cellInfo = tm.getAllCellInfo().stream()
                    .filter(CellInfo::isRegistered)
                    .findFirst()
                    .orElse(null);
            if (cellInfo == null) {
                callback.accept(null, new NoCellsException());
                return;
            }

            Call<APIResult> serviceCall = getBTSLocation(cellInfo, callback);
            if (cancelSignal != null && serviceCall != null) {
                cancelSignal.setOnCancelListener(serviceCall::cancel);
            }
        }
    }

    private Call<APIResult> getBTSLocation(CellInfo cellInfo, BiConsumer<Result, Throwable> callback) {
        Call<APIResult> serviceCall;

        int mcc, mnc, tac, ci;
        if (cellInfo instanceof CellInfoLte) {
            CellIdentityLte identity = ((CellInfoLte) cellInfo).getCellIdentity();
            mcc = identity.getMcc();
            mnc = identity.getMnc();
            tac = identity.getTac();
            ci = identity.getCi();
        } else if (cellInfo instanceof CellInfoWcdma) {
            CellIdentityWcdma identity = ((CellInfoWcdma) cellInfo).getCellIdentity();
            mcc = identity.getMcc();
            mnc = identity.getMnc();
            tac = identity.getLac();
            ci = identity.getCid();
        } else if (cellInfo instanceof CellInfoGsm) {
            CellIdentityGsm identity = ((CellInfoGsm) cellInfo).getCellIdentity();
            mcc = identity.getMcc();
            mnc = identity.getMnc();
            tac = identity.getLac();
            ci = identity.getCid();
        } else {
            callback.accept(null, new UnknownCellTypeException());
            return null;
        }

        Log.i(TAG, "getBTSLocation: " +
                "MCC: " + mcc +
                " MNC: " + mnc +
                " TAC: " + tac +
                " CI: " + ci);
        serviceCall = service.getCellLocation(mcc, mnc, tac, ci);

        serviceCall.enqueue(new Callback<APIResult>() {
            @Override
            public void onResponse(@NonNull Call<APIResult> call, @NonNull Response<APIResult> response) {
                APIResult result = response.body();
                if (result == null) {
                    callback.accept(null, null);
                } else {
                    callback.accept(new Result(result, mcc, mnc, tac, ci), null);
                }
            }

            @Override
            public void onFailure(@NonNull Call<APIResult> call, @NonNull Throwable t) {
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

        public final int mcc;
        public final int mnc;
        public final int tac;
        public final int ci;

        public Result(APIResult apiResult, int mcc, int mnc, int tac, int ci) {
            this.lon = apiResult.lon;
            this.lat = apiResult.lat;
            this.range = apiResult.range;

            this.mcc = mcc;
            this.mnc = mnc;
            this.tac = tac;
            this.ci = ci;
        }
    }

    private static class APIResult {
        public final double lon;
        public final double lat;
        public final int range;

        public APIResult(String lon, String lat, String range) {
            this.lon = Double.parseDouble(lon);
            this.lat = Double.parseDouble(lat);
            this.range = Integer.parseInt(range);
        }
    }

    private interface OpenCellIDService {
        @GET("searchCell.php")
        Call<APIResult> getCellLocation(@Query("mcc") int mcc, @Query("mnc") int mnc, @Query("lac") int lac, @Query("cell_id") int ci);
    }

    public static class BTSException extends RuntimeException {
    }

    public static class NoCellsException extends BTSException {
    }

    public static class UnknownCellTypeException extends BTSException {
    }
}
