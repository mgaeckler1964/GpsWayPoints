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

import static java.lang.Double.MAX_VALUE;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.location.GnssStatus;
import android.location.Location;
import android.os.Bundle;
import android.os.Environment;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatDelegate;

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

	private boolean					m_darkMode = false;

	private GpsWayPointsWidget		m_theRose = null;
	private TextView				m_statusView = null;
	private TextView				m_altitudeView = null;
	private TextView				m_waypointNameView = null;
	
	private String					m_myStatus = null;

	String 							m_lastName = null;			// default access
	Location						m_home = new Location("");	// default access
	SharedPreferences 				m_waypoints = null;			// default access

	public void showMessage( String message, final boolean terminate, DialogCallback callback )
	{
		String title = getString(R.string.app_name);
		showMessage( R.drawable.icon, title, message, terminate, callback );
	}
	public void showError( String title, String message )
	{
		showMessage( R.drawable.error, title, message, false, null );
	}
	public void showMessage( String message )
	{
		String title = getString(R.string.app_name);
		showMessage( R.drawable.icon, title, message, false, null );
	}

	private void switchColorMode()
	{
		if( m_darkMode )
		{
			AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
			m_theRose.useBlackBackground();
		}
		else
		{
			AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
			m_theRose.useWhiteBackground();
		}
	}

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		if( !checkLocationPermission() )
		{
			return;
		}

		if( requestStoragePermission(R.drawable.icon, "GPS-Waypoints") == RequestCode.rcDenied )
		{
			return;
		}

		m_waypoints = getSharedPreferences(WAYPOINTS_FILE, Context.MODE_PRIVATE);

		SharedPreferences settings = getSharedPreferences(CONFIGURATION_FILE, Context.MODE_PRIVATE);
		String homeStr = settings.getString(HOME_KEY,"");
		m_lastName = settings.getString(LAST_NAME_KEY,"");
		m_darkMode = settings.getBoolean(DARK_MODE_KEY,false);
		int gpsInterval = settings.getInt(GPS_SPEED_KEY,0);

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
		m_myStatus = getString(R.string.welcome);
		setStatus( m_myStatus );
		m_theRose = findViewById( R.id.myRose );
		m_altitudeView = findViewById( R.id.altitudeView );
		m_waypointNameView = findViewById( R.id.waypointNameView );

		System.out.println("showSpeed");
		clearRose();

		updateWaypointName();
		//simulateLocationFix(m_home);
		switchColorMode();
	}

	private boolean savePositionAs(
		Location lastLocation,
		EditText positionName,
		EditText positionLongitude,
	 	EditText positionLatitude,
		EditText positionAltitude
	)
	{
		boolean ok = false;
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
							String longitudestyle = getString(R.string.positionLongitude);
							showError( longitudestyle, getString(R.string.invalidRange3, longitudestyle, -180, 180));
							return false;
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
							String latitudestyle = getString(R.string.positionLatitude);
							showError( latitudestyle, getString(R.string.invalidRange3, latitudestyle, -90, 90));
							return false;
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
							String altitudeLabel = getString(R.string.altitudeLabel);
							showError( altitudeLabel, getString(R.string.invalidRange3, altitudeLabel, -11000, 9000));
							return false;
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

				ok = true;
			}
			onLocationChanged(lastLocation);
		}
		catch (NumberFormatException e)
		{
			String unknownStyle = getString(R.string.unknown);
			showError( unknownStyle, getString(R.string.invalidRange1, unknownStyle));
			ok = false;
		}
		return ok;
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

		alertDialog.setButton(DialogInterface.BUTTON_POSITIVE, getText(R.string.okLabel), (DialogInterface.OnClickListener)null );
		alertDialog.setButton(DialogInterface.BUTTON_NEGATIVE, getText(R.string.cancelLabel), (DialogInterface.OnClickListener)null );

		alertDialog.setView(view);
		alertDialog.show();

		Button okButton = alertDialog.getButton(DialogInterface.BUTTON_POSITIVE );
		okButton.setOnClickListener((v) ->
		{
			boolean success = savePositionAs(
					lastLocation,
					positionName,
					positionLongitude,
					positionLatitude,
					positionAltitude
			);
			if(success)
			{
				alertDialog.dismiss();
			}
		});
	}
	
	private enum SelectorMode { LOAD_POS, DELETE_POS }

	// Simple helper class to hold paired data
	static final private class PositionItem
	{
		String name;
		double distance;
		PositionItem(String name, double distance)
		{
			this.name = name;
			this.distance = distance;
		}
	}

	private Map<String, Double> getDistanceMap( Location current )
	{
		Map<String, Double>	result = new HashMap<>();

		for( Map.Entry<String, ?> entry : m_waypoints.getAll().entrySet() )
		{
			boolean ok = false;
			Object value = entry.getValue();
			if( value instanceof String locationStr )
			{
				Location loc = locationString( locationStr );
				if( loc != null )
				{
					result.put( entry.getKey(), (double)current.distanceTo(loc) );
					ok = true;
				}
			}
			if( !ok )
			{
				result.put( entry.getKey(), Double.MAX_VALUE );
			}
		}
		return result;
	}

	private void selectPosition( final SelectorMode mode )
	{
		// build the dialog
		LayoutInflater layoutInflater = getLayoutInflater();
		final View view = layoutInflater.inflate(R.layout.select_position, null);
		final AlertDialog alertDialog = new AlertDialog.Builder(this).create();

		alertDialog.setTitle(getString((mode == SelectorMode.LOAD_POS) ? R.string.loadWayPoint : R.string.deleteWayPoint));

		alertDialog.setIcon(R.drawable.icon);
		alertDialog.setCancelable(true);
		alertDialog.setMessage(getString(R.string.selectWayPoint));

		// load the way points
		Map<String,?> map = m_waypoints.getAll();
		Set<String> keys = map.keySet();
		final ArrayList<String> myArray = new ArrayList<>(keys);

		final ListView positionList = view.findViewById(R.id.positionList);
		Location lastLocation = getLastLocation();
		if( lastLocation != null )
		{
			Map<String, Double>	distMap = getDistanceMap(lastLocation);
			List<PositionItem> items = myArray.stream()
				.map(key -> new PositionItem(
					key,
					distMap.get(key)
				))
				.sorted(Comparator.comparingDouble(item -> item.distance))
				.collect(Collectors.toList())
			;

			myArray.clear();
			List<String> displayStrings = new ArrayList<>();
			for(PositionItem item : items)
			{
				myArray.add(item.name);
				if( item.distance < MAX_VALUE )
				{
					displayStrings.add(String.format("%s (%dm)", item.name, (int) item.distance));
				}
				else
				{
					displayStrings.add(String.format("%s (---)", item.name));
				}
			}

			ArrayAdapter<String> adapter = new ArrayAdapter<>(
				this,
				R.layout.select_position,
				R.id.positionListItem,
				displayStrings
			);
			positionList.setAdapter(adapter);
		}
		else
		{
			Collections.sort(myArray);

			// fill the list view
			ArrayAdapter<String> adapter = new ArrayAdapter<>(
				this,
				R.layout.select_position,
				R.id.positionListItem,
				myArray
			);
			positionList.setAdapter(adapter);
		}

		// configure the click handler
		OnItemClickListener messageClickedHandler = (parent, v, listViewPosition, id) ->
		{
			// Do something in response to the click.
			String viewItem = myArray.get(listViewPosition);
			if( mode == SelectorMode.DELETE_POS)
			{
				String message = getString(R.string.confirmDelete, viewItem);
				showMessage( message, false, okClicked ->
				{
					if( okClicked )
					{
						alertDialog.dismiss();
						m_waypoints.edit().remove(viewItem).apply();
					}
				});

			}
			else if( mode == SelectorMode.LOAD_POS)
			{
				alertDialog.dismiss();
				m_lastName = viewItem;
				updateWaypointName();
				m_home = locationString(m_waypoints.getString(viewItem, ""));
				Location last = getLastLocation();
				if(last != null)                    // do we have a GPS-fix?
				{
					onLocationChanged(last);        // update the display
				}
			}
		};
		positionList.setOnItemClickListener(messageClickedHandler);

		// configure the cancel button
		alertDialog.setButton(DialogInterface.BUTTON_NEGATIVE, getText(R.string.cancelLabel), (DialogInterface.OnClickListener)null );

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
		showMessage( name + " "+version+"\n"+copyright+"\n"+url );
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
		String message = getString(R.string.itemsSaved, itemsSaved, target);
		showMessage( message );
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
		String message = getString(R.string.itemsLoaded, itemsLoaded, source);
		showMessage( message );
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

		editor.apply();
	}

	@Override
	public void onPause()
	{
		/*
		 	if location permission check failed we did not load the last settings
		 	=> we do not have any usefull data to save and I don't want to overwrite
		 	the last settings with the default values.
		 */
		if( m_theRose != null )
		{
			saveSharedPreferences();
		}

		super.onPause();
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
		int		snapedAltitude = getCorrectedAltitude(newLocation);
		double	longitude = newLocation.getLongitude();
		double	latitude = newLocation.getLatitude();
		double	altitude = (int)newLocation.getAltitude();
		m_altitudeView.setText(
			(isCalibrationMode() ? "*" : " ") +
			snapedAltitude + "m (" + (int)(altitude+0.5) + ")/" +
			longitude + '/' + latitude
		);
	}
	
	private void updateRose(double speed, double distanceDM, double distanceHM, double absHomeBearing, double currBearing )
	{
		m_theRose.showMovement(
			GpsProcessor.speedToKmh(speed),
			(int)(distanceDM+0.5), (int)(distanceHM+0.5),
			absHomeBearing, currBearing
		);
	}
	void clearRose()
	{
		m_theRose.clearMovementDisplay();
	}

	void setStatus( String text )
	{
		m_myStatus = text;
		if(m_statusView != null)
		{
			m_statusView.setText( getString(
				R.string.accuracy_format,
				text,
				getAccuracy(),
				getLocationFixCount(),
				getNumLocations()
			));
		}
	}

	void updateWaypointName()
	{
		m_waypointNameView.setText(m_lastName);
	}

	@Override
	public void onLocationEnabled()
	{
		setStatus( getString(R.string.gpsEnabled) );
	}

	@Override
	public void onLocationDisabled()
	{
		setStatus( getString(R.string.gpsDisabled) );
		clearRose();
	}
	
	@Override
	public void onGnssStatusChanged2(int event, GnssStatus status)
	{
		if(event == GPS_EVENT_STARTED)
		{
			setStatus(getString(R.string.gpsStarted));
		}
		else if(event == GPS_EVENT_STOPPED)
		{
			setStatus(getString(R.string.gpsStoped));
		}
		else if(event == GPS_EVENT_FIRST_FIX)
		{
			setStatus(getString(R.string.gpsFirstFix));
		}
		else if(event == GPS_EVENT_SATELLITE_STATUS)
		{
			int Satellites = status.getSatelliteCount();
			int SatellitesInFix = 0;

			for(int i = 0; i < Satellites; i++)
			{
				if(status.usedInFix(i))
				{
					SatellitesInFix++;
				}
			}

			setStatus(getString(R.string.gpsSatellites2,SatellitesInFix, Satellites) );
		}
	}

	@Override
	public void onLocationChanged( Location newLocation )
	{
		setStatus( m_myStatus );
		final double absHomeBearing = newLocation.bearingTo(m_home);

		float distance;
		double distanceHM;
		if(isCalibrationMode())
		{
			Location calibLocation = getCalibratedLocation(newLocation.getProvider());
			distance = calibLocation.distanceTo(newLocation);
			distanceHM = calibLocation.getAltitude()-newLocation.getAltitude();
		}
		else
		{
			distance = newLocation.distanceTo(m_home);
			distanceHM = m_home.getAltitude()-newLocation.getAltitude();
		}

		updateRose(
			getSpeed(),
			distance, distanceHM,
			absHomeBearing, getCurBearing()
		);

		showLocation(newLocation);
	}
}