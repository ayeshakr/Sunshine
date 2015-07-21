package com.example.ayesha.sunshine.app;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.GregorianCalendar;

/**
 * A placeholder fragment containing a simple view.
 */
public class ForecastFragment extends Fragment {

    private static final String TAG = "ForecastFragment";

    ArrayAdapter<String> mForecastAdapter;

    public ForecastFragment() {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.forecastfragment, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item){
        int id = item.getItemId();
        if(id==R.id.action_refresh) {
            updateWeather();
            return true;
        }

        if(id==R.id.action_map) {
            displayInMap();
            return true;
            }

        return super.onOptionsItemSelected(item);
    }

    public void displayInMap() {
        //get location from Sharedpreferences
        Intent intent = new Intent(Intent.ACTION_VIEW);
        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(getActivity());
        String location = sharedPref.getString(getString(R.string.pref_location_key),
                getString(R.string.pref_location_default));
        String uriStr = "geo:0,0?q=" + location;
        Uri uri = Uri.parse(uriStr);
        intent.setData(uri);
        //if there is a map app that can be used, launch it, else display a toast msg.
        if (intent.resolveActivity(getActivity().getPackageManager()) != null) {
            startActivity(intent);
        } else {
            String toastText = "No map application available";
            int duration = Toast.LENGTH_SHORT;
            Toast.makeText(getActivity(), toastText, duration).show();
        }
    }

    public void updateWeather() {

        final FetchWeatherTask fetchWeather = new FetchWeatherTask();
        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(getActivity());
        String location = sharedPref.getString(getString(R.string.pref_location_key), getString(R.string.pref_location_default));
        fetchWeather.execute(location);
    }

    public void onStart(){
        super.onStart();
        updateWeather();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        mForecastAdapter =
                new ArrayAdapter<String>(
                        getActivity(), // The current context (this activity)
                        R.layout.list_item_forecast, // The name of the layout ID (TextView).
                        R.id.list_item_forecast_textview, // The ID of the textview to populate.
                        new ArrayList<String>());

        View rootView = inflater.inflate(R.layout.fragment_main, container, false);

        // Get a reference to the ListView, and attach this adapter to it.
        ListView listView = (ListView) rootView.findViewById(R.id.listview_forecast);
        listView.setAdapter(mForecastAdapter);

        listView.setOnItemClickListener(new AdapterView.OnItemClickListener(){
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                String toastText = mForecastAdapter.getItem(i);
                int duration = Toast.LENGTH_SHORT;
                Toast.makeText(getActivity(), toastText, duration).show();

                Intent detailIntent = new Intent(getActivity(), DetailActivity.class);
                detailIntent.putExtra(Intent.EXTRA_TEXT, toastText);
                startActivity(detailIntent);
            }
        });

        return rootView;
    }

    public class FetchWeatherTask extends AsyncTask<String, Void, String[]> {
        //lol this is for my commit streak
        private final String LOG_TAG = FetchWeatherTask.class.getSimpleName();

        @Override
        protected String[] doInBackground(String... params) {

            if (params.length==0) {
                return null; //no city/zip code means nothing to do
            }
            // These two need to be declared outside the try/catch
            // so that they can be closed in the finally block.
            HttpURLConnection urlConnection = null;
            BufferedReader reader = null;

            // Will contain the raw JSON response as a string.
            String forecastJsonStr = null;
            int numDays = 7;

            try {
                // Construct the URL for the OpenWeatherMap query
                // Possible parameters are avaiable at OWM's forecast API page, at
                // http://openweathermap.org/API#forecast
                //URL url = new URL("http://api.openweathermap.org/data/2.5/forecast/daily?q=Montreal&mode=json&units=metric&cnt=7");
                Uri.Builder forecastB = new Uri.Builder();
                final String QUERY = "q";
                final String MODE = "mode";
                final String UNITS = "units";
                final String COUNT = "cnt";

                forecastB.scheme("http")
                        .authority("api.openweathermap.org")
                        .appendPath("data")
                        .appendPath("2.5")
                        .appendPath("forecast")
                        .appendPath("daily")
                        .appendQueryParameter(QUERY, params[0])
                        .appendQueryParameter(MODE, "json")
                        .appendQueryParameter(UNITS, "metric")
                        .appendQueryParameter(COUNT, Integer.toString(numDays));

                String urlStr = forecastB.build().toString();
                URL url = new URL(urlStr);
                //Log.v(LOG_TAG, "Built URI:" + url);
                // Create the request to OpenWeatherMap, and open the connection
                urlConnection = (HttpURLConnection) url.openConnection();
                urlConnection.setRequestMethod("GET");
                urlConnection.connect();
                // Read the input stream into a String
                InputStream inputStream = urlConnection.getInputStream();
                StringBuilder buffer = new StringBuilder();
                if (inputStream == null) {
                    // Nothing to do.
                    return null;
                }
                reader = new BufferedReader(new InputStreamReader(inputStream));

                String line;
                while ((line = reader.readLine()) != null) {
                    // Since it's JSON, adding a newline isn't necessary (it won't affect parsing)
                    // But it does make debugging a *lot* easier if you print out the completed
                    // buffer for debugging.
                    buffer.append(line).append("\n");
                }

                if (buffer.length() == 0) {
                    // Stream was empty.  No point in parsing.
                    return null;
                }

                forecastJsonStr = buffer.toString();
                //Log.v(LOG_TAG, "Forecast JSON String: " + forecastJsonStr);

            } catch (IOException e) {
                Log.e(LOG_TAG, "Error ", e);
                // If the code didn't successfully get the weather data, there's no point in attempting
                // to parse it.
                return null;
            } finally {
                if (urlConnection != null) {
                    urlConnection.disconnect();
                }
                if (reader != null) {
                    try {
                        reader.close();
                    } catch (final IOException e) {
                        Log.e(LOG_TAG, "Error closing stream", e);
                    }
                }
            }

            try {
                return getWeatherDataFromJson(forecastJsonStr, numDays);
            } catch (JSONException e) {
                Log.e(LOG_TAG, "Error getting data from JSON", e);
            }
            return null; //only happens if there is an error getting/parsing weather data
        }

/*    private String getReadableDateString(long time){
        // Because the API returns a unix timestamp (measured in seconds),
        // it must be converted to milliseconds in order to be converted to valid date.
        SimpleDateFormat shortenedDateFormat = new SimpleDateFormat("EEE MMM dd");
        return shortenedDateFormat.format(time);
    }*/

        private String formatHighLows(double high, double low) {

            SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(getActivity());

            String unit = sharedPref.getString(getString(R.string.unitskey), getString(R.string.units_default));

            if (unit.equals("m")) {
                long rHigh = Math.round(high);
                long rLow = Math.round(low);
                String hiLo = rHigh + " / " + rLow;
                return hiLo; }
            else {
                high = 1.8*high + 32;
                low = 1.8*low + 32;
                long rHigh = Math.round(high);
                long rLow = Math.round(low);
                String hiLo = rHigh + " / " + rLow;
                return hiLo;
            }
        }

        private String[] getWeatherDataFromJson(String forecastJsonStr, int numDays)
                throws JSONException {

            //JSON things that we need
            final String JLIST = "list";
            final String JWEATHER = "weather";
            final String JMAX = "max";
            final String JMIN = "min";
            final String JTEMP = "temp";
            final String JMAIN = "main";
            //JSON array of all days
            JSONObject forecastJson = new JSONObject(forecastJsonStr);
            JSONArray weatherArray = (JSONArray) forecastJson.get(JLIST);

            String[] resultStrs = new String[numDays];
            for (int i = 0; i < weatherArray.length(); i++) {
                // For now, using the format "Day, description, hi/low"
                String day;
                String description;
                String highAndLow;
                JSONObject dayForecast = (JSONObject) weatherArray.get(i);

                //create a Gregorian Calendar, which is in current date
                GregorianCalendar gc = new GregorianCalendar();
                //add i dates to current date of calendar
                gc.add(GregorianCalendar.DATE, i);
                //get that date, format it, and "save" it on variable day
                Date time = gc.getTime();
                SimpleDateFormat shortenedDateFormat = new SimpleDateFormat("EEE MMM dd");
                day = shortenedDateFormat.format(time);

                //getJSONObject(0) because weather contains one element
                JSONObject weatherObject = dayForecast.getJSONArray(JWEATHER).getJSONObject(0);
                description = weatherObject.getString(JMAIN);

                JSONObject temperatureObject = dayForecast.getJSONObject(JTEMP);
                double high = temperatureObject.getDouble(JMAX);
                double low = temperatureObject.getDouble(JMIN);


                highAndLow = formatHighLows(high, low);
                if (i==0) {
                    resultStrs[i] = "Today - " + description + " - " + highAndLow;
                } else if (i==1) {
                    resultStrs[i] = "Tomorrow - " + description + " - " + highAndLow;
                } else {
                    resultStrs[i] = day + " - " + description + " - " + highAndLow;
                }
            }
            return resultStrs;
        }

        public void onPostExecute(String[] result) {
            if (result != null) {
                mForecastAdapter.clear();
                for (String s : result) {
                    mForecastAdapter.add(s);
                }
            }
        }
    }
 }

