/**
 * 
 */
package at.gaeckler.GpsWayPoints;

import java.text.DecimalFormat;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

/**
 * @author gak
 *
 */

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
	private double m_currentSpeed = 0;
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

	private void initKompass()
	{
		m_kompassPaint = new Paint();
		m_kompassPaint.setARGB(255, 0, 0, 0);
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
		m_homeNeedlePaint.setARGB(255, 255, 0, 0);

		m_wayNeedlePaint = new Paint();
		m_wayNeedlePaint.set( m_homeNeedlePaint );
		m_wayNeedlePaint.setARGB(255, 0, 255, 0);

		m_speedPaint = new Paint();
		m_speedPaint.set( m_labelPaint );
		m_speedPaint.setARGB(255, 0, 0, 255);
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
		
		final double bearingRad = bearingDeg/180.0*Math.PI; 
		return bearingRad;
	}
	private KompassPos getCirclePosForBearing( double bearingDeg )
	{
		final double bearingRAD = getAngleRad( bearingDeg );
		
		KompassPos pos = new KompassPos( Math.cos( bearingRAD ), Math.sin( bearingRAD ));
		
		return pos;
	}
	private KompassPos transferToScreen( KompassPos pos, double factor )
	{
		factor *= m_kompassRadius;
		
		pos.xPos *= factor;
		pos.yPos *= factor;
		
		pos.xPos += m_centerX;
		pos.yPos += m_centerY;
		
		pos.yPos = m_kompassHeight-pos.yPos;
		
		return pos;
	}
	private KompassPos getCirclePosForBearing( double bearingDEG, double factor )
	{
		KompassPos pos = getCirclePosForBearing( bearingDEG );
		pos = transferToScreen( pos, factor );

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
		double textOffset = m_labelPaint.getTextSize()/2.0;
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


    	textOffset = m_speedPaint.getTextSize();
    	canvas.drawText(s_speedFormat.format(m_currentSpeed), (float)m_centerX, (float)(m_centerY+textOffset), m_speedPaint);

    	textOffset += m_labelPaint.getTextSize();
    	canvas.drawText(
			s_totalDistanceFormat.format(m_distanceDM)+'/'+
			s_totalDistanceFormat.format(m_distanceHM), 
			(float)m_centerX, (float)(m_centerY+textOffset), m_labelPaint
    	);
    }

	public void showMovement( double newSpeed, int distanceDM, int distanceHM, double absHomeBearing, double currBearing )
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
}
