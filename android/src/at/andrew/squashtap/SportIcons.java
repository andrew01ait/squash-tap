package at.andrew.squashtap;

import android.graphics.*;
import android.graphics.drawable.Drawable;

/** Small, code-native sport marks; no font or emoji support required on the watch. */
public final class SportIcons extends Drawable {
    public static int accent(String sport){
        switch(sport){case "squash":return 0xffffb377;case "tennis":return 0xffb4e775;case "badminton":return 0xff79d9ee;case "padel":return 0xffc9a2ff;case "table-tennis":return 0xffffd36e;case "pickleball":return 0xff70e2c1;default:return 0xffcbd5e1;}
    }
    private final String sport;private final int size;private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
    public SportIcons(String sport,int size){this.sport=sport;this.size=size;}
    @Override public int getIntrinsicWidth(){return size;}@Override public int getIntrinsicHeight(){return size;}
    @Override public void draw(Canvas c){c.save();Rect b=getBounds();c.translate(b.left,b.top);c.scale(b.width()/24f,b.height()/24f);p.setColor(accent(sport));p.setStrokeWidth(1.6f);p.setStrokeCap(Paint.Cap.ROUND);p.setStyle(Paint.Style.STROKE);
        if(sport.equals("badminton")){Path path=new Path();path.moveTo(5,3);path.lineTo(19,3);path.lineTo(15,15);path.lineTo(9,15);path.close();c.drawPath(path,p);c.drawLine(9,3,11,15,p);c.drawLine(15,3,13,15,p);c.drawOval(9,15,15,21,p);}
        else if(sport.equals("table-tennis")){p.setStyle(Paint.Style.FILL);c.drawOval(4,2,17,15,p);c.drawRoundRect(9,13,13,22,1,1,p);p.setColor(0xff86ddf5);c.drawCircle(20,17,2,p);}
        else if(sport.equals("pickleball")){c.drawRoundRect(4,2,16,15,4,4,p);c.drawLine(9,15,9,22,p);c.drawLine(12,15,12,22,p);c.drawCircle(20,18,3,p);p.setStyle(Paint.Style.FILL);for(int i=0;i<4;i++)c.drawCircle(19+(i%2)*2,17+(i/2)*2,.5f,p);}
        else if(sport.equals("padel")){c.drawRoundRect(5,2,18,16,5,5,p);c.drawLine(10,16,10,22,p);c.drawLine(13,16,13,22,p);p.setStyle(Paint.Style.FILL);for(int x=8;x<18;x+=4)for(int y=6;y<15;y+=4)c.drawCircle(x,y,.8f,p);}
        else{c.save();c.rotate(30,12,12);c.drawOval(6,1,18,15,p);c.drawLine(12,15,12,23,p);for(int y=5;y<=12;y+=3)c.drawLine(8,y,16,y,p);c.drawLine(10,3,10,13,p);c.drawLine(14,3,14,13,p);c.restore();if(sport.equals("squash")){c.drawLine(2,2,2,14,p);c.drawCircle(20,19,1.3f,p);}else{p.setColor(0xffb4e775);p.setStyle(Paint.Style.FILL);c.drawCircle(20,18,3,p);}}
        c.restore();}
    @Override public void setAlpha(int alpha){p.setAlpha(alpha);}@Override public void setColorFilter(ColorFilter filter){p.setColorFilter(filter);}@Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
}
