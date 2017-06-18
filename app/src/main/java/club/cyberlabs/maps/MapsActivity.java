package club.cyberlabs.maps;

import android.Manifest;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.AsyncTask;
import android.os.Build;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.FragmentActivity;
import android.os.Bundle;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

import org.apache.http.NameValuePair;
import org.apache.http.message.BasicNameValuePair;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class MapsActivity extends AppCompatActivity implements OnMapReadyCallback,ConnectivityReceiver.ConnectivityReceiverListener,GoogleMap.InfoWindowAdapter,
        GoogleMap.OnMyLocationButtonClickListener,
        ActivityCompat.OnRequestPermissionsResultCallback{


    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1;
    private boolean mPermissionDenied = false;

    private GoogleMap mMap;
    boolean mapReady = false;
    private ProgressDialog pDialog;
    JSONParser jsonParser = new JSONParser();
    GPSTracker gps;
    private static final String VALUES_URL = "http://192.168.42.123:8080/CSE/value.php";
    private static String KEY_SUCCESS = "success";
    private static String KEY_VALUE= "value";
    private static final String TAG_MESSAGE = "message";
    private String TAG = MapsActivity.class.getSimpleName();
    Button button;

    HashMap<Double,HashMap<String,Double[]>> hm = new HashMap<Double,HashMap<String,Double[]>>();
    CameraPosition DHANBAD = CameraPosition.builder()
            .target(new LatLng(23.8145422,86.4410816))
            .zoom(15)
            .bearing(0)
            .tilt(45)
            .build();
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);
        final LocationManager manager = (LocationManager) getSystemService( Context.LOCATION_SERVICE );

        if ( !manager.isProviderEnabled( LocationManager.GPS_PROVIDER ) ) {
            buildAlertMessageNoGps();
        }
        button = (Button)findViewById(R.id.btn);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = getIntent();
                finish();
                startActivity(intent);
            }
        });
        checkConnection();
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);
    }

    // Method to manually check connection status
    private void checkConnection() {
        boolean isConnected = ConnectivityReceiver.isConnected();
        showSnack(isConnected);
    }

    // Showing the status in Snackbar
    private void showSnack(boolean isConnected) {
        String message;
        int color;
        if (isConnected) {
            message = "Good! Connected to Internet";
            color = Color.GREEN;
        } else {
            message = "Internet Connection Lost";
            color = Color.RED;
        }

        Snackbar snackbar = Snackbar
                .make(findViewById(R.id.fab), message, Snackbar.LENGTH_LONG);

        View sbView = snackbar.getView();
        TextView textView = (TextView) sbView.findViewById(android.support.design.R.id.snackbar_text);
        textView.setTextColor(color);
        snackbar.show();
    }

    @Override
    protected void onResume() {
        super.onResume();

        // register connection status listener
        MyApplication.getInstance().setConnectivityListener(this);
    }

    @Override
    public void onNetworkConnectionChanged(boolean isConnected) {
        showSnack(isConnected);
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mapReady = true;
        mMap = googleMap;
        mMap.setOnMyLocationButtonClickListener(this);
        enableMyLocation();
        new AttemptValue().execute("5", "5");
        flyTo(DHANBAD);
        mMap.setInfoWindowAdapter(this);
    }
    private void flyTo(CameraPosition target){
        mMap.animateCamera(CameraUpdateFactory.newCameraPosition(target), 1000, null);
    }
    class AttemptValue extends AsyncTask<String, String, String> {

        boolean failure = false;

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            pDialog = new ProgressDialog(MapsActivity.this);
            pDialog.setMessage("Fetching Details");
            pDialog.setIndeterminate(false);
            pDialog.setCancelable(true);
            pDialog.show();
        }


        @Override
        protected String doInBackground(String... args) {
            int success;
            String id = args[0];
            List<NameValuePair> params = new ArrayList<NameValuePair>();
            params.add(new BasicNameValuePair("id", id));
            Log.d("request!", "starting");
            JSONObject json = jsonParser.makeHttpRequest(VALUES_URL,"POST",params);
            if(json!=null){
                try{
                    Log.d("Login attempt", json.toString());
                    success = json.getInt(KEY_SUCCESS);
                    if(success==1){
                        JSONArray jsonArray = json.getJSONArray("values");
                        for (int i = 0; i < jsonArray.length(); i++){
                            JSONObject object = jsonArray.getJSONObject(i);
                            final String name = object.getString("name");
                            final Double pm_2 = object.getDouble("pm_2");
                            final Double pm_10 = object.getDouble("pm_10");
                            final Double so2 = object.getDouble("s02");
                            final Double co2 = object.getDouble("co2");
                            final double lat = object.getDouble("lat");
                            final double lang = object.getDouble("lang");
                            final int value  = Integer.parseInt(object.getString("value"));
                            final int idd;
                            HashMap<String,Double[]> innerMap = new HashMap<String,Double[]>();
                            innerMap.put(name, new Double[]{pm_2,pm_10,so2,co2});
                            hm.put(lat, innerMap);
                            if(value>10){
                                idd = R.drawable.blue;
                            }else if(value >5 ){
                                idd = R.drawable.green;
                            }else{
                                idd = R.drawable.red;
                            }
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    MarkerOptions p = new MarkerOptions()
                                            .position(new LatLng(lat, lang))
                                            .title(name)
                                            .icon(BitmapDescriptorFactory.fromResource(idd))
                                            .snippet("Level : " + value);
                                    mMap.addMarker(p);
                                }
});
        }
        return json.getString(TAG_MESSAGE);
        }else{
        return json.getString(TAG_MESSAGE);
        }
        }catch (final JSONException e){
        Log.e(TAG, "Json parsing error: " + e.getMessage());
        runOnUiThread(new Runnable() {
@Override
public void run() {
        Toast.makeText(getApplicationContext(),
        "Json parsing error: " + e.getMessage(),
        Toast.LENGTH_LONG)
        .show();
        }
        });
        }
        }else{
        Log.e(TAG, "Couldn't get json from server.");
        runOnUiThread(new Runnable() {
@Override
public void run() {
        Toast.makeText(getApplicationContext(),
        "Server Is Not Connected",
        Toast.LENGTH_LONG)
        .show();
        }
        });
        }
        return null;
        }
    @Override
    protected void onPostExecute(String s) {
            super.onPostExecute(s);
            pDialog.dismiss();
            if (s != null){
            Toast.makeText(MapsActivity.this, "Server Is Connected", Toast.LENGTH_LONG).show();
            }
            }

            }

    @Override
    public View getInfoWindow(Marker marker) {
            return null;
            }

    @Override
    public View getInfoContents(Marker marker) {
            //return null;
            return prepareInfoView(marker);

            }

    private View prepareInfoView(Marker marker){
            //prepare InfoView programmatically
            //View infoView = getLayoutInflater().inflate(R.layout.info_window, null);
            LinearLayout infoView = new LinearLayout(MapsActivity.this);
            LinearLayout.LayoutParams infoViewParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            infoView.setOrientation(LinearLayout.HORIZONTAL);
            infoView.setLayoutParams(infoViewParams);

            ImageView infoImageView = new ImageView(MapsActivity.this);
            //Drawable drawable = getResources().getDrawable(R.mipmap.ic_launcher);
            Drawable drawable = getResources().getDrawable(android.R.drawable.ic_dialog_map);
            infoImageView.setImageDrawable(drawable);
            infoView.addView(infoImageView);

            LinearLayout subInfoView = new LinearLayout(MapsActivity.this);
            LinearLayout.LayoutParams subInfoViewParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            subInfoView.setOrientation(LinearLayout.VERTICAL);
            subInfoView.setLayoutParams(subInfoViewParams);

            HashMap<String,Double[]> Map = hm.get(marker.getPosition().latitude);
            String name = null;
            for (String key : Map.keySet()) {
            name = key;
            }

            TextView Name = new TextView(MapsActivity.this);
            Name.setText(name);
            Name.setTextColor(Color.parseColor("#3F51B5"));
            TextView subInfopm_2 = new TextView(MapsActivity.this);
            subInfopm_2.setText("PM 2: " + Map.get(name)[0]);
            subInfopm_2.setTextColor(Color.parseColor("#3F51B5"));
            TextView subInfopm_10 = new TextView(MapsActivity.this);
            subInfopm_10.setText("PM 10: " + Map.get(name)[1]);
            subInfopm_10.setTextColor(Color.parseColor("#3F51B5"));
            TextView so2 = new TextView(MapsActivity.this);
            so2.setText("So2: " + Map.get(name)[2]);
            so2.setTextColor(Color.parseColor("#3F51B5"));
            TextView co2 = new TextView(MapsActivity.this);
            co2.setText("Co2: " + Map.get(name)[3]);
            co2.setTextColor(Color.parseColor("#3F51B5"));
            subInfoView.addView(Name);
            subInfoView.addView(subInfopm_2);
            subInfoView.addView(subInfopm_10);
            subInfoView.addView(so2);
            subInfoView.addView(co2);
            infoView.addView(subInfoView);
            return infoView;
            }

    //GPS Part
    private void enableMyLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            // Permission to access the location is missing.
            PermissionUtils.requestPermission(this, LOCATION_PERMISSION_REQUEST_CODE,
                    Manifest.permission.ACCESS_FINE_LOCATION, true);
        } else if (mMap != null) {
            // Access to the location has been granted to the app.
            mMap.setMyLocationEnabled(true);
        }
    }

    @Override
    public boolean onMyLocationButtonClick() {
        //Toast.makeText(this, "MyLocation button clickeddddddd", Toast.LENGTH_SHORT).show();
        // Return false so that we don't consume the event and the default behavior still occurs
        // (the camera animates to the user's current position).
        return false;
    }


    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        if (requestCode != LOCATION_PERMISSION_REQUEST_CODE) {
            return;
        }

        if (PermissionUtils.isPermissionGranted(permissions, grantResults,
                Manifest.permission.ACCESS_FINE_LOCATION)) {
            // Enable the my location layer if the permission has been granted.
            enableMyLocation();
        } else {
            // Display the missing permission error dialog when the fragments resume.
            mPermissionDenied = true;
        }
    }

    @Override
    protected void onResumeFragments() {
        super.onResumeFragments();
        if (mPermissionDenied) {
            // Permission was not granted, display error dialog.
            showMissingPermissionError();
            mPermissionDenied = false;
        }
    }

    /**
     * Displays a dialog with error message explaining that the location permission is missing.
     */
    private void showMissingPermissionError() {
        PermissionUtils.PermissionDeniedDialog
                .newInstance(true).show(getSupportFragmentManager(), "dialog");
    }

    //bjbjduhdh
    private void buildAlertMessageNoGps() {
        final AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage("Your GPS seems to be disabled, do you want to enable it?")
                .setCancelable(false)
                .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                    public void onClick(@SuppressWarnings("unused") final DialogInterface dialog, @SuppressWarnings("unused") final int id) {
                        startActivity(new Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS));
                    }
                })
                .setNegativeButton("No", new DialogInterface.OnClickListener() {
                    public void onClick(final DialogInterface dialog, @SuppressWarnings("unused") final int id) {
                        dialog.cancel();
                    }
                });
        final AlertDialog alert = builder.create();
        alert.show();
    }
}
