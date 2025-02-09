package at.gaeckler.GpsWayPoints;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.GpsSatellite;
import android.location.GpsStatus;
import android.location.Location;
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
import at.gaeckler.gps.GpsActivity;
import at.gaeckler.gps.GpsProcessor;

public class GpsWayPointsActivity extends GpsActivity
{
	static final String CONFIGURATION_FILE = "GpsWayPoints.cfg";
	static final String WAYPOINTS_FILE = "GpsWayPoints.gwp";
	static final String	HOME_KEY = "homePosition";
	static final String	GPS_SPEED_KEY = "gpsInterval";
	static final String	LAST_NAME_KEY = "lastName";
	static final String	DARK_MODE_KEY = "darkMode";

	static final String	CALIBRATION_KEY = "calibrationMode";
	static final String	FIX_COUNT_KEY = "fixCount";
	static final String	SUM_LONGITUDE_KEY = "sumLongitude";
	static final String	SUM_LATITUDE_KEY = "sumLatitude";
	static final String	SUM_ALTITUDE_KEY = "sumAltitude";

	boolean					m_darkMode = false;

	GpsWayPointsWidget		m_theRose = null;
	TextView				m_statusView = null;
	TextView				m_altitudeView = null;
	TextView				m_waypointNameView = null;
	double					m_homeBearing = 0;
	
	String					m_myStatus = "Willkommen";

	boolean					m_calibration = false;
	double					m_sumLongitude = 0;
	double					m_sumLatitude = 0;
	double					m_sumAltitude = 0;
	long					m_locationFixCount = 0;
	private DecimalFormat	m_accuracyFormat = new DecimalFormat( "Genauigkeit: 0.000m" );
	PowerManager.WakeLock	m_wakeLock;
	
	String 					m_lastName = null;
	Location				m_home = new Location("");
	SharedPreferences 		m_waypoints = null;
	
    public void showMessage( String title, String message, final boolean terminate )
    {
    	AlertDialog.Builder builder = new AlertDialog.Builder(this);
    	builder.setMessage(message)
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

    private void switchColorMode()
    {
        if( m_darkMode )
        {
        	m_theRose.useBlackBackground();
        }
        else
        {
        	m_theRose.useWhiteBackground();
        }
    }

    /** Called when the activity is first created. */
	@Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        if( checkCallingOrSelfPermission("android.permission.ACCESS_FINE_LOCATION") == PackageManager.PERMISSION_DENIED )
        {
        	return;
        }

    	m_waypoints = getSharedPreferences(WAYPOINTS_FILE, 0);

    	String homeStr;
    	int gpsInterval;
    	if( savedInstanceState != null )
        {
        	homeStr = savedInstanceState.getString(HOME_KEY,"");
        	m_lastName = savedInstanceState.getString(LAST_NAME_KEY,"");
            m_locationFixCount = savedInstanceState.getLong(FIX_COUNT_KEY,0);
            m_calibration = savedInstanceState.getBoolean(CALIBRATION_KEY,false);
            m_sumLongitude = savedInstanceState.getDouble(SUM_LONGITUDE_KEY,0);
            m_sumLatitude = savedInstanceState.getDouble(SUM_LATITUDE_KEY,0);
            m_sumAltitude = savedInstanceState.getDouble(SUM_ALTITUDE_KEY,0);
            m_darkMode = savedInstanceState.getBoolean(DARK_MODE_KEY,false);
            gpsInterval = savedInstanceState.getInt(GPS_SPEED_KEY,0); 
        }
        else
        {
        	SharedPreferences settings = getSharedPreferences(CONFIGURATION_FILE, 0);
        	homeStr = settings.getString(HOME_KEY,"");
        	m_lastName = settings.getString(LAST_NAME_KEY,"");
        	m_darkMode = settings.getBoolean(DARK_MODE_KEY,false);
            gpsInterval = settings.getInt(GPS_SPEED_KEY,0); 
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
        	setCorrectedAltitude(m_home, 260);
    	}
    	createGpsTimer(gpsInterval);

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
    	m_waypointNameView = (TextView)findViewById( R.id.waypointNameView );

        System.out.println("showSpeed");
        clearMovementDisplay();

        updateWaypointName();
        //simulateLocationFix(m_home);
        switchColorMode();
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
		String provider = elements[0];
		double longitude = Double.parseDouble(elements[1]);
		double latitude = Double.parseDouble(elements[2]);
		
		if( Math.abs(longitude) < 0.01 && Math.abs(latitude) < 0.01)
		{
			return null;
		}
		Location newLocation = new Location(provider);
		newLocation.setLongitude(longitude);
		newLocation.setLatitude(latitude);
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
    	if (m_lastName != null)
    	{
    		positionName.setText(m_lastName);
    	}

    	final EditText positionLongitude = (EditText) view.findViewById(R.id.positionLongitude);
    	final EditText positionLatitude = (EditText) view.findViewById(R.id.positionLatitude);
    	final EditText positionAltitude = (EditText) view.findViewById(R.id.positionAltitude);

    	alertDialog.setButton(DialogInterface.BUTTON_POSITIVE, "OK", new DialogInterface.OnClickListener() {
    	    @Override
    	    public void onClick(DialogInterface dialog, int which) {

    			try
    			{
    	        	String homeName = positionName.getText().toString();
    	        	if ( homeName != null && homeName.length()>0 )
    	        	{
        				m_home = lastLocation;
        				
        				{
	        				String homeLongitude = positionLongitude.getText().toString();
	        				if( homeLongitude != null && homeLongitude.length() > 0 )
	        				{
	        					double longitude = Double.parseDouble(homeLongitude);
	        					if (longitude < -180 || longitude > 180 )
	        					{
	        						throw new NumberFormatException();   
	        					}
	        					m_home.setLongitude(longitude);
	        				}
        				}

        				{
	        				String homeLatitude = positionLatitude.getText().toString();
	        				if( homeLatitude != null && homeLatitude.length() > 0 )
	        				{
	        					double latitude = Double.parseDouble(homeLatitude);
	        					if (latitude < -90 || latitude > 90 )
	        					{
	        						throw new NumberFormatException();   
	        					}
	        					m_home.setLatitude(latitude);
	        				}
        				}

        				{
	        				String homeAltitude = positionAltitude.getText().toString();
	        				if( homeAltitude != null && homeAltitude.length() > 0 )
	        				{
	        					double altitude = Double.parseDouble(homeAltitude);
	        					if (altitude < -11000 || altitude > 9000 )
	        					{
	        						throw new NumberFormatException();   
	        					}
	        					setCorrectedAltitude( m_home, altitude );
	        				}
        				}

        	        	String homeStr = locationString(m_home);

        	        	SharedPreferences.Editor editor = m_waypoints.edit();
        	            editor.putString(homeName, homeStr );
        	            editor.commit();
        	            
        	            m_lastName = homeName;
        	            updateWaypointName();

        	        	alertDialog.dismiss();
    	        	}
    			}
    			catch (NumberFormatException e)
    			{
    				// stop processing the input
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
					m_lastName = viewItem;
			        updateWaypointName();
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

		boolean hasLocation = getHasLocation();
		menu.findItem(R.id.savePos).setEnabled(hasLocation);
		menu.findItem(R.id.savePosAs).setEnabled(hasLocation);

		menu.findItem(R.id.calibration).setChecked(m_calibration);
		menu.findItem(R.id.darkMode).setChecked(m_darkMode);

		int gpsInterval = getInterval();
		menu.findItem(R.id.autoGps).setChecked(gpsInterval==AUTO_GPS);
		menu.findItem(R.id.fastGps).setChecked(gpsInterval==FAST_GPS);
		menu.findItem(R.id.normalGps).setChecked(gpsInterval==NORMAL_GPS);
		menu.findItem(R.id.slowGps).setChecked(gpsInterval==SLOW_GPS);
		
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
        	Location lastLocation = getLastLocation();
        	if (lastLocation != null)
        	{
        		savePositionAs(lastLocation);
        	}
        	break;
    	}
    	case R.id.savePos:
    	{
        	Location lastLocation = getLastLocation();
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

    	case R.id.autoGps:
    		removeGpsTimer();
    		break;
    	case R.id.fastGps:
    		createGpsTimer(FAST_GPS);
    		break;
    	case R.id.normalGps:
    		createGpsTimer(NORMAL_GPS);
    		break;
    	case R.id.slowGps:
    		createGpsTimer(SLOW_GPS);
    		break;
    	case R.id.darkMode:
    		m_darkMode = !m_darkMode;
            switchColorMode();
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
        editor.putString(LAST_NAME_KEY, m_lastName);
        editor.putBoolean(DARK_MODE_KEY, m_darkMode);
        editor.putInt(GPS_SPEED_KEY, getInterval() );

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
    	saveSharedPreferences();

        m_wakeLock.release();
        super.onDestroy();
    }
	
	@Override
	protected void  onSaveInstanceState (Bundle outState)
	{
		outState.putString(HOME_KEY, locationString(m_home));
		outState.putString(LAST_NAME_KEY, m_lastName);
		outState.putLong(FIX_COUNT_KEY, m_locationFixCount);
		outState.putBoolean(CALIBRATION_KEY, m_calibration);
		outState.putDouble(SUM_LONGITUDE_KEY, m_sumLongitude);
		outState.putDouble(SUM_LATITUDE_KEY, m_sumLatitude);
		outState.putDouble(SUM_ALTITUDE_KEY, m_sumAltitude);
		outState.putInt(GPS_SPEED_KEY, getInterval());
		outState.putBoolean(DARK_MODE_KEY, m_darkMode);
	}
	
	// correction valid for Linz/Austria
	static private int getCorrectedAltidute( Location loc )
	{
		return (int)loc.getAltitude()-50;
	}
	static void setCorrectedAltitude( Location loc, double altitude )
	{
		loc.setAltitude(altitude+50);
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
    		GpsProcessor.speedToKmh(speed), 
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
			m_accuracyFormat.format(getAccuracy()) + ' ' + 
			Long.toString(m_locationFixCount) + '/' +
			Integer.toString(getNumLocations())
    	);
    }
    void updateWaypointName()
    {
    	m_waypointNameView.setText(m_lastName);
    }

	@Override
	public void onLocationServiceOff() {
		setStatus( "Kein GPS Empfang" );
		clearMovementDisplay();
	}

	@Override
	public void onLocationTempOff() {
		setStatus( "Kurzfristig kein GPS Empfang" );
	}

	@Override
	public void onLocationServiceOn() {
		setStatus( "GPS Empfang" );
	}

	@Override
	public void onLocationEnabled()
	{
    	setStatus( "GPS ist eingeschaltet");
	}

	@Override
	public void onLocationDisabled()
	{
    	setStatus( "GPS ist abgeschaltet");
    	clearMovementDisplay();
	}
	
	@Override
	public void onGpsStatusChanged2(int event)
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
			for (GpsSatellite sat : getSatellites())
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

	@Override
	public void onLocationChanged( Location newLocation )
    {
    	++m_locationFixCount;
    	if( m_calibration )
    	{
    		m_sumLongitude += newLocation.getLongitude();
    		m_sumLatitude += newLocation.getLatitude();
    		m_sumAltitude += newLocation.getAltitude();
    	}

    	setStatus( m_myStatus );

    	{
    		final double absHomeBearing = newLocation.bearingTo(m_home);
    		showMovement( 
    			getSpeed(), 
    			m_home.distanceTo(newLocation), m_home.getAltitude()-newLocation.getAltitude(), 
    			absHomeBearing, getCurBearing() 
    		);
    	}
    	
    	setAltitude(newLocation);
    }

	@Override
	public void onPermissionError() {
		showMessage("GpsWayPoints", "Berechtigung für Standort fehlt!", true);
	}


}