package github.me_asri.multiloc.location;

import android.Manifest;
import android.content.Context;
import android.os.Build;
import android.os.CancellationSignal;
import android.telephony.CellIdentityGsm;
import android.telephony.CellIdentityLte;
import android.telephony.CellIdentityNr;
import android.telephony.CellIdentityWcdma;
import android.telephony.CellInfo;
import android.telephony.CellInfoGsm;
import android.telephony.CellInfoLte;
import android.telephony.CellInfoNr;
import android.telephony.CellInfoWcdma;
import android.telephony.TelephonyManager;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresPermission;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSyntaxException;

import java.lang.reflect.Type;
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
        GsonBuilder gsonBuilder = new GsonBuilder();
        gsonBuilder.create();

        gsonBuilder.registerTypeAdapter(APIResult.class, new ResponseDeserializer());
        Gson customGson = gsonBuilder.create();

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(API_URL)
                .addConverterFactory(GsonConverterFactory.create(customGson))
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

        String mcc, mnc;
        int tac;
        long ci;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q && cellInfo instanceof CellInfoNr) {
            // ! UNTESTED !

            CellIdentityNr identity = (CellIdentityNr) ((CellInfoNr) cellInfo).getCellIdentity();

            mcc = identity.getMccString();
            mnc = identity.getMncString();
            tac = identity.getTac();
            ci = identity.getNci();
        } else if (cellInfo instanceof CellInfoLte) {
            CellIdentityLte identity = ((CellInfoLte) cellInfo).getCellIdentity();
            mcc = Integer.toString(identity.getMcc());
            mnc = Integer.toString(identity.getMnc());
            tac = identity.getTac();
            ci = identity.getCi();
        } else if (cellInfo instanceof CellInfoWcdma) {
            CellIdentityWcdma identity = ((CellInfoWcdma) cellInfo).getCellIdentity();
            mcc = Integer.toString(identity.getMcc());
            mnc = Integer.toString(identity.getMnc());
            tac = identity.getLac();
            ci = identity.getCid();
        } else if (cellInfo instanceof CellInfoGsm) {
            CellIdentityGsm identity = ((CellInfoGsm) cellInfo).getCellIdentity();
            mcc = Integer.toString(identity.getMcc());
            mnc = Integer.toString(identity.getMnc());
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
                    if (result.isSuccess()) {
                        callback.accept(new Result(result.result, mcc, mnc, tac, ci), null);
                    } else {
                        callback.accept(null, BTSException.fromErrorResponse(result.error));
                    }

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

        public final String mcc;
        public final String mnc;
        public final int tac;
        public final long ci;

        public Result(LocationResult locationResult, String mcc, String mnc, int tac, long ci) {
            this.lon = locationResult.lon;
            this.lat = locationResult.lat;
            this.range = locationResult.range;

            this.mcc = mcc;
            this.mnc = mnc;
            this.tac = tac;
            this.ci = ci;
        }
    }

    private static class LocationResult {
        public final double lon;
        public final double lat;
        public final int range;

        public LocationResult(String lon, String lat, String range) {
            this.lon = Double.parseDouble(lon);
            this.lat = Double.parseDouble(lat);
            this.range = Integer.parseInt(range);
        }
    }

    private static class APIResult {
        public final String error;
        public final LocationResult result;


        public APIResult(LocationResult locationResult) {
            this.result = locationResult;
            this.error = null;
        }

        public APIResult(String error) {
            this.error = error;
            this.result = null;
        }

        public boolean isSuccess() {
            return (error == null);
        }
    }


    private static class ResponseDeserializer implements JsonDeserializer<APIResult> {
        @Override
        public APIResult deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            Gson gson = new Gson();

            try {
                LocationResult locationResult = gson.fromJson(json, LocationResult.class);
                return new APIResult(locationResult);
            } catch (JsonSyntaxException e) {
                return new APIResult(json.getAsString());
            }
        }
    }

    private interface OpenCellIDService {
        @GET("searchCell.php")
        Call<APIResult> getCellLocation(@Query("mcc") String mcc, @Query("mnc") String mnc, @Query("lac") int lac, @Query("cell_id") long ci);
    }

    public static class BTSException extends RuntimeException {
        public BTSException(String message) {
            super(message);
        }

        public static BTSException fromErrorResponse(String error) {
            switch (error) {
                case "Invalid Request":
                    return new InvalidRequestException();

                case "false":
                    return new UnregisteredBTSException();

                default:
                    return new BTSException("Unknown error from backend");
            }
        }
    }

    public static class NoCellsException extends BTSException {
        public NoCellsException() {
            super("Phone not connected to any BTS");
        }
    }

    public static class UnknownCellTypeException extends BTSException {
        public UnknownCellTypeException() {
            super("Phone connected to unknown BTS type");
        }
    }

    public static class InvalidRequestException extends BTSException {
        public InvalidRequestException() {
            super("Received invalid request error from OpenCelliD");
        }
    }

    public static class UnregisteredBTSException extends BTSException {
        public UnregisteredBTSException() {
            super("BTS not registered in OpenCelliD");
        }
    }
}
