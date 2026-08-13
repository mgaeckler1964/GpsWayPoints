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
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.location.GnssStatus;
import android.location.Location;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
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

import org.osmdroid.api.IMapController;
import org.osmdroid.config.Configuration;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Polyline;
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider;
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay;

import at.gaeckler.gps.GpsActivity;
import at.gaeckler.gps.GpsProcessor;
import at.gaeckler.gps.GpsService;
import at.gaeckler.gps.GpsUtils;

public class GpsWayPointsActivity extends GpsActivity
{
	private static final String CONFIGURATION_FILE = "GpsWayPoints.cfg";
	private static final String WAYPOINTS_FILE = "GpsWayPoints.gwp";
	private static final String	HOME_KEY = "homePosition";
	private static final String	GPS_SPEED_KEY = "gpsInterval";
	private static final String	LAST_NAME_KEY = "lastName";
	private static final String	DARK_MODE_KEY = "darkMode";
	private static final String	MAP_MODE_KEY = "mapMode";
	private static final String FOLLOW_POS_KEY = "followPos";

	private static final String s_filenameExternalPublic = "gpsWayPointsPub.txt";
	private static final String s_filenameExternalPrivate = "gpsWayPointsPriv.txt";
	private static final String s_filenameExternalGpxWayPoints = "gpxWayPoints.gpx";

	private boolean					m_darkMode = false;

	private GpsWayPointsWidget		m_theRose = null;
	private TextView				m_statusView = null;
	private TextView				m_altitudeView = null;
	private TextView				m_waypointNameView = null;
	
	private String					m_myStatus = null;

	private String 					m_lastName = null;
	private Location				m_home = new Location("");
	private SharedPreferences 		m_waypoints = null;
	private boolean 				m_showMap = false;
	private boolean 				m_followPos = false;

	private void calibration()
	{
		GpsService myService = getService();
		if( myService != null )
		{
			if(myService.getCalibration())
			{
				myService.disableCalibration();
			}
			else
			{
				myService.enableCalibration();
				myService.createGpsTimer(GpsService.NORMAL_GPS);
			}
		}
	}

	private void saveGpxTrack()
	{
		try
		{
			getService().getGpsLogger().createGpxTrack();
		}
		catch(IOException e)
		{
			// ignore
		}
	}

	private void trackGps()
	{
		GpsService service = getService();
		if( service != null && isGpsEnabled() )
		{
			if(checkWriteStoragePermission())
			{
				if(service.getGpsLogger().getTrackGps())
				{
					saveGpxTrack();
					getService().updateNotification(getString(R.string.app_name), getString(R.string.notificationMsg), getClass());
				}
				else
				{
					service.getGpsLogger().startTrack();
					getService().updateNotification(getString(R.string.app_name), getString(R.string.gpsTrackMsg), getClass());
				}
			}
			else
			{
				service.getGpsLogger().stopTrack();
			}
		}
	}

	/*
		--------------------------------------------------------------------------------------------
			The OSM Map Display
		--------------------------------------------------------------------------------------------
	 */
	private MapView m_mapView = null;

	private void toggleView(boolean showMap)
	{
		m_showMap = showMap;
		if (showMap)
		{
			m_theRose.setVisibility(View.GONE);
			m_mapView.setVisibility(View.VISIBLE);
			//initOsmDroid(); // Karte initialisieren falls nötig
		}
		else
		{
			m_mapView.setVisibility(View.GONE);
			m_theRose.setVisibility(View.VISIBLE);
		}
	}

	// ...
	private void createMarker(Location location, String title)
	{
		if(m_mapView == null || location == null)
		{
			return;
		}

		GeoPoint point = new GeoPoint(location.getLatitude(), location.getLongitude());

		Marker marker = new Marker(m_mapView);
		marker.setTitle(title);
		marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
//			m_locationMarker.setIcon(getResources().getDrawable(R.drawable.icon)); // Dein Icon nutzen
		m_mapView.getOverlays().add(marker);

		// Position aktualisieren
		marker.setPosition(point);

		m_mapView.invalidate();
	}

	private void displayWaypoints()
	{
		Map<String,?> map = m_waypoints.getAll();
		Set<String> keys = map.keySet();
		for( String key : keys )
			createMarker(GpsUtils.locationString(m_waypoints.getString(key, "")), key);
	}

	private void scrollToLocation( Location location )
	{
		IMapController mapController = m_mapView.getController();
		mapController.setCenter(
				new GeoPoint(location.getLatitude(), location.getLongitude())
		);
	}

	private void zoomToLocation(Location location, double zoomLevel )
	{
		IMapController mapController = m_mapView.getController();
		mapController.setZoom(zoomLevel);
		scrollToLocation(location);
	}

	private MyLocationNewOverlay m_locationOverlay;
	private Polyline m_trackLine = null;

	/*
		--------------------------------------------------------------------------------------------
			The Activity Lifecycle
		--------------------------------------------------------------------------------------------
	 */
	private void saveSharedPreferences()
	{
		SharedPreferences settings = getSharedPreferences(CONFIGURATION_FILE, Context.MODE_PRIVATE);
		SharedPreferences.Editor editor = settings.edit();

		editor.putString(HOME_KEY, GpsUtils.locationString(m_home) );
		editor.putString(LAST_NAME_KEY, m_lastName);
		editor.putBoolean(DARK_MODE_KEY, m_darkMode);
		editor.putBoolean(MAP_MODE_KEY, m_showMap);
		editor.putBoolean(FOLLOW_POS_KEY, m_followPos);

		if(isServiceBound())
		{
			editor.putInt(GPS_SPEED_KEY, getService().getInterval());
		}

		editor.apply();
	}

	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		if( !checkLocationPermission() )
		{
			return;
		}

		if( requestStoragePermission(R.drawable.icon, "GPS-Waypoints") == RequestCode.DENIED )
		{
			return;
		}

		m_waypoints = getSharedPreferences(WAYPOINTS_FILE, Context.MODE_PRIVATE);

		SharedPreferences settings = getSharedPreferences(CONFIGURATION_FILE, Context.MODE_PRIVATE);
		String homeStr = settings.getString(HOME_KEY,"");
		m_lastName = settings.getString(LAST_NAME_KEY,"");
		m_darkMode = settings.getBoolean(DARK_MODE_KEY,false);
		m_showMap = settings.getBoolean(MAP_MODE_KEY,false);
		m_followPos = settings.getBoolean(FOLLOW_POS_KEY,false);

		Location tmpLocation = GpsUtils.locationString(homeStr);
		if( tmpLocation != null )
		{
			m_home = tmpLocation;
		}
		else
		{
			m_home.setLongitude(14.282733);
			m_home.setLatitude(48.298820);
			GpsUtils.setCorrectedAltitude(m_home, 260);
		}
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

		Configuration.getInstance().setUserAgentValue(getPackageName());

		setContentView(R.layout.main);

		m_statusView = findViewById( R.id.statusView );
		m_myStatus = getString(R.string.welcome);
		setStatus( m_myStatus );
		m_theRose = findViewById( R.id.myRose );
		m_altitudeView = findViewById( R.id.altitudeView );
		m_waypointNameView = findViewById( R.id.waypointNameView );
		clearRose();

		m_mapView = findViewById(R.id.mapView);
		m_locationOverlay = new MyLocationNewOverlay(new GpsMyLocationProvider(this), m_mapView);
		m_locationOverlay.enableMyLocation();
		m_locationOverlay.setDrawAccuracyEnabled(true);
		m_mapView.getOverlays().add(m_locationOverlay);

		m_trackLine = new Polyline();
		m_trackLine.getOutlinePaint().setColor(Color.BLUE); // Farbe der Linie
		m_trackLine.getOutlinePaint().setStrokeWidth(5.0f); // Dicke der Linie
		m_mapView.getOverlays().add(m_trackLine);

		zoomToLocation(m_home, 18.5);
		displayWaypoints();


		updateWaypointName(m_lastName);
		switchColorMode();
		toggleView(m_showMap);
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

		if(m_mapView != null)
		{
			m_mapView.onPause();
		}
		super.onPause();
	}

	@Override
	public void onResume()
	{
		super.onResume();
		if(m_mapView != null)
		{
			m_mapView.onResume();
		}
	}

	/*
		--------------------------------------------------------------------------------------------
			The Activity Interface
		--------------------------------------------------------------------------------------------
	 */
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

		boolean gpsEnabled = isGpsEnabled();
		boolean hasLocation = gpsEnabled && hasLocation();
		menu.findItem(R.id.savePos).setEnabled(hasLocation);
		menu.findItem(R.id.savePosAs).setEnabled(hasLocation);

		boolean hasWritePermission = checkWriteStoragePermission();
		menu.findItem(R.id.trackGps).setEnabled(hasWritePermission&&gpsEnabled);

		menu.findItem(R.id.calibration).setEnabled(gpsEnabled);
		menu.findItem(R.id.calibration).setChecked(getCalibration());
		menu.findItem(R.id.darkMode).setChecked(m_darkMode);

		menu.findItem(R.id.gpsSpeed).setEnabled(gpsEnabled);
		if( gpsEnabled )
		{
			int gpsInterval = getInterval();
			menu.findItem(R.id.autoGps).setChecked(gpsInterval == GpsService.AUTO_GPS);
			menu.findItem(R.id.fastGps).setChecked(gpsInterval == GpsService.FAST_GPS);
			menu.findItem(R.id.normalGps).setChecked(gpsInterval == GpsService.NORMAL_GPS);
			menu.findItem(R.id.slowGps).setChecked(gpsInterval == GpsService.SLOW_GPS);
			menu.findItem(R.id.trackGps).setChecked(getTrackGps());
		}

		menu.findItem(R.id.selectPublicFolder).setChecked( !hasStorageFolder() && hasWritePermission );
		menu.findItem(R.id.selectStorageFolder).setChecked( hasStorageFolder() && hasWritePermission );

		if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
		{
			menu.findItem(R.id.displayStorageManagePermission).setChecked(checkIsExternalStorageManager());
		}
		else
		{
			menu.findItem(R.id.displayStorageManagePermission).setVisible(false);
		}
		menu.findItem(R.id.mapLabel).setChecked(m_showMap);
		menu.findItem(R.id.followPos).setChecked(m_followPos);
		menu.findItem(R.id.followPos).setEnabled(m_showMap);

		return super.onPrepareOptionsMenu(menu);
	}

	@Override
	public boolean onOptionsItemSelected( MenuItem item )
	{
		int	itemId = item.getItemId();

		if( itemId == R.id.loadPos )
		{
			selectWayPoint(SelectorMode.LOAD_POS);
		}
		else if( itemId == R.id.deletePos )
		{
			selectWayPoint(SelectorMode.DELETE_POS);
		}
		else if( itemId == R.id.savePosAs )
		{
			savePosAs();
		}
		else if( itemId == R.id.saveHomeAs )
		{
			saveHomeAs();
		}
		else if( itemId == R.id.savePos )
		{
			saveHome();
		}

		else if( itemId == R.id.displayStorageManagePermission )
		{
			displayStorageManagePermission();
		}
		else if(itemId == R.id.selectStorageFolder)
		{
			selectStorageFolder();
		}
		else if(itemId == R.id.selectPublicFolder)
		{
			selectPublicFolder();
			requestStoragePermission(R.drawable.icon, "GPS-Waypoints");
		}

		else if( itemId == R.id.trackGps )
		{
			trackGps();
		}
		else if( itemId == R.id.saveWPT )
		{
			saveWPT();
		}
		else if( itemId == R.id.loadWPT )
		{
			loadWPT();
		}

		else if( itemId == R.id.calibration )
		{
			calibration();
		}
		else if( itemId == R.id.autoGps )
		{
			getService().removeGpsTimer();
		}
		else if( itemId == R.id.fastGps )
		{
			getService().createGpsTimer(GpsService.FAST_GPS);
		}
		else if( itemId == R.id.normalGps )
		{
			getService().createGpsTimer(GpsService.NORMAL_GPS);
		}
		else if( itemId == R.id.slowGps )
		{
			getService().createGpsTimer(GpsService.SLOW_GPS);
		}
		else if( itemId == R.id.darkMode )
		{
			m_darkMode = !m_darkMode;
			switchColorMode();
		}
		else if( itemId ==  R.id.exit )
		{
			saveGpxTrack();
			stopGpsService();
			finish();
		}
		else if( itemId == R.id.about )
		{
			showAbout();
		}
		else if( itemId == R.id.mapLabel )
		{
			toggleView(!m_showMap);
		}
		else if( itemId == R.id.followPos )
		{
			m_followPos = !m_followPos;
		}



		return super.onOptionsItemSelected(item);
	}

	@Override
	protected void onNotificationClick()
	{
		trackGps();
	}

	@Override
	public void onOptionsMenuClosed(Menu menu)
	{
		super.onOptionsMenuClosed(menu);
		// Workaround for https://issuetracker.google.com/issues/315761686
		invalidateOptionsMenu();
	}


	/*
		--------------------------------------------------------------------------------------------
			The Service Management
		--------------------------------------------------------------------------------------------
	 */
	private void updateNotification()
	{
		GpsService	service = getService();
		if( service != null )
		{
			if(!service.isGpsEnabled())
			{
				service.updateNotification(getString(R.string.app_name), getString(R.string.gpsDisabled), getClass());
			}
			else if(service.getGpsLogger().getTrackGps())
			{
				service.updateNotification(getString(R.string.app_name), getString(R.string.gpsTrackMsg), getClass());
			}
			else
			{
				service.updateNotification(getString(R.string.app_name), getString(R.string.notificationMsg), getClass());
			}
		}
	}

	@Override
	protected void onConfigureService()
	{
		super.onConfigureService();

		SharedPreferences	settings = getSharedPreferences(CONFIGURATION_FILE, Context.MODE_PRIVATE);
		GpsService			service = getService();
		int					gpsInterval = settings.getInt(GPS_SPEED_KEY,0);

		service.createGpsTimer(gpsInterval);
		simulateLocationFix(m_home);
		updateNotification();
		if( m_trackLine != null )
		{
			List<Location> savedPoints = service.getTrackPoints();
			if (!savedPoints.isEmpty())
			{
				for( Location loc : savedPoints )
				{
					m_trackLine.addPoint(new GeoPoint(loc.getLatitude(), loc.getLongitude()));
				}
				m_mapView.invalidate();
			}
		}
	}


	/*
		--------------------------------------------------------------------------------------------
			The Way Points
		--------------------------------------------------------------------------------------------
	 */
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
						double longitude = parseInternationalDouble(homeLongitude);
						if (longitude < -180 || longitude > 180 )
						{
							String longitudeLabel = getString(R.string.longitudeLabel);
							showError( longitudeLabel, getString(R.string.invalidRange3, longitudeLabel, -180, 180));
							return false;
						}
						m_home.setLongitude(longitude);
					}
				}

				{
					String homeLatitude = positionLatitude.getText().toString();
					if( !homeLatitude.isEmpty() )
					{
						double latitude = parseInternationalDouble(homeLatitude);
						if (latitude < -90 || latitude > 90 )
						{
							String latitudeLabel = getString(R.string.latitudeLabel);
							showError( latitudeLabel, getString(R.string.invalidRange3, latitudeLabel, -90, 90));
							return false;
						}
						m_home.setLatitude(latitude);
					}
				}

				{
					String homeAltitude = positionAltitude.getText().toString();
					if( !homeAltitude.isEmpty() )
					{
						double altitude = parseInternationalDouble(homeAltitude);
						if (altitude < -11000 || altitude > 9000 )
						{
							String altitudeLabel = getString(R.string.altitudeLabel);
							showError( altitudeLabel, getString(R.string.invalidRange3, altitudeLabel, -11000, 9000));
							return false;
						}
						GpsUtils.setCorrectedAltitude( m_home, altitude );
					}
				}

				String homeStr = GpsUtils.locationString(m_home);

				SharedPreferences.Editor editor = m_waypoints.edit();
				editor.putString(homeName, homeStr );
				editor.apply();

				updateWaypointName(homeName);

				ok = true;
			}

			onLocationChanged(lastLocation);
		}
		catch( NumberFormatException e )
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
		alertDialog.setTitle(getString(R.string.savePos));
		alertDialog.setIcon(R.drawable.icon);
		alertDialog.setCancelable(false);
		alertDialog.setMessage(getString(R.string.enterName));

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

	private void saveHomeAs()
	{
		savePositionAs(m_home);
	}

	private enum SelectorMode { LOAD_POS, DELETE_POS }

	// Simple helper class to hold paired
	private record PositionItem(String name, double distance) {}

	private Map<String, Double> getDistanceMap( Location current )
	{
		Map<String, Double>	result = new HashMap<>();

		for( Map.Entry<String, ?> entry : m_waypoints.getAll().entrySet() )
		{
			boolean ok = false;
			Object value = entry.getValue();
			if( value instanceof String locationStr )
			{
				Location loc = GpsUtils.locationString( locationStr );
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

	private void selectWayPoint(final SelectorMode mode )
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
		Location lastLocation = lastLocation();
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
					displayStrings.add(String.format(Locale.getDefault(), "%s (%dm)", item.name, (int) item.distance));
				}
				else
				{
					displayStrings.add(String.format(Locale.getDefault(),"%s (---)", item.name));
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
				updateWaypointName(viewItem);
				m_home = GpsUtils.locationString(m_waypoints.getString(viewItem, ""));
				Location last = lastLocation();
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

	private void savePosAs()
	{
		Location lastLocation;
		if(getCalibration())
		{
			lastLocation = getService().getCalibratedLocation("GPS");
		}
		else
		{
			lastLocation = lastLocation();
		}

		if (lastLocation != null)
		{
			savePositionAs(lastLocation);
		}
	}

	private void saveHome()
	{
		Location lastLocation = lastLocation();
		if (lastLocation != null)
		{
			m_home = lastLocation;
			onLocationChanged(lastLocation);
		}
	}

	private void saveWPT()
	{
		int itemsSaved = 0;
		String target="Public";
		try
		{
			saveGpxWPT();
			itemsSaved = saveWaypointFile(true);
			if(hasStorageFolder())
			{
				target = getString(R.string.selectStorageFolder);
			}
		}
		catch( Exception e )
		{
			Log.e(getLocalClassName(), "Saving public file failed", e);
			try
			{
				target="Private";
				itemsSaved = saveWaypointFile(false);
			}
			catch( Exception e2 )
			{
				Log.e(getLocalClassName(), "Saving private file failed", e2);
			}
		}
		String message = getString(R.string.itemsSaved, itemsSaved, target);
		showMessage( message );
	}

	private void loadWPT()
	{
		String	error1 = null;
		String	error2 = null;
		int		itemsLoaded = 0;
		String	source="Public";
		try
		{
			itemsLoaded = loadWPT2(true);
			if(hasStorageFolder())
			{
				source = getString(R.string.selectStorageFolder);
			}
		}
		catch( Exception e)
		{
			error1=e.toString();
			try
			{
				itemsLoaded = loadWPT2(false);
				source="Private";
			}
			catch( Exception e2 )
			{
				if(e instanceof FileNotFoundException)
				{
					error1 = getString(R.string.fileNotFound);
					error2 = error1;
				}
				else
				{
					error2 = e2.toString();
				}
			}
		}
		if( error2 != null )
		{
			if( !error1.equals(error2) )
			{
				error1 = error1 + '\n' + error2;
			}
			showError(getString(R.string.error), error1);
		}
		else
		{
			String message = getString(R.string.itemsLoaded, itemsLoaded, source);
			showMessage(message);
		}
	}

	private int loadWPT2(boolean pub) throws Exception
	{
		int itemsLoaded = 0;
		//Checking the availability state of the External Storage.
		String state = Environment.getExternalStorageState();
		if (!Environment.MEDIA_MOUNTED.equals(state))
		{
			//If it isn't mounted - we can't write into it.
			Log.e(getLocalClassName(), "Not mounted");
			return 0;
		}

		try(
				InputStream		is = getService().getGpsLogger().openInputStream( pub, pub ? s_filenameExternalPublic : s_filenameExternalPrivate);
				Reader			reader = new InputStreamReader( is );
				BufferedReader	buffer = new BufferedReader( reader )
		)
		{
			SharedPreferences.Editor editor = m_waypoints.edit();

			while(true)
			{
				String text = buffer.readLine();
				if(text == null)
				{
					break;
				}
				Location loc = GpsUtils.locationString(text);
				Bundle bundle = loc.getExtras();
				String name = bundle.getString(GpsUtils.NAME_KEY);
				editor.putString(name, GpsUtils.locationString(loc));
				++itemsLoaded;
			}
			editor.apply();
		}

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
			Log.e(getLocalClassName(), "Not mounted");
			return 0;
		}

		{
			try( OutputStream  outputStream = getService().getGpsLogger().openOutputStream( pub, pub ? s_filenameExternalPublic : s_filenameExternalPrivate, false) )
			{
				Map<String, ?> map = m_waypoints.getAll();
				Set<String> keys = map.keySet();
				for(String key : keys)
				{
					outputStream.write(map.get(key).toString().getBytes());
					outputStream.write('|');
					outputStream.write(key.getBytes());
					outputStream.write(13);
					++itemsSaved;
				}
			}
		}
		return itemsSaved;
	}

	private void saveGpxWPT() throws Exception
	{
		//Checking the availability state of the External Storage.
		String state = Environment.getExternalStorageState();
		if (!Environment.MEDIA_MOUNTED.equals(state))
		{
			//If it isn't mounted - we can't write into it.
			Log.e(getLocalClassName(), "Not mounted");
			return;
		}

		try (OutputStream os = getService().getGpsLogger().openOutputStream(true, s_filenameExternalGpxWayPoints, false);
			 PrintWriter writer = new PrintWriter(os))
		{
			writer.println("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\" ?>");
			writer.println("<gpx xmlns=\"http://www.topografix.com/GPX/1/1\" creator=\"" + getLocalClassName() + "\" version=\"1.1\">");
			writer.println("<metadata>");
			writer.println("<name>" + s_filenameExternalGpxWayPoints + "</name>");
			writer.println("<desc>Gpx Created with " + getLocalClassName() + " for Android</desc>");
			writer.println("<author><name>Martin Gäckler</name></author>");
			writer.println("</metadata>");

			Map<String, ?> map = m_waypoints.getAll();
			Set<String> keys = map.keySet();
			for(String key : keys)
			{
				Location loc = GpsUtils.locationString(m_waypoints.getString(key, ""));
				if(loc != null)
				{
					writer.write("<wpt lon=\"");
					writer.print(loc.getLongitude());
					writer.write("\" lat=\"");
					writer.print(loc.getLatitude());
					writer.println("\">");
					writer.write("\t<ele>");
					writer.print(GpsUtils.getCorrectedAltitude(loc));
					writer.println("</ele>");
					writer.write("\t<geoidheight>");
					writer.print(loc.getAltitude());
					writer.println("</geoidheight>");
					writer.write("\t<name>");
					writer.print(key);
					writer.println("</name>");
					writer.println("</wpt>");
				}
			}
			writer.println("</gpx>");
		}
	}

	void updateWaypointName( String theName )
	{
		m_lastName = theName;
		m_waypointNameView.setText(theName);
	}

	/*
		--------------------------------------------------------------------------------------------
			UI Elements
		--------------------------------------------------------------------------------------------
	 */
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

	private void showAbout()
	{
		String name = getString(R.string.app_name);
		String version = getString(R.string.app_version);
		String copyright = getString(R.string.app_copyright);
		String url = getString(R.string.app_url);
		showMessage( name + " "+version+"\n"+copyright+"\n"+url );
	}

	@SuppressLint("SetTextI18n")
	private void showLocation( Location newLocation )
	{
		int		snapedAltitude = GpsUtils.getCorrectedAltitude(newLocation);
		double	longitude = newLocation.getLongitude();
		double	latitude = newLocation.getLatitude();
		double	altitude = newLocation.getAltitude();
		m_altitudeView.setText(
			(getCalibration() ? "*" : " ") +
			snapedAltitude + "m (" + (int)(altitude+0.5) + ")/" +
			String.format(Locale.getDefault(), "%.6f/%.6f",longitude,latitude)
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
		if(m_statusView != null )
		{
			if(isServiceBound() && isGpsEnabled())
			{
				GpsService service = getService();
				m_statusView.setText(getString(
						R.string.accuracy_format,
						text,
						service.getAccuracy(),
						service.getLocationFixCount(),
						service.getNumLocations()
				));
			}
			else
			{
				m_statusView.setText(text);
			}
		}
	}

	/*
		--------------------------------------------------------------------------------------------
			GPS Events
		--------------------------------------------------------------------------------------------
	 */
	@Override
	protected void onLocationEnabled()
	{
		setStatus( getString(R.string.gpsEnabled) );
		updateNotification();
	}

	@Override
	protected void onLocationDisabled()
	{
		setStatus( getString(R.string.gpsDisabled) );
		clearRose();
		updateNotification();
	}
	
	@Override
	protected void onGnssStatusChanged2(int event, GnssStatus status)
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
	protected void onLocationChanged(Location newLocation)
	{
		setStatus( m_myStatus );
		final double absHomeBearing = newLocation.bearingTo(m_home);

		float distance;
		double distanceHM;
		if(getCalibration())
		{
			Location calibLocation = getService().getCalibratedLocation(newLocation.getProvider());
			distance = calibLocation.distanceTo(newLocation);
			distanceHM = calibLocation.getAltitude()-newLocation.getAltitude();
		}
		else
		{
			distance = newLocation.distanceTo(m_home);
			distanceHM = m_home.getAltitude()-newLocation.getAltitude();
		}

		if (m_trackLine != null)
		{
			m_trackLine.addPoint(new GeoPoint(newLocation.getLatitude(), newLocation.getLongitude()));

			if (getService() != null)
			{
				getService().addTrackPoint(newLocation);
			}
			m_mapView.invalidate();
		}

		if( m_showMap )
		{
			if(m_followPos)
			{
				scrollToLocation(newLocation);
			}
		}
		else
		{
			updateRose(
				getSpeed(),
				distance, distanceHM,
				absHomeBearing, getCurBearing()
			);
		}
		showLocation(newLocation);
	}
}