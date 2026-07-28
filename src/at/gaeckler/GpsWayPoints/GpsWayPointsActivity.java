/*
		Project:		GpsWayPoints
		Module:			GpsWayPointsActivity.java
		Description:	The android activity
		Author:			Martin Gäckler
		Address:		Hofmannsthalweg 14, A-4030 Linz
		Web:			https://www.gaeckler.at/

		Copyright:		(c) 2024-2026 Martin Gäckler

		This program is free software: you can redistribute it and/or modify
		it under the terms of the GNU General Public License as published by
		the Free Software Foundation, version 3.

		You should have received a copy of the GNU General Public License
		along with this program. If not, see <http://www.gnu.org/licenses/>.

		THIS SOFTWARE IS PROVIDED BY Martin Gäckler, Linz, Austria ``AS IS''
		AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED
		TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A
		PARTICULAR PURPOSE ARE DISCLAIMED.  IN NO EVENT SHALL THE AUTHOR OR
		CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
		SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
		LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF
		USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
		ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
		OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT
		OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF
		SUCH DAMAGE.
*/
package at.gaeckler.GpsWayPoints;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.GnssStatus;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
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
	private static final String CONFIGURATION_FILE = "GpsWayPoints.cfg";
	private static final String WAYPOINTS_FILE = "GpsWayPoints.gwp";
	private static final String	HOME_KEY = "homePosition";
	private static final String	GPS_SPEED_KEY = "gpsInterval";
	private static final String	LAST_NAME_KEY = "lastName";
	private static final String	DARK_MODE_KEY = "darkMode";

	private static final String s_filenameExternalPublic = "gpsWayPointsPub.txt";
	private static final String s_filenameExternalPrivate = "gpsWayPointsPriv.txt";

	private static final DecimalFormat	s_accuracyFormat = new DecimalFormat( "Genauigkeit: 0.000m" );

	private boolean					m_darkMode = false;

	private GpsWayPointsWidget		m_theRose = null;
	private TextView				m_statusView = null;
	private TextView				m_altitudeView = null;
	private TextView				m_waypointNameView = null;
	
	private String					m_myStatus = "Willkommen";

	String 							m_lastName = null;			// default access
	Location						m_home = new Location("");	// default access
	SharedPreferences 				m_waypoints = null;			// default access
	
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
        // Prüfen, ob "Zugriff auf alle Dateien" bereits gewährt wurde:
        if (!Environment.isExternalStorageManager()) {
            try {
                Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                intent.addCategory("android.intent.category.DEFAULT");
                intent.setData(Uri.parse(String.format("package:%s", getPackageName())));
                startActivity(intent);
            } catch (Exception e) {
                Intent intent = new Intent();
                intent.setAction(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                startActivity(intent);
            }
        }

    	m_waypoints = getSharedPreferences(WAYPOINTS_FILE, Context.MODE_PRIVATE);

    	String homeStr;
    	int gpsInterval;
    	if( savedInstanceState != null )
        {
        	homeStr = savedInstanceState.getString(HOME_KEY,"");
        	m_lastName = savedInstanceState.getString(LAST_NAME_KEY,"");
            m_darkMode = savedInstanceState.getBoolean(DARK_MODE_KEY,false);
            gpsInterval = savedInstanceState.getInt(GPS_SPEED_KEY,0); 
        }
        else
        {
        	SharedPreferences settings = getSharedPreferences(CONFIGURATION_FILE, Context.MODE_PRIVATE);
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

		getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

		System.out.println("setContentView");
        setContentView(R.layout.main);

        m_statusView = findViewById( R.id.statusView );
    	setStatus( m_myStatus );
    	m_theRose = findViewById( R.id.myRose );
    	m_altitudeView = findViewById( R.id.altitudeView );
    	m_waypointNameView = findViewById( R.id.waypointNameView );

        System.out.println("showSpeed");
        clearMovementDisplay();

        updateWaypointName();
        //simulateLocationFix(m_home);
        switchColorMode();
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


    	final EditText positionName = view.findViewById(R.id.positionName);
    	if (m_lastName != null)
    	{
    		positionName.setText(m_lastName);
    	}

    	final EditText positionLongitude = view.findViewById(R.id.positionLongitude);
    	final EditText positionLatitude = view.findViewById(R.id.positionLatitude);
    	final EditText positionAltitude = view.findViewById(R.id.positionAltitude);

    	alertDialog.setButton(DialogInterface.BUTTON_POSITIVE, "OK", new DialogInterface.OnClickListener() {
    	    @Override
    	    public void onClick(DialogInterface dialog, int which) {

    			try
    			{
    	        	String homeName = positionName.getText().toString();
    	        	if ( !homeName.isEmpty() )
    	        	{
        				m_home = lastLocation;
        				
        				{
	        				String homeLongitude = positionLongitude.getText().toString();
	        				if( !homeLongitude.isEmpty() )
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
	        				if( !homeLatitude.isEmpty() )
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
	        				if( !homeAltitude.isEmpty() )
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
        	            editor.apply();
        	            
        	            m_lastName = homeName;
        	            updateWaypointName();

        	        	alertDialog.dismiss();
    	        	}
    	        	onLocationChanged(lastLocation);
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
	
	private enum SelectorMode { LOAD_POS, DELETE_POS }
	
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
		final ArrayList<String> myArray = new ArrayList<>(keys);
    	Collections.sort(myArray);
    	
    	// fill the list view
    	final ListView positionList = view.findViewById(R.id.positionList);
    	ArrayAdapter<String> adapter = new ArrayAdapter<>(this,R.layout.select_position,R.id.positionListItem, myArray);
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
			        editor.apply();
				}
				else if( mode == SelectorMode.LOAD_POS)
				{
					m_lastName = viewItem;
			        updateWaypointName();
					m_home = locationString(m_waypoints.getString(viewItem, ""));
					Location last = getLastLocation();
					if( last != null )					// do we have a GPS-fix?
						onLocationChanged(last);		// update the display
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
		boolean hasWayPoints = m_waypoints!= null && !m_waypoints.getAll().isEmpty();
		menu.findItem(R.id.loadPos).setEnabled(hasWayPoints);
		menu.findItem(R.id.deletePos).setEnabled(hasWayPoints);

		boolean hasLocation = getHasLocation();
		menu.findItem(R.id.savePos).setEnabled(hasLocation);
		menu.findItem(R.id.savePosAs).setEnabled(hasLocation);

		menu.findItem(R.id.calibration).setChecked(isCalibrationMode());
		menu.findItem(R.id.darkMode).setChecked(m_darkMode);

		int gpsInterval = getInterval();
		menu.findItem(R.id.autoGps).setChecked(gpsInterval==AUTO_GPS);
		menu.findItem(R.id.fastGps).setChecked(gpsInterval==FAST_GPS);
		menu.findItem(R.id.normalGps).setChecked(gpsInterval==NORMAL_GPS);
		menu.findItem(R.id.slowGps).setChecked(gpsInterval==SLOW_GPS);
		
		return super.onPrepareOptionsMenu(menu);
	}

	private void showAbout()
	{
		String name = getString(R.string.app_name);
		String version = getString(R.string.app_version);
		String copyright = getString(R.string.app_copyright);
		String url = getString(R.string.app_url);
		showMessage(
				name,
				name + " "+version+"\n"+copyright+"\n"+url,
				false
		);
	}
	private void savePosAs()
	{
		Location lastLocation;
		if(isCalibrationMode())
		{
			lastLocation = getCalibratedLocation("GPS");
		}
		else
		{
			lastLocation = getLastLocation();
		}

		if (lastLocation != null)
		{
			savePositionAs(lastLocation);
		}
	}
	private void savePos()
	{
		Location lastLocation = getLastLocation();
		if (lastLocation != null)
		{
			m_home = lastLocation;
			onLocationChanged(lastLocation);
		}
	}
	private void saveGpx()
	{
		int itemsSaved = 0;
		String target="Public";
		try
		{
			itemsSaved = saveWaypointFile(true);
		}
		catch( Exception e )
		{
			String str=e.toString();
			System.out.println(str);
			try
			{
				target="Private";
				itemsSaved = saveWaypointFile(false);
			}
			catch( Exception e2 )
			{
				String str2=e2.toString();
				System.out.println(str2);
			}
		}
		String name = getString(R.string.app_name);
		showMessage(
				name,
				itemsSaved+" items saved to " + target,
				false
		);
	}
	private void loadGpx()
	{
		int itemsLoaded = 0;
		String source="Public";
		try
		{
			itemsLoaded = loadWaypointFile(true);
		}
		catch( Exception e)
		{
			String str=e.toString();
			System.out.println(str);
			try
			{
				itemsLoaded = loadWaypointFile(false);
				source="Private";
			}
			catch( Exception e2 )
			{
				String str2=e2.toString();
				System.out.println(str2);
			}
		}
		String name = getString(R.string.app_name);
		showMessage(
				name,
				itemsLoaded+" items loaded from " + source,
				false
		);
	}
	private void calibration()
	{
		if( isCalibrationMode() )
		{
			removeGpsTimer();
			disableCalibartion();
		}
		else
		{
			createGpsTimer(NORMAL_GPS);
			enableCalibartion();
		}
	}

	@Override
    public boolean onOptionsItemSelected( MenuItem item )
    {
    	int	itemId = item.getItemId();
    	System.out.println( itemId );
    	if( itemId == R.id.loadPos )
		{
            selectPosition(SelectorMode.LOAD_POS);
        }
        else if( itemId == R.id.deletePos )
		{
            selectPosition(SelectorMode.DELETE_POS);
        }
        else if( itemId == R.id.savePosAs )
		{
			savePosAs();
    	}
        else if( itemId == R.id.savePos )
		{
			savePos();
    	}
        else if( itemId == R.id.saveGpx )
		{
			saveGpx();
    	}
        else if( itemId == R.id.loadGpx )
		{
			loadGpx();
    	}
        else if( itemId == R.id.calibration )
		{
			calibration();
        }
        else if( itemId == R.id.autoGps )
		{
            removeGpsTimer();
        }
        else if( itemId == R.id.fastGps )
		{
            createGpsTimer(FAST_GPS);
        }
        else if( itemId == R.id.normalGps )
		{
            createGpsTimer(NORMAL_GPS);
        }
        else if( itemId == R.id.slowGps )
		{
            createGpsTimer(SLOW_GPS);
        }
        else if( itemId == R.id.darkMode )
		{
            m_darkMode = !m_darkMode;
            switchColorMode();
        }
        else if( itemId ==  R.id.exit )
		{
            finish();
        }
        else if( itemId == R.id.about )
		{
			showAbout();
    	}

    	return super.onOptionsItemSelected(item);
    }

    @Override
    public void onOptionsMenuClosed(Menu menu)
	{
        super.onOptionsMenuClosed(menu);
		// Workaround for https://issuetracker.google.com/issues/315761686
		invalidateOptionsMenu();
    }

    private File getExternalFileName( boolean pub )
    {
        File dir = pub 
        		? Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS) 
        		: getExternalFilesDir(null);

        System.out.println(dir.getPath());
        if( !dir.exists() )
        {
        	dir.mkdir();
        }
        File file = new File(dir, pub ? s_filenameExternalPublic : s_filenameExternalPrivate);
        System.out.println(file.getPath());
        
        return file;
    }

    private int loadWaypointFile(boolean pub) throws Exception
    {
    	int itemsLoaded = 0;
        //Checking the availability state of the External Storage.
        String state = Environment.getExternalStorageState();
        if (!Environment.MEDIA_MOUNTED.equals(state)) 
        {
            //If it isn't mounted - we can't write into it.
            System.out.println("Not mounted");
            return 0;
        }

        File file = getExternalFileName(pub);

        if( !file.exists() )
        {
        	throw new FileNotFoundException( file.getPath() );
        }
        FileInputStream inputStream = new FileInputStream(file);
        Reader reader = new InputStreamReader ( inputStream );
        BufferedReader buffer = new BufferedReader ( reader );
    	SharedPreferences.Editor editor = m_waypoints.edit();

        while( true )
        {
        	String text = buffer.readLine();
        	if( text == null )
        	{
        		break;
        	}
        	Location loc = locationString(text);
        	Bundle bundle = loc.getExtras();
        	String name = bundle.getString(NAME_KEY);
            editor.putString(name, locationString(loc) );
            ++itemsLoaded;
        }
        editor.apply();
        buffer.close();
        
        return itemsLoaded;
    }

	private int saveWaypointFile(boolean pub) throws Exception
   	{
		int itemsSaved=0;
        //Checking the availability state of the External Storage.
        String state = Environment.getExternalStorageState();
        if (!Environment.MEDIA_MOUNTED.equals(state)) 
        {
            //If it isn't mounted - we can't write into it.
            System.out.println("Not mounted");
            return 0;
        }

        //Create a new file that points to the root directory, with the given name:
        File file = getExternalFileName(pub);

        //This point and below is responsible for the write operation
        FileOutputStream outputStream;

        file.createNewFile();
        outputStream = new FileOutputStream(file, false);

    	Map<String,?> map = m_waypoints.getAll();
    	Set<String> keys = map.keySet();
    	for( String key : keys )
    	{
            outputStream.write(map.get(key).toString().getBytes());
            outputStream.write('|');
            outputStream.write(key.getBytes());
            outputStream.write(13);
            ++itemsSaved;
    	}

        outputStream.flush();
        outputStream.close();
        
        return itemsSaved;
    }
    
    private void saveSharedPreferences()
    {
    	SharedPreferences settings = getSharedPreferences(CONFIGURATION_FILE, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = settings.edit();

        editor.putString(HOME_KEY, locationString(m_home) );
        editor.putString(LAST_NAME_KEY, m_lastName);
        editor.putBoolean(DARK_MODE_KEY, m_darkMode);
        editor.putInt(GPS_SPEED_KEY, getInterval() );

		// Commit the edits!
        editor.apply();
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
        super.onDestroy();
    }
	
	@Override
	protected void  onSaveInstanceState (Bundle outState)
	{
		super.onSaveInstanceState(outState);
		outState.putString(HOME_KEY, locationString(m_home));
		outState.putString(LAST_NAME_KEY, m_lastName);

		outState.putInt(GPS_SPEED_KEY, getInterval());
		outState.putBoolean(DARK_MODE_KEY, m_darkMode);
	}
	
	// correction valid for Linz/Austria
	static private int getCorrectedAltitude( Location loc )
	{
		return (int)loc.getAltitude()-50;
	}
	static void setCorrectedAltitude( Location loc, double altitude )
	{
		loc.setAltitude(altitude+50);
	}
	
	private void showLocation( Location newLocation )
	{
		float	distance=0;
		if(isCalibrationMode())
		{
			Location calibLocation = getCalibratedLocation(newLocation.getProvider());
			distance = calibLocation.distanceTo(newLocation);
		}
		int snapedAltitude = getCorrectedAltitude(newLocation);

		double longitude = newLocation.getLongitude();
		double latitude = newLocation.getLatitude();
		double altitude = (int)newLocation.getAltitude();
		StringBuilder sb = new StringBuilder().append(isCalibrationMode() ? "*" : " ")
				.append(snapedAltitude).append("m (").append((int) (altitude+0.5)).append(")/")
				.append(longitude).append('/')
				.append(latitude);
		if( distance > 0 )
			sb.append('/').append((int)(distance+0.5)).append('m');
		m_altitudeView.setText( sb.toString() );
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
			text + ' ' + s_accuracyFormat.format(getAccuracy()) + ' ' + getLocationFixCount() + '/' + getNumLocations()
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
	public void onGnssStatusChanged2(int event, GnssStatus status)
	{
		if( event == GPS_EVENT_STARTED )
			setStatus( "GPS gestartet");
		else if( event == GPS_EVENT_STOPPED )
			setStatus( "GPS gestoppt");
		else if( event == GPS_EVENT_FIRST_FIX )
			setStatus( "GPS erster Fix");
		else if( event == GPS_EVENT_SATELLITE_STATUS  )
		{
			int Satellites = status.getSatelliteCount();
			int SatellitesInFix = 0;

			for (int i = 0; i < Satellites; i++)
			{
				if(status.usedInFix(i))
				{
					SatellitesInFix++;
				}
			}

			setStatus( "GPS Satelliten: " + SatellitesInFix + "/" + Satellites );
		}
	}

	@Override
	public void onLocationChanged( Location newLocation )
    {
    	setStatus( m_myStatus );
    	{
    		final double absHomeBearing = newLocation.bearingTo(m_home);
    		showMovement( 
    			getSpeed(), 
    			m_home.distanceTo(newLocation), m_home.getAltitude()-newLocation.getAltitude(), 
    			absHomeBearing, getCurBearing() 
    		);
    	}

		showLocation(newLocation);
    }

	@Override
	public void onPermissionError() {
		showMessage("GpsWayPoints", "Berechtigung für Standort fehlt!", true);
	}


}