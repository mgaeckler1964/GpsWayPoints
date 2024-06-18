package at.gaeckler.GpsWayPoints;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.GpsSatellite;
import android.location.GpsStatus;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
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
	static final int MAX_AGE_MS = 5000;
	static final int MIN_LOCATION_COUNT = 20;
	static final int MIN_BEARING_COUNT = 2;
	static final String CONFIGURATION_FILE = "GpsWayPoints.cfg";
	static final String WAYPOINTS_FILE = "GpsWayPoints.gwp";
	static final String	HOME_KEY = "homePosition";

	static final String	CALIBRATION_KEY = "calibrationMode";
	static final String	FIX_COUNT_KEY = "fixCount";
	static final String	SUM_LONGITUDE_KEY = "sumLongitude";
	static final String	SUM_LATITUDE_KEY = "sumLatitude";
	static final String	SUM_ALTITUDE_KEY = "sumAltitude";
	
	GpsWayPointsWidget		m_theRose = null;
	TextView				m_statusView = null;
	TextView				m_altitudeView = null;
	double					m_altitude = 0;
	double					m_curBearing = 0;
	double					m_homeBearing = 0;
	
	String					m_myStatus = "Willkommen";
	LocationManager			m_locationManager;
	LocationListener		m_locationListener = null;
	GpsStatus.Listener		m_gpsStatusListener;
	Queue<Location>			m_locationList = new LinkedList<Location>();
	boolean					m_calibration = false;
	double					m_sumLongitude = 0;
	double					m_sumLatitude = 0;
	double					m_sumAltitude = 0;
	long					m_locationFixCount = 0;
	double					m_accuracy = 0.0;
	private DecimalFormat	m_accuracyFormat = new DecimalFormat( "Genauigkeit: 0.000m" );
	PowerManager.WakeLock	m_wakeLock;
	CountDownTimer			m_gpsTimer = null;

	Location				m_home = new Location("");
	SharedPreferences 		m_waypoints = null;
	
	/** Called when the activity is first created. */
	@Override
    public void onCreate(Bundle savedInstanceState)
    {
		System.out.println("onCreate");
        super.onCreate(savedInstanceState);
        if( checkCallingOrSelfPermission("android.permission.ACCESS_FINE_LOCATION") == PackageManager.PERMISSION_DENIED )
        {
        	showMessage("GpsWayPoints", "Berechtigung für Standort fehlt!", true);
        	return;
        }

    	m_waypoints = getSharedPreferences(WAYPOINTS_FILE, 0);

    	String homeStr = "";
    	if( savedInstanceState != null )
        {
        	homeStr = savedInstanceState.getString(HOME_KEY);
            m_locationFixCount = savedInstanceState.getLong(FIX_COUNT_KEY);
            m_calibration = savedInstanceState.getBoolean(CALIBRATION_KEY);
            m_sumLongitude = savedInstanceState.getDouble(SUM_LONGITUDE_KEY);
            m_sumLatitude = savedInstanceState.getDouble(SUM_LATITUDE_KEY);
            m_sumAltitude = savedInstanceState.getDouble(SUM_ALTITUDE_KEY);
        }
        else
        {
        	SharedPreferences settings = getSharedPreferences(CONFIGURATION_FILE, 0);
        	homeStr = settings.getString(HOME_KEY,"");
        }
    	Location tmpLocation = locationString(homeStr);
    	if( tmpLocation != null )
    	{
    		m_home = tmpLocation;
    	}
    	else
    	{
        	m_home.setLongitude(14.282733);
        	m_home.setLatitude(48.298820);
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

        m_statusView = (TextView)findViewById( R.id.statusView );
    	setStatus( m_myStatus );
    	m_theRose = (GpsWayPointsWidget)findViewById( R.id.myRose );
    	m_altitudeView = (TextView)findViewById( R.id.altitudeView );

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
            		clearMovementDisplay();
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
            	clearMovementDisplay();
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
        clearMovementDisplay();

        m_gpsTimer = new CountDownTimer(100000000, 1000) {

        	private Location m_lastKnown=null;

        	@Override
        	public void onTick(long millisUntilFinished) {
        		Location newLocation = m_locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
        		if (newLocation != null && (m_lastKnown==null || !m_lastKnown.equals(newLocation)))
        		{
        			onLocationChanged2(newLocation);
        		}
        	}
		
        	@Override
        	public void onFinish() {
        		m_gpsTimer.start();
        	}
		}.start();

        //onLocationChanged2(m_home);
	}

	String locationString( Location src )
	{
		return src.getProvider() + '|' + 
				Double.toString(src.getLongitude()) + '|' + 
				Double.toString(src.getLatitude()) + '|' +
				Double.toString(src.getAltitude());  
	}
	Location locationString( String src )
	{
		String [] elements = src.split("[|]");
		if(elements.length < 3 || elements.length > 4) {
			return null;
		}
		
		Location newLocation = new Location(elements[0]);
		newLocation.setLongitude(Double.parseDouble(elements[1]));
		newLocation.setLatitude(Double.parseDouble(elements[2]));
		if (elements.length == 4) {
			newLocation.setAltitude(Double.parseDouble(elements[3]));;
		}
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

		menu.findItem(R.id.calibration).setChecked(m_calibration);

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
    	case R.id.calibration:
    		if( !m_calibration )
    		{
    	    	m_calibration = true;
    	    	m_sumLongitude = 0;
    	    	m_sumLatitude = 0;
    	    	m_sumAltitude = 0;
    	    	m_locationFixCount = 0;    		}
    		else
    		{
    	    	m_calibration = false;
    		}
    		break;

    	case R.id.exit:
    		finish();
            break;
    	case R.id.about:
    	{
    		String name = getString(R.string.app_name);
    		String version = getString(R.string.app_version);
    		showMessage(
    			name, 
    			name + " "+version+"\n(c) 2024 by Martin Gäckler\nhttps://www.gaeckler.at/",
    			false
    		);
    		break;
    	}
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
    
    private void saveSharedPreferences()
    {
    	SharedPreferences settings = getSharedPreferences(CONFIGURATION_FILE, 0);
        SharedPreferences.Editor editor = settings.edit();

        editor.putString(HOME_KEY, locationString(m_home) );

		// Commit the edits!
        editor.commit();
    }
    
    @Override
    public void onPause()
    {
    	saveSharedPreferences();
        super.onPause();
    }
	@Override
	public void onDestroy()
	{
        // Acquire a reference to the system Location Manager
        // LocationManager locationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);

		m_locationManager.removeUpdates( m_locationListener );
		m_locationManager.removeGpsStatusListener( m_gpsStatusListener );
        
    	saveSharedPreferences();

        m_wakeLock.release();
        super.onDestroy();
    }
	
	@Override
	protected void  onSaveInstanceState (Bundle outState)
	{
		outState.putString(HOME_KEY, locationString(m_home));
		outState.putLong(FIX_COUNT_KEY, m_locationFixCount);
		outState.putBoolean(CALIBRATION_KEY, m_calibration);
		outState.putDouble(SUM_LONGITUDE_KEY, m_sumLongitude);
		outState.putDouble(SUM_LATITUDE_KEY, m_sumLatitude);
		outState.putDouble(SUM_ALTITUDE_KEY, m_sumAltitude);
	}
	
	private final ReentrantLock m_lock = new ReentrantLock();
	void onLocationChanged2( Location newLocation )
    {
		 m_lock.lock();
		 try {
			 onLocationChanged3( newLocation );
		 } finally {
			 m_lock.unlock();
		 }
		 
    }

	private void onLocationChanged3( Location newLocation )
    {

		double	speed, sDistance, elapsedTime, curBearing;
    	
    	++m_locationFixCount;
    	if( m_calibration )
    	{
    		m_sumLongitude += newLocation.getLongitude();
    		m_sumLatitude += newLocation.getLatitude();
    		m_sumAltitude += newLocation.getAltitude();
    	}

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
	    		if( curLoc.distanceTo(newLocation) >= m_accuracy )
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
    		showMovement( 
    			speed, 
    			m_home.distanceTo(newLocation), m_home.getAltitude()-newLocation.getAltitude(), 
    			absHomeBearing, curBearing 
    		);
    	}
    	
    	setAltitude(newLocation);
    	
    	m_locationList.add(newLocation);
    }

	// correction valid for Linz/Austria
	private int getCorrectedAltidute( Location loc)
	{
		return (int)loc.getAltitude()-50;
	}
	
	private void setAltitude( Location newLocation )
	{
		int snapedAltidute = getCorrectedAltidute(newLocation);
		double longitude, latitude, altitude;

		if(m_calibration)
		{
			longitude = m_sumLongitude/m_locationFixCount;
			latitude = m_sumLatitude/m_locationFixCount;
			altitude = m_sumAltitude/m_locationFixCount;
		}
		else
		{
			longitude = newLocation.getLongitude();
			latitude = newLocation.getLatitude();
			altitude = (int)newLocation.getAltitude();
		}
		
    	m_altitudeView.setText( 
    		(m_calibration ? "*" : " ") +
    		Integer.toString(snapedAltidute) + "m (" + Integer.toString((int)(altitude+0.5)) + ")/" +
    		Double.toString(longitude) + '/' + Double.toString(latitude)
    	);
	}
	
    void showMovement( double speed, double distanceDM, double distanceHM, double absHomeBearing, double currBearing )
    {
    	m_theRose.showMovement(
    		speed, 
    		(int)(distanceDM+0.5), (int)(distanceHM+0.5), 
    		absHomeBearing, currBearing 
    	);
    }
    void clearMovementDisplay()
    {
    	m_theRose.clearMovementDisplay();
    }
    void setStatus( String text )
    {
    	m_myStatus = text;
    	m_statusView.setText( 
			text + ' ' + 
			m_accuracyFormat.format(m_accuracy) + ' ' + 
			Long.toString(m_locationFixCount) + '/' +
			Integer.toString(m_locationList.size())
    	);
    }
    private void showMessage( String title, String resultString, final boolean terminate )
    {
    	AlertDialog.Builder builder = new AlertDialog.Builder(this);
    	builder.setMessage(resultString)
    		   .setTitle(title)
    	       .setCancelable(false)
    	       .setNegativeButton("Fertig", new DialogInterface.OnClickListener() {
    	           public void onClick(DialogInterface dialog, int id) {
    	                dialog.cancel();
    	                if( terminate )
    	                {
    	                	finish();
    	                }
    	           }
    	       })
    	       .setIcon(R.drawable.icon);
    	AlertDialog alert = builder.create();
    	alert.show();
    }
}