/*
		Project:		GpsWayPoints
		Module:			GpsWayPointsWidget.java
		Description:	The widget with the navigation kompass
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

import java.text.DecimalFormat;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

class KompassPos
{
	public double xPos, yPos;
	
	KompassPos( double newX, double newY )
	{
		xPos = newX;
		yPos = newY;
	}
}

public class GpsWayPointsWidget extends View
{
	private int m_kompassWidth = 0, m_kompassHeight = 0;
	private double m_centerX = 0, m_centerY = 0, m_kompassRadius = 0;
	private long m_currentSpeed = 0;
	private double m_absHomeBearing = 0;
	private double m_currBearing = 0;
	private int m_distanceDM = 0;
	private int m_distanceHM = 0;
	private Paint m_kompassPaint = null;
	private Paint m_labelPaint = null;
	private Paint m_homeNeedlePaint = null;
	private Paint m_wayNeedlePaint = null;
	private Paint m_speedPaint = null;
	private static final DecimalFormat s_speedFormat = new DecimalFormat( "0.0 km/h" );
	private static final DecimalFormat s_totalDistanceFormat = new DecimalFormat( ",##0" );

	private static final int blackColor = 0xFF000000;
	private static final int whiteColor = 0xFFFFFFFF;
	private static int s_backGroundCol = whiteColor;
	private static int s_foreGroundCol = blackColor;
	private static final int homeNeedleCol = 0xFFFF0000;
	private static final int wayNeedleCol = 0xFF00FF00;
	private static final int speedCol = 0xFF0000FF;
	private void initKompass()
	{
		setBackgroundColor(s_backGroundCol);
		m_kompassPaint = new Paint();
		m_kompassPaint.setColor(s_foreGroundCol);
		m_kompassPaint.setStyle(Paint.Style.STROKE);
		m_kompassPaint.setTextAlign(Paint.Align.CENTER);
		m_kompassPaint.setAntiAlias( true );

		m_labelPaint = new Paint();
		m_labelPaint.set( m_kompassPaint );
		m_labelPaint.setStyle(Paint.Style.FILL);

		m_homeNeedlePaint = new Paint();
		m_homeNeedlePaint.set( m_kompassPaint );
		m_homeNeedlePaint.setStrokeWidth(20);
		m_homeNeedlePaint.setStrokeCap(Paint.Cap.ROUND);
		m_homeNeedlePaint.setColor(homeNeedleCol);

		m_wayNeedlePaint = new Paint();
		m_wayNeedlePaint.set( m_homeNeedlePaint );
		m_wayNeedlePaint.setColor(wayNeedleCol);

		m_speedPaint = new Paint();
		m_speedPaint.set( m_labelPaint );
		m_speedPaint.setColor(speedCol);
		
		if( m_kompassRadius > 0 )
		{
			m_labelPaint.setTextSize((float)(m_kompassRadius * 0.1));
			m_speedPaint.setTextSize((float)(m_kompassRadius * 0.25));
		}

	}

	private double getAngleRad( double bearingDeg )
	{
		bearingDeg = -bearingDeg + 90;
		
		while( bearingDeg > 180) {
			bearingDeg -= 360;
		}
		while( bearingDeg < -180) {
			bearingDeg += 360;
		}
		
		return bearingDeg/180.0*Math.PI;
	}
	private KompassPos getCirclePosForBearing( double bearingDeg )
	{
		final double bearingRAD = getAngleRad( bearingDeg );
		
		return new KompassPos( Math.cos( bearingRAD ), Math.sin( bearingRAD ));
	}
	private void transferToScreen( KompassPos pos, double factor )
	{
		factor *= m_kompassRadius;
		
		pos.xPos *= factor;
		pos.yPos *= factor;
		
		pos.xPos += m_centerX;
		pos.yPos += m_centerY;
		
		pos.yPos = m_kompassHeight-pos.yPos;
	}

	private KompassPos getCirclePosForBearing( double bearingDEG, double factor )
	{
		KompassPos pos = getCirclePosForBearing( bearingDEG );
		transferToScreen( pos, factor );

		return pos;
	}

	public GpsWayPointsWidget(Context context)
	{
		super(context);
		initKompass();
	}

	public GpsWayPointsWidget(Context context, AttributeSet attrs) 
	{
		super(context, attrs);
		initKompass();
	}

	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec)
	{
		m_kompassWidth = MeasureSpec.getSize(widthMeasureSpec);
		m_kompassHeight = MeasureSpec.getSize(heightMeasureSpec);
		m_centerX = m_kompassWidth/2;
		m_centerY = m_kompassHeight/2;
		m_kompassRadius = Math.min( m_centerX, m_centerY);

		m_labelPaint.setTextSize((float)(m_kompassRadius * 0.1));
		m_speedPaint.setTextSize((float)(m_kompassRadius * 0.25));

		setMeasuredDimension( m_kompassWidth, m_kompassHeight );
	}

	@Override
	protected void onDraw(Canvas canvas)
	{
		super.onDraw(canvas);
		// canvas.drawLine( 0, 0, (float)centerX, (float)centerY, circlePaint);
		canvas.drawCircle( (float)m_centerX, (float)m_centerY, (float)m_kompassRadius, m_kompassPaint);

		{
			final KompassPos needlePos = getCirclePosForBearing( m_absHomeBearing, 1 );
			canvas.drawLine(
				(float)needlePos.xPos, (float)needlePos.yPos,
				(float)m_centerX, (float)m_centerY,
				m_homeNeedlePaint
			);
		}
		{
			final KompassPos needlePos = getCirclePosForBearing( m_currBearing, 0.5 );
			canvas.drawLine(
				(float)needlePos.xPos, (float)needlePos.yPos,
				(float)m_centerX, (float)m_centerY,
				m_wayNeedlePaint
			);
		}

		double textOffset = m_speedPaint.getTextSize();
		canvas.drawText(s_speedFormat.format(m_currentSpeed), (float)m_centerX, (float)(m_centerY+textOffset), m_speedPaint);

		textOffset += m_labelPaint.getTextSize();
		canvas.drawText(
			s_totalDistanceFormat.format(m_distanceDM)+'/'+
			s_totalDistanceFormat.format(m_distanceHM), 
			(float)m_centerX, (float)(m_centerY+textOffset), m_labelPaint
		);
	}

	public void showMovement( long newSpeed, int distanceDM, int distanceHM, double absHomeBearing, double currBearing )
	{
		m_currentSpeed = newSpeed;
		m_distanceDM = distanceDM;
		m_distanceHM = distanceHM;
		m_absHomeBearing = absHomeBearing;
		m_currBearing = currBearing;
		invalidate();
	}
	public void clearMovementDisplay()
	{
		m_currentSpeed = 0;
		m_distanceDM = 0;
		m_distanceHM = 0;
		m_absHomeBearing = 0;
		m_currBearing = 0;
		invalidate();
	}
	public void useBlackBackground()
	{
		s_backGroundCol = blackColor;
		s_foreGroundCol = whiteColor;
		initKompass();
		invalidate();
	}
	public void useWhiteBackground()
	{
		s_backGroundCol = whiteColor;
		s_foreGroundCol = blackColor;
		initKompass();
		invalidate();
	}
}
