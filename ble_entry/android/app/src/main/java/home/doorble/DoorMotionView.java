package home.doorble;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.*;
import android.view.View;
import android.view.animation.LinearInterpolator;

/** Vector motion stays crisp, follows the theme and respects disabled system animations. */
public class DoorMotionView extends View {
    public static final int TAP=0, CODE=1, NEARBY=2, FACE=3;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final int ink,accent;
    private final ValueAnimator clock=ValueAnimator.ofFloat(0,1);
    private int method;
    private float phase;
    private boolean complete,animate=true;
    public DoorMotionView(Context context,int method,int ink,int accent){
        super(context);this.method=method;this.ink=ink;this.accent=accent;
        clock.setDuration(2600);clock.setRepeatCount(ValueAnimator.INFINITE);
        clock.setInterpolator(new LinearInterpolator());
        clock.addUpdateListener(a->{phase=(float)a.getAnimatedValue();invalidate();});
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }
    public void complete(boolean value){complete=value;invalidate();}
    public void animate(boolean value){animate=value;if(value&&isAttachedToWindow()&&ValueAnimator.areAnimatorsEnabled())clock.start();else clock.cancel();}
    @Override protected void onAttachedToWindow(){super.onAttachedToWindow();animate(animate);}
    @Override protected void onDetachedFromWindow(){clock.cancel();super.onDetachedFromWindow();}
    @Override protected void onWindowVisibilityChanged(int visibility){super.onWindowVisibilityChanged(visibility);if(clock==null)return;if(visibility==VISIBLE&&animate&&isAttachedToWindow()&&ValueAnimator.areAnimatorsEnabled())clock.start();else clock.cancel();}
    private void stroke(Canvas c,float width,int color){paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(width);paint.setStrokeCap(Paint.Cap.ROUND);paint.setStrokeJoin(Paint.Join.ROUND);paint.setColor(color);}
    private void fill(int color){paint.setStyle(Paint.Style.FILL);paint.setColor(color);}
    private void line(Canvas c,float x,float y,float xx,float yy){c.drawLine(x,y,xx,yy,paint);}
    @Override protected void onDraw(Canvas original){
        super.onDraw(original);Canvas c=original;int saved=c.save();float size=Math.min(getWidth(),getHeight());
        c.translate((getWidth()-size)/2,(getHeight()-size)/2);c.scale(size/200,size/200);
        float wave=(float)Math.sin(phase*Math.PI*2);
        fill(accent);c.drawCircle(100,100,79+wave*2,paint);
        if(complete&&method==TAP){
            stroke(c,7,ink);c.drawRoundRect(68,86,132,139,14,14,paint);
            c.drawArc(79,55,122,108,175,205,false,paint);
            fill(ink);c.drawCircle(101,108,4,paint);
            stroke(c,5,ink);line(c,137,56,147,67);line(c,147,67,165,43);
        }else if(method==TAP){
            stroke(c,5,ink);c.drawRoundRect(64,68,136,137,22,22,paint);
            c.drawArc(81,47,119,99,180,180,false,paint);
            fill(ink);c.drawCircle(100,99,5,paint);c.drawRoundRect(97,100,103,116,3,3,paint);
            stroke(c,3,ink);float r=49+phase*24;paint.setAlpha((int)(110*(1-phase)));c.drawCircle(100,101,r,paint);paint.setAlpha(255);
        }else if(method==CODE){
            stroke(c,5,ink);c.drawArc(41,41,159,159,-90,phase*330+25,false,paint);
            fill(ink);paint.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));paint.setTextAlign(Paint.Align.CENTER);paint.setTextSize(25);
            c.drawText("•••",100,92,paint);c.drawText("•••",100,121,paint);
            fill(ink);double a=(phase*330-65)*Math.PI/180;c.drawCircle(100+(float)Math.cos(a)*59,100+(float)Math.sin(a)*59,5,paint);
        }else if(method==NEARBY){
            stroke(c,3,ink);for(int i=0;i<3;i++){float p=(phase+i/3f)%1;paint.setAlpha((int)((1-p)*130));c.drawCircle(100,100,35+p*48,paint);}paint.setAlpha(255);
            fill(accent);c.drawRoundRect(79,63,121,137,10,10,paint);stroke(c,5,ink);c.drawRoundRect(79,63,121,137,10,10,paint);line(c,94,126,106,126);
            line(c,96,82,107,94);line(c,107,94,95,105);line(c,101,79,101,109);
        }else{
            stroke(c,5,ink);c.drawRoundRect(57,49,143,148,35,35,paint);
            float look=wave*4;
            if(complete||phase>.91){line(c,77,91,87,91);line(c,112,91,122,91);}else{fill(ink);c.drawOval(78+look,80,86+look,96,paint);c.drawOval(112+look,80,120+look,96,paint);}
            stroke(c,4,ink);c.drawArc(82,99,118,126,15,150,false,paint);
            stroke(c,3,ink);line(c,40,57,40,40);line(c,40,40,57,40);line(c,143,40,160,40);line(c,160,40,160,57);
            line(c,40,143,40,160);line(c,40,160,57,160);line(c,143,160,160,160);line(c,160,160,160,143);
        }
        if(complete&&method!=TAP){fill(ink);c.drawCircle(149,145,23,paint);stroke(c,4,accent);line(c,138,144,146,152);line(c,146,152,161,136);}
        c.restoreToCount(saved);
    }
}
