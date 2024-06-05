package com.gak.GpsWayPoints;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.location.GpsSatellite;
import android.location.GpsStatus;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;

public class GpsWayPointsActivity extends Activity
{
	static final int MIN_DISTANCE = 10;
	static final int MAX_AGE_MS = 2000;
	static final int MIN_LOCATION_COUNT = 10;
	static final int MIN_BEARING_COUNT = 2;
	static final String CONFIGURATION_FILE = "GpsWayPoints.cfg";
	static final String WAYPOINTS_FILE = "GpsWayPoints.gwp";
	static final String	HOME_LONGITUDE_KEY = "homeLongitude";
	static final String	HOME_LATITUDE_KEY = "homeLatitude";

	static final String	FIX_COUNT_KEY = "homeLatitude";

	GpsWayPointsWidget		m_theRose = null;
	TextView				m_statusLabel = null;
	TextView				m_altitudeLabel = null;
	double					m_altitude = 0;
	double					m_curBearing = 0;
	double					m_homeBearing = 0;
	
	String					m_myStatus = "Willkommen";
	LocationManager			m_locationManager;
	LocationListener		m_locationListener = null;
	GpsStatus.Listener		m_gpsStatusListener;
	Queue<Location>			m_locationList = new LinkedList<Location>();
	long					m_locationFixCount = 0;
	double					m_accuracy = 0.0;
	private DecimalFormat	m_accuracyFormat = new DecimalFormat( "Genauigkeit: 0.000m" );
	PowerManager.WakeLock	m_wakeLock;

	Location				m_home = new Location("");
	SharedPreferences 		m_waypoints = null;

	/** Called when the activity is first created. */
	@Override
    public void onCreate(Bundle savedInstanceState)
    {
		System.out.println("onCreate");
        super.onCreate(savedInstanceState);

    	m_waypoints = getSharedPreferences(WAYPOINTS_FILE, 0);
        if( savedInstanceState != null )
        {
        	m_home.setLongitude(savedInstanceState.getDouble(HOME_LONGITUDE_KEY));
            m_home.setLatitude(savedInstanceState.getDouble(HOME_LATITUDE_KEY));
            m_locationFixCount = savedInstanceState.getLong(FIX_COUNT_KEY);
        }
        else
        {
        	SharedPreferences settings = getSharedPreferences(CONFIGURATION_FILE, 0);
        	double homeLongitude = settings.getFloat(HOME_LONGITUDE_KEY,0); 
        	double homeLaitude = settings.getFloat(HOME_LATITUDE_KEY,0);
        	if (homeLongitude == 0 && homeLaitude == 0)
        	{
            	homeLongitude = 14.282733; 
            	homeLaitude = 48.298820;
        	}

        	m_home.setLongitude(homeLongitude);
        	m_home.setLatitude(homeLaitude);
        }

        PowerManager	pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        m_wakeLock = pm.newWakeLock( PowerManager.SCREEN_DIM_WAKE_LOCK | PowerManager.ON_AFTER_RELEASE, "GpsWayPoints" );
        m_wakeLock.acquire();
        getWindow().addFlags(
        	WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED|
        	WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON|
        	WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        );
        
		System.out.println("setContentView");
        setContentView(R.layout.main);

        m_statusLabel = (TextView)findViewById( R.id.statusLabel );
    	setStatus( m_myStatus );
    	m_theRose = (GpsWayPointsWidget)findViewById( R.id.myRose );
    	m_altitudeLabel = (TextView)findViewById( R.id.altitudeLabel );

        // Acquire a reference to the system Location Manager
		System.out.println("m_locationManager");
        m_locationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);

        // Define a listener that responds to location updates
		System.out.println("m_locationListener");
        m_locationListener = new LocationListener()
        {
            @Override
            public void onStatusChanged(String provider, int status, Bundle extras) 
            {
            	if( status == LocationProvider.OUT_OF_SERVICE )
            	{
            		setStatus( "Kein GPS Empfang" );
            		showMovement( 0, 0, 0, 0  );
            	}
            	else if( status == LocationProvider.TEMPORARILY_UNAVAILABLE )
            		setStatus( "Kurzfristig kein GPS Empfang" );
            	else if( status == LocationProvider.AVAILABLE )
            		setStatus( "GPS Empfang" );
            }

            @Override
            public void onProviderEnabled(String provider)
            {
            	setStatus( "GPS ist eingeschaltet");
            }

            @Override
            public void onProviderDisabled(String provider)
            {
            	setStatus( "GPS ist abgeschaltet");
            	showMovement( 0, 0, 0, 0 );
            }

			@Override
			public void onLocationChanged(Location location)
			{
				onLocationChanged2( location );
			}
          };

          // Register the listener with the Location Manager to receive location updates
          System.out.println("requestLocationUpdates");
	      m_locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 50, (float) 0.1, m_locationListener);
          m_locationManager.requestLocationUpdates(LocationManager.PASSIVE_PROVIDER, 50, (float) 0.1, m_locationListener);

          System.out.println("m_gpsStatusListener");
          m_gpsStatusListener = new GpsStatus.Listener()
          {

			@Override
			public void onGpsStatusChanged(int event)
			{
				if( event == GpsStatus.GPS_EVENT_STARTED )
	            	setStatus( "GPS gestartet");
				else if( event == GpsStatus.GPS_EVENT_STOPPED )
	            	setStatus( "GPS gestoppt");
				else if( event == GpsStatus.GPS_EVENT_FIRST_FIX )
	            	setStatus( "GPS erster Fix");
				else if( event == GpsStatus.GPS_EVENT_SATELLITE_STATUS  )
				{
					int Satellites = 0;
					int SatellitesInFix = 0;
					for (GpsSatellite sat : m_locationManager.getGpsStatus(null).getSatellites())
					{
						if(sat.usedInFix())
							SatellitesInFix++;              

						Satellites++;
					}
					setStatus( 
						"GPS Satelliten: " + 
						Integer.toString(SatellitesInFix) + "/" + 
						Integer.toString(Satellites)
					);
				}
			}
          };
          System.out.println("addGpsStatusListener");
          m_locationManager.addGpsStatusListener(m_gpsStatusListener);

          System.out.println("showSpeed");
          showMovement( 0, 0, 0, 0 );
//          onLocationChanged2(m_home);
	}

	String locationString( Location src )
	{
		return src.getProvider() + '|' + Double.toString(src.getLongitude()) + '|' + Double.toString(src.getLatitude());  
	}
	Location locationString( String src )
	{
		String [] elements = src.split("[|]");
		if(elements.length != 3) {
			return null;
		}
		
		Location newLocation = new Location(elements[0]);
		newLocation.setLongitude(Double.parseDouble(elements[1]));
		newLocation.setLatitude(Double.parseDouble(elements[2]));
		return newLocation;  
	}

	private void savePositionAs(final Location lastLocation)
	{
    	LayoutInflater layoutInflater = getLayoutInflater();
    	final View view = layoutInflater.inflate(R.layout.save_position, null);
    	final AlertDialog alertDialog = new AlertDialog.Builder(this).create();
    	alertDialog.setTitle("Position speichern");
    	alertDialog.setIcon(R.drawable.icon);
    	alertDialog.setCancelable(false);
    	alertDialog.setMessage("Geben Sie hier einen Namen ein:");


    	final EditText positionName = (EditText) view.findViewById(R.id.positionName);

    	alertDialog.setButton(DialogInterface.BUTTON_POSITIVE, "OK", new DialogInterface.OnClickListener() {
    	    @Override
    	    public void onClick(DialogInterface dialog, int which) {

    			try
    			{
    				m_home = lastLocation;

    	        	String homeName = positionName.getText().toString();
    	        	String homeStr = locationString(m_home);

    	        	SharedPreferences.Editor editor = m_waypoints.edit();
    	            editor.putString(homeName, homeStr );
    	            editor.commit();

    	        	alertDialog.dismiss();
    			}
    			catch (NumberFormatException e)
    			{
    				// ignore, don't change speed
    			}
    	    }
    	});


    	alertDialog.setButton(DialogInterface.BUTTON_NEGATIVE, "Abbruch", new DialogInterface.OnClickListener() {
    	    @Override
    	    public void onClick(DialogInterface dialog, int which) {
    	        alertDialog.dismiss();
    	    }
    	});

    	       
    	alertDialog.setView(view);
    	alertDialog.show();    	
	}
	
	private enum SelectorMode { LOAD_POS, DELETE_POS };
	
	private void selectPosition( final SelectorMode mode )
	{
		// build the dialog
    	LayoutInflater layoutInflater = getLayoutInflater();
    	final View view = layoutInflater.inflate(R.layout.select_position, null);
    	final AlertDialog alertDialog = new AlertDialog.Builder(this).create();
    	alertDialog.setTitle("Position " + ((mode == SelectorMode.LOAD_POS) ? "laden" : "löschen"));
    	alertDialog.setIcon(R.drawable.icon);
    	alertDialog.setCancelable(true);
    	alertDialog.setMessage("Wählen Sie einen Wegpunkt aus:");

    	// load the way points
    	Map<String,?> map = m_waypoints.getAll();
    	Set<String> keys = map.keySet();
    	final ArrayList<String>  myArray = new ArrayList<String>();
    	for (String name : keys )
    	{
    		myArray.add(name);
    	}
    	Collections.sort(myArray);
    	
    	// fill the list view
    	final ListView positionList = (ListView) view.findViewById(R.id.positionList);
    	ArrayAdapter<String> adapter = new ArrayAdapter<String>(this,R.layout.select_position,R.id.positionListItem, myArray);
		positionList.setAdapter(adapter);

		// configure the click handler
		OnItemClickListener messageClickedHandler = new OnItemClickListener() {
			@Override
		    public void onItemClick(AdapterView<?> parent, View v, int listViewPosition, long id) 
			{
		        // Do something in response to the click.
				String viewItem = myArray.get(listViewPosition);
				if( mode == SelectorMode.DELETE_POS)
				{
			        SharedPreferences.Editor editor = m_waypoints.edit();

			        editor.remove(viewItem);

			        // Commit the edits!
			        editor.commit();
				}
				else if( mode == SelectorMode.LOAD_POS)
				{
					m_home = locationString(m_waypoints.getString(viewItem, ""));
				}

				alertDialog.dismiss();
		    }
		};
		positionList.setOnItemClickListener(messageClickedHandler);

		// configure the cancel button
    	alertDialog.setButton(DialogInterface.BUTTON_NEGATIVE, "Abbruch", new DialogInterface.OnClickListener() {
    	    @Override
    	    public void onClick(DialogInterface dialog, int which) {
    	        alertDialog.dismiss();
    	    }
    	});

    	alertDialog.setView(view);
    	alertDialog.show();    	
	}

	@Override
    public boolean onCreateOptionsMenu( android.view.Menu menu )
    {
    	MenuInflater inflater = getMenuInflater();
    	inflater.inflate(R.menu.gwp_menu, menu);
    	
    	return super.onCreateOptionsMenu(menu);
    }
	
	@Override
	public boolean onPrepareOptionsMenu(Menu menu)
	{
		boolean hasWayPoints = m_waypoints!= null && m_waypoints.getAll().size() > 0;
		menu.findItem(R.id.loadPos).setEnabled(hasWayPoints);
		menu.findItem(R.id.deletePos).setEnabled(hasWayPoints);
		boolean hasLocation = m_locationList.peek() != null;;
		menu.findItem(R.id.savePos).setEnabled(hasLocation);
		menu.findItem(R.id.savePosAs).setEnabled(hasLocation);

		return super.onPrepareOptionsMenu(menu);
	}

	@Override
    public boolean onOptionsItemSelected( MenuItem item )
    {
    	int	itemId = item.getItemId();
    	System.out.println( itemId );
    	switch( itemId )
    	{
    	case R.id.loadPos:
    		selectPosition(SelectorMode.LOAD_POS);
    		break;
    	case R.id.deletePos:
    		selectPosition(SelectorMode.DELETE_POS);
    		break;
    	case R.id.savePosAs:
    	{
        	Location lastLocation = m_locationList.peek();
        	if (lastLocation != null)
        	{
        		savePositionAs(lastLocation);
        	}
        	break;
    	}
    	case R.id.savePos:
    	{
        	Location lastLocation = m_locationList.peek();
        	if (lastLocation != null)
        	{
        		m_home = lastLocation;
        	}
        	break;
    	}
    	case R.id.exit:
    		finish();
            break;
    	case R.id.about:
    		showResult(
    			"GpsWayPoints", 
    			"GpsWayPoints 2.6.5\n(c) 2024 by Martin Gäckler\nhttps://www.gaeckler.at/"
    		);
    		break;
    	default:
    		break;
    	}

    	return super.onOptionsItemSelected(item);
    }

    @Override
    public void onOptionsMenuClosed(Menu menu) {
        super.onOptionsMenuClosed(menu);
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.CUR_DEVELOPMENT) {
            // Workaround for https://issuetracker.google.com/issues/315761686
            invalidateOptionsMenu();
        }
    }
    
    @Override
    public void onPause()
    {
    	SharedPreferences settings = getSharedPreferences(CONFIGURATION_FILE, 0);
        SharedPreferences.Editor editor = settings.edit();

        editor.putFloat(HOME_LONGITUDE_KEY, (float) m_home.getLongitude() );
        editor.putFloat(HOME_LATITUDE_KEY, (float) m_home.getLatitude());

		// Commit the edits!
        editor.commit();

        super.onPause();
    }
	@Override
	public void onDestroy()
	{
        // Acquire a reference to the system Location Manager
        // LocationManager locationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);

		m_locationManager.removeUpdates( m_locationListener );
		m_locationManager.removeGpsStatusListener( m_gpsStatusListener );
        
    	SharedPreferences settings = getSharedPreferences(CONFIGURATION_FILE, 0);
        SharedPreferences.Editor editor = settings.edit();

        editor.putFloat(HOME_LONGITUDE_KEY, (float) m_home.getLongitude() );
        editor.putFloat(HOME_LATITUDE_KEY, (float) m_home.getLatitude());

        // Commit the edits!
        editor.commit();

        m_wakeLock.release();
        super.onDestroy();
    }
	
	@Override
	protected void  onSaveInstanceState (Bundle outState)
	{
		outState.putDouble(HOME_LONGITUDE_KEY, m_home.getLongitude() );
		outState.putDouble(HOME_LATITUDE_KEY, m_home.getLatitude());
		outState.putLong(FIX_COUNT_KEY, m_locationFixCount);
	}
	
	void onLocationChanged2( Location newLocation )
    {
    	double	speed, sDistance, elapsedTime, curBearing;
    	
    	++m_locationFixCount;
    	m_accuracy = newLocation.getAccuracy();
    	setStatus( m_myStatus );

    	// calculate the current bearing
    	{
    		double sumBearing = 0;
    		double minBearing = 1000;
    		double maxBearing = -1000;
    		int countPoints = 0;
	    	for( Location curLoc : m_locationList )
	    	{
	    		if( curLoc.distanceTo(newLocation) > MIN_DISTANCE )
	    		{
	    			final double bearing = curLoc.bearingTo(newLocation);
	    			if( bearing < minBearing )
	    			{
	    				minBearing = bearing;
	    			}
	    			else if( bearing > maxBearing )
	    			{
	    				maxBearing = bearing;
	    			}
	    			sumBearing += bearing;
	    			countPoints++;
	    		}
	    	}
	    	sumBearing -= minBearing;
	    	sumBearing -= maxBearing;
	    	countPoints -= 2;
	    	if( countPoints > MIN_BEARING_COUNT )
	    	{
	    		m_curBearing = curBearing = sumBearing / countPoints;
	    		
	    	}
	    	else
	    	{
	    		curBearing = m_curBearing;
	    	}
    	}
    	
    	// remove outdated way points
    	Location speedLocation = m_locationList.peek(); 
    	if( speedLocation != null )
    	{
    		long maxTime = newLocation.getTime() - MAX_AGE_MS;
    		while( (speedLocation.distanceTo(newLocation) > m_accuracy*2 || speedLocation.getTime() < maxTime) 
    				&& m_locationList.size() > MIN_LOCATION_COUNT)
    		{
    			m_locationList.remove();
    			Location tmpLocation = m_locationList.peek();
    			if( tmpLocation == null )
    				break;
    			if( tmpLocation.distanceTo(newLocation) < m_accuracy )
    				break;
    			speedLocation = tmpLocation;
    		}
    		
    	}

    	// find the position to calculate the speed
    	if( speedLocation != null )
    	{
    		long maxTime = newLocation.getTime() - 2000;
			speedLocation = null;
			for( Location curLoc : m_locationList )
			{
				if( curLoc.getTime() < maxTime )
				{
					break;
				}
	    		if( curLoc.distanceTo(newLocation) > m_accuracy )
	    		{
	    			speedLocation = curLoc;
	    			break;
	    		}
			}
    	}
    	
    	// calculate the current speed
    	if( speedLocation != null )
    	{
			sDistance = speedLocation.distanceTo(newLocation);
			elapsedTime = newLocation.getTime() - speedLocation.getTime();
			elapsedTime /= 1000;
	    }
    	else
    	{
    		sDistance = 0;
    		elapsedTime = 0;
    	}
    	
    	if( elapsedTime > 0 && sDistance >= m_accuracy )
    		speed = (sDistance / elapsedTime) * 3.6;
    	else if( newLocation.hasSpeed() )
    		speed = newLocation.getSpeed() * 3.6;
    	else
    		speed = 0;
    	
    	{
    		final double absHomeBearing = newLocation.bearingTo(m_home);
    		showMovement( speed, m_home.distanceTo(newLocation), absHomeBearing, curBearing );
    	}
    	
    	setAltitude(newLocation);
    	
    	m_locationList.add(newLocation);
    }
	
	private int getCorrectedAltidute( Location loc)
	{
		return (int)loc.getAltitude()-70;
	}
	private void setAltitude( Location newLocation )
	{
		int altidute = getCorrectedAltidute(newLocation); 
		int gpsAltidute = (int)newLocation.getAltitude();
		
    	m_altitudeLabel.setText( 
    		Integer.toString(altidute) + "m (" + Integer.toString(gpsAltidute) + ")/" +
    		Double.toString(newLocation.getLongitude()) + '/' + Double.toString(newLocation.getLatitude())
    	);
	}
	
    void showMovement( double speed, double totalDistance, double absHomeBearing, double currBearing )
    {
    	m_theRose.showMovement(speed, (int)totalDistance, absHomeBearing, currBearing );
    }
    void setStatus( String text )
    {
    	m_myStatus = text;
    	m_statusLabel.setText( 
			text + ' ' + 
			m_accuracyFormat.format(m_accuracy) + ' ' + 
			Long.toString(m_locationFixCount) + '/' +
			Integer.toString(m_locationList.size())
    	);
    }
    private void showResult( String title, String resultString )
    {
    	AlertDialog.Builder builder = new AlertDialog.Builder(this);
    	builder.setMessage(resultString)
    		   .setTitle(title)
    	       .setCancelable(false)
    	       .setNegativeButton("Fertig", new DialogInterface.OnClickListener() {
    	           public void onClick(DialogInterface dialog, int id) {
    	                dialog.cancel();
    	           }
    	       })
    	       .setIcon(R.drawable.icon);
    	AlertDialog alert = builder.create();
    	alert.show();
    }
}