package edu.uw.nanxiao.geopaint;

import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by Nan on 11/12/17.
 */

public class GeoJsonConverter {

    public static String convertToGeoJson(List<Polyline> lines) {
        StringBuilder builder = new StringBuilder();
        builder.append("{\"type\": \"FeatureCollection\", " +
                "\"features\": [");
        for (Polyline line : lines) {
            builder.append("{ \"type\": \"Feature\", " + //new feature for each line
                    "\"geometry\": { \"type\": \"LineString\", "+ "\"coordinates\": [ ");
            for(LatLng point : line.getPoints()) { //add points
                builder.append("["+point.longitude+","+point.latitude+"],"); //invert lat/lng for GeoJSON

            }
            builder = builder.deleteCharAt(builder.length() - 1); //remove trailing comma
            builder.append("]},"); //end geometry
            builder.append("\"properties\": { ");
            builder.append("\"color\": " + line.getColor() + ","); //color property
            builder.append("\"width\": " + line.getWidth()); //width property
            builder.append("} },"); //end properties/feature
        }
        builder = builder.deleteCharAt(builder.length() - 1); //remove trailing comma
        builder.append("]}"); //end json
        return builder.toString();
    }

    public static List<PolylineOptions> convertFromGeoJson(String geojson) throws JSONException {

        ArrayList<PolylineOptions> polyLineList = new ArrayList<PolylineOptions>();

        JSONArray featuresArray = new JSONObject(geojson).getJSONArray("features");

        //loop through features, creating an options for each line.
        for(int i=0; i< featuresArray.length(); i++){
            JSONObject featureObj = featuresArray.getJSONObject(i);

            //get LatLng coordinates
            JSONArray coordinates = featureObj.getJSONObject("geometry").getJSONArray("coordinates");
            JSONArray startCoord = coordinates.getJSONArray(0);
            JSONArray endCoord = coordinates.getJSONArray(1);
            LatLng start = new LatLng(startCoord.getDouble(1), startCoord.getDouble(0));
            LatLng end = new LatLng(endCoord.getDouble(1), endCoord.getDouble(0));

            //get polyline properties
            JSONObject properties = featureObj.getJSONObject("properties");
            int color = properties.getInt("color");
            float width = (float)properties.getDouble("width");

            //define the polyline
            PolylineOptions line = new PolylineOptions()
                    .add(start)
                    .add(end)
                    .color(color)
                    .width(width);
            polyLineList.add(line);
        }
        return polyLineList;
    }
}
