package edu.uw.nanxiao.geopaint;

import android.Manifest;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.os.Bundle;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.MenuItemCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.ShareActionProvider;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.EditText;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;

import com.flask.colorpicker.ColorPickerView;
import com.flask.colorpicker.OnColorSelectedListener;
import com.flask.colorpicker.builder.ColorPickerClickListener;
import com.flask.colorpicker.builder.ColorPickerDialogBuilder;

import org.json.JSONException;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import static edu.uw.nanxiao.geopaint.R.id.pen;

public class MapsActivity extends AppCompatActivity implements OnMapReadyCallback, GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener, LocationListener {

    private static final String TAG = "Map Activity";
    private GoogleMap mMap;
    private GoogleApiClient mGoogleApiClient;

    private static final int LOCATION_REQUEST_CODE = 0;
    private static final String DEFAULT_FILE_NAME = "drawing.geojson";
    private static final int DEFAULT_DRAWING_COLOR = -1;

    private boolean penDown;
    private Polyline mPolyline;
    List<Polyline> lineShape;
    private int drawingColor;

    private String fileName;
    private SharedPreferences sharedPref;
    private ShareActionProvider mShareActionProvider;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);

        penDown = false;
        lineShape = new ArrayList<>();
        fileName = DEFAULT_FILE_NAME;
        sharedPref = this.getPreferences(Context.MODE_PRIVATE);

        drawingColor = sharedPref.getInt("drawingColor", DEFAULT_DRAWING_COLOR);


        // Obtain the SupportMapFragment, get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);
        mapFragment.setRetainInstance(true); //retain data even when rotate the screen
        mapFragment.setHasOptionsMenu(true); //show option menu at the action bar

        // Create Google API Client that tracks the location data
        if (mGoogleApiClient == null) {
            mGoogleApiClient = new GoogleApiClient.Builder(this)
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this)
                    .addApi(LocationServices.API)
                    .build();
        }
    }

    public void draw(LatLng newPoint) {
        if(penDown){
            // initialize the mPolyline
            if(mPolyline == null){
                mPolyline = mMap.addPolyline(new PolylineOptions().color(drawingColor));
            }
            //add points to the current line
            List<LatLng> points = mPolyline.getPoints();
            points.add(newPoint);
            mPolyline.setPoints(points);
            mPolyline.setColor(drawingColor);
        }
    }

    //start drawing on a map
    @Override
    protected void onStart() {
        super.onStart();
        mGoogleApiClient.connect();
        File file = new File(MapsActivity.this.getExternalFilesDir(null), fileName);
        //Read text from file
        String geoJson = "";
        try {
            InputStream is = new FileInputStream(file);
            BufferedReader buf = new BufferedReader(new InputStreamReader(is));
            String line = buf.readLine();
            StringBuilder sb = new StringBuilder();
            while(line != null){ sb.append(line).append("\n");
                line = buf.readLine(); }
            geoJson = sb.toString();
        }
        catch (IOException e) {
            Log.e(TAG, e.toString());
        }
        try {
            if (geoJson != null) {
                List<PolylineOptions> lines = GeoJsonConverter.convertFromGeoJson(geoJson);
                Log.v(TAG, lines.toString());
                if (mMap != null) {
                    for (PolylineOptions line : lines) {
                        mPolyline = mMap.addPolyline(line);
                        lineShape.add(mPolyline);
                    }
                }
            }
        } catch (JSONException e) {
            Log.e(TAG, e.toString());
        }
    }
    // pen up, stop drawing
    @Override
    protected void onStop() {
        mGoogleApiClient.disconnect();
        if (lineShape != null && lineShape.size() > 1) {
            lineShape.add(mPolyline);
            String geoJsonString = GeoJsonConverter.convertToGeoJson(lineShape);
            new SaveState().execute(geoJsonString);
        }
        super.onStop();
    }

    //changing the pen color
    public void setDrawingColor() {
        ColorPickerDialogBuilder
                .with(this)
                .setTitle("Choose color")
                .initialColor(drawingColor)
                .wheelType(ColorPickerView.WHEEL_TYPE.FLOWER)
                .density(10)
                .setOnColorSelectedListener(new OnColorSelectedListener() {
                    @Override
                    public void onColorSelected(int selectedColor) {
                        Toast.makeText(MapsActivity.this, "Color Selected: " + Integer.toHexString(selectedColor), Toast.LENGTH_SHORT).show();
                        Log.v(TAG, "onColorSelected: " + Integer.toHexString(selectedColor));
                    }
                })
                .setPositiveButton("ok", new ColorPickerClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int selectedColor, Integer[] allColors) {
                        drawingColor = selectedColor;
                        SharedPreferences.Editor editor = sharedPref.edit();
                        editor.putInt("drawingColor", drawingColor);
                        editor.commit();
                        Log.v(TAG, "drawingColor set to: " + Integer.toHexString(selectedColor));
                        lineShape.add(mPolyline);
                        mPolyline = null; //reset the current polyline
                        int permissionCheck = ContextCompat.checkSelfPermission(MapsActivity.this, Manifest.permission.ACCESS_FINE_LOCATION);
                        if(permissionCheck == PackageManager.PERMISSION_GRANTED) {
                            Location currentLoc = LocationServices.FusedLocationApi.getLastLocation(mGoogleApiClient);
                            LatLng newPoint = new LatLng(currentLoc.getLatitude(), currentLoc.getLongitude()); //get the current lat/lng
                            draw(newPoint);
                        } else {
                            ActivityCompat.requestPermissions(MapsActivity.this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_REQUEST_CODE);
                        }
                    }
                })
                .setNegativeButton("cancel", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                    }
                })
                .build()
                .show();

    }

    //Change the state of the pen
    public void togglePen(MenuItem item){
        if(penDown){
            penDown = false;
            item.setIcon(R.drawable.ic_pen_up);
            Toast.makeText(this, "Drawing Stopped", Toast.LENGTH_SHORT).show();
            lineShape.add(mPolyline);
            mPolyline = null;
        }else{
            penDown = true;
            // Runtime permission check
            int permissionCheck = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION);
            if(permissionCheck == PackageManager.PERMISSION_GRANTED) {
                Location currentLoc = LocationServices.FusedLocationApi.getLastLocation(mGoogleApiClient);
                LatLng newPoint = new LatLng(currentLoc.getLatitude(), currentLoc.getLongitude());
                draw(newPoint);
                item.setIcon(R.drawable.ic_pause);
                Toast.makeText(this, "Drawing Started", Toast.LENGTH_SHORT).show();
            } else {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_REQUEST_CODE);
            }
        }
    }

    @Override
    public void onConnected(@Nullable Bundle bundle) {
        LocationRequest request = new LocationRequest();
        request.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        request.setInterval(10000);
        request.setFastestInterval(5000);

        //Runtime permission check
        int permissionCheck = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION);
        if(permissionCheck == PackageManager.PERMISSION_GRANTED){
            mMap.setMyLocationEnabled(true);
            Location currentLoc = LocationServices.FusedLocationApi.getLastLocation(mGoogleApiClient);
            if (currentLoc != null) {
                mMap.moveCamera(CameraUpdateFactory.newLatLng(new LatLng(currentLoc.getLatitude(), currentLoc.getLongitude())));
            }

            LocationServices.FusedLocationApi.requestLocationUpdates(mGoogleApiClient, request, this);
        }else{
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_REQUEST_CODE);
        }

    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        switch (requestCode) {
            case LOCATION_REQUEST_CODE: {
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    onConnected(null);
                }
            }
            default:
                super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }

    @Override
    public void onConnectionSuspended(int i) {
        Log.v(TAG, "Google API Connection Suspended");
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
        Log.v(TAG, "Google API Connection Failed");
    }

    @Override
    public void onLocationChanged(Location location) {
        Log.v(TAG, "Location Changed");
        LatLng newPoint = new LatLng(location.getLatitude(), location.getLongitude()); //get the current lat/lng
        draw(newPoint);
        mMap.moveCamera(CameraUpdateFactory.newLatLng(newPoint));
    }

    // Checks if the external storage is writable
    public boolean isExternalStorageWritable() {
        String state = Environment.getExternalStorageState();
        if (Environment.MEDIA_MOUNTED.equals(state)) {
            return true;
        }
        return false;
    }

    //saving and sharing the drawing
    private class SaveState extends AsyncTask<String, Void, String> {

        @Override
        protected String doInBackground(String... params) {
            if(isExternalStorageWritable()){
                try {
                    File file = new File(MapsActivity.this.getExternalFilesDir(null), fileName);
                    FileOutputStream outputStream = new FileOutputStream(file);
                    outputStream.write(params[0].getBytes());
                    Log.v(TAG, "Geo JSON String: " + params[0]);
                    outputStream.close();
                    Log.v(TAG, "Drawing Saved");
                } catch (IOException ioe) {
                    ioe.printStackTrace();
                }
            }
            return "executed";
        }

        @Override
        protected void onPostExecute(String result) {
        }
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;

        mMap.getUiSettings().setCompassEnabled(true);
        mMap.getUiSettings().setZoomControlsEnabled(true);
        mMap.getUiSettings().setAllGesturesEnabled(true);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu, menu);
        if (penDown) {
            menu.getItem(0).setIcon(R.drawable.ic_pause);
        }
        else {
            menu.getItem(0).setIcon(R.drawable.ic_pen_up);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle item selection
        switch (item.getItemId()) {
            case pen:
                Log.v(TAG, "Selected pen");
                togglePen(item);
                return true;
            case R.id.picker:
                Log.v(TAG, "Selected color picker");
                setDrawingColor();
                return true;
            case R.id.save:
                Log.v(TAG, "Selected save function");
                saveDrawing();
                return true;
            case R.id.share:
                Log.v(TAG, "Selected share function");
                //instantiate the ShareActionProvider for share
                mShareActionProvider = (ShareActionProvider) MenuItemCompat.getActionProvider(item);
                shareDrawing();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    //handles the sharing of the file using the ShareActionProvider
    public void shareDrawing(){
        Log.v(TAG, "Share button clicked");

        Uri fileUri;

        File dir = this.getExternalFilesDir(null);
        File file = new File(dir, fileName);

        fileUri = Uri.fromFile(file);
        Log.v(TAG, "File is at: " + fileUri);

        //we can share a file by using intent with Extra
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain"); //set the type of intent
        shareIntent.putExtra(Intent.EXTRA_STREAM, fileUri);

        mShareActionProvider.setShareIntent(shareIntent);
    }

    //save the drawing on the map into a .geojson file
    public void saveDrawing(){
        Log.v(TAG, "Saving the drawing");

        if (penDown) {
            lineShape.add(mPolyline);
        }
        final EditText field = new EditText(this);
        final AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage("File Name")
                .setTitle("Name the File")
                .setView(field)
                .setPositiveButton("Save", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        fileName = field.getText().toString() + ".geojson";
                        Log.v(TAG, "File Name: " + fileName);
                        if(isExternalStorageWritable()){
                            try {
                                File file = new File(MapsActivity.this.getExternalFilesDir(null), fileName);
                                FileOutputStream outputStream = new FileOutputStream(file);
                                GeoJsonConverter geoJsonConverter = new GeoJsonConverter();
                                String geoJsonString = geoJsonConverter.convertToGeoJson(lineShape);
                                outputStream.write(geoJsonString.getBytes());
                                Log.v(TAG, "Geo JSON String: " + geoJsonString);
                                outputStream.close();
                            } catch (IOException ioe) {
                                ioe.printStackTrace();
                            }
                            Log.v(TAG, "Drawing Saved");
                            Toast.makeText(MapsActivity.this,
                                    "Drawing Saved",
                                    Toast.LENGTH_SHORT).show();
                        }
                    }
                });
        builder.create().show();
    }
}

